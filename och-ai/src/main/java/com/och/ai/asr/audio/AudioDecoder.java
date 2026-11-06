package com.och.ai.asr.audio;

import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * 音频解码器（方案 7.3）：将 {@link CallAudioFrame} 的原始字节统一解码为
 * <b>16bit 线性 PCM</b>（小端 short 数组），保留帧原生采样率。
 *
 * <p>支持 PCMA/PCMU（G.711）→ 线性，PCM（已是线性）直通；
 * OPUS 不在 MVP 范围，抛 {@code IllegalArgumentException} 由上层降级。</p>
 */
public final class AudioDecoder {

    /** 帧字节数不足或长度与编码不符时的最大宽容，避免异常帧导致越界。 */
    private AudioDecoder() {
    }

    /**
     * 解码为 short 数组（16bit 线性 PCM）。
     *
     * @param frame 音频帧
     * @return 线性样本；空帧返回长度 0 数组
     */
    public static short[] decodeToPcm(CallAudioFrame frame) {
        if (frame == null || frame.getData() == null || frame.getData().length == 0) {
            return new short[0];
        }
        AudioCodec codec = frame.getCodec();
        if (codec == null) {
            codec = AudioCodec.PCM;
        }
        byte[] data = frame.getData();
        return switch (codec) {
            case PCMA -> decodeG711(data, true);
            case PCMU -> decodeG711(data, false);
            case PCM -> bytesToShorts(data);
            case OPUS -> throw new IllegalArgumentException("OPUS 解码暂不支持，请改用 PCMA/PCMU/PCM");
        };
    }

    /**
     * short 数组编码为小端 16bit PCM 字节（发送给云端用）。
     */
    public static byte[] shortsToBytes(short[] samples) {
        ByteBuffer bb = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer sb = bb.asShortBuffer();
        sb.put(samples);
        return bb.array();
    }

    /** 小端字节 → short。 */
    public static short[] bytesToShorts(byte[] data) {
        int n = data.length / 2;
        short[] out = new short[n];
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
        return out;
    }

    private static short[] decodeG711(byte[] data, boolean alaw) {
        short[] out = new short[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = alaw ? G711.decodeAlaw(data[i]) : G711.decodeUlaw(data[i]);
        }
        return out;
    }
}
