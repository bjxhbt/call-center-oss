package com.och.ai.asr.session;

import com.alibaba.fastjson2.JSONObject;
import com.och.ai.asr.audio.AudioNormalizer;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.provider.MockAsrProvider;
import com.och.ai.asr.result.AsrResultHandler;
import com.och.ai.asr.push.AsrWsPushService;
import com.och.ai.asr.testsupport.H2AsrDb;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import com.och.websocket.service.IWebSocketService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 端到端一致性测试（方案 11.2 P0 前端与存储闭环 / 10.3 #2）：
 * 真实 Mock 流 → 真实结果处理 → 真实 WS 推送序列化 → H2 真实持久化 → 真实查询，
 * 断言四处文本、segmentId、callId 一致（仅网络层 IWebSocketService 为 Mock）。
 */
class AsrEndToEndConsistencyTest {

    private static H2AsrDb db;
    private static AsrSessionManager manager;
    private static IWebSocketService ws;
    private static final List<String> sentMessages = new ArrayList<>();

    private static final long CALL_ID = 9008L;
    private static final String AGENT_ID = "1000";

    @BeforeAll
    static void setup() throws Exception {
        db = H2AsrDb.create();
        AsrProperties props = new AsrProperties();
        props.setEnabled(true);
        props.setSampleRate(16000);
        props.setInputCodec(AudioCodec.PCM);
        props.getMock().setIntervalMs(10);
        props.setFinalWaitTimeoutMs(2000L);
        props.setMaxReconnect(2);

        ws = Mockito.mock(IWebSocketService.class);
        when(ws.getSessionId(AGENT_ID)).thenReturn("sess-1000");
        doAnswer(inv -> {
            sentMessages.add(inv.getArgument(1));
            return null;
        }).when(ws).sendMsg(anyString(), anyString());

        MockAsrProvider provider = new MockAsrProvider(props);
        AsrProviderRegistry registry = new AsrProviderRegistry(List.of(provider), props);
        registry.init();
        AudioNormalizer normalizer = new AudioNormalizer(props);
        AsrWsPushService push = new AsrWsPushService(ws);
        AsrResultHandler handler = new AsrResultHandler(push, db.service());
        manager = new AsrSessionManager(props, registry, normalizer, handler, push, db.service());
    }

    @AfterAll
    static void teardown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void fourWayTextSegmentIdCallIdConsistent() {
        manager.onBridge(CallBridgeEvent.builder()
                .callId(CALL_ID).uniqueId("u-9008").caller("13800000000").callee("1000")
                .agentId(1000L).direction(2).bridgeTime(System.currentTimeMillis()).build());

        for (int i = 0; i < 200; i++) {
            manager.onAudioFrame(CallAudioFrame.builder()
                    .callId(CALL_ID).uniqueId("u-9008").codec(AudioCodec.PCM).sampleRate(16000)
                    .channels(1).data(new byte[320]).sequence((long) i).build());
        }
        manager.onHangup(CallHangupEvent.builder().callId(CALL_ID).uniqueId("u-9008")
                .endTime(System.currentTimeMillis()).record("/recordings/9008.wav").build());

        // 1) WS 推送的最终文本（解析 TRANSCRIPT_FINAL 载荷）
        StringBuilder wsFinalText = new StringBuilder();
        List<String> wsSegmentIds = new ArrayList<>();
        for (String msg : sentMessages) {
            JSONObject outer = JSONObject.parseObject(msg);
            if (!"TRANSCRIPT_FINAL".equals(outer.getString("event"))) {
                continue;
            }
            JSONObject inner = JSONObject.parseObject(outer.getString("data"));
            assertEquals(String.valueOf(CALL_ID), inner.getString("callId"), "WS callId 应一致");
            wsFinalText.append(inner.getString("text"));
            wsSegmentIds.add(inner.getString("segmentId"));
        }
        assertTrue(wsFinalText.length() > 0, "应推送最终文本");

        // 2) DB 段落文本
        var dbSegments = db.service().listSegments(String.valueOf(CALL_ID));
        StringBuilder dbSegText = new StringBuilder();
        for (var s : dbSegments) {
            dbSegText.append(s.getText());
        }

        // 3) DB 摘要全文
        CallTranscript summary = db.service().getByCallId(String.valueOf(CALL_ID));
        String summaryText = summary.getFullText();

        // 4) 查询 VO
        var vo = db.service().getDetail(summary.getId());
        String voText = vo.getFullText();

        // 四处一致
        assertEquals(wsFinalText.toString(), dbSegText.toString(), "WS最终文本 == DB段落拼接");
        assertEquals(summaryText, dbSegText.toString(), "DB摘要全文 == DB段落拼接");
        assertEquals(voText, summaryText, "查询VO全文 == DB摘要全文");
        assertEquals(IAsrTranscriptService_STATUS_SUCCESS(summary), true);
        assertEquals(summary.getRecordUrl(), "/recordings/9008.wav", "录音地址应回填，关联回放/话单");

        // segmentId 一致：WS 推送的 segmentId 应能在 DB 段落中找到
        for (String sid : wsSegmentIds) {
            assertTrue(dbSegments.stream().anyMatch(s -> sid.equals(s.getSegmentId())),
                    "WS segmentId 应在 DB 中存在: " + sid);
        }
    }

    /** 从持久化层读取状态判断是否 SUCCESS（避免直接依赖内部常量字面量）。 */
    private static boolean IAsrTranscriptService_STATUS_SUCCESS(CallTranscript t) {
        return "SUCCESS".equals(t.getStatus());
    }
}
