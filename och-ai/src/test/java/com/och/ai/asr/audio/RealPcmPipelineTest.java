package com.och.ai.asr.audio;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实通话 PCM 流水线验证（方案 7.3 / 文档 10.2 P0）。
 *
 * <p>核对电话音频格式契约与发送节奏，确保 ASR 接收的音频与通话内容一致、无持续丢帧：</p>
 * <ul>
 *   <li>G.711(PCMA/PCMU) 8kHz：20ms = 160 字节 → 解码 160 样本(320B PCM) → 升采样 16kHz = 320 样本(640B)；</li>
 *   <li>线性 PCM 8kHz：20ms = 320 字节(160 样本) → 升采样 16kHz = 640 字节(320 样本)；</li>
 *   <li>PCM 16kHz 直通：20ms = 640 字节(320 样本)。</li>
 * </ul>
 */
class RealPcmPipelineTest {

    private AsrProperties props16k() {
        AsrProperties p = new AsrProperties();
        p.setSampleRate(16000);
        return p;
    }

    /** G.711 A-law 帧：160 字节(20ms@8k) → 解码+升采样后应为 640 字节(16k)。 */
    @Test
    void g711AlawFrameProducesCorrect16kChunk() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        byte[] g711 = new byte[160]; // 20ms@8k A-law（用静音字节 0xD5 填充）
        java.util.Arrays.fill(g711, (byte) 0xD5);
        CallAudioFrame frame = CallAudioFrame.builder()
                .callId(1L).uniqueId("u").codec(AudioCodec.PCMA).sampleRate(8000).channels(1).data(g711).build();
        AudioNormalizer.NormalizedAudio out = n.normalize(frame);
        assertTrue(out.valid());
        assertEquals(16000, out.sampleRate());
        assertEquals(640, out.pcm().length, "160 字节 A-law → 16k 应为 640 字节 PCM");
    }

    /** 线性 PCM 8kHz 帧：320 字节(160 样本) → 升采样 16kHz = 640 字节。 */
    @Test
    void pcm8kFrameUpsamplesTo16k() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        byte[] pcm = new byte[320]; // 20ms@8k 16bit
        CallAudioFrame frame = CallAudioFrame.builder()
                .callId(1L).uniqueId("u").codec(AudioCodec.PCM).sampleRate(8000).channels(1).data(pcm).build();
        AudioNormalizer.NormalizedAudio out = n.normalize(frame);
        assertTrue(out.valid());
        assertEquals(640, out.pcm().length);
    }

    /** 1 秒 8kHz 通话音频按 20ms 切片：50 帧，每帧归一化后均为 640 字节（节奏一致、无丢帧）。 */
    @Test
    void oneSecond8kCadenceProduces50FramesOf640Bytes() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        int sampleRate = 8000;
        int frameSamples = sampleRate / 50; // 20ms = 160 样本
        int totalFrames = 50;
        for (int i = 0; i < totalFrames; i++) {
            byte[] pcm = toneFrame8k(frameSamples, 220.0, i); // 真实音频样例：正弦音
            CallAudioFrame frame = CallAudioFrame.builder()
                    .callId(1L).uniqueId("u").codec(AudioCodec.PCM).sampleRate(sampleRate).channels(1)
                    .data(pcm).sequence((long) i).build();
            AudioNormalizer.NormalizedAudio out = n.normalize(frame);
            assertTrue(out.valid(), "第 " + i + " 帧应有效");
            assertEquals(640, out.pcm().length, "第 " + i + " 帧归一化后应为 640 字节");
        }
    }

    /** 静音帧：被检测为 silent 但仍 valid（不跳过，保证 ASR 时序连续）。 */
    @Test
    void silenceFrameDetectedButValid() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        byte[] zeros = new byte[640]; // 20ms@16k 全零
        CallAudioFrame frame = CallAudioFrame.builder()
                .callId(1L).uniqueId("u").codec(AudioCodec.PCM).sampleRate(16000).channels(1).data(zeros).build();
        AudioNormalizer.NormalizedAudio out = n.normalize(frame);
        assertTrue(out.valid());
        assertTrue(out.silent(), "全零帧应判定为静音");
    }

    /** 16k PCM 直通：20ms = 640 字节，不重采样。 */
    @Test
    void pcm16kPassthrough() {
        AudioNormalizer n = new AudioNormalizer(props16k());
        byte[] pcm = new byte[640];
        CallAudioFrame frame = CallAudioFrame.builder()
                .callId(1L).uniqueId("u").codec(AudioCodec.PCM).sampleRate(16000).channels(1).data(pcm).build();
        AudioNormalizer.NormalizedAudio out = n.normalize(frame);
        assertTrue(out.valid());
        assertEquals(640, out.pcm().length);
        assertEquals(16000, out.sampleRate());
    }

    /** 生成 8k 正弦音 PCM（16bit 小端），模拟真实通话样例。 */
    private static byte[] toneFrame8k(int samples, double freq, int frameIndex) {
        byte[] out = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = (frameIndex * samples + i) / 8000.0;
            short v = (short) (Math.sin(2 * Math.PI * freq * t) * 8000);
            out[2 * i] = (byte) (v & 0xFF);
            out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
}
