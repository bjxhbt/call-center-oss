package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

/**
 * TTS 通道播放事件（统一事件结构）。
 *
 * 由通信链路方案（FreeSWITCH 播放实现）在播放开始/完成/失败时回写；
 * 本方案不修改 FreeSWITCH 底层播放实现，仅约定事件结构与回调入口
 * （见 {@code TtsPlaybackPort}）。前端通过方案一的 WebSocket 事件规范消费。
 */
@Data
@Builder
public class TtsPlaybackEvent {

    /** 事件状态。 */
    private TtsPlaybackStatus status;

    /** 关联的 TTS 任务标识。 */
    private String taskId;

    /** 关联的缓存键/文件标识。 */
    private String cacheKey;

    /** 关联通话/通道。 */
    private String callId;

    private String channelUuid;

    /** 播放的文件路径。 */
    private String filePath;

    /** 失原因（失败时），见 {@code TtsErrorCode}。 */
    private String errorCode;

    private String errorMsg;

    /** 事件时间戳（毫秒）。 */
    private Long timestamp;
}
