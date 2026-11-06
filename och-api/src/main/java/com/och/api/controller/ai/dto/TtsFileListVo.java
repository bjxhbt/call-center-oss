package com.och.api.controller.ai.dto;

import com.och.ai.tts.entity.TtsFile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * TTS 文件列表 VO（不暴露 delFlag 等内部字段）。
 */
@Data
@Schema(description = "TTS 文件")
public class TtsFileListVo {

    private Long id;
    private String cacheKey;
    private String provider;
    private String voice;
    private String speechText;
    private String fileName;
    private String listenUrl;
    private Long fileSize;
    private Long durationMs;
    private Integer sampleRate;
    private String format;
    private String status;
    private String errorCode;
    private String errorMsg;
    private Integer refCount;
    private Date createTime;

    public static TtsFileListVo of(TtsFile f) {
        if (f == null) {
            return null;
        }
        TtsFileListVo vo = new TtsFileListVo();
        vo.setId(f.getId());
        vo.setCacheKey(f.getCacheKey());
        vo.setProvider(f.getProvider());
        vo.setVoice(f.getVoice());
        vo.setSpeechText(f.getSpeechText());
        vo.setFileName(f.getFileName());
        vo.setListenUrl(f.getListenUrl());
        vo.setFileSize(f.getFileSize());
        vo.setDurationMs(f.getDurationMs());
        vo.setSampleRate(f.getSampleRate());
        vo.setFormat(f.getFormat());
        vo.setStatus(f.getStatus());
        vo.setErrorCode(f.getErrorCode());
        vo.setErrorMsg(f.getErrorMsg());
        vo.setRefCount(f.getRefCount());
        vo.setCreateTime(f.getCreateTime());
        return vo;
    }
}
