package com.och.system.domain.vo.playback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通道播放状态。
 *
 * @author och
 */
@Schema
@Data
public class PlaybackStatusInfo {

    /** 请求中 */
    public static final String STATE_REQUESTED = "REQUESTED";
    /** 播放中 */
    public static final String STATE_PLAYING = "PLAYING";
    /** 播放完成 */
    public static final String STATE_COMPLETED = "COMPLETED";
    /** 已停止 */
    public static final String STATE_STOPPED = "STOPPED";
    /** 播放失败(不挂断通话) */
    public static final String STATE_FAILED = "FAILED";

    @Schema(description = "通道UUID")
    private String uuid;

    @Schema(description = "播放文件")
    private String file;

    @Schema(description = "状态 REQUESTED/PLAYING/COMPLETED/STOPPED/FAILED")
    private String state;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "最近更新时间戳(ms)")
    private Long updateTime;
}
