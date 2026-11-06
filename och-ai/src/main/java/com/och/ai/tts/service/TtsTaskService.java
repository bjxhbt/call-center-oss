package com.och.ai.tts.service;

import com.och.ai.tts.entity.TtsTask;

/**
 * TTS 任务记录持久化（tts_task）。
 */
public interface TtsTaskService {

    /** 创建一条 PENDING/SYNTHESIZING 任务，返回任务标识。 */
    String create(com.och.ai.tts.entity.TtsTask task);

    TtsTask getByTaskId(String taskId);

    /** 更新状态（及可选的 fileId/error）。 */
    int updateStatus(String taskId, String status, Long fileId, String errorCode, String errorMsg);
}
