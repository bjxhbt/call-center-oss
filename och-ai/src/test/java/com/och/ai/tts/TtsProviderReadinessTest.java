package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.provider.AliyunTtsProvider;
import com.och.ai.tts.provider.CosyVoiceTtsProvider;
import com.och.ai.tts.provider.TencentTtsProvider;
import com.och.common.config.oss.AliCloudConfig;
import com.och.common.config.oss.TxCloudConfig;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.SysSettingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TtsProviderReadinessTest {

    @Test
    void aliyunReadinessOnlyChecksLocalCredentialPresence() {
        TtsProperties properties = new TtsProperties();
        SysSettingConfig settings = new SysSettingConfig();
        AliyunTtsProvider provider = new AliyunTtsProvider(properties, settings, mock(RedisService.class));

        assertFalse(provider.ready());

        AliCloudConfig config = new AliCloudConfig();
        AliCloudConfig.AliTtsConfig tts = new AliCloudConfig.AliTtsConfig();
        tts.setAccessKeyId("test-id");
        tts.setAccessKeySecret("test-secret");
        tts.setAppKey("test-app");
        config.setTts(tts);
        settings.setAliConfig(config);

        assertTrue(provider.ready());
    }

    @Test
    void tencentReadinessOnlyChecksLocalCredentialPresence() {
        TtsProperties properties = new TtsProperties();
        SysSettingConfig settings = new SysSettingConfig();
        TencentTtsProvider provider = new TencentTtsProvider(properties, settings);

        assertFalse(provider.ready());

        TxCloudConfig config = new TxCloudConfig();
        TxCloudConfig.TxCosConfig cos = new TxCloudConfig.TxCosConfig();
        cos.setAccessKey("test-id");
        cos.setSecretKey("test-secret");
        config.setCos(cos);
        settings.setTxConfig(config);

        assertTrue(provider.ready());
    }

    @Test
    void cosyVoiceReadinessRequiresOnlyConfiguredEndpoint() {
        TtsProperties properties = new TtsProperties();
        CosyVoiceTtsProvider provider = new CosyVoiceTtsProvider(properties);

        assertFalse(provider.ready());

        properties.getCosyvoice().setBaseUrl("http://127.0.0.1:50031");
        assertTrue(provider.ready());
    }
}
