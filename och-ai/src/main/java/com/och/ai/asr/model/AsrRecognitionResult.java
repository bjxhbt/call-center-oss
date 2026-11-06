package com.och.ai.asr.model;

import lombok.Builder;
import lombok.Data;

/**
 * Provider 回调给会话管理/结果处理的统一结果（方案 7.6 / 7.16）。
 *
 * <p>承载一次识别输出（临时或最终）及延迟度量，供效果评估统计首文本延迟/最终延迟。</p>
 */
@Data
@Builder
public class AsrRecognitionResult {

    /** 来源 Provider。 */
    private AsrProviderType provider;

    /** 本地会话 ID（业务侧）。 */
    private String sessionId;

    /** 业务呼叫 ID。 */
    private Long callId;

    /** FS 通道 UUID。 */
    private String uniqueId;

    /** 识别片段。 */
    private AsrSegment segment;

    /** 首文本延迟（毫秒），仅在本会话首条结果上携带。 */
    private Long firstTextLatencyMs;

    /** 最终文本延迟（毫秒），仅最终结果携带。 */
    private Long finalLatencyMs;

    /** 是否成功（false 时看 errorCode）。 */
    private boolean success;

    /** 错误码（AsrErrorCode）。 */
    private String errorCode;

    /** 错误描述。 */
    private String errorMsg;

    /** 收到该结果的服务端时间（毫秒）。 */
    private long receivedAt;
}
