package com.och.esl.service;

/**
 * 桥接状态缓存(按 callId 标记是否已发起 uuid_bridge)。
 * <p>
 * 用于在 FsCallBridgeProcess 入口做幂等守卫，防止坐席多注册(多 Contact 并发振铃)或
 * 重复 ANSWER 事件导致同一通话被多次 uuid_bridge(进而触发"接听后立即挂断")。
 * 进程级缓存；挂断清理时移除。
 *
 * @author och
 */
public interface IBridgeStateCache {

    /**
     * 标记 callId 已发起桥接。返回 true 表示本次是首次(应继续桥接)，false 表示已存在(应跳过)。
     */
    boolean markBridging(Long callId);

    /** 是否已发起桥接但尚未收到真实 CHANNEL_BRIDGE 事件 */
    boolean isBridging(Long callId);

    /** 标记桥接已建立 */
    void markBridged(Long callId);

    /** 是否已建立桥接 */
    boolean isBridged(Long callId);

    /** 首次进入满意度阶段时返回 true */
    boolean markSatisfaction(Long callId);

    /** 是否正在满意度阶段 */
    boolean isSatisfaction(Long callId);

    /** 清理 */
    void remove(Long callId);
}
