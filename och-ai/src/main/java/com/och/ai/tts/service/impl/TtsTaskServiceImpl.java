package com.och.ai.tts.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.och.ai.tts.entity.TtsTask;
import com.och.ai.tts.mapper.TtsTaskMapper;
import com.och.ai.tts.service.TtsTaskService;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsTaskServiceImpl implements TtsTaskService {

    private final TtsTaskMapper ttsTaskMapper;

    @Override
    public String create(TtsTask task) {
        if (StringUtils.isBlank(task.getTaskId())) {
            task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (StringUtils.isBlank(task.getStatus())) {
            task.setStatus("PENDING");
        }
        if (task.getDelFlag() == null) {
            task.setDelFlag(DeleteStatusEnum.DELETE_NO.getIndex());
        }
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        ttsTaskMapper.insert(task);
        return task.getTaskId();
    }

    @Override
    public TtsTask getByTaskId(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return null;
        }
        return ttsTaskMapper.selectOne(new LambdaQueryWrapper<TtsTask>()
                .eq(TtsTask::getTaskId, taskId)
                .eq(TtsTask::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .last("LIMIT 1"));
    }

    @Override
    public int updateStatus(String taskId, String status, Long fileId, String errorCode, String errorMsg) {
        LambdaUpdateWrapper<TtsTask> wrapper = new LambdaUpdateWrapper<TtsTask>()
                .eq(TtsTask::getTaskId, taskId)
                .set(TtsTask::getStatus, status)
                .set(TtsTask::getUpdateTime, new Date());
        if (fileId != null) {
            wrapper.set(TtsTask::getFileId, fileId);
        }
        if (errorCode != null) {
            wrapper.set(TtsTask::getErrorCode, errorCode);
        }
        if (errorMsg != null) {
            wrapper.set(TtsTask::getErrorMsg, errorMsg);
        }
        return ttsTaskMapper.update(null, wrapper);
    }
}
