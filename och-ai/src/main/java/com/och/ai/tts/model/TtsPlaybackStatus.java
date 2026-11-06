package com.och.ai.tts.model;

/**
 * TTS 通道播放事件状态。
 */
public enum TtsPlaybackStatus {

    /** 播放开始。 */
    START,
    /** 播放完成。 */
    COMPLETE,
    /** 播放失败（不影响当前通话，仅回写错误）。 */
    FAILED
}
