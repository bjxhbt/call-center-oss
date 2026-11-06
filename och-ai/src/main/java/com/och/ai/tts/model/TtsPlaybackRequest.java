package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

/**
 * 下发给通信链路方案的播放请求（方案 9.6）。
 *
 * <p>本方案不修改 FreeSWITCH 底层播放实现；通过 {@code TtsPlaybackPort} 把
 * “在指定通道播放这个文件”交给通信链路方案，并约定异步事件回写结构。
 */
@Data
@Builder
public class TtsPlaybackRequest {

    private String taskId;
    private String cacheKey;
    /** FreeSWITCH 可读的文件绝对路径。 */
    private String filePath;
    private String format;
    private Integer sampleRate;
    private String callId;
    /** FreeSWITCH 通道 UUID。 */
    private String channelUuid;
    private Long durationMs;
}
