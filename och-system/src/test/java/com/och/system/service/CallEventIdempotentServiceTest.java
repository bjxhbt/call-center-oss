package com.och.system.service;

import com.och.common.config.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link CallEventIdempotentService} 单元测试（任务 9.8）。
 * 模拟 Redis tryLock 返回值，验证 PARK/ANSWER/BRIDGE/HANGUP 等重复事件的去重闸门。
 *
 * @author och
 */
class CallEventIdempotentServiceTest {

    private RedisService redisService;
    private CallEventIdempotentService service;

    @BeforeEach
    void setUp() {
        redisService = Mockito.mock(RedisService.class);
        service = new CallEventIdempotentService(redisService);
    }

    @Test
    void firstOccurrenceAcquiresLock() {
        when(redisService.tryLock(anyString(), anyString(), anyLong())).thenReturn(Boolean.TRUE);
        assertThat(service.tryAcquire("CALL-001", "ANSWER")).isTrue();
    }

    @Test
    void duplicateEventRejected() {
        // 第二次同 callId+eventType 的 tryLock 返回 false（已被占用）
        when(redisService.tryLock(anyString(), anyString(), anyLong())).thenReturn(Boolean.FALSE);
        assertThat(service.tryAcquire("CALL-001", "ANSWER")).isFalse();
    }

    @Test
    void blankKeyDoesNotBlock() {
        // 无法构造幂等键时不应阻断业务（由下游守卫兜底）
        assertThat(service.tryAcquire(null, "ANSWER")).isTrue();
        assertThat(service.tryAcquire("CALL-001", null)).isTrue();
        assertThat(service.tryAcquire("", "")).isTrue();
    }

    @Test
    void redisExceptionDoesNotBlock() {
        // Redis 异常时放行，避免基础设施抖动导致通话事件丢失
        when(redisService.tryLock(anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("redis down"));
        assertThat(service.tryAcquire("CALL-001", "HANGUP")).isTrue();
    }

    @Test
    void keyIncludesCallIdAndEventType() {
        when(redisService.tryLock(eq("fs:event:idem:CALL-002:BRIDGE"), anyString(), anyLong()))
                .thenReturn(Boolean.TRUE);
        assertThat(service.tryAcquire("CALL-002", "BRIDGE")).isTrue();
        Mockito.verify(redisService).tryLock(eq("fs:event:idem:CALL-002:BRIDGE"), eq("CALL-002"), anyLong());
    }
}
