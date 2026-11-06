package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * TTS 任务对外视图（前端/接口消费）。
 */
@Data
@Builder
public class TtsTaskView {

    private String taskId;
    private String status;
    private String cacheKey;
    private String text;
    private String provider;
    private String voice;
    private String format;
    private Integer sampleRate;
    private Long fileId;
    private String fileName;
    private String filePath;
    private String listenUrl;
    private Long durationMs;
    private Long sizeBytes;
    private Boolean cacheHit;
    private String callId;
    private String channelUuid;
    private String errorCode;
    private String errorMsg;
    private Date createTime;
    private Date updateTime;
}
