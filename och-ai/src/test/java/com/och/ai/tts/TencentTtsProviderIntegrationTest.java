package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.provider.TencentTtsProvider;
import com.och.ai.tts.util.TtsAudioUtils;
import com.och.common.config.oss.TxCloudConfig;
import com.och.common.constant.SysSettingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯云 TTS 真实合成集成测试（任务 8.3 / 12.2 第二云实测）。
 *
 * <p>仅在提供有效凭证时运行（环境变量 {@code OCH_TTS_TENCENT_AK/SK}），否则自动跳过——不伪造通过。
 * 凭证复用同账号 COS 的 SecretId/SecretKey（与生产适配一致），运行后把真实 WAV 落盘到 {@code outputs/tts_tencent_it/}。
 *
 * <p>用法：
 * <pre>
 *   set OCH_TTS_TENCENT_AK=AKID...      ; SecretId
 *   set OCH_TTS_TENCENT_SK=xxxx          ; SecretKey
 *   set OCH_TTS_TENCENT_REGION=ap-beijing ; 可选
 *   set OCH_TTS_TENCENT_VOICE=101001      ; 可选，默认 101001(智瑜)
 *   mvn -pl och-ai test -Dtest=TencentTtsProviderIntegrationTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "OCH_TTS_TENCENT_AK", matches = ".+")
class TencentTtsProviderIntegrationTest {

    private static final String TEXT = "您好，欢迎致电呼叫中心，业务咨询请按 1，人工服务请按 0。";

    @Test
    void synthesizeRealTencentAudio() throws Exception {
        SysSettingConfig sys = new SysSettingConfig();
        TxCloudConfig tx = new TxCloudConfig();
        TxCloudConfig.TxCosConfig cos = new TxCloudConfig.TxCosConfig();
        cos.setAccessKey(System.getenv("OCH_TTS_TENCENT_AK"));
        cos.setSecretKey(System.getenv("OCH_TTS_TENCENT_SK"));
        String region = System.getenv("OCH_TTS_TENCENT_REGION");
        if (region != null && !region.isBlank()) {
            cos.setRegionName(region);
        }
        tx.setCos(cos);
        sys.setTxConfig(tx);

        TtsProperties props = new TtsProperties();
        String voice = System.getenv().getOrDefault("OCH_TTS_TENCENT_VOICE", "101001");
        props.getTencent().setVoice(voice);

        TencentTtsProvider provider = new TencentTtsProvider(props, sys);
        TtsRequest req = TtsRequest.builder()
                .text(TEXT).provider(TtsProviderType.TENCENT).voice(voice)
                .sampleRate(8000).format(TtsAudioFormat.WAV).build();
        TtsResult result = provider.synthesize(req);

        assertTrue(result.isSuccess(), "腾讯云合成应成功，errorCode=" + result.getErrorCode()
                + " msg=" + result.getErrorMsg());
        assertTrue(result.getAudioBytes() != null && result.getAudioBytes().length > 44, "应返回完整 WAV");
        assertTrue(TtsAudioUtils.validateWav(result.getAudioBytes()) > 0, "WAV 头应合法");
        assertTrue(result.getDurationMs() != null && result.getDurationMs() > 0, "时长应>0");

        File dir = new File("outputs", "tts_tencent_it");
        dir.mkdirs();
        Path out = Paths.get(dir.getAbsolutePath(), "tencent_it_" + System.currentTimeMillis() + ".wav");
        Files.write(out, result.getAudioBytes());
        System.out.println("[TencentIT] 字节数=" + result.getSizeBytes() + " 时长ms=" + result.getDurationMs()
                + " 耗时ms=" + result.getElapsedMs() + " -> " + out);
    }
}
