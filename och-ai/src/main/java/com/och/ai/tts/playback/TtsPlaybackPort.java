package com.och.ai.tts.playback;

import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackRequest;

/**
 * 通道播放端口（与通信链路方案的对接契约，方案 9.6/8.8）。
 *
 * <p>通信链路方案（FreeSWITCH 播放实现）提供本接口的 Bean，负责把音频文件
 * 在指定通道上播放/停止，并通过 {@code TtsBusinessService.onPlaybackEvent} 回写
 * START / COMPLETE / FAILED 事件。
 *
 * <p>本方案不实现、不修改 FreeSWITCH 底层播放；默认装配 {@link NoopTtsPlaybackPort}
 * 作为占位，通信链路方案接入后自动替换。
 */
public interface TtsPlaybackPort {

    /**
     * 提交播放。立即返回受理回执；播放开始/完成/失败以异步事件回写。
     */
    PlaybackAck play(TtsPlaybackRequest request);

    /**
     * 停止指定通道的播放（可选能力）。
     */
    default PlaybackAck stop(String channelUuid) {
        return PlaybackAck.builder().accepted(false).message("not supported").build();
    }
}
