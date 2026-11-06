package com.och.esl.service.impl;

import com.och.esl.service.IBridgeStateCache;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的桥接状态缓存实现。
 *
 * @author och
 */
@Service
public class BridgeStateCacheImpl implements IBridgeStateCache {

    private final ConcurrentHashMap<Long, State> store = new ConcurrentHashMap<>();

    @Override
    public boolean markBridging(Long callId) {
        if (callId == null) {
            return true;
        }
        State existing = store.putIfAbsent(callId, State.BRIDGING);
        return existing == null;
    }

    @Override
    public boolean isBridging(Long callId) {
        if (callId == null) {
            return false;
        }
        return store.get(callId) == State.BRIDGING;
    }

    @Override
    public void markBridged(Long callId) {
        if (callId != null) {
            store.compute(callId, (key, current) ->
                    current == State.SATISFACTION ? current : State.BRIDGED);
        }
    }

    @Override
    public boolean isBridged(Long callId) {
        if (callId == null) {
            return false;
        }
        return store.get(callId) == State.BRIDGED;
    }

    @Override
    public boolean markSatisfaction(Long callId) {
        if (callId == null) {
            return false;
        }
        return store.replace(callId, State.BRIDGED, State.SATISFACTION);
    }

    @Override
    public boolean isSatisfaction(Long callId) {
        if (callId == null) {
            return false;
        }
        return store.get(callId) == State.SATISFACTION;
    }

    @Override
    public void remove(Long callId) {
        if (callId != null) {
            store.remove(callId);
        }
    }

    private enum State {
        BRIDGING, BRIDGED, SATISFACTION
    }
}
