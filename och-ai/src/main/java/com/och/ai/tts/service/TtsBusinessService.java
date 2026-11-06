package com.och.ai.tts.service;

import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsTaskView;

import java.util.List;

/**
 * TTS 业务流转（方案 9.6）。
 *
 * <p>参数 → 合成/缓存 → 任务状态 → 通道播放 → 播放事件回写 → 前端状态。
 */
public interface TtsBusinessService {

    /** 提交一次合成（若携带通道信息则同时下发播放）。 */
    TtsTaskView submit(TtsRequest request);

    /** 查询任务状态。 */
    TtsTaskView getTask(String taskId);

    /** 对已存在的文件再次下发播放（复用缓存，不重复合成）。 */
    TtsTaskView replay(String taskId, String callId, String channelUuid);

    /** 通信链路方案回写的播放事件入口。 */
    void onPlaybackEvent(TtsPlaybackEvent event);

    /** 按 cacheKey 取文件记录（试听/FS 读取）。 */
    TtsFile getFileByCacheKey(String cacheKey);

    /** 按 id 取文件记录。 */
    TtsFile getFileById(Long id);

    /** 文件列表查询。 */
    List<TtsFile> listFiles(String provider, String keyword);

    /** 删除文件（含引用检查）。 */
    boolean deleteFile(Long id);
}
