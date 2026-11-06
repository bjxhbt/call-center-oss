package com.och.ai.asr.provider;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AsrSessionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FunASR 本地 runtime 协议单测（方案 7.9）：验证就绪判定、2pass 首帧与
 * online/offline 结果解析（真实联调由本地服务就绪后的集成测试覆盖）。
 */
class FunAsrProviderTest {

    private AsrProperties props() {
        AsrProperties p = new AsrProperties();
        p.setSampleRate(16000);
        p.setConvertDigit(true);
        p.getFunasr().setEnabled(true);
        p.getFunasr().setWsUrl("ws://127.0.0.1:10095");
        p.getFunasr().setMode("2pass");
        return p;
    }

    private AsrSessionContext ctx() {
        AsrSessionContext c = new AsrSessionContext();
        c.setSessionId("asr-3");
        c.setCallId(3L);
        c.setUniqueId("uuid-3");
        c.setSampleRate(16000);
        c.setState(AsrSessionState.ACTIVE);
        c.setFirstAudioSentAt(System.currentTimeMillis());
        return c;
    }

    private static class Capturing implements AsrResultCallback {
        final List<AsrRecognitionResult> results = new ArrayList<>();
        public void onResult(AsrRecognitionResult r) { results.add(r); }
        public void onError(String code, String message) { }
        public void onClosed() { }
    }

    @Test
    void readyRequiresEnabledAndWsUrl() {
        assertTrue(new FunAsrProvider(props()).ready());
        AsrProperties blank = new AsrProperties();
        assertFalse(new FunAsrProvider(blank).ready(), "未启用应未就绪");
    }

    @Test
    void startFrameIs2passSpeaking() {
        FunAsrProvider provider = new FunAsrProvider(props());
        String frame = provider.buildStartFrame(ctx());
        assertTrue(frame.contains("\"mode\":\"2pass\""), "应为 2pass 模式");
        assertTrue(frame.contains("\"is_speaking\":true"), "应标记开始说话");
        assertTrue(frame.contains("\"wav_format\":\"pcm\""));
        assertTrue(frame.contains("\"audio_fs\":16000"));
    }

    @Test
    void endFrameSignalsStopSpeaking() {
        FunAsrProvider provider = new FunAsrProvider(props());
        String frame = provider.buildEndFrame(ctx());
        assertTrue(frame.contains("\"is_speaking\":false"));
    }

    @Test
    void parseOnlinePartialAndOfflineFinal() {
        FunAsrProvider provider = new FunAsrProvider(props());
        AsrSessionContext ctx = ctx();
        Capturing cb = new Capturing();
        provider.parseTextMessage(ctx, cb, "{\"mode\":\"2pass-online\",\"text\":\"你好\"}");
        provider.parseTextMessage(ctx, cb, "{\"mode\":\"2pass-offline\",\"text\":\"你好世界\"}");

        assertEquals(2, cb.results.size());
        assertFalse(cb.results.get(0).getSegment().isFinalResult(), "online 应为增量");
        assertTrue(cb.results.get(1).getSegment().isFinalResult(), "offline 应为最终");
        assertEquals("你好世界", cb.results.get(1).getSegment().getText());
    }
}
