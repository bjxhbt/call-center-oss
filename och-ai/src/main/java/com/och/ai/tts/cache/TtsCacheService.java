package com.och.ai.tts.cache;

import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.common.config.redis.RedisService;
import com.och.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * TTS 缓存键与缓存读写。
 *
 * <p>缓存键 = MD5(文本 + 归一化参数)：相同文本+相同参数必命中同一键，
 * 任一参数变化（音色/语速/采样率/格式…）必产生不同键（方案 8.7）。
 * 命中元数据存在 Redis（快路径），持久化在 tts_file（权威）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsCacheService {

    private static final String CACHE_PREFIX = "tts:file:";
    private static final String TEXT_DIGEST_PREFIX = "tts:digest:";

    private final RedisService redisService;

    /**
     * 构造缓存键。参数归一化后参与指纹，保证可复现。
     */
    public String buildKey(TtsRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(request.getText())).append('|');
        sb.append(request.getProvider() == null ? "" : request.getProvider().name()).append('|');
        sb.append(nullToEmpty(request.getVoice())).append('|');
        sb.append(formatDouble(request.getSpeed())).append('|');
        sb.append(formatDouble(request.getPitch())).append('|');
        sb.append(formatDouble(request.getVolume())).append('|');
        sb.append(request.getSampleRate() == null ? 8000 : request.getSampleRate()).append('|');
        sb.append(request.getFormat() == null ? TtsAudioFormat.WAV.name() : request.getFormat().name());
        return md5(sb.toString());
    }

    /** 文本指纹（仅文本），用于“保留最近成功结果”的降级查询。 */
    public String textDigest(String text) {
        return md5(nullToEmpty(text));
    }

    /** 写入缓存（fileId by cacheKey）。 */
    public void putFileId(String cacheKey, Long fileId, long ttlSeconds) {
        try {
            redisService.setCacheObject(CACHE_PREFIX + cacheKey, fileId,
                    ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (fileId != null) {
                redisService.setCacheObject(CACHE_PREFIX + cacheKey + ":meta", String.valueOf(fileId),
                        ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("写 TTS 缓存失败（忽略，仍以 DB 为准）: {}", e.getMessage());
        }
    }

    /** 读取缓存中的 fileId，未命中返回 null。 */
    public Long getFileId(String cacheKey) {
        try {
            Long id = redisService.getCacheObject(CACHE_PREFIX + cacheKey);
            return id;
        } catch (Exception e) {
            log.warn("读 TTS 缓存失败（降级查 DB）: {}", e.getMessage());
            return null;
        }
    }

    /** 失效缓存。 */
    public void evict(String cacheKey) {
        try {
            redisService.deleteObject(CACHE_PREFIX + cacheKey);
            redisService.deleteObject(CACHE_PREFIX + cacheKey + ":meta");
        } catch (Exception ignored) {
        }
    }

    /** 记录某文本最近一次成功的 fileId（用于主用失败时的降级）。 */
    public void putLastSuccessByDigest(String textDigest, Long fileId, long ttlSeconds) {
        try {
            redisService.setCacheObject(TEXT_DIGEST_PREFIX + textDigest, fileId,
                    ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public Long getLastSuccessByDigest(String textDigest) {
        try {
            return redisService.getCacheObject(TEXT_DIGEST_PREFIX + textDigest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String formatDouble(Double d) {
        if (d == null) {
            return "";
        }
        return String.format("%.3f", d);
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // MD5 一定存在；退化为 hashcode 兜底，保证不抛
            return Integer.toHexString(input.hashCode());
        }
    }

    /** 提供给调用方做文本裁剪前的摘要断言。 */
    public boolean isBlank(Object o) {
        return o == null || (o instanceof String s && StringUtils.isBlank(s));
    }
}
