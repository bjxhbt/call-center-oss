package com.och.system.service;

import com.och.common.config.redis.RedisService;
import com.och.system.constant.SystemCacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ESL 事件幂等服务（任务 9.8）。
 *
 * <p>为 PARK / ANSWER / BRIDGE / HANGUP 等可能重复下发的 ESL 事件提供幂等处理基础能力：
 * 以 {@code (callId, eventType)} 作为幂等键，借助 Redis {@code SET NX EX} 语义保证同一事件
 * 在 TTL 窗口内只被处理一次。</p>
 *
 * <p>典型用法（方案三通信链路在 ESL 事件处理器中调用）：</p>
 * <pre>
 * if (!callEventIdempotentService.tryAcquire(callId, "ANSWER")) {
 *     log.info("重复 ANSWER 事件已忽略 callId:{}", callId);
 *     return;
 * }
 * // 首次出现，执行应答处理...
 * </pre>
 *
 * <p>幂等键仅作为“去重闸门”，真正的“不覆盖已有数据”由
 * {@link ICallRecordService#saveOrUpdateByCallId} 的守卫式更新与数据库唯一约束共同保证。</p>
 *
 * @author och
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallEventIdempotentService {

    private final RedisService redisService;

    /**
     * 尝试获取幂等锁（默认 TTL）。
     *
     * @param callId    业务呼叫ID
     * @param eventType 事件类型（如 PARK / ANSWER / BRIDGE / HANGUP）
     * @return true=首次出现，应继续处理；false=重复事件，应跳过
     */
    public boolean tryAcquire(String callId, String eventType) {
        return tryAcquire(callId, eventType, SystemCacheKeys.DEFAULT_EVENT_TTL_SECONDS);
    }

    /**
     * 尝试获取幂等锁（自定义 TTL）。
     *
     * @param callId     业务呼叫ID
     * @param eventType  事件类型
     * @param ttlSeconds 锁 TTL（秒），需大于一次呼叫最长生命周期
     * @return true=首次出现；false=重复事件
     */
    public boolean tryAcquire(String callId, String eventType, long ttlSeconds) {
        // 无法构造幂等键时不阻断业务（由下游守卫式更新兜底），但记录告警
        if (callId == null || callId.isEmpty() || eventType == null || eventType.isEmpty()) {
            log.warn("幂等键参数缺失 callId:{}, eventType:{}，跳过幂等校验", callId, eventType);
            return true;
        }
        String key = SystemCacheKeys.eventIdempotentKey(callId, eventType);
        try {
            Boolean acquired = redisService.tryLock(key, callId, ttlSeconds);
            boolean first = Boolean.TRUE.equals(acquired);
            if (!first) {
                log.info("重复ESL事件已忽略 callId:{}, eventType:{}", callId, eventType);
            }
            return first;
        } catch (Exception e) {
            // Redis 异常时不阻断业务，下游守卫式更新与唯一约束仍可保证最终一致
            log.error("幂等锁获取异常 callId:{}, eventType:{}", callId, eventType, e);
            return true;
        }
    }
}
