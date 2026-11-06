package com.och.utils;

public class G711Utils {

    private static final short BIAS = 0x84;   // 定义u-law量化偏移量
    private static final int MAX = 32635;     // 最大量化输入绝对值（避免溢出）

    /**
     * 将16位PCM小端序字节数组转换为G.711 μ-law字节数组
     * @param pcmData 输入PCM数据（16位小端序）
     * @return μ-law编码后的字节数组
     */
    public static byte[] pcmToULaw(byte[] pcmData) {
        byte[] ulawData = new byte[pcmData.length / 2];
        for (int i = 0; i < ulawData.length; i++) {
            // 小端序转换（低字节在前）
            short sample = (short) ((pcmData[2*i + 1] << 8) | (pcmData[2*i] & 0xFF));
            ulawData[i] = linearToULaw(sample);
        }
        return ulawData;
    }

    private static byte linearToULaw(short sample) {
        // 标准 ITU-T G.711 µ-law（与 Sun Microsystems 参考实现一致，mod_unimrcp 解码器兼容）
        final int BIAS = 0x84;
        final int CLIP = 32635;
        int sign = (sample >> 8) & 0x80;
        int magnitude = Math.min(Math.abs(sample), CLIP);
        magnitude += BIAS;
        int exponent = 7;
        for (int bit = 0x4000; (magnitude & bit) == 0 && exponent > 0; bit >>= 1) {
            exponent--;
        }
        int mantissa = (magnitude >> (exponent + 3)) & 0x0F;
        // µ-law 标准要求所有位取反
        return (byte) (~(sign | (exponent << 4) | mantissa));
    }


    /**
     * PCM转G.711 A-law
     * 算法参考ITU-T G.711标准
     */
    public static byte[] pcmToALaw(byte[] pcmData) {
        byte[] alawData = new byte[pcmData.length / 2];
        for (int i = 0; i < alawData.length; i++) {
            short sample = (short) ((pcmData[2*i + 1] << 8) | (pcmData[2*i] & 0xFF));
            alawData[i] = linearToALaw(sample);
        }
        return alawData;
    }

    private static byte linearToALaw(short pcmSample) {
        int sign = (pcmSample < 0) ? 0x00 : 0x80;
        int absSample = Math.min(Math.abs(pcmSample), 0x7FFF);
        int quantization = (absSample < 0x20) ? 0 : 7 - Integer.numberOfLeadingZeros(absSample >> 4);
        quantization = Math.max(0, Math.min(quantization, 7));
        int mantissa = (absSample >> (quantization + 3)) & 0x0F;
        return (byte) (sign | (quantization << 4) | mantissa);
    }
}
