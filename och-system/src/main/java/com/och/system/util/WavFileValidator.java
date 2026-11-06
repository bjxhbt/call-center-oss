package com.och.system.util;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * WAV/PCM 文件头校验(纯逻辑，便于单测)。
 * <p>
 * FreeSWITCH playback 要求可访问的音频文件，推荐 8000Hz 单声道 PCM/PCMA/PCMU WAV。
 * 本校验只解析文件头，不读取全部内容，失败时返回明确原因且不抛异常(播放失败不挂断通话)。
 *
 * @author och
 */
public final class WavFileValidator {

    /** 常见音频格式码 */
    public static final int FORMAT_PCM = 1;
    public static final int FORMAT_ALAW = 6;
    public static final int FORMAT_ULAW = 7;

    private WavFileValidator() {
    }

    @Data
    public static class WavInfo {
        private boolean valid;
        private String error;
        private Integer audioFormat;
        private String codec;
        private Integer channels;
        private Integer sampleRate;
        private Integer bitsPerSample;
        private Long dataSize;
    }

    /**
     * 校验字节数据(至少包含完整 fmt 块)。
     *
     * @param data              wav 文件字节
     * @param allowedSampleRates 允许的采样率(如 8000, 16000)，为空则不限制
     */
    public static WavInfo validate(byte[] data, Set<Integer> allowedSampleRates) {
        WavInfo info = new WavInfo();
        if (data == null || data.length < 44) {
            info.setError("文件过小或为空，无法解析 WAV 头(至少需要44字节)");
            return info;
        }
        if (!isRiff(data) || !isWave(data)) {
            info.setError("不是合法的 RIFF/WAVE 文件");
            return info;
        }
        int fmtOffset = findChunk(data, "fmt ");
        if (fmtOffset < 0 || fmtOffset + 16 > data.length) {
            info.setError("未找到 fmt 块");
            return info;
        }
        int audioFormat = readUint16(data, fmtOffset + 8);
        int channels = readUint16(data, fmtOffset + 10);
        int sampleRate = readUint32(data, fmtOffset + 12);
        int bitsPerSample = readUint16(data, fmtOffset + 22);

        info.setAudioFormat(audioFormat);
        info.setCodec(formatName(audioFormat));
        info.setChannels(channels);
        info.setSampleRate(sampleRate);
        info.setBitsPerSample(bitsPerSample);

        int dataOffset = findChunk(data, "data");
        if (dataOffset >= 0 && dataOffset + 8 <= data.length) {
            info.setDataSize((long) readUint32(data, dataOffset + 4));
        }

        if (StringUtils.isBlank(info.getCodec())) {
            info.setError("不支持的音频格式码: " + audioFormat + " (仅支持 PCM/PCMA/PCMU)");
            return info;
        }
        if (channels < 1) {
            info.setError("声道数非法: " + channels);
            return info;
        }
        if (allowedSampleRates != null && !allowedSampleRates.isEmpty() && !allowedSampleRates.contains(sampleRate)) {
            info.setError("采样率 " + sampleRate + " 不在允许范围 " + allowedSampleRates + " 内");
            return info;
        }
        info.setValid(true);
        return info;
    }

    /**
     * 从输入流读取并校验(读取前 64 字节头即可)。
     */
    public static WavInfo validate(InputStream stream, Set<Integer> allowedSampleRates) throws IOException {
        byte[] header = new byte[64];
        int read = 0;
        int n;
        while (read < header.length && (n = stream.read(header, read, header.length - read)) != -1) {
            read += n;
        }
        byte[] exact = Arrays.copyOf(header, read);
        return validate(exact, allowedSampleRates);
    }

    public static String formatName(int audioFormat) {
        return switch (audioFormat) {
            case FORMAT_PCM -> "PCM";
            case FORMAT_ALAW -> "PCMA(A-law)";
            case FORMAT_ULAW -> "PCMU(mu-law)";
            default -> null;
        };
    }

    private static boolean isRiff(byte[] data) {
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F';
    }

    private static boolean isWave(byte[] data) {
        return data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    /**
     * 在 RIFF 块中查找指定四字符块，返回其块大小字段所在偏移(块ID起始偏移 + 4)。
     */
    private static int findChunk(byte[] data, String chunkId) {
        if (chunkId == null || chunkId.length() != 4) {
            return -1;
        }
        int offset = 12;
        while (offset + 8 <= data.length) {
            if (data[offset] == chunkId.charAt(0) && data[offset + 1] == chunkId.charAt(1)
                    && data[offset + 2] == chunkId.charAt(2) && data[offset + 3] == chunkId.charAt(3)) {
                return offset;
            }
            int chunkSize = readUint32(data, offset + 4);
            offset += 8 + chunkSize;
            // 块大小为奇数时有一个填充字节
            if ((chunkSize & 1) == 1) {
                offset += 1;
            }
        }
        return -1;
    }

    private static int readUint16(byte[] data, int offset) {
        if (offset < 0 || offset + 1 >= data.length) {
            return -1;
        }
        return ((data[offset] & 0xFF)) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readUint32(byte[] data, int offset) {
        if (offset < 0 || offset + 3 >= data.length) {
            return -1;
        }
        return ((data[offset] & 0xFF))
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    public static String describe(WavInfo info) {
        if (info == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "valid=%s codec=%s channels=%d sampleRate=%d bits=%d dataSize=%d err=%s",
                info.isValid(), info.getCodec(), info.getChannels(), info.getSampleRate(),
                info.getBitsPerSample(), info.getDataSize(), info.getError());
    }
}
