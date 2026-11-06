package com.och.ai.asr.integration;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AsrSessionState;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.Speaker;
import com.och.ai.asr.provider.AsrResultCallback;
import com.och.ai.asr.provider.IAsrProvider;
import com.och.ai.asr.provider.TencentAsrProvider;
import com.och.ai.asr.audio.AudioDecoder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主用云（腾讯云）真实凭证集成测试（方案 7.7 / 文档 10.2 P0）。
 *
 * <p>无凭证时自动跳过（不失败）；凭证就绪后用真实 WAV 跑端到端实时识别，校验
 * “鉴权→建连→分片发送→增量/最终结果→关闭”。</p>
 *
 * <p>运行方式（注入凭证后）：</p>
 * <pre>
 * OCH_ASR_TENCENT_APPID=... OCH_ASR_TENCENT_SECRETID=... OCH_ASR_TENCENT_SECRETKEY=... \
 * OCH_ASR_IT_WAV=/abs/path/test.wav mvn -pl och-ai -am test -Dtest=CloudAsrIntegrationTest
 * </pre>
 */
class CloudAsrIntegrationTest {

    @Test
    void tencentRealtimeRecognizeRealAudio() throws Exception {
        String appId = System.getenv("OCH_ASR_TENCENT_APPID");
        String secretId = System.getenv("OCH_ASR_TENCENT_SECRETID");
        String secretKey = System.getenv("OCH_ASR_TENCENT_SECRETKEY");
        String wav = System.getenv("OCH_ASR_IT_WAV");
        // 无凭证或无音频：跳过（不计为失败）
        Assumptions.assumeTrue(appId != null && secretId != null && secretKey != null && wav != null,
                "未提供腾讯云凭证/OCH_ASR_IT_WAV，跳过真实集成测试");

        AsrProperties props = new AsrProperties();
        props.setSampleRate(16000);
        props.setInputCodec(AudioCodec.PCM);
        props.setPunctuation(true);
        props.setConvertDigit(true);
        props.setConnectTimeoutMs(8000L);
        props.setFinalWaitTimeoutMs(8000L);
        props.getTencent().setEnabled(true);
        props.getTencent().setAppId(appId);
        props.getTencent().setSecretId(secretId);
        props.getTencent().setSecretKey(secretKey);
        props.getTencent().setEngineModelType("16k_zh");
        props.getTencent().setVoiceFormat(1);

        TencentAsrProvider provider = new TencentAsrProvider(props);
        org.junit.jupiter.api.Assertions.assertTrue(provider.ready(), "凭证齐全应就绪");

        AsrSessionContext ctx = new AsrSessionContext();
        ctx.setSessionId("it-tx");
        ctx.setCallId(System.currentTimeMillis());
        ctx.setUniqueId("it-uuid");
        ctx.setSampleRate(16000);
        ctx.setCodec(AudioCodec.PCM);
        ctx.setState(AsrSessionState.ACTIVE);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> finalText = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>();
        AsrResultCallback cb = new AsrResultCallback() {
            public void onResult(com.och.ai.asr.model.AsrRecognitionResult r) {
                if (r.getSegment().isFinalResult()) {
                    finalText.set(finalText.get() + r.getSegment().getText());
                }
            }
            public void onError(String code, String msg) { error.set(code + ":" + msg); done.countDown(); }
            public void onClosed() { done.countDown(); }
        };
        provider.createSession(ctx, cb);
        // 读取 16k WAV PCM（复用模拟器解析）并发送
        byte[] wavBytes = Files.readAllBytes(Paths.get(wav));
        com.och.ai.asr.simulator.MockRecordingAudioFeeder feeder = new com.och.ai.asr.simulator.MockRecordingAudioFeeder();
        feeder.feed(wav, ctx.getCallId(), ctx.getUniqueId(), 20, false, 0, Speaker.UNKNOWN,
                frame -> provider.sendAudio(ctx, AudioDecoder.shortsToBytes(AudioDecoder.decodeToPcm(frame))));
        provider.finishSession(ctx);
        org.junit.jupiter.api.Assertions.assertTrue(done.await(15, TimeUnit.SECONDS), "应收到关闭/最终");
        org.junit.jupiter.api.Assertions.assertNull(error.get(), "不应有错误: " + error.get());
        org.junit.jupiter.api.Assertions.assertFalse(finalText.get().isBlank(), "应识别到文本");
    }
}
