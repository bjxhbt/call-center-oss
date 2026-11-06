package com.och.ai.asr.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.och.ai.asr.domain.query.TranscriptQuery;
import com.och.ai.asr.domain.vo.TranscriptSummaryVo;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.entity.CallTranscriptSegment;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.Speaker;
import com.och.ai.asr.testsupport.H2AsrDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转写持久化真实 DB 测试（方案 11.2 P0 / 10.3）：用 H2 验证摘要/段落的
 * 创建、幂等、追加全文、状态终结、按 callId/号码/坐席查询与分页。
 *
 * <p>不触碰生产 KingbaseES（H2 内存库，测试结束即销毁）。</p>
 */
class AsrTranscriptPersistenceTest {

    private static H2AsrDb db;

    @BeforeAll
    static void setup() throws Exception {
        db = H2AsrDb.create();
    }

    @AfterAll
    static void teardown() {
        if (db != null) {
            db.close();
        }
    }

    private AsrSessionContext ctx(long callId) {
        AsrSessionContext c = new AsrSessionContext();
        c.setSessionId("asr-" + callId);
        c.setCallId(callId);
        c.setUniqueId("uuid-" + callId);
        c.setCaller("13800000000");
        c.setCallee("1000");
        c.setAgentId(1000L);
        c.setAgentNumber("1000");
        c.setAgentName("张三");
        c.setDirection(2);
        c.setProvider(AsrProviderType.MOCK);
        c.setSampleRate(16000);
        c.setCodec(AudioCodec.PCMA);
        return c;
    }

    private AsrSegment seg(String segmentId, int seq, String text, Speaker speaker, boolean fin) {
        return AsrSegment.builder()
                .segmentId(segmentId).sequence(seq).text(text).finalResult(fin).speaker(speaker)
                .confidence(0.9).build();
    }

    @Test
    void summaryCreatedOnceAndIdempotent() {
        IAsrTranscriptService svc = db.service();
        CallTranscript t1 = svc.getOrCreateSummary(ctx(9001L));
        CallTranscript t2 = svc.getOrCreateSummary(ctx(9001L));
        assertNotNull(t1.getId());
        assertEquals(t1.getId(), t2.getId(), "同 callId 摘要幂等");
        assertEquals(IAsrTranscriptService.STATUS_RUNNING, t1.getStatus());
        assertEquals("9001", t1.getCallId());
    }

    @Test
    void appendFinalsBuildFullTextAndCount() {
        IAsrTranscriptService svc = db.service();
        CallTranscript t = svc.getOrCreateSummary(ctx(9002L));
        svc.appendFinalSegment("9002", seg("9002-1", 1, "您好，", Speaker.CUSTOMER, true), AsrProviderType.MOCK, t.getId());
        svc.appendFinalSegment("9002", seg("9002-2", 2, "请问账号是多少", Speaker.AGENT, true), AsrProviderType.MOCK, t.getId());

        CallTranscript after = svc.getByCallId("9002");
        assertEquals("您好，请问账号是多少", after.getFullText());
        assertEquals(2, after.getSegmentCount());
        assertEquals(after.getFullText().length(), after.getCharCount());
    }

    @Test
    void appendSameSegmentIdIsIdempotent() {
        IAsrTranscriptService svc = db.service();
        CallTranscript t = svc.getOrCreateSummary(ctx(9003L));
        boolean first = svc.appendFinalSegment("9003", seg("9003-1", 1, "你好", Speaker.CUSTOMER, true), AsrProviderType.MOCK, t.getId());
        boolean dup = svc.appendFinalSegment("9003", seg("9003-1", 1, "你好", Speaker.CUSTOMER, true), AsrProviderType.MOCK, t.getId());
        assertTrue(first);
        assertFalse(dup, "同 callId+segmentId 重复落库应被去重");
        List<CallTranscriptSegment> segs = svc.listSegments("9003");
        assertEquals(1, segs.size(), "只应有 1 行段落");
    }

