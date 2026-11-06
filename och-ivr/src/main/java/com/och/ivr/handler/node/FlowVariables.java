package com.och.ivr.handler.node;

import com.och.common.config.redis.RedisService;
import com.och.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程变量读写工具。变量按 IVR 实例(instanceId)维度存储在 Redis。
 * 对应原系统 assign/condition/database 节点共享的流程变量作用域。
 */
public final class FlowVariables {

    /** Redis key 前缀：CALL_IVR_FLOW_VARIABLES:{instanceId} */
    public static final String KEY_PREFIX = "call:ivr:variables:";

    private FlowVariables() {
    }

    public static String key(Long instanceId) {
        return KEY_PREFIX + instanceId;
    }

    public static void put(RedisService redisService, Long instanceId, String name, Object value) {
        if (redisService == null || instanceId == null || StringUtils.isBlank(name)) {
            return;
        }
        String strVal = value == null ? "" : String.valueOf(value);
        redisService.setCacheMapValue(key(instanceId), name, strVal);
    }

    public static String get(RedisService redisService, Long instanceId, String name) {
        if (redisService == null || instanceId == null || StringUtils.isBlank(name)) {
            return null;
        }
        Object val = redisService.getCacheMapValue(key(instanceId), name);
        return val == null ? null : String.valueOf(val);
    }

    public static Map<String, String> all(RedisService redisService, Long instanceId) {
        if (redisService == null || instanceId == null) {
            return new HashMap<>();
        }
        Map<String, String> map = new HashMap<>();
        Map<String, Object> raw = redisService.getCacheMap(key(instanceId));
        if (raw != null) {
            raw.forEach((k, v) -> map.put(k, v == null ? "" : String.valueOf(v)));
        }
        return map;
    }

    public static void clear(RedisService redisService, Long instanceId) {
        if (redisService == null || instanceId == null) {
            return;
        }
        redisService.deleteObject(key(instanceId));
    }

    /** 递增数值变量，返回递增后的整数值(变量不存在时按0计) */
    public static int increment(RedisService redisService, Long instanceId, String name) {
        String cur = get(redisService, instanceId, name);
        int val = 0;
        if (StringUtils.isNotBlank(cur)) {
            try {
                val = Integer.parseInt(cur.trim());
            } catch (NumberFormatException ignored) {
                val = 0;
            }
        }
        val++;
        put(redisService, instanceId, name, val);
        return val;
    }
}
