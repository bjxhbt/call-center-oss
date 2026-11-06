package com.och.ai.asr.audio;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G.711 解码与字节/short 互转测试（方案 7.3）。
 */
class G711Test {

    @Test
    void decodeAlaw_allBytesWithinRange() {
        for (int b = 0; b < 256; b++) {
            short s = G711.decodeAlaw(b);
            assertTrue(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE, "越界 b=" + b);
        }
    }

    @Test
    void decodeUlaw_allBytesWithinRange() {
        for (int b = 0; b < 256; b++) {
            short s = G711.decodeUlaw(b);
            assertTrue(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE, "越界 b=" + b);
        }
    }

    @Test
    void decodeAlaw_mostBytesDistinct() {
        Set<Short> distinct = new HashSet<>();
        for (int b = 0; b < 256; b++) {
            distinct.add(G711.decodeAlaw(b));
        }
        // 256 字节应映射出大量不同线性值（查表为 256 段）
        assertTrue(distinct.size() > 200, "a-law 解码应高度分散，实际=" + distinct.size());
    }

    @Test
    void shortsBytesRoundtrip() {
        short[] samples = new short[]{0, 1, -1, 100, -100, 32767, -32768, 12345};
        byte[] bytes = AudioDecoder.shortsToBytes(samples);
        short[] back = AudioDecoder.bytesToShorts(bytes);
        assertEquals(samples.length, back.length);
        for (int i = 0; i < samples.length; i++) {
            assertEquals(samples[i], back[i], "roundtrip idx=" + i);
        }
    }
}
