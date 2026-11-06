package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.provider.AliyunTtsProvider;
import com.och.ai.tts.util.TtsAudioUtils;
import com.och.common.config.oss.AliCloudConfig;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.SysSettingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阿里云 TTS 真实合成集成测试（任务 8.2 云端验证）。
 *
 * <p>仅在提供有效凭证时运行（环境变量 {@code OCH_TTS_ALIYUN_APPKEY/AK/SK}），
 * 否则自动跳过——不伪造通过。运行后把真实 WAV 落盘到 {@code outputs/tts_aliyun_it/} 供人工试听。
 *
 * <p>用法：
 * <pre>
 *   set OCH_TTS_ALIYUN_APPKEY=xxxx
 *   set OCH_TTS_ALIYUN_AK=yyyy      ; AccessKeyId
 *   set OCH_TTS_ALIYUN_SK=zzzz      ; AccessKeySecret
 *   set OCH_TTS_ALIYUN_VOICE=xiaoyun  ; 可选
 *   mvn -pl och-ai test -Dtest=AliyunTtsProviderIntegrationTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "OCH_TTS_ALIYUN_APPKEY", matches = ".+")
class AliyunTtsProviderIntegrationTest {

    private static final String TEXT = "您好，欢迎致电呼叫中心，业务咨询请按 1，人工服务请按 0。";

    @Test
    void synthesizeRealAliyunAudio() throws Exception {
        SysSettingConfig sys = new SysSettingConfig();
        AliCloudConfig ali = new AliCloudConfig();
        AliCloudConfig.AliTtsConfig tts = new AliCloudConfig.AliTtsConfig();
        tts.setAppKey(System.getenv("OCH_TTS_ALIYUN_APPKEY"));
        tts.setAccessKeyId(System.getenv("OCH_TTS_ALIYUN_AK"));
        tts.setAccessKeySecret(System.getenv("OCH_TTS_ALIYUN_SK"));
        String endpoint = System.getenv("OCH_TTS_ALIYUN_ENDPOINT");
        if (endpoint != null && !endpoint.isBlank()) {
            tts.setUrl(endpoint);
        }
        tts.setVoice(System.getenv().getOrDefault("OCH_TTS_ALIYUN_VOICE", "xiaoyun"));
        ali.setTts(tts);
        sys.setAliConfig(ali);

        TtsProperties props = new TtsProperties();
        RedisService redis = Mockito.mock(RedisService.class);
        AliyunTtsProvider provider = new AliyunTtsProvider(props, sys, redis);

        TtsRequest req = TtsRequest.builder()
                .text(TEXT).provider(TtsProviderType.ALIYUN).voice(tts.getVoice())
                .sampleRate(8000).format(TtsAudioFormat.WAV).build();
        TtsResult result = provider.synthesize(req);

        assertTrue(result.isSuccess(), "阿里云合成应成功，errorCode=" + result.getErrorCode()
                + " msg=" + result.getErrorMsg());
        assertTrue(result.getAudioBytes() != null && result.getAudioBytes().length > 44,
                "应返回完整 WAV");
        assertTrue(TtsAudioUtils.validateWav(result.getAudioBytes()) > 0, "WAV 头应合法");
        assertTrue(result.getDurationMs() != null && result.getDurationMs() > 0, "时长应>0");

        // 落盘供人工试听（不入 Git）
        File dir = new File("outputs", "tts_aliyun_it");
        dir.mkdirs();
        Path out = Paths.get(dir.getAbsolutePath(), "aliyun_it_" + System.currentTimeMillis() + ".wav");
        Files.write(out, result.getAudioBytes());
        System.out.println("[AliyunIT] 字节数=" + result.getSizeBytes() + " 时长ms=" + result.getDurationMs()
                + " 耗时ms=" + result.getElapsedMs() + " -> " + out);
    }
}
