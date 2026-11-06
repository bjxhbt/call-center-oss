package com.och.ai.tts.model;

/**
 * TTS 任务生命周期状态（统一业务流转用）。
 *
 * <pre>
 * PENDING → SYNTHESIZING → SUCCESS → PLAYING → PLAYED
 *                      ↘ FAILED
 * </pre>
 */
public enum TtsTaskStatus {

    /** 已受理，尚未调用 Provider。 */
    PENDING,
    /** 正在调用 Provider 合成。 */
    SYNTHESIZING,
    /** 合成成功，文件已落盘，可试听/可播放。 */
    SUCCESS,
    /** 合成失败（含参数错误、鉴权失败、超时、限流等）。 */
    FAILED,
    /** 已下发给通信链路播放（收到 PLAYBACK-START 事件）。 */
    PLAYING,
    /** 播放完成（收到 PLAYBACK-COMPLETE 事件）。 */
    PLAYED
}
