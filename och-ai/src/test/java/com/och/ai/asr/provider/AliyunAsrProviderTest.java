package com.och.ai.asr.provider;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AsrSessionState;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阿里云 NLS 实时 ASR 协议单测（方案 7.8）：验证就绪判定、token URL、StartTranscriber 首帧
 * 与 SentenceEnd/TranscriptionResultChanged 解析（真实联调由凭证就绪后的集成测试覆盖）。
 */
class AliyunAsrProviderTest {

    private AsrProperties propsWithCreds() {
        AsrProperties p = new AsrProperties();
        p.setSampleRate(16000);
        p.setPunctuation(true);
        p.setConvertDigit(true);
        p.getAliyun().setEnabled(true);
        p.getAliyun().setAppKey("test-appkey");
        p.getAliyun().setAccessToken("test-token");
        return p;
    }

    private AsrSessionContext ctx() {
        AsrSessionContext c = new AsrSessionContext();
        c.setSessionId("asr-2");
        c.setCallId(2L);
        c.setUniqueId("uuid-2");
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
    void readyRequiresAppKeyAndToken() {
        assertTrue(new AliyunAsrProvider(propsWithCreds()).ready());
        AsrProperties blank = new AsrProperties();
        blank.getAliyun().setEnabled(true);
        assertFalse(new AliyunAsrProvider(blank).ready());
    }

    @Test
    void buildUriContainsToken() throws Exception {
        AliyunAsrProvider provider = new AliyunAsrProvider(propsWithCreds());
        URI uri = provider.buildUri(ctx());
        assertTrue(uri.toString().startsWith("wss://nls-gateway.aliyuncs.com/ws/v1"));
        assertTrue(uri.toString().contains("token=test-token"));
    }

    @Test
    void startFrameContainsStartTranscriber() {
        AliyunAsrProvider provider = new AliyunAsrProvider(propsWithCreds());
        String frame = provider.buildStartFrame(ctx());
        assertTrue(frame.contains("\"name\":\"StartTranscriber\""), "首帧应为 StartTranscriber");
        assertTrue(frame.contains("\"appkey\":\"test-appkey\""));
        assertTrue(frame.contains("\"enable_punctuation\":true"));
        assertTrue(frame.contains("\"sample_rate\":16000"));
    }

    @Test
    void parseSentenceEndAndChanged() {
        AliyunAsrProvider provider = new AliyunAsrProvider(propsWithCreds());
        AsrSessionContext ctx = ctx();
        Capturing cb = new Capturing();
        provider.parseTextMessage(ctx, cb,
                "{\"header\":{\"name\":\"TranscriptionResultChanged\",\"status\":20000000}," +
                        "\"payload\":{\"result\":\"你好\",\"sentence_id\":1,\"confidence\":0.8}}");
        provider.parseTextMessage(ctx, cb,
                "{\"header\":{\"name\":\"SentenceEnd\",\"status\":20000000}," +
                        "\"payload\":{\"result\":\"你好世界\",\"sentence_id\":1,\"confidence\":0.9}}");

        assertEquals(2, cb.results.size());
        assertFalse(cb.results.get(0).getSegment().isFinalResult(), "Changed 应为增量");
        assertTrue(cb.results.get(1).getSegment().isFinalResult(), "SentenceEnd 应为最终");
        assertEquals("你好世界", cb.results.get(1).getSegment().getText());
    }
}
