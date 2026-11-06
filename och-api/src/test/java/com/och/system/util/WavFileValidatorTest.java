package com.och.system.util;

import com.och.system.util.WavFileValidator.WavInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAV/PCM 文件头校验测试：构造合成 WAV 头验证 PCM 8000/16000 与非法文件。
 */
class WavFileValidatorTest {

    private static final Set<Integer> ALLOWED = Set.of(8000, 16000);

    /** 构造一个最小合法 PCM WAV 头(44 字节)。 */
    private static byte[] pcmWav(int sampleRate, int channels, int bitsPerSample, int dataSize) {
        byte[] w = new byte[44];
        // RIFF
        w[0] = 'R'; w[1] = 'I'; w[2] = 'F'; w[3] = 'F';
        int chunkSize = 36 + dataSize;
        writeUint32(w, 4, chunkSize);
        w[8] = 'W'; w[9] = 'A'; w[10] = 'V'; w[11] = 'E';
        // fmt
        w[12] = 'f'; w[13] = 'm'; w[14] = 't'; w[15] = ' ';
        writeUint32(w, 16, 16);            // fmt chunk size
        writeUint16(w, 20, WavFileValidator.FORMAT_PCM);
        writeUint16(w, 22, channels);
        writeUint32(w, 24, sampleRate);
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        writeUint32(w, 28, byteRate);
        writeUint16(w, 32, channels * bitsPerSample / 8);
        writeUint16(w, 34, bitsPerSample);
        // data
        w[36] = 'd'; w[37] = 'a'; w[38] = 't'; w[39] = 'a';
        writeUint32(w, 40, dataSize);
        return w;
    }

    private static void writeUint32(byte[] w, int off, int v) {
        w[off] = (byte) (v & 0xFF);
        w[off + 1] = (byte) ((v >> 8) & 0xFF);
        w[off + 2] = (byte) ((v >> 16) & 0xFF);
        w[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void writeUint16(byte[] w, int off, int v) {
        w[off] = (byte) (v & 0xFF);
        w[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    @Test
    void validPcm8000Mono_passes() {
        WavInfo info = WavFileValidator.validate(pcmWav(8000, 1, 16, 16000), ALLOWED);
        assertTrue(info.isValid());
        assertEquals("PCM", info.getCodec());
        assertEquals(8000, info.getSampleRate());
        assertEquals(1, info.getChannels());
        assertEquals(16, info.getBitsPerSample());
        assertEquals(16000L, info.getDataSize());
        assertNull(info.getError());
    }

    @Test
    void sampleRateNotAllowed_fails() {
        WavInfo info = WavFileValidator.validate(pcmWav(44100, 2, 16, 1000), ALLOWED);
        assertFalse(info.isValid());
        assertTrue(info.getError().contains("44100"));
    }

    @Test
    void noRateRestriction_allSampleRatesPass() {
        WavInfo info = WavFileValidator.validate(pcmWav(22050, 1, 16, 100), null);
        assertTrue(info.isValid());
    }

    @Test
    void notRiff_fails() {
        byte[] bad = pcmWav(8000, 1, 16, 10);
        bad[0] = 'X';
        WavInfo info = WavFileValidator.validate(bad, ALLOWED);
        assertFalse(info.isValid());
        assertTrue(info.getError().contains("RIFF"));
    }

    @Test
    void tooSmall_fails() {
        WavInfo info = WavFileValidator.validate(new byte[10], ALLOWED);
        assertFalse(info.isValid());
    }

    @Test
    void streamValidation_works() throws Exception {
        byte[] wav = pcmWav(16000, 1, 16, 1000);
        WavInfo info = WavFileValidator.validate(new ByteArrayInputStream(wav), ALLOWED);
        assertTrue(info.isValid());
        assertEquals(16000, info.getSampleRate());
    }

    @Test
    void formatName_knownCodes() {
        assertEquals("PCM", WavFileValidator.formatName(WavFileValidator.FORMAT_PCM));
        assertEquals("PCMA(A-law)", WavFileValidator.formatName(WavFileValidator.FORMAT_ALAW));
        assertEquals("PCMU(mu-law)", WavFileValidator.formatName(WavFileValidator.FORMAT_ULAW));
        assertNull(WavFileValidator.formatName(99));
    }
}
