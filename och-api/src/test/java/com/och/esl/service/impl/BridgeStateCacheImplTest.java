package com.och.esl.service.impl;

import com.och.esl.service.IBridgeStateCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 桥接状态缓存幂等测试：同一 callId 仅首次 markBridging 返回 true。
 * BridgeStateCacheImpl 是纯内存实现，无需 Spring 上下文。
 */
class BridgeStateCacheImplTest {

    @Test
    void firstMarkBridgingWins_subsequentReturnFalse() {
        IBridgeStateCache cache = new BridgeStateCacheImpl();
        Long callId = 1001L;
        assertTrue(cache.markBridging(callId), "首次应允许桥接");
        assertFalse(cache.markBridging(callId), "重复应被跳过(幂等)");
        assertFalse(cache.markBridging(callId), "第三次仍跳过");
    }

    @Test
    void markBridged_thenIsBridged() {
        IBridgeStateCache cache = new BridgeStateCacheImpl();
        Long callId = 2002L;
        cache.markBridging(callId);
        assertTrue(cache.isBridging(callId));
        assertFalse(cache.isBridged(callId));
        cache.markBridged(callId);
        assertFalse(cache.isBridging(callId));
        assertTrue(cache.isBridged(callId));
    }

    @Test
    void remove_allowsReBridge() {
        IBridgeStateCache cache = new BridgeStateCacheImpl();
        Long callId = 3003L;
        assertTrue(cache.markBridging(callId));
        cache.remove(callId);
        assertTrue(cache.markBridging(callId), "清理后应可再次桥接");
    }

    @Test
    void nullCallId_isBridgedFalse() {
        IBridgeStateCache cache = new BridgeStateCacheImpl();
        assertFalse(cache.isBridged(null));
    }
}
