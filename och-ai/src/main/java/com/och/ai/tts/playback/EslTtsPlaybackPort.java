package com.och.ai.tts.playback;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 基于 FreeSWITCH ESL 的真实通道播放实现（方案 9.6 / 8.8）。
 *
 * <p>仅当 {@code och.tts.playback.engine=direct-esl} 时装配，作为绕过
 * {@code IFsPlaybackService} 的显式诊断/兜底实现；
 * 通过既有 {@link FsClient}（ESL 客户端单例）下发播放/停止命令，**不修改 FreeSWITCH 底层实现**。
 *
 * <p>播放方式（{@code och.tts.playback.method}）：
 * <ul>
 *   <li>execute（默认）：sendmsg execute playback，与 FsPlaybackServiceImpl 一致。</li>
 *   <li>broadcast：uuid_broadcast（bgapi，fire-and-forget）。</li>
 * </ul>
 *
 * <p>本端口只负责“下发命令并回执受理状态”。任务 PLAYING 状态由 TtsBusinessService 在受理时置位；
 * 播放完成/失败由 FreeSWITCH 事件经平台 ESL 处理器回写 {@code POST /tts/v1/playback/event}。
 * 文件路径需为 FreeSWITCH 可读的绝对路径（och-api 与 FS 同机部署时直接可用）。
 *
 * <p>任何 FS 异常都被吞掉并返回 accepted=false，**不会抛穿影响当前通话**。
 *
 * <p><b>去重</b>：生产 {@code engine=esl} 仅装配 och-api 的 {@code FsTtsPlaybackAdapter}
 * （经 IFsPlaybackService 走 WAV 校验与统一事件回写）；本实现使用独立值 {@code direct-esl}。
 * 与 {@code noop}/{@code esl} 互斥，不再依赖 {@code @ConditionalOnMissingBean} 的扫描顺序。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "och.tts.playback", name = "engine", havingValue = "direct-esl")
public class EslTtsPlaybackPort implements TtsPlaybackPort {

    private static final String CMD_UUID_BROADCAST = "uuid_broadcast";
    private static final String METHOD_BROADCAST = "broadcast";

    private final FsClient fsClient;
    private final TtsProperties properties;

    public EslTtsPlaybackPort(@Lazy FsClient fsClient, TtsProperties properties) {
        this.fsClient = fsClient;
        this.properties = properties;
    }

    @Override
    public PlaybackAck play(TtsPlaybackRequest request) {
        if (request == null || StringUtils.isBlank(request.getChannelUuid())) {
            return PlaybackAck.builder().accepted(false).message("缺少 channelUuid").build();
        }
        if (StringUtils.isBlank(request.getFilePath())) {
            return PlaybackAck.builder().accepted(false).message("缺少音频文件路径").build();
        }
        String address;
        try {
            address = fsClient.anyAddress();
        } catch (Exception e) {
            log.warn("获取 FreeSWITCH 地址失败: {}", e.getMessage());
            return PlaybackAck.builder().accepted(false).message("FreeSWITCH 未连接").build();
        }
        if (StringUtils.isBlank(address)) {
            log.warn("TTS 播放未受理：无可用 FreeSWITCH 连接（taskId={}）", request.getTaskId());
            return PlaybackAck.builder().accepted(false).message("FreeSWITCH 未连接").build();
        }

        String uuid = request.getChannelUuid();
        String file = request.getFilePath();
        String method = properties.getPlayback() == null || properties.getPlayback().getMethod() == null
                ? "execute" : properties.getPlayback().getMethod().toLowerCase();
        try {
            if (METHOD_BROADCAST.equals(method)) {
                // uuid_broadcast <uuid> playback::<file>  (bgapi，返回 job id 或 null)
                String args = uuid + " playback::" + file;
                String jobId = fsClient.sendAsyncMsg(address, CMD_UUID_BROADCAST, args);
                if (StringUtils.isBlank(jobId)) {
                    return PlaybackAck.builder().accepted(false)
                            .message("uuid_broadcast 未受理（通道可能不存在）").build();
                }
                log.info("TTS 播放(uuid_broadcast) address={} uuid={} jobId={} file={}", address, uuid, jobId, file);
            } else {
                // sendmsg execute playback（默认，与 FsPlaybackServiceImpl 一致）
                fsClient.playFile(address, uuid, file);
                log.info("TTS 播放(execute playback) address={} uuid={} file={}", address, uuid, file);
            }
            return PlaybackAck.builder().accepted(true).message("submitted to " + address).build();
        } catch (Exception e) {
            // 播放失败不影响当前通话
            log.error("TTS 下发播放命令异常 address={} uuid={}: {}", address, uuid, e.getMessage(), e);
            return PlaybackAck.builder().accepted(false)
                    .message("下发播放命令异常: " + e.getMessage()).build();
        }
    }

    @Override
    public PlaybackAck stop(String channelUuid) {
        if (StringUtils.isBlank(channelUuid)) {
            return PlaybackAck.builder().accepted(false).message("缺少 channelUuid").build();
        }
        String address;
        try {
            address = fsClient.anyAddress();
            if (StringUtils.isBlank(address)) {
                return PlaybackAck.builder().accepted(false).message("FreeSWITCH 未连接").build();
            }
            fsClient.playBreak(address, channelUuid);
            log.info("TTS 停止播放 address={} uuid={}", address, channelUuid);
            return PlaybackAck.builder().accepted(true).message("break sent").build();
        } catch (Exception e) {
            log.error("TTS 停止播放异常 uuid={}: {}", channelUuid, e.getMessage());
            return PlaybackAck.builder().accepted(false).message("停止播放异常: " + e.getMessage()).build();
        }
    }
}
