package com.och.api.service.impl;

import com.och.api.service.IFsPlaybackService;
import com.och.esl.client.FsClient;
import com.och.esl.service.IPlaybackRegistry;
import com.och.system.domain.vo.playback.PlaybackStatusInfo;
import com.och.system.util.WavFileValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Set;

/**
 * FreeSWITCH 通道播放服务实现。
 * <p>
 * - 校验本地可访问的 WAV 文件头(采样率/声道/编码)，不达标仅告警并记录失败状态，不挂断通话；
 * - 通过 ESL playback/break 控制；播放状态由 PlaybackRegistry 维护。
 *
 * @author och
 */
@Slf4j
@Service
public class FsPlaybackServiceImpl implements IFsPlaybackService {

    /** FreeSWITCH 电话录音/播放常用采样率 */
    private static final Set<Integer> ALLOWED_SAMPLE_RATES = Set.of(8000, 16000, 32000, 48000);

    @Lazy
    @Autowired
    private FsClient fsClient;

    @Autowired
    private IPlaybackRegistry playbackRegistry;

    /** 是否校验本地可访问的 wav 文件头(默认开启) */
    @Value("${och.playback.validate-wav:true}")
    private boolean validateWav;

    @Override
    public PlaybackStatusInfo play(String uuid, String file, Integer loop) {
        if (StringUtils.isBlank(uuid) || StringUtils.isBlank(file)) {
            return fail(uuid, "uuid 或 file 为空");
        }
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            return fail(uuid, "无可用 FreeSWITCH ESL 连接");
        }
        // 仅对本地可访问的普通文件做 WAV 头校验；FS 内置流(say:/tone_stream:/shout:// 等)跳过
        String validation = validateIfLocal(file);
        if (validation != null && validateWav) {
            // 校验不通过：记录失败状态，但不挂断通话，由调用方决定降级
            log.warn("播放文件校验未通过，跳过本次播放(不挂断通话) uuid:{} file:{} reason:{}", uuid, file, validation);
            return fail(uuid, "音频校验未通过: " + validation);
        }

        playbackRegistry.requested(uuid, file);
        try {
            if (loop != null && loop > 1) {
                fsClient.playFile(address, uuid, file, loop);
            } else {
                fsClient.playFile(address, uuid, file);
            }
            log.info("已下发通道播放 uuid:{} file:{} loop:{}", uuid, file, loop);
        } catch (Exception e) {
            log.error("通道播放下发异常 uuid:{} file:{}", uuid, file, e);
            return fail(uuid, "播放下发异常: " + e.getMessage());
        }
        return playbackRegistry.get(uuid);
    }

    @Override
    public PlaybackStatusInfo stop(String uuid) {
        if (StringUtils.isBlank(uuid)) {
            return fail(uuid, "uuid 为空");
        }
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            return fail(uuid, "无可用 FreeSWITCH ESL 连接");
        }
        try {
            fsClient.playBreak(address, uuid);
            playbackRegistry.stopped(uuid);
            log.info("已停止通道播放 uuid:{}", uuid);
        } catch (Exception e) {
            log.error("停止通道播放异常 uuid:{}", uuid, e);
            return fail(uuid, "停止播放异常: " + e.getMessage());
        }
        return playbackRegistry.get(uuid);
    }

    @Override
    public PlaybackStatusInfo status(String uuid) {
        return playbackRegistry.get(uuid);
    }

    /**
     * 本地可访问的 wav 文件头校验；返回 null 表示通过或跳过，返回非空字符串表示失败原因。
     */
    private String validateIfLocal(String file) {
        if (file == null || file.isEmpty()) {
            return "文件为空";
        }
        // 非本地文件路径(如 say:/tone_stream:/http://)直接跳过
        if (file.contains("://") || file.startsWith("say:") || file.startsWith("tone_stream")
                || file.startsWith("shout") || !file.toLowerCase().endsWith(".wav")) {
            return null;
        }
        File local = new File(file);
        if (!local.exists() || !local.isFile()) {
            // 文件不在本机(FS 服务器上)，无法校验，跳过
            return null;
        }
        try (FileInputStream in = new FileInputStream(local)) {
            WavFileValidator.WavInfo info = WavFileValidator.validate(in, ALLOWED_SAMPLE_RATES);
            if (!info.isValid()) {
                return info.getError() == null ? "WAV 头解析失败" : info.getError();
            }
            return null;
        } catch (Exception e) {
            return "读取 WAV 头异常: " + e.getMessage();
        }
    }

    private PlaybackStatusInfo fail(String uuid, String reason) {
        playbackRegistry.failed(uuid, reason);
        return playbackRegistry.get(uuid);
    }
}
