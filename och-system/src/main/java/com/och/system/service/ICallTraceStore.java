package com.och.system.service;

import com.och.system.domain.vo.call.CallTrace;

/**
 * 通话链路可追溯信息暂存(基于 Redis，按 callId 存取)。
 * <p>
 * 在路由/桥接阶段写入原始号码、转换后号码、路由与网关等信息，
 * 挂断落库话单时读取并合并，随后清理。
 *
 * @author och
 */
public interface ICallTraceStore {

    void save(Long callId, CallTrace trace);

    CallTrace get(Long callId);

    /** 不存在则新建一个空对象返回，便于链式填充 */
    CallTrace getOrCreate(Long callId);

    void remove(Long callId);
}
