package com.och.ai.tts.model;

/**
 * 统一 TTS 输出音频格式。
 *
 * FreeSWITCH 电话通道使用 8kHz / 16bit / mono 的 WAV 或裸 PCM；
 * 浏览器试听需要带 RIFF 头的 WAV。因此默认输出 {@link #WAV}，
 * 由 {@code TtsFileManager} 统一落盘为可被两端读取的文件。
 */
public enum TtsAudioFormat {

    /** 裸 PCM（16bit little-endian, mono），不含 WAV 头。 */
    PCM,
    /** 标准 WAV（RIFF），浏览器与 FreeSWITCH 均可直接播放，默认格式。 */
    WAV,
    /** MP3，仅用于试听转码场景，不在 MVP 电话通道主链路。 */
    MP3;

    public static TtsAudioFormat parse(String name, TtsAudioFormat fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return TtsAudioFormat.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
