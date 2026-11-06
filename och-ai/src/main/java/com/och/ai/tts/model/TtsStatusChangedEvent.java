package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

/**
 * TTS 状态变更事件（Spring 应用事件）。
 *
 * <p>由 {@code TtsBusinessService} 在任务状态流转时发布，方案一（平台基础/事件规范）
 * 可监听本事件并通过统一 WebSocket 结构转发前端。本方案不直接依赖方案一实现。
 */
@Data
@Builder
public class TtsStatusChangedEvent {

    private String taskId;
    private String status;
    private String cacheKey;
    private String callId;
    private String channelUuid;
    private String errorCode;
    private String errorMsg;
    private String listenUrl;
    private Long timestamp;
}
