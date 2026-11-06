package com.och.esl.service;

import com.och.common.config.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallPlaybackCompletionService {

    private static final String HANGUP_AFTER_PLAYBACK_KEY_PREFIX = "och:call:hangup:after-playback:";
    private static final String FALLBACK_AFTER_PLAYBACK_KEY_PREFIX = "och:call:fallback:after-playback:";
    private static final long MARKER_TTL_MINUTES = 10;

    private final RedisService redisService;

    public void armHangupAfterPlayback(Long callId, String uniqueId) {
        if (callId == null || StringUtils.isBlank(uniqueId)) {
            return;
        }
        redisService.setCacheObject(key(callId, uniqueId), Boolean.TRUE,
                MARKER_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("通话已等待提示音播放完成后挂机 callId:{}, uniqueId:{}", callId, uniqueId);
    }

    public void armHangupAfterPlayback(Long callId, String uniqueId, String fallbackPath) {
        armHangupAfterPlayback(callId, uniqueId);
        if (callId != null && StringUtils.isNotBlank(uniqueId) && StringUtils.isNotBlank(fallbackPath)) {
            redisService.setCacheObject(fallbackKey(callId, uniqueId), fallbackPath,
                    MARKER_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    public String consumeFallbackAfterPlaybackFailure(Long callId, String uniqueId) {
        if (callId == null || StringUtils.isBlank(uniqueId) || !redisService.keyIsExists(key(callId, uniqueId))) {
            return null;
        }
        String fallbackKey = fallbackKey(callId, uniqueId);
        String fallbackPath = redisService.getCacheObject(fallbackKey);
        redisService.deleteObject(fallbackKey);
        return fallbackPath;
    }

    public boolean consumeHangupAfterPlayback(Long callId, String uniqueId) {
        if (callId == null || StringUtils.isBlank(uniqueId)) {
            return false;
        }
        String markerKey = key(callId, uniqueId);
        if (!redisService.keyIsExists(markerKey)) {
            return false;
        }
        redisService.deleteObject(markerKey);
        redisService.deleteObject(fallbackKey(callId, uniqueId));
        return true;
    }

    private String key(Long callId, String uniqueId) {
        return HANGUP_AFTER_PLAYBACK_KEY_PREFIX + callId + ":" + uniqueId;
    }

    private String fallbackKey(Long callId, String uniqueId) {
        return FALLBACK_AFTER_PLAYBACK_KEY_PREFIX + callId + ":" + uniqueId;
    }
}
