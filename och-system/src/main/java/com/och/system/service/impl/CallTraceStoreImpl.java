package com.och.system.service.impl;

import com.och.common.config.redis.RedisService;
import com.och.common.utils.StringUtils;
import com.och.system.domain.vo.call.CallTrace;
import com.och.system.service.ICallTraceStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的通话链路可追溯信息暂存实现。
 *
 * @author och
 */
@Slf4j
@Service
public class CallTraceStoreImpl implements ICallTraceStore {

    /** 缓存 key 前缀(独立常量，避免修改共享 CacheConstants) */
    private static final String CALL_TRACE_KEY = "call:trace:";

    /** 缓存有效期 6 小时，保证异常挂断后最终被清理 */
    private static final long TTL_HOURS = 6L;

    @Autowired
    private RedisService redisService;

    @Override
    public void save(Long callId, CallTrace trace) {
        if (callId == null || trace == null) {
            return;
        }
        trace.setCallId(callId);
        try {
            redisService.setCacheObject(key(callId), trace, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("保存 CallTrace 失败 callId:{}", callId, e);
        }
    }

    @Override
    public CallTrace get(Long callId) {
        if (callId == null) {
            return null;
        }
        try {
            return redisService.getCacheObject(key(callId));
        } catch (Exception e) {
            log.warn("读取 CallTrace 失败 callId:{}", callId, e);
            return null;
        }
    }

    @Override
    public CallTrace getOrCreate(Long callId) {
        if (callId == null) {
            return new CallTrace();
        }
        CallTrace trace = get(callId);
        if (trace == null) {
            trace = new CallTrace();
            trace.setCallId(callId);
        }
        return trace;
    }

    @Override
    public void remove(Long callId) {
        if (callId == null) {
            return;
        }
        try {
            redisService.deleteObject(key(callId));
        } catch (Exception e) {
            log.warn("删除 CallTrace 失败 callId:{}", callId, e);
        }
    }

    private String key(Long callId) {
        return StringUtils.format(CALL_TRACE_KEY + "%s", callId);
    }
}
