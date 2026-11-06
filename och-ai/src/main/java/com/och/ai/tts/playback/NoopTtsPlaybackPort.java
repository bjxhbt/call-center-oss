package com.och.ai.tts.playback;

import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 显式空实现：仅供关闭真实通道播放时使用。
 *
 * <p>仅当 {@code och.tts.playback.engine=noop} 时装配。生产默认使用
 * {@code FsTtsPlaybackAdapter}；通过互斥属性而不是依赖组件扫描顺序，确保运行时只有一个
 * {@link TtsPlaybackPort}。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "och.tts.playback", name = "engine", havingValue = "noop")
public class NoopTtsPlaybackPort implements TtsPlaybackPort {

    @Override
    public PlaybackAck play(TtsPlaybackRequest request) {
        log.info("[Noop] TTS 播放占位：taskId={} callId={} channel={} file={}（等待通信链路方案接入）",
                request.getTaskId(), request.getCallId(), request.getChannelUuid(), request.getFilePath());
        return PlaybackAck.builder()
                .accepted(true)
                .message("noop: 等待通信链路方案接入 TtsPlaybackPort")
                .build();
    }
}
