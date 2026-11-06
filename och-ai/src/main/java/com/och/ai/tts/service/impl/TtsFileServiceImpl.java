package com.och.ai.tts.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.mapper.TtsFileMapper;
import com.och.ai.tts.service.TtsFileService;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.exception.CommonException;
import com.och.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsFileServiceImpl implements TtsFileService {

    private static final String STATUS_SUCCESS = "SUCCESS";

    private final TtsFileMapper ttsFileMapper;
    private final TtsFileManager fileManager;

    @Override
    public Long save(TtsFile file) {
        TtsFile existing = findAnyByCacheKey(file.getCacheKey());
        if (existing == null) {
            if (file.getRefCount() == null) {
                file.setRefCount(0);
            }
            if (file.getDelFlag() == null) {
                file.setDelFlag(DeleteStatusEnum.DELETE_NO.getIndex());
            }
            file.setCreateTime(new Date());
            file.setUpdateTime(new Date());
            ttsFileMapper.insert(file);
            return file.getId();
        }
        file.setId(existing.getId());
        file.setUpdateTime(new Date());
        ttsFileMapper.updateById(file);
        return existing.getId();
    }

    @Override
    public TtsFile findSuccessByCacheKey(String cacheKey) {
        if (StringUtils.isBlank(cacheKey)) {
            return null;
        }
        return ttsFileMapper.selectOne(new LambdaQueryWrapper<TtsFile>()
                .eq(TtsFile::getCacheKey, cacheKey)
                .eq(TtsFile::getStatus, STATUS_SUCCESS)
                .eq(TtsFile::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .last("LIMIT 1"));
    }

    private TtsFile findAnyByCacheKey(String cacheKey) {
        return ttsFileMapper.selectOne(new LambdaQueryWrapper<TtsFile>()
                .eq(TtsFile::getCacheKey, cacheKey)
                .eq(TtsFile::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .last("LIMIT 1"));
    }

    @Override
    public TtsFile findLatestSuccessByTextDigest(String textDigest) {
        if (StringUtils.isBlank(textDigest)) {
            return null;
        }
        return ttsFileMapper.selectOne(new LambdaQueryWrapper<TtsFile>()
                .eq(TtsFile::getTextDigest, textDigest)
                .eq(TtsFile::getStatus, STATUS_SUCCESS)
                .eq(TtsFile::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .orderByDesc(TtsFile::getId)
                .last("LIMIT 1"));
    }

    @Override
    public TtsFile getById(Long id) {
        return ttsFileMapper.selectById(id);
    }

    @Override
    public List<TtsFile> list(String provider, String keyword) {
        LambdaQueryWrapper<TtsFile> wrapper = new LambdaQueryWrapper<TtsFile>()
                .eq(TtsFile::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .orderByDesc(TtsFile::getId);
        if (StringUtils.isNotBlank(provider)) {
            wrapper.eq(TtsFile::getProvider, provider.toUpperCase());
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(TtsFile::getSpeechText, keyword);
        }
        return ttsFileMapper.selectList(wrapper);
    }

    @Override
    public boolean delete(Long id) {
        TtsFile file = ttsFileMapper.selectById(id);
        if (file == null) {
            throw new CommonException("TTS 文件不存在");
        }
        if (file.getRefCount() != null && file.getRefCount() > 0) {
            throw new CommonException("TTS 文件被业务引用(refCount=" + file.getRefCount() + ")，禁止删除");
        }
        // 软删记录（updateById 只更新非空字段；避免 LambdaUpdateWrapper 在无 Spring 容器/未初始化 lambda 缓存时报错）
        file.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
        file.setUpdateTime(new Date());
        ttsFileMapper.updateById(file);
        // 删除磁盘文件
        boolean removed = fileManager.delete(file.getFilePath());
        log.info("删除 TTS 文件 id={} diskRemoved={}", id, removed);
        return removed;
    }

    @Override
    public int adjustRefCount(Long id, int delta) {
        TtsFile file = ttsFileMapper.selectById(id);
        if (file == null) {
            return 0;
        }
        int current = file.getRefCount() == null ? 0 : file.getRefCount();
        int next = Math.max(0, current + delta);
        file.setRefCount(next);
        file.setUpdateTime(new Date());
        ttsFileMapper.updateById(file);
        return next;
    }

    @Override
    public void touch(Long id) {
        TtsFile file = ttsFileMapper.selectById(id);
        if (file == null) {
            return;
        }
        file.setUpdateTime(new Date());
        ttsFileMapper.updateById(file);
    }
}
