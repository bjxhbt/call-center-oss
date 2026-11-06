package com.och.esl.event;

import lombok.Builder;
import lombok.Data;

/**
 * 通道播放状态变化事件（渠道层 → TTS/AI 业务层）。
 *
 * <p>由 {@code PlaybackRegistryImpl} 在 FreeSWITCH 通道播放状态变化时发布（Spring 事件）。
 * 通信链路方案的 TTS 播放适配器通过 {@code @EventListener} 消费，将播放开始/完成/失败
 * 回写为 {@code TtsPlaybackEvent}，使 TTS 任务从 PLAYING 进入 PLAYED 或 FAILED。</p>
 *
 * <p>载荷只携带必要字段，不携带 {@code EslEvent}，便于跨模块解耦。</p>
 *
 * @author och
 */
@Data
@Builder
public class ChannelPlaybackEvent {

    /** 通道 FS UUID。 */
    private String uuid;

    /** 播放文件。 */
    private String file;

    /** 状态：PLAYING / COMPLETED / STOPPED / FAILED（与 PlaybackStatusInfo.STATE_* 一致）。 */
    private String state;

    /** 失败原因(FAILED 时)。 */
    private String reason;

    /** 事件时间戳(毫秒)。 */
    private Long timestamp;
}
