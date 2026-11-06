package com.och.ai.asr.audio;

/**
 * G.711 解码（A-law / U-law → 16bit 线性 PCM，方案 7.3）。
 *
 * <p>电话音频（SIP/E1）普遍使用 G.711。本类提供无查表的<b>解码</b>实现（标准公式），
 * 单字节样本解码为一个 short（16bit 有符号），用于在 ASR 入口统一转线性 PCM。</p>
 *
 * <p>实现遵循 ITU-T G.711。ASR 入口只需解码；编码不在链路上，故不提供，避免引入易错的查表/分段编码。</p>
 */
public final class G711 {

    private G711() {
    }

    /**
     * A-law 解码：8bit → 16bit 线性（中国 E1/SIP 主流）。
     *
     * <p>A-law 在传输前按位取反，解码先取反；随后按指数段还原幅度。</p>
     */
    public static short decodeAlaw(int b) {
        int t = (~b) & 0xFF;
        int sign = t & 0x80;
        int exponent = (t & 0x70) >> 4;
        int mantissa = t & 0x0F;
        int sample = (mantissa << 4) + 8;
        if (exponent != 0) {
            sample += 0x100;
        }
        if (exponent > 1) {
            sample <<= (exponent - 1);
        }
        return (short) (sign == 0 ? -sample : sample);
    }

    /**
     * U-law（μ-law）解码：8bit → 16bit 线性（北美）。
     */
    public static short decodeUlaw(int b) {
        int t = (~b) & 0xFF;
        int sign = t & 0x80;
        int exponent = (t & 0x70) >> 4;
        int mantissa = t & 0x0F;
        int sample = ((mantissa << 3) + 0x84) << exponent;
        sample -= 0x84;
        // u-law：sign 位为 1 代表正值
        return (short) (sign != 0 ? -sample : sample);
    }
}
