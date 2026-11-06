package com.och.ai.tts.util;

import com.och.ai.tts.model.TtsAudioFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TTS 音频工具：生成确定性测试音、计算时长、封装 WAV 头。
 *
 * 仅供 Mock Provider 与 Provider 适配层把裸 PCM 封装成 WAV 使用，
 * 保证浏览器试听与 FreeSWITCH 播放都能读取（8k/16bit/mono）。
 */
public final class TtsAudioUtils {

    /** 16bit PCM 每秒字节数 = 采样率 × 2（单声道）。 */
    public static long bytesPerSecond(int sampleRate) {
        return sampleRate * 2L;
    }

    private TtsAudioUtils() {
    }

    /**
     * 根据字节数与采样率估算时长（毫秒）。
     */
    public static long estimateDurationMs(long byteCount, int sampleRate) {
        if (byteCount <= 0 || sampleRate <= 0) {
            return 0L;
        }
        return Math.round(byteCount * 1000.0 / bytesPerSecond(sampleRate));
    }

    /**
     * 给 16bit mono little-endian PCM 加上标准 44 字节 WAV 头。
     */
    public static byte[] wrapWav(byte[] pcm, int sampleRate) throws IOException {
        if (pcm == null) {
            pcm = new byte[0];
        }
        int blockAlign = 2;
        int byteRate = sampleRate * blockAlign;
        int dataLength = pcm.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataLength);
        out.writeBytes("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeLeInt(out, 36 + dataLength);
        out.writeBytes("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.writeBytes("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeLeInt(out, 16);
        writeLeShort(out, (short) 1);           // PCM
        writeLeShort(out, (short) 1);           // mono
        writeLeInt(out, sampleRate);
        writeLeInt(out, byteRate);
        writeLeShort(out, (short) blockAlign);
        writeLeShort(out, (short) 16);          // bits per sample
        out.writeBytes("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeLeInt(out, dataLength);
        out.write(pcm);
        return out.toByteArray();
    }

    /**
     * 校验字节数组是否为合法 WAV（RIFF....WAVE fmt ），返回 data 区长度。
     */
    public static int validateWav(byte[] wav) {
        if (wav == null || wav.length < 44) {
            return -1;
        }
        if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            return -1;
        }
        if (wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
            return -1;
        }
        // data chunk size (little-endian) at offset 40
        return ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * 生成确定性正弦波 PCM，文本不同/参数不同则时长或频率不同，便于单测断言。
     * 时长 = max(600ms, 文本长度 × 120ms)。
     */
    public static byte[] generateTonePcm(String text, int sampleRate, double freq) {
        int durationMs = Math.max(600, (text == null ? 0 : text.length()) * 120);
        int samples = (int) ((long) sampleRate * durationMs / 1000L);
        byte[] pcm = new byte[samples * 2];
        double twoPiF = 2 * Math.PI * freq;
        for (int i = 0; i < samples; i++) {
            // 简单包络，避免咔哒声
            double envelope = Math.min(1.0, i / (double) sampleRate * 20.0)
                    * Math.min(1.0, (samples - i) / (double) sampleRate * 20.0);
            short sample = (short) (Short.MAX_VALUE * 0.3 * envelope * Math.sin(twoPiF * i / sampleRate));
            pcm[i * 2] = (byte) (sample & 0xff);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return pcm;
    }

    private static void writeLeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 24) & 0xff);
    }

    private static void writeLeShort(ByteArrayOutputStream out, short v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    /** 根据目标格式归一化输出字节：WAV 加头，PCM 原样。 */
    public static byte[] normalize(byte[] pcm, TtsAudioFormat format, int sampleRate) throws IOException {
        if (format == TtsAudioFormat.WAV) {
            return wrapWav(pcm, sampleRate);
        }
        return pcm;
    }
}
