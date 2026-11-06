package com.och.ai.tts;

import com.och.ai.tts.util.TtsAudioUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TTS 音频工具测试：WAV 头合法性、时长估算、PCM/WAV 归一化。
 *
 * <p>这些断言同时保证 Mock Provider 产出的音频可被浏览器试听与 FreeSWITCH 播放。
 */
class TtsAudioUtilsTest {

    @Test
    void wrapWav_producesValidRiffHeader() throws IOException {
        byte[] pcm = new byte[16000]; // 1s @8k mono 16bit
        byte[] wav = TtsAudioUtils.wrapWav(pcm, 8000);

        assertEquals('R', (char) wav[0]);
        assertEquals('I', (char) wav[1]);
        assertEquals('F', (char) wav[2]);
        assertEquals('F', (char) wav[3]);
        assertEquals('W', (char) wav[8]);
        assertEquals('A', (char) wav[9]);
        assertEquals('V', (char) wav[10]);
        assertEquals('E', (char) wav[11]);
        // data chunk size @ offset 40 == pcm.length (little-endian)
        int dataLen = TtsAudioUtils.validateWav(wav);
        assertEquals(pcm.length, dataLen);
        assertEquals(44 + pcm.length, wav.length);
    }

    @Test
    void validateWav_rejectsNonWav() {
        assertEquals(-1, TtsAudioUtils.validateWav(new byte[10]));
        assertEquals(-1, TtsAudioUtils.validateWav(null));
    }

    @Test
    void estimateDurationMs_matches8kMono16bit() {
        // 16000 字节/s @8k mono 16bit
        assertEquals(1000L, TtsAudioUtils.estimateDurationMs(16000, 8000));
        assertEquals(500L, TtsAudioUtils.estimateDurationMs(8000, 8000));
        assertEquals(0L, TtsAudioUtils.estimateDurationMs(0, 8000));
    }

    @Test
    void normalize_wav_addsHeader_pcmPassthrough() throws IOException {
        byte[] pcm = TtsAudioUtils.generateTonePcm("hi", 8000, 300.0);
        byte[] asWav = TtsAudioUtils.normalize(pcm, com.och.ai.tts.model.TtsAudioFormat.WAV, 8000);
        assertTrue(TtsAudioUtils.validateWav(asWav) > 0);
        byte[] asPcm = TtsAudioUtils.normalize(pcm, com.och.ai.tts.model.TtsAudioFormat.PCM, 8000);
        assertSame(pcm, asPcm);
    }

    @Test
    void generateTonePcm_isNonEmptyAndDeterministic() {
        byte[] a = TtsAudioUtils.generateTonePcm("hello", 8000, 220.0);
        byte[] b = TtsAudioUtils.generateTonePcm("hello", 8000, 220.0);
        assertNotNull(a);
        assertTrue(a.length > 0);
        assertArrayEquals(a, b);
    }
}
