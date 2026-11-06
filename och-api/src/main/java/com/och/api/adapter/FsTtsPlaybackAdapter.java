package com.och.api.adapter;

import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.ai.tts.model.TtsPlaybackStatus;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.service.TtsBusinessService;
import com.och.api.service.IFsPlaybackService;
import com.och.esl.event.ChannelPlaybackEvent;
import com.och.system.domain.vo.playback.PlaybackStatusInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 真实 TTS 通道播放适配器（任务 8.8 / 9.6，方案三 P0 唯一生产实现）。
 *
 * <p>连接 TTS 业务层（{@link TtsPlaybackPort}）与 FreeSWITCH 通道播放（{@link IFsPlaybackService}）：
 * <ul>
 *   <li>{@link #play} 把 TTS 文件按 channelUuid 下发到 FreeSWITCH 通道（经 IFsPlaybackService，含 WAV 校验），返回受理回执；</li>
 *   <li>通过 {@link ChannelPlaybackEvent}（PLAYBACK_START/STOP/失败）异步回写
 *       {@link TtsBusinessService#onPlaybackEvent}，使 TTS 任务从 PLAYING 进入 PLAYED 或 FAILED。</li>
 * </ul>
 *
 * <p><b>唯一生产实现策略</b>（统一播放 Bean 路径，运行时仅一个 {@code TtsPlaybackPort}）：
 * <ul>
 *   <li>本 Bean 在 {@code och.tts.playback.engine=esl} 或<b>未配置时(matchIfMissing=true)</b>装配，
 *       即默认即真实播放——为生产唯一实现；</li>
 *   <li>{@code engine=direct-esl} 才装配 och-ai 的 {@code EslTtsPlaybackPort} 诊断实现；</li>
 *   <li>{@code engine=noop} 才装配 {@code NoopTtsPlaybackPort}；三个实现通过属性值互斥，
 *       不依赖组件扫描顺序。</li>
 * </ul>
 *
 * <p>{@code @Primary} 保证即便出现意外多 Bean，注入也唯一定位到本实现。播放失败仅回写 FAILED，<b>不挂断</b>当前通话。
 *
 * @author och
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(prefix = "och.tts.playback", name = "engine", havingValue = "esl", matchIfMissing = true)
public class FsTtsPlaybackAdapter implements TtsPlaybackPort {

    @Autowired
    private IFsPlaybackService fsPlaybackService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private TtsBusinessService ttsBusinessService;

    /** channelUuid → 上下文(taskId/callId/cacheKey/file)，用于事件回写关联 TTS 任务 */
    private final ConcurrentHashMap<String, PlaybackContext> contextMap = new ConcurrentHashMap<>();

    @Override
    public PlaybackAck play(TtsPlaybackRequest request) {
        if (request == null || StringUtils.isBlank(request.getChannelUuid()) || StringUtils.isBlank(request.getFilePath())) {
            log.warn("TTS 播放请求参数缺失: taskId={} channelUuid={} filePath={}",
                    request == null ? null : request.getTaskId(),
                    request == null ? null : request.getChannelUuid(),
                    request == null ? null : request.getFilePath());
            return PlaybackAck.builder().accepted(false).message("channelUuid 或 filePath 为空").build();
        }
        // 记录上下文，供异步播放事件回写关联
        PlaybackContext ctx = new PlaybackContext();
        ctx.taskId = request.getTaskId();
        ctx.callId = request.getCallId();
        ctx.cacheKey = request.getCacheKey();
        ctx.filePath = request.getFilePath();
        contextMap.put(request.getChannelUuid(), ctx);

        PlaybackStatusInfo info = fsPlaybackService.play(request.getChannelUuid(), request.getFilePath(), null);
        boolean accepted = info != null && !PlaybackStatusInfo.STATE_FAILED.equals(info.getState());
        String message = info == null ? "播放下发无返回" : info.getState();
        log.info("TTS 播放下发 taskId={} channelUuid={} file={} accepted={} state={}",
                request.getTaskId(), request.getChannelUuid(), request.getFilePath(), accepted, message);
        return PlaybackAck.builder().accepted(accepted).message(message).build();
    }

    @Override
    public PlaybackAck stop(String channelUuid) {
        if (StringUtils.isBlank(channelUuid)) {
            return PlaybackAck.builder().accepted(false).message("channelUuid 为空").build();
        }
        fsPlaybackService.stop(channelUuid);
        return PlaybackAck.builder().accepted(true).message("stop submitted").build();
    }

    /**
     * 监听 FreeSWITCH 通道播放状态变化，回写 TTS 任务状态。
     */
    @EventListener
    public void onChannelPlayback(ChannelPlaybackEvent event) {
        if (event == null || StringUtils.isBlank(event.getUuid())) {
            return;
        }
        TtsPlaybackStatus status = mapStatus(event.getState());
        if (status == null) {
            return;
        }
        PlaybackContext ctx = contextMap.get(event.getUuid());
        if (ctx == null || StringUtils.isBlank(ctx.taskId)) {
            // 非 TTS 触发的播放(如 IVR/下班提示语)，无需回写 TTS 任务
            return;
        }
        TtsPlaybackEvent ttsEvent = TtsPlaybackEvent.builder()
                .status(status)
                .taskId(ctx.taskId)
                .cacheKey(ctx.cacheKey)
                .callId(ctx.callId)
                .channelUuid(event.getUuid())
                .filePath(ctx.filePath)
                .errorCode(status == TtsPlaybackStatus.FAILED ? "TTS_013" : null)
                .errorMsg(status == TtsPlaybackStatus.FAILED ? event.getReason() : null)
                .timestamp(event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp())
                .build();
        try {
            ttsBusinessService.onPlaybackEvent(ttsEvent);
            log.info("TTS 播放事件回写 taskId={} channelUuid={} status={}", ctx.taskId, event.getUuid(), status);
        } catch (Exception e) {
            log.error("TTS 播放事件回写异常 taskId={} channelUuid={}", ctx.taskId, event.getUuid(), e);
        }
        // 终态清理上下文，避免内存泄漏
        if (status == TtsPlaybackStatus.COMPLETE || status == TtsPlaybackStatus.FAILED) {
            contextMap.remove(event.getUuid());
        }
    }

    /**
     * 通道播放状态 → TTS 播放事件状态。
     * PLAYING→START，COMPLETED/STOPPED→COMPLETE，FAILED→FAILED，其余(REQUESTED)忽略。
     */
    private TtsPlaybackStatus mapStatus(String state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case PlaybackStatusInfo.STATE_PLAYING -> TtsPlaybackStatus.START;
            case PlaybackStatusInfo.STATE_COMPLETED, PlaybackStatusInfo.STATE_STOPPED -> TtsPlaybackStatus.COMPLETE;
            case PlaybackStatusInfo.STATE_FAILED -> TtsPlaybackStatus.FAILED;
            default -> null;
        };
    }

    @Data
    private static class PlaybackContext {
        private String taskId;
        private String callId;
        private String cacheKey;
        private String filePath;
    }
}
