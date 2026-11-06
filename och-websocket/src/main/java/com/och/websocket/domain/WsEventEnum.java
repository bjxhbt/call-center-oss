package com.och.websocket.domain;


import lombok.Getter;

/**
 * WebSocket 统一事件类型字典（任务 9.7）。
 *
 * <p>所有实时消息使用 {@code event} 字段标识类型，事件名前后端共用。
 * 前端在 {@code src/consts/wsEvents.ts} 镜像同一套事件名。</p>
 *
 * <p>事件方向：</p>
 * <ul>
 *   <li>S -&gt; C：服务端推送到客户端；</li>
 *   <li>C -&gt; S：客户端上报到服务端；</li>
 *   <li>B：双向。</li>
 * </ul>
 *
 * <p>注意：保持向后兼容，原有 SUCCEED / HEARTBEAT / AGENT_STATUS / INCOMING_CALL 含义不变，
 * 只做向后兼容的新增。</p>
 *
 * @author danmo
 * @date 2025/05/27 18:00
 */
@Getter
public enum WsEventEnum {

    /* ============ 连接与会话 ============ */
    /** 登录/连接成功（S -&gt; C） */
    SUCCEED,
    /** 心跳（B） */
    HEARTBEAT,
    /** 坐席签入/注册（C -&gt; S 上报，S -&gt; C 确认） */
    AGENT_REGISTER,
    /** 坐席状态变更（B） */
    AGENT_STATUS,

    /* ============ 通话 ============ */
    /** 来电通知（S -&gt; C） */
    INCOMING_CALL,
    /*
     * 通话生命周期事件（事件名与前端 consts/softphone.ts WsEvent 冻结一致）。
     * 与 9.8 幂等键一一对应：CALL_PARK/CALL_ANSWER/CALL_BRIDGE/CALL_HANGUP
     * 分别对应 ESL PARK/ANSWER/BRIDGE/HANGUP 事件，重复事件由幂等闸门过滤。
     */
    /** 通话被驻留 PARK（S -&gt; C） */
    CALL_PARK,
    /** 通话被接听 ANSWER（S -&gt; C） */
    CALL_ANSWER,
    /** 双方桥接 BRIDGE，正式通话开始（S -&gt; C） */
    CALL_BRIDGE,
    /** 通话挂断 HANGUP（S -&gt; C） */
    CALL_HANGUP,
    /** 通话失败（网关/号码/媒体等）（S -&gt; C） */
    CALL_FAIL,
    /** 外呼通知：坐席发起外呼后服务端推送（S -&gt; C） */
    CALL_OUT,
    /** 通话状态变更：振铃/接通/保持等（S -&gt; C） */
    CALL_STATUS,
    /** 挂断（S -&gt; C）。@deprecated 改用 CALL_HANGUP，与前端对齐 */
    @Deprecated
    HANGUP,
    /** 呼叫失败（S -&gt; C）。@deprecated 改用 CALL_FAIL，与前端对齐 */
    @Deprecated
    FAIL,

    /* ============ ASR 转写 ============ */
    /** ASR 增量（中间）识别结果（S -&gt; C） */
    ASR_RESULT,
    /** ASR 最终识别结果（S -&gt; C） */
    ASR_FINAL,
    /**
     * 转写增量结果（S -&gt; C）。与前端 {@code CallSyncClient} 路由的事件名一致
     * （前端权威消息结构：consts/softphone.ts WsEvent.TRANSCRIPT_PARTIAL）。
     */
    TRANSCRIPT_PARTIAL,
    /**
     * 转写最终结果（S -&gt; C）。同 segmentId 的最终文本替换临时文本。
     */
    TRANSCRIPT_FINAL,
    /**
     * 转写会话状态/异常（S -&gt; C）。payload 含 status/code、message、callId。
     */
    TRANSCRIPT_STATUS,

    /* ============ 系统 ============ */
    /** 服务端错误/异常通知（S -&gt; C） */
    ERROR,
    ;

    /**
     * 安全解析事件名：未知名返回 null，避免前端误传导致反序列化异常中断连接。
     */
    public static WsEventEnum safeOf(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return WsEventEnum.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
