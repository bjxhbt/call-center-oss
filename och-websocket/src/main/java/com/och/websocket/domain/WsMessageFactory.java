package com.och.websocket.domain;

import com.alibaba.fastjson2.JSON;

/**
 * WebSocket 消息构造工厂（任务 9.7）。
 *
 * <p>统一封装外层结构 {@link WsMsgPayload} 的构造，避免各业务模块手工拼装字段、遗漏时间戳或 callId。</p>
 *
 * @author och
 */
public final class WsMessageFactory {

    private WsMessageFactory() {
    }

    /**
     * 构造标准消息。
     *
     * @param event     事件类型
     * @param sessionId 会话ID（服务端推送时可先置空，由发送方填充）
     * @param callId    业务呼叫ID，非通话事件传 null
     * @param data      事件数据对象，将被序列化为 JSON 字符串；传 null 则 data 为 null
     */
    public static WsMsgPayload of(WsEventEnum event, String sessionId, String callId, Object data) {
        return WsMsgPayload.builder()
                .event(event)
                .sessionId(sessionId)
                .callId(callId)
                .timestamp(System.currentTimeMillis())
                .data(data == null ? null : JSON.toJSONString(data))
                .build();
    }

    /**
     * 构造非通话类消息（callId 为空）。
     */
    public static WsMsgPayload of(WsEventEnum event, String sessionId, Object data) {
        return of(event, sessionId, null, data);
    }
}
