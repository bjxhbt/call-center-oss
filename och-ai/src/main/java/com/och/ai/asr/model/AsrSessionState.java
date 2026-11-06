package com.och.ai.asr.model;

/**
 * 转写会话状态（方案 7.5 / 7.15）。
 *
 * <p>用于会话生命周期与异常判定：CREATED→CONNECTING→ACTIVE→FINALIZING→FINISHED；
 * 任一阶段失败进入 ERROR；显式取消进入 CANCELLED。</p>
 */
public enum AsrSessionState {

    /** 已创建（待连接云端）。 */
    CREATED,
    /** 连接中/鉴权中。 */
    CONNECTING,
    /** 已连接，正在收发音频。 */
    ACTIVE,
    /** 已发送结束信号，等待最终结果。 */
    FINALIZING,
    /** 已结束并持久化最终结果。 */
    FINISHED,
    /** 已取消（不等待最终结果）。 */
    CANCELLED,
    /** 异常终止。 */
    ERROR;

    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED || this == ERROR;
    }

    public boolean isActiveLike() {
        return this == ACTIVE || this == CONNECTING;
    }
}
