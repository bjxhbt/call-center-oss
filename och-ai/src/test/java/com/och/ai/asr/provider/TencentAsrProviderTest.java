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
 * 腾讯云实时 ASR 协议单测（方案 7.7 / 文档 10.2 P0）：无网络/无凭证下验证
 * 就绪判定、鉴权 URL 签名、首帧构造与结果解析逻辑（真实联调由凭证就绪后的集成测试覆盖）。
 */
class TencentAsrProviderTest {

    private AsrProperties propsWithCreds() {
        AsrProperties p = new AsrProperties();
        p.setSampleRate(16000);
        p.setPunctuation(true);
        p.setConvertDigit(true);
        p.getTencent().setEnabled(true);
        p.getTencent().setAppId("1234567890");
        p.getTencent().setSecretId("test-secret-id");
        p.getTencent().setSecretKey("test-secret-key");
        p.getTencent().setEngineModelType("16k_zh");
        p.getTencent().setVoiceFormat(1);
        return p;
    }

    private AsrSessionContext ctx() {
        AsrSessionContext c = new AsrSessionContext();
        c.setSessionId("asr-1");
        c.setCallId(1L);
        c.setUniqueId("uuid-1");
        c.setSampleRate(16000);
        c.setState(AsrSessionState.ACTIVE);
        c.setFirstAudioSentAt(System.currentTimeMillis());
        return c;
    }

    private static class Capturing implements AsrResultCallback {
        final List<AsrRecognitionResult> results = new ArrayList<>();
        String errCode;

        public void onResult(AsrRecognitionResult r) { results.add(r); }
        public void onError(String code, String message) { errCode = code; }
        public void onClosed() { }
    }

    @Test
    void readyRequiresAllCredentials() {
        assertTrue(new TencentAsrProvider(propsWithCreds()).ready());
        AsrProperties blank = new AsrProperties();
        blank.getTencent().setEnabled(true); // 但无凭证
        assertFalse(new TencentAsrProvider(blank).ready(), "缺凭证应未就绪");
    }

    @Test
    void buildUriContainsSignatureAndConfig() throws Exception {
        TencentAsrProvider provider = new TencentAsrProvider(propsWithCreds());
        URI uri = provider.buildUri(ctx());
        String s = uri.toString();
        assertTrue(s.startsWith("wss://asr.cloud.tencent.com/asr/v2/1234567890?"), "URL 前缀应含 appid");
        assertTrue(s.contains("engine_model_type=16k_zh"), "应携带引擎模型");
        assertTrue(s.contains("voice_format=1"), "应携带 PCM 格式");
        assertTrue(s.contains("filter_punc=1"), "应开启标点");
        assertTrue(s.contains("convert_num_mode=1"), "应开启数字转换");
        assertTrue(s.contains("signature="), "必须含签名");
        // 日志脱敏：redact 隐藏 signature
        String redacted = provider.redact(uri);
        assertTrue(redacted.contains("signature=***"));
        assertFalse(redacted.matches("signature=[^*]"));
    }

    @Test
    void parseFinalAndPartial() {
        TencentAsrProvider provider = new TencentAsrProvider(propsWithCreds());
        AsrSessionContext ctx = ctx();
        Capturing cb = new Capturing();
        provider.parseTextMessage(ctx, cb,
                "{\"code\":0,\"message_id\":\"m1\",\"voice_id\":\"v\"," +
                        "\"result\":{\"slice_type\":2,\"voice_text_str\":\"你好\",\"start_time\":0,\"end_time\":500}}");
        provider.parseTextMessage(ctx, cb,
                "{\"code\":0,\"message_id\":\"m2\",\"voice_id\":\"v\"," +
                        "\"result\":{\"slice_type\":0,\"voice_text_str\":\"你\",\"start_time\":600}}");

        assertEquals(2, cb.results.size());
        assertTrue(cb.results.get(0).getSegment().isFinalResult(), "slice_type=2 应为最终");
        assertEquals("你好", cb.results.get(0).getSegment().getText());
        assertEquals("m1", cb.results.get(0).getSegment().getSegmentId());
        assertFalse(cb.results.get(1).getSegment().isFinalResult(), "slice_type=0 应为增量");
    }

    @Test
    void parseErrorCodeTriggersOnError() {
        TencentAsrProvider provider = new TencentAsrProvider(propsWithCreds());
        Capturing cb = new Capturing();
        provider.parseTextMessage(ctx(), cb, "{\"code\":40001,\"message\":\"auth failed\"}");
        assertEquals("ASR_999", cb.errCode);
    }
}
