package com.och.ai.tts.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.och.ai.tts.TtsErrorCode;
import com.och.ai.tts.TtsException;
import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.entity.TtsTask;
import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.ai.tts.model.TtsPlaybackStatus;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsStatusChangedEvent;
import com.och.ai.tts.model.TtsTaskStatus;
import com.och.ai.tts.model.TtsTaskView;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.service.TtsBusinessService;
import com.och.ai.tts.service.TtsFileService;
import com.och.ai.tts.service.TtsService;
import com.och.ai.tts.service.TtsTaskService;
import com.och.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsBusinessServiceImpl implements TtsBusinessService {

    private final TtsService ttsService;
    private final TtsTaskService ttsTaskService;
    private final TtsFileService ttsFileService;
    private final TtsPlaybackPort playbackPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public TtsTaskView submit(TtsRequest request) {
        TtsTask task = new TtsTask();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        task.setTaskId(taskId);
        task.setSpeechText(request.getText());
        task.setProvider(request.getProvider() == null ? null : request.getProvider().name());
        task.setVoice(request.getVoice());
        task.setParamsJson(paramsJson(request));
        task.setCallId(request.getCallId());
        task.setChannelUuid(request.getChannelUuid());
        task.setStatus(TtsTaskStatus.PENDING.name());
        // 以持久层返回的 taskId 为准（持久层可能在为空时另行生成）
        taskId = ttsTaskService.create(task);
        task.setTaskId(taskId);

        try {
            ttsTaskService.updateStatus(taskId, TtsTaskStatus.SYNTHESIZING.name(), null, null, null);
            var result = ttsService.synthesize(request);

            if (!result.isSuccess()) {
                ttsTaskService.updateStatus(taskId, TtsTaskStatus.FAILED.name(), null,
                        result.getErrorCode(), result.getErrorMsg());
                TtsTaskView view = toView(task, result, TtsTaskStatus.FAILED.name());
                publish(view);
                return view;
            }

            ttsTaskService.updateStatus(taskId, TtsTaskStatus.SUCCESS.name(), result.getFileId(), null, null);

            // 携带通道信息时下发播放（通信链路方案实现 TtsPlaybackPort）
            if (StringUtils.isNotBlank(request.getChannelUuid()) || StringUtils.isNotBlank(request.getCallId())) {
                return dispatchPlayback(task, result);
            }
            TtsTaskView view = toView(task, result, TtsTaskStatus.SUCCESS.name());
            publish(view);
            return view;
        } catch (TtsException e) {
            log.warn("TTS 业务流转参数非法: code={} msg={}", e.getCode(), e.getMessage());
            ttsTaskService.updateStatus(taskId, TtsTaskStatus.FAILED.name(), null, e.getCode(), e.getMessage());
            TtsTaskView view = TtsTaskView.builder()
                    .taskId(taskId).status(TtsTaskStatus.FAILED.name())
                    .text(request.getText()).callId(request.getCallId()).channelUuid(request.getChannelUuid())
                    .errorCode(e.getCode()).errorMsg(e.getMessage()).createTime(new Date()).build();
            publish(view);
            return view;
        }
    }

    private TtsTaskView dispatchPlayback(TtsTask task, com.och.ai.tts.model.TtsResult result) {
        TtsPlaybackRequest pb = TtsPlaybackRequest.builder()
                .taskId(task.getTaskId())
                .cacheKey(result.getCacheKey())
                .filePath(result.getFilePath())
                .format(result.getFormat() == null ? null : result.getFormat().name())
                .sampleRate(result.getSampleRate())
                .callId(task.getCallId())
                .channelUuid(task.getChannelUuid())
                .durationMs(result.getDurationMs())
                .build();
        try {
            PlaybackAck ack = playbackPort.play(pb);
            if (ack != null && ack.isAccepted()) {
                ttsFileService.adjustRefCount(result.getFileId(), 1);
                ttsTaskService.updateStatus(task.getTaskId(), TtsTaskStatus.PLAYING.name(), null, null, null);
                TtsTaskView view = toView(task, result, TtsTaskStatus.PLAYING.name());
                publish(view);
                return view;
            }
            ttsTaskService.updateStatus(task.getTaskId(), TtsTaskStatus.FAILED.name(), null,
                    TtsErrorCode.PLAYBACK_FAILED, ack == null ? "播放未受理" : ack.getMessage());
            TtsTaskView view = toView(task, result, TtsTaskStatus.FAILED.name());
            view.setErrorCode(TtsErrorCode.PLAYBACK_FAILED);
            view.setErrorMsg(ack == null ? "播放未受理" : ack.getMessage());
            publish(view);
            return view;
        } catch (Exception e) {
            log.error("下发播放异常 taskId={}: {}", task.getTaskId(), e.getMessage(), e);
            // 播放失败不影响基础通话，仅回写错误
            ttsTaskService.updateStatus(task.getTaskId(), TtsTaskStatus.FAILED.name(), null,
                    TtsErrorCode.PLAYBACK_FAILED, e.getMessage());
            TtsTaskView view = toView(task, result, TtsTaskStatus.FAILED.name());
            view.setErrorCode(TtsErrorCode.PLAYBACK_FAILED);
            view.setErrorMsg(e.getMessage());
            publish(view);
            return view;
        }
    }

    @Override
    public TtsTaskView getTask(String taskId) {
        TtsTask task = ttsTaskService.getByTaskId(taskId);
        if (task == null) {
            return null;
        }
        TtsFile file = task.getFileId() == null ? null : ttsFileService.getById(task.getFileId());
        return TtsTaskView.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .cacheKey(task.getCacheKey())
                .text(task.getSpeechText())
                .provider(task.getProvider())
                .voice(task.getVoice())
                .fileId(task.getFileId())
                .fileName(file == null ? null : file.getFileName())
                .filePath(file == null ? null : file.getFilePath())
                .listenUrl(file == null ? null : file.getListenUrl())
                .durationMs(file == null ? null : file.getDurationMs())
                .sizeBytes(file == null ? null : file.getFileSize())
                .sampleRate(file == null ? null : file.getSampleRate())
                .format(file == null ? null : file.getFormat())
                .callId(task.getCallId())
                .channelUuid(task.getChannelUuid())
                .errorCode(task.getErrorCode())
                .errorMsg(task.getErrorMsg())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    @Override
    public TtsTaskView replay(String taskId, String callId, String channelUuid) {
        TtsTask task = ttsTaskService.getByTaskId(taskId);
        if (task == null || task.getFileId() == null) {
            return TtsTaskView.builder().taskId(taskId).status(TtsTaskStatus.FAILED.name())
                    .errorCode(TtsErrorCode.FILE_NOT_FOUND).errorMsg("任务或文件不存在").build();
        }
        TtsFile file = ttsFileService.getById(task.getFileId());
        if (file == null) {
            return TtsTaskView.builder().taskId(taskId).status(TtsTaskStatus.FAILED.name())
                    .errorCode(TtsErrorCode.FILE_NOT_FOUND).errorMsg("文件不存在").build();
        }
        task.setCallId(callId);
        task.setChannelUuid(channelUuid);
        com.och.ai.tts.model.TtsResult result = com.och.ai.tts.model.TtsResult.builder()
                .success(true).cacheKey(file.getCacheKey()).fileId(file.getId())
                .filePath(file.getFilePath()).fileName(file.getFileName())
                .listenUrl(file.getListenUrl()).durationMs(file.getDurationMs())
                .sizeBytes(file.getFileSize()).format(file.getFormat() == null ? null
                        : com.och.ai.tts.model.TtsAudioFormat.valueOf(file.getFormat()))
                .sampleRate(file.getSampleRate()).provider(file.getProvider() == null ? null
                        : com.och.ai.tts.model.TtsProviderType.valueOf(file.getProvider()))
                .voice(file.getVoice()).build();
        return dispatchPlayback(task, result);
    }

    @Override
    public void onPlaybackEvent(TtsPlaybackEvent event) {
        if (event == null || StringUtils.isBlank(event.getTaskId())) {
            return;
        }
        TtsTask task = ttsTaskService.getByTaskId(event.getTaskId());
        if (task == null) {
            log.warn("收到未知任务的播放事件 taskId={}", event.getTaskId());
            return;
        }
        TtsTaskStatus status;
        String code = null;
        String msg = null;
        if (event.getStatus() == TtsPlaybackStatus.START) {
            status = TtsTaskStatus.PLAYING;
        } else if (event.getStatus() == TtsPlaybackStatus.COMPLETE) {
            status = TtsTaskStatus.PLAYED;
        } else {
            status = TtsTaskStatus.FAILED;
            code = StringUtils.isBlank(event.getErrorCode()) ? TtsErrorCode.PLAYBACK_FAILED : event.getErrorCode();
            msg = event.getErrorMsg();
            if (task.getFileId() != null) {
                ttsFileService.adjustRefCount(task.getFileId(), -1);
            }
        }
        ttsTaskService.updateStatus(task.getTaskId(), status.name(), null, code, msg);
        publish(TtsTaskView.builder()
                .taskId(task.getTaskId()).status(status.name()).cacheKey(task.getCacheKey())
                .callId(task.getCallId()).channelUuid(task.getChannelUuid())
                .errorCode(code).errorMsg(msg).updateTime(new Date()).build());
        log.info("回写播放事件 taskId={} status={}", task.getTaskId(), status);
    }

    @Override
    public TtsFile getFileByCacheKey(String cacheKey) {
        return ttsFileService.findSuccessByCacheKey(cacheKey);
    }

    @Override
    public TtsFile getFileById(Long id) {
        return ttsFileService.getById(id);
    }

    @Override
    public List<TtsFile> listFiles(String provider, String keyword) {
        return ttsFileService.list(provider, keyword);
    }

    @Override
    public boolean deleteFile(Long id) {
        return ttsFileService.delete(id);
    }

    private void publish(TtsTaskView view) {
        try {
            eventPublisher.publishEvent(TtsStatusChangedEvent.builder()
                    .taskId(view.getTaskId())
                    .status(view.getStatus())
                    .cacheKey(view.getCacheKey())
                    .callId(view.getCallId())
                    .channelUuid(view.getChannelUuid())
                    .errorCode(view.getErrorCode())
                    .errorMsg(view.getErrorMsg())
                    .listenUrl(view.getListenUrl())
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception ignored) {
        }
    }

    private TtsTaskView toView(TtsTask task, com.och.ai.tts.model.TtsResult result, String status) {
        return TtsTaskView.builder()
                .taskId(task.getTaskId())
                .status(status)
                .cacheKey(result.getCacheKey())
                .text(task.getSpeechText())
                .provider(result.getProvider() == null ? null : result.getProvider().name())
                .voice(result.getVoice())
                .format(result.getFormat() == null ? null : result.getFormat().name())
                .sampleRate(result.getSampleRate())
                .fileId(result.getFileId())
                .fileName(result.getFileName())
                .filePath(result.getFilePath())
                .listenUrl(result.getListenUrl())
                .durationMs(result.getDurationMs())
                .sizeBytes(result.getSizeBytes())
                .cacheHit(result.isCacheHit())
                .callId(task.getCallId())
                .channelUuid(task.getChannelUuid())
                .errorCode(result.getErrorCode())
                .errorMsg(result.getErrorMsg())
                .createTime(task.getCreateTime())
                .updateTime(new Date())
                .build();
    }

    private static String paramsJson(TtsRequest request) {
        JSONObject json = new JSONObject();
        json.put("speed", request.getSpeed());
        json.put("pitch", request.getPitch());
        json.put("volume", request.getVolume());
        json.put("sampleRate", request.getSampleRate());
        json.put("format", request.getFormat() == null ? null : request.getFormat().name());
        return json.toJSONString();
    }
}
