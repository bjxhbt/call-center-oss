package com.och.websocket.domain;


import com.alibaba.fastjson2.JSONObject;
import lombok.Builder;
import lombok.Data;

/**
 * WebSocket 统一消息外层结构（任务 9.7）。
 *
 * <p>统一外层字段：{@code event}、{@code sessionId}、{@code callId}、{@code timestamp}、{@code data}。</p>
 * <table border="1">
 * <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 * <tr><td>event</td><td>String</td><td>事件类型，取值见 {@link WsEventEnum}</td></tr>
 * <tr><td>sessionId</td><td>String</td><td>WebSocket 会话ID</td></tr>
 * <tr><td>callId</td><td>String</td><td>业务呼叫唯一标识，非通话类事件可为空</td></tr>
 * <tr><td>timestamp</td><td>Long</td><td>毫秒级时间戳</td></tr>
 * <tr><td>data</td><td>String</td><td>事件数据（JSON 字符串），具体结构见各事件 DTO</td></tr>
 * </table>
 *
 * <p>向后兼容：{@code callId} 为新增可选字段，旧客户端忽略即可；旧事件的构造方式不变。</p>
 *
 * @author danmo
 * @date 2025/05/27 17:59
 */
@Builder
@Data
public class WsMsgPayload {

    //会话ID
    private String sessionId;

    //业务呼叫唯一标识（可选，非通话事件为空）
    private String callId;

    //时间戳
    private Long timestamp;

    //事件类型
    private WsEventEnum event;

    //数据（JSON 字符串）
    private String data;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}
