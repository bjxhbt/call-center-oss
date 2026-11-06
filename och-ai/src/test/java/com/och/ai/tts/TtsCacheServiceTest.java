package com.och.ai.tts;

import com.och.ai.tts.cache.TtsCacheService;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.common.config.redis.RedisService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存键测试：相同文本+参数必命中同一键；任一参数变化必产生不同键（方案 8.7 核心）。
 */
class TtsCacheServiceTest {

    private final TtsCacheService service = new TtsCacheService(Mockito.mock(RedisService.class));

    private TtsRequest req(String text, String voice, Double speed, Integer rate, TtsAudioFormat fmt) {
        return TtsRequest.builder()
                .text(text).voice(voice).speed(speed).sampleRate(rate).format(fmt)
                .provider(TtsProviderType.MOCK)
                .build();
    }

    @Test
    void sameInputSameKey() {
        String k1 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        String k2 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        assertEquals(k1, k2);
        assertEquals(32, k1.length(), "应为 32 位 MD5");
    }

    @Test
    void differentTextDifferentKey() {
        String k1 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        String k2 = service.buildKey(req("你好吗", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        assertNotEquals(k1, k2);
    }

    @Test
    void differentVoiceDifferentKey() {
        String k1 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        String k2 = service.buildKey(req("你好", "v2", 0.0, 8000, TtsAudioFormat.WAV));
        assertNotEquals(k1, k2);
    }

    @Test
    void differentSpeedDifferentKey() {
        String k1 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        String k2 = service.buildKey(req("你好", "v1", 0.5, 8000, TtsAudioFormat.WAV));
        assertNotEquals(k1, k2);
    }

    @Test
    void differentSampleRateDifferentKey() {
        String k1 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        String k2 = service.buildKey(req("你好", "v1", 0.0, 16000, TtsAudioFormat.WAV));
        assertNotEquals(k1, k2);
    }

    @Test
    void differentFormatDifferentKey() {
        String k1 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.WAV));
        String k2 = service.buildKey(req("你好", "v1", 0.0, 8000, TtsAudioFormat.PCM));
        assertNotEquals(k1, k2);
    }

    @Test
    void textDigestStable() {
        assertEquals(service.textDigest("abc"), service.textDigest("abc"));
        assertNotEquals(service.textDigest("abc"), service.textDigest("abd"));
    }

    @Test
    void md5_knownVector() {
        // md5("abc") == 900150983cd24fb0d6963f7d28e17f72
        assertEquals("900150983cd24fb0d6963f7d28e17f72", TtsCacheService.md5("abc"));
    }
}
