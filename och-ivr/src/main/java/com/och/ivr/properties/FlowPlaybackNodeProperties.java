package com.och.ivr.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FlowPlaybackNodeProperties extends FlowNodeProperties{

    /**
     * 打断
     */
    private Boolean interrupt;

    /**
     * DTMF keys allowed to interrupt playback. Blank accepts every key.
     */
    private String interruptKeys;

    /**
     * Store playback-time DTMF for the next menu node to consume.
     */
    private String dtmfForwardVariable;

    /**
     * Start call recording after this playback finishes normally.
     */
    private Boolean startRecordingAfterPlayback;

    /**
     * 播放类型 0-文件 1-内容
     */
    private Integer playbackType;

    /**
     * 播放文件
     */
    private Long fileId;

    /**
     * Store the resolved file ID in an IVR instance variable before playback.
     */
    private String rememberFileVariable;

    /**
     * Resolve the playback file ID from an IVR instance variable.
     */
    private String fileIdVariable;

    /**
     * 播放内容
     */
    private String content;

    /**
     * 播放次数
     */
    private Integer num;
}
