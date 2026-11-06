package com.och.websocket.service;

import com.och.websocket.domain.WsEventEnum;
import com.och.websocket.domain.WsMsgPayload;

import java.io.IOException;

/**
 * @author danmo
 * @date 2023年09月22日 11:10
 */
public interface IWebSocketService {

    /**
     * 获取sessionId
     * @param userId 员工ID
     * @return
     */
    public String getSessionId(String userId);

    /**
     * 发送消息
     * @param key
     * @param msg
     * @throws IOException
     */
    public void sendMsg(String key, String msg) throws IOException;


    /**
     * 发送Pong消息
     * @param key
     * @throws IOException
     */
    public void sendPongMsg(String key) throws IOException;

    /**
     * 发送Ping消息
     * @param key
     * @throws IOException
     */
    public void sendPingMsg(String key) throws IOException;

    /**
     * 发送广播消息
     * @param msg
     */
    public void sendBroadcastMsg(String msg) throws IOException;

    /**
     * 向指定用户推送一条已构造好的消息（任务 9.7）。
     *
     * @param userId  目标用户ID
     * @param payload 消息外层结构
     * @return true=已发送；false=用户当前不在线（无活动会话）
     */
    boolean sendToUser(Long userId, WsMsgPayload payload);

    /**
     * 向指定用户推送一条结构化事件（任务 9.7）。
     *
     * @param userId 目标用户ID
     * @param event  事件类型
     * @param callId 业务呼叫ID，非通话事件传 null
     * @param data   事件数据对象（自动序列化为 JSON）
     * @return true=已发送；false=用户不在线
     */
    boolean sendEvent(Long userId, WsEventEnum event, String callId, Object data);
}
