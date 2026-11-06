package com.och.ai.tts.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.och.ai.tts.TtsErrorCode;
import com.och.ai.tts.TtsException;
import com.och.ai.tts.cache.TtsCacheService;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.provider.TtsProviderRegistry;
import com.och.ai.tts.provider.TtsServiceProvider;
import com.och.ai.tts.service.TtsFileService;
import com.och.ai.tts.service.TtsService;
import com.och.common.enums.DeleteStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsServiceImpl implements TtsService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final TtsProperties properties;
    private final TtsProviderRegistry registry;
    private final TtsCacheService cacheService;
    private final TtsFileService fileService;
    private final TtsFileManager fileManager;

    @Override
    public TtsResult synthesize(TtsRequest request) {
        validate(request);

        String cacheKey = cacheService.buildKey(request);
        String textDigest = cacheService.textDigest(request.getText());

        // 1. 缓存命中（forceRefresh / skipCache 时跳过）
        if (properties.isCacheEnabled() && !request.isForceRefresh() && !request.isSkipCache()) {
            TtsFile hit = resolveCacheHit(cacheKey);
            if (hit != null && fileManager.exists(hit.getFilePath())) {
                cacheService.putFileId(cacheKey, hit.getId(), properties.getCacheTtlSeconds());
                log.info("TTS 缓存命中 cacheKey={} fileId={} provider={}", cacheKey, hit.getId(), hit.getProvider());
                return toResult(hit, cacheKey, true);
            }
        }

        // 2. 选择并调用 Provider（registry 内置 MOCK 兜底，保证不抛 NPE）
        TtsServiceProvider provider = registry.select(request.getProvider());
        TtsResult result = provider.synthesize(request);
        result.setCacheKey(cacheKey);

        if (!result.isSuccess()) {
            // 3a. 失败：明确返回错误，落一条 FAILED 记录便于诊断；不自动切第二云（避免复杂切换）
            recordFailure(cacheKey, textDigest, request, result);
            log.warn("TTS 合成失败 provider={} code={} msg={}", result.getProvider(),
                    result.getErrorCode(), result.getErrorMsg());
            return result;
        }

        // 3b. 成功：落盘 + 持久化 + 回写缓存
        try {
            TtsFileManager.Written written = fileManager.write(cacheKey, result.getAudioBytes(),
                    result.getFormat() == null ? TtsAudioFormat.WAV.name() : result.getFormat().name());
            TtsFile rec = toEntity(cacheKey, textDigest, request, result, written);
            Long fileId = fileService.save(rec);
            result.setFileId(fileId);
            result.setFileName(written.fileName());
            result.setFilePath(written.filePath());
            result.setListenUrl("/tts/v1/file/" + cacheKey);
            result.setCacheHit(false);
            if (properties.isCacheEnabled() && !request.isSkipCache()) {
                cacheService.putFileId(cacheKey, fileId, properties.getCacheTtlSeconds());
                cacheService.putLastSuccessByDigest(textDigest, fileId, properties.getCacheTtlSeconds());
            }
            log.info("TTS 合成成功 provider={} fileId={} bytes={} durationMs={} elapsedMs={}",
                    result.getProvider(), fileId, result.getSizeBytes(), result.getDurationMs(), result.getElapsedMs());
            return result;
        } catch (Exception e) {
            log.error("TTS 文件落盘失败 cacheKey={}: {}", cacheKey, e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorCode(TtsErrorCode.FILE_WRITE_FAILED);
            result.setErrorMsg("TTS 文件落盘失败: " + e.getMessage());
            return result;
        }
    }

    private TtsFile resolveCacheHit(String cacheKey) {
        Long fileId = cacheService.getFileId(cacheKey);
        if (fileId != null) {
            TtsFile f = fileService.getById(fileId);
            if (f != null && STATUS_SUCCESS.equals(f.getStatus())) {
                return f;
            }
        }
        return fileService.findSuccessByCacheKey(cacheKey);
    }

    private void recordFailure(String cacheKey, String textDigest, TtsRequest request, TtsResult result) {
        try {
            TtsFile rec = new TtsFile();
            rec.setCacheKey(cacheKey);
            rec.setTextDigest(textDigest);
            rec.setSpeechText(truncate(request.getText(), 500));
            rec.setProvider(result.getProvider() == null ? null : result.getProvider().name());
            rec.setVoice(request.getVoice());
            rec.setParamsJson(paramsJson(request));
            rec.setStatus(STATUS_FAILED);
            rec.setErrorCode(result.getErrorCode());
            rec.setErrorMsg(truncate(result.getErrorMsg(), 500));
            rec.setSampleRate(request.getSampleRate());
            rec.setFormat(request.getFormat() == null ? null : request.getFormat().name());
            rec.setRefCount(0);
            rec.setDelFlag(DeleteStatusEnum.DELETE_NO.getIndex());
            rec.setCreateTime(new Date());
            rec.setUpdateTime(new Date());
            fileService.save(rec);
        } catch (Exception ignored) {
            // 诊断记录失败不影响主流程
        }
    }

    private TtsFile toEntity(String cacheKey, String textDigest, TtsRequest request, TtsResult result,
                             TtsFileManager.Written written) {
        TtsFile rec = new TtsFile();
        rec.setCacheKey(cacheKey);
        rec.setTextDigest(textDigest);
        rec.setSpeechText(truncate(request.getText(), 1000));
        rec.setProvider(result.getProvider() == null ? null : result.getProvider().name());
        rec.setVoice(result.getVoice());
        rec.setParamsJson(paramsJson(request));
        rec.setFileName(written.fileName());
        rec.setFilePath(written.filePath());
        rec.setListenUrl("/tts/v1/file/" + cacheKey);
        rec.setFileSize(result.getSizeBytes());
        rec.setDurationMs(result.getDurationMs());
        rec.setSampleRate(result.getSampleRate());
        rec.setFormat(result.getFormat() == null ? null : result.getFormat().name());
        rec.setStatus(STATUS_SUCCESS);
        rec.setRefCount(0);
        rec.setDelFlag(DeleteStatusEnum.DELETE_NO.getIndex());
        return rec;
    }

    private TtsResult toResult(TtsFile hit, String cacheKey, boolean cacheHit) {
        return TtsResult.builder()
                .success(true)
                .provider(hit.getProvider() == null ? null
                        : com.och.ai.tts.model.TtsProviderType.valueOf(hit.getProvider()))
                .voice(hit.getVoice())
                .format(hit.getFormat() == null ? null
                        : com.och.ai.tts.model.TtsAudioFormat.valueOf(hit.getFormat()))
                .sampleRate(hit.getSampleRate())
                .durationMs(hit.getDurationMs())
                .sizeBytes(hit.getFileSize())
                .fileName(hit.getFileName())
                .filePath(hit.getFilePath())
                .listenUrl(hit.getListenUrl() == null ? "/tts/v1/file/" + cacheKey : hit.getListenUrl())
                .cacheKey(cacheKey)
                .cacheHit(cacheHit)
                .fileId(hit.getId())
                .providerStatus("cache-hit")
                .build();
    }

    private void validate(TtsRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new TtsException(TtsErrorCode.EMPTY_TEXT);
        }
        if (request.getText().length() > properties.getMaxTextLength()) {
            throw new TtsException(TtsErrorCode.TEXT_TOO_LONG,
                    "文本长度 " + request.getText().length() + " 超过限制 " + properties.getMaxTextLength());
        }
        if (request.getSpeed() != null && (request.getSpeed() < -1.0 || request.getSpeed() > 1.0)) {
            throw new TtsException(TtsErrorCode.INVALID_PARAM, "speed 取值范围 [-1.0, 1.0]");
        }
        if (request.getPitch() != null && (request.getPitch() < -1.0 || request.getPitch() > 1.0)) {
            throw new TtsException(TtsErrorCode.INVALID_PARAM, "pitch 取值范围 [-1.0, 1.0]");
        }
        if (request.getVolume() != null && (request.getVolume() < 0.0 || request.getVolume() > 1.0)) {
            throw new TtsException(TtsErrorCode.INVALID_PARAM, "volume 取值范围 [0.0, 1.0]");
        }
        if (request.getSampleRate() != null && request.getSampleRate() <= 0) {
            throw new TtsException(TtsErrorCode.INVALID_PARAM, "sampleRate 必须为正");
        }
    }

    private static String paramsJson(TtsRequest request) {
        JSONObject json = new JSONObject();
        json.put("speed", request.getSpeed());
        json.put("pitch", request.getPitch());
        json.put("volume", request.getVolume());
        json.put("sampleRate", request.getSampleRate());
        json.put("format", request.getFormat() == null ? null : request.getFormat().name());
        json.put("provider", request.getProvider() == null ? null : request.getProvider().name());
        return json.toJSONString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
