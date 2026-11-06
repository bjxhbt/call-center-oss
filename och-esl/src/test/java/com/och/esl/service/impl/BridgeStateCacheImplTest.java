package com.och.esl.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeStateCacheImplTest {

    @Test
    void bridgeEventCannotDowngradeSatisfactionState() {
        BridgeStateCacheImpl cache = new BridgeStateCacheImpl();
        cache.markBridged(123L);
        assertThat(cache.markSatisfaction(123L)).isTrue();

        cache.markBridged(123L);

        assertThat(cache.isSatisfaction(123L)).isTrue();
        assertThat(cache.isBridged(123L)).isFalse();
    }
}