    @Test
    void finalizeSummarySetsSuccessAndRecord() {
        IAsrTranscriptService svc = db.service();
        svc.getOrCreateSummary(ctx(9004L));
        svc.appendFinalSegment("9004", seg("9004-1", 1, "结束", Speaker.CUSTOMER, true), AsrProviderType.MOCK,
                svc.getByCallId("9004").getId());
        IAsrTranscriptService.FinalizeRequest req = IAsrTranscriptService.FinalizeRequest.of(IAsrTranscriptService.STATUS_SUCCESS);
        req.endMs = System.currentTimeMillis();
        req.recordUrl = "/recordings/9004.wav";
        svc.finalizeSummary("9004", req);

        CallTranscript t = svc.getByCallId("9004");
        assertEquals(IAsrTranscriptService.STATUS_SUCCESS, t.getStatus());
        assertEquals("/recordings/9004.wav", t.getRecordUrl(), "录音地址应保留（关联回放）");
        assertNotNull(t.getEndTime());
        assertNotNull(t.getDurationMs());
    }

    @Test
    void markErrorSetsFailedAndQuery() {
        IAsrTranscriptService svc = db.service();
        svc.getOrCreateSummary(ctx(9005L));
        svc.markError("9005", "ASR_004", "connection broken");
        CallTranscript t = svc.getByCallId("9005");
        assertEquals(IAsrTranscriptService.STATUS_FAILED, t.getStatus(), "错误应可查询");
        assertEquals("ASR_004", t.getErrorCode());
    }

    @Test
    void listSegmentsOrderedAndDetail() {
        IAsrTranscriptService svc = db.service();
        CallTranscript t = svc.getOrCreateSummary(ctx(9006L));
        svc.appendFinalSegment("9006", seg("9006-3", 3, "三", Speaker.CUSTOMER, true), AsrProviderType.MOCK, t.getId());
        svc.appendFinalSegment("9006", seg("9006-1", 1, "一", Speaker.CUSTOMER, true), AsrProviderType.MOCK, t.getId());
        svc.appendFinalSegment("9006", seg("9006-2", 2, "二", Speaker.CUSTOMER, true), AsrProviderType.MOCK, t.getId());

        List<CallTranscriptSegment> segs = svc.listSegments("9006");
        assertEquals(3, segs.size());
        assertEquals("一", segs.get(0).getText(), "应按 seq 升序");
        assertEquals("二", segs.get(1).getText());
        assertEquals("三", segs.get(2).getText());

        TranscriptSummaryVo detail = svc.getDetail(t.getId());
        assertNotNull(detail);
        assertEquals(3, detail.getSegments().size(), "详情应携带段落");
    }

    @Test
    void pageListFiltersByAgentAndNumber() {
        IAsrTranscriptService svc = db.service();
        // 9007 属坐席 1001 / 主叫 13900000000
        AsrSessionContext c = ctx(9007L);
        c.setAgentId(1001L);
        c.setAgentNumber("1001");
        c.setCaller("13900000000");
        svc.getOrCreateSummary(c);

        TranscriptQuery qAgent = new TranscriptQuery();
        qAgent.setAgentId(1001L);
        IPage<TranscriptSummaryVo> page = svc.pageList(qAgent);
        assertTrue(page.getRecords().stream().anyMatch(v -> "9007".equals(v.getCallId())));

        TranscriptQuery qNumber = new TranscriptQuery();
        qNumber.setCallerNumber("13900000000");
        assertTrue(svc.pageList(qNumber).getRecords().stream().anyMatch(v -> "9007".equals(v.getCallId())));

        TranscriptQuery qNone = new TranscriptQuery();
        // 用确实不存在的号码（注意 like 是子串匹配，避开 13900000000 自身包含的片段）
        qNone.setCallerNumber("77777777");
        assertNull(svc.pageList(qNone).getRecords().stream().filter(v -> "9007".equals(v.getCallId())).findFirst().orElse(null));
    }
}
