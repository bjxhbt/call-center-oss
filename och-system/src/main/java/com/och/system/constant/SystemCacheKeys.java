package com.och.system.constant;

/**
 * 系统侧 Redis 缓存 key 定义（任务 9.8）。
 *
 * <p>och-common 的 {@code CacheConstants} 为外部 JAR 无法扩展，系统模块新增的 key 统一在此声明，
 * 避免散落在各服务中产生魔法字符串。</p>
 *
 * @author och
 */
public final class SystemCacheKeys {

    private SystemCacheKeys() {
    }

    /** ESL 事件幂等锁 key 前缀：fs:event:idem:{callId}:{eventType} */
    public static final String EVENT_IDEMPOTENT_PREFIX = "fs:event:idem:";

    /** 通话话单幂等/去重 key 前缀：fs:call:record:idem:{callId} */
    public static final String CALL_RECORD_IDEMPOTENT_PREFIX = "fs:call:record:idem:";

    /** 录音记录幂等 key 前缀：fs:call:record:audio:{callId} */
    public static final String AUDIO_RECORD_IDEMPOTENT_PREFIX = "fs:call:record:audio:";

    /** 转写记录幂等 key 前缀：fs:call:transcribe:{callId} */
    public static final String TRANSCRIBE_IDEMPOTENT_PREFIX = "fs:call:transcribe:";

    /** 幂等锁默认 TTL（秒），覆盖一次呼叫生命周期 + 余量。 */
    public static final long DEFAULT_EVENT_TTL_SECONDS = 24 * 3600L;

    /**
     * 构造 ESL 事件幂等锁 key。
     */
    public static String eventIdempotentKey(String callId, String eventType) {
        return EVENT_IDEMPOTENT_PREFIX + callId + ":" + eventType;
    }

    /**
     * 构造通话话单幂等 key。
     */
    public static String callRecordIdempotentKey(String callId) {
        return CALL_RECORD_IDEMPOTENT_PREFIX + callId;
    }
}
