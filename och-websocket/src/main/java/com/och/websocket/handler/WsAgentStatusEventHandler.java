package com.och.websocket.handler;


import com.alibaba.fastjson2.JSONObject;
import com.och.common.annotation.WsEventName;
import com.och.system.service.ISipAgentService;
import com.och.system.domain.enums.AgentOnlineStatusEnum;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.websocket.domain.WsEventEnum;
import com.och.websocket.domain.WsMsgPayload;
import com.och.websocket.factory.AbstractWsMsgEventStrategy;
import com.och.websocket.service.IWebSocketService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author danmo
 * @date 2025/05/27 22:20
 */
@WsEventName(name = "AGENT_STATUS")
@Slf4j
@AllArgsConstructor
@Component
public class WsAgentStatusEventHandler extends AbstractWsMsgEventStrategy {

    private final ISipAgentService iSipAgentService;
    private final IWebSocketService webSocketService;

    @Override
    public void doHandler(Long userId, WsMsgPayload payload) {
        log.info("WsAgentStatusEventHandler handle");
        WsAgentStatusData data = JSONObject.parseObject(payload.getData(), WsAgentStatusData.class);
        if (data == null || data.getAgentId() == null
                || AgentOnlineStatusEnum.getByCode(data.getOnlineStatus()) == null) {
            log.warn("忽略无效坐席状态消息 userId:{}, data:{}", userId, data);
            return;
        }
        SipAgentVo agent = iSipAgentService.getDetail(data.getAgentId());
        if (agent == null || !userId.equals(agent.getUserId())) {
            log.warn("拒绝越权坐席状态消息 userId:{}, agentId:{}", userId, data.getAgentId());
            return;
        }
        iSipAgentService.updateOnlineStatus(data.getAgentId(), data.getOnlineStatus(), payload.getTimestamp());
        webSocketService.sendEvent(userId, WsEventEnum.AGENT_STATUS, null, data);
    }


    @Data
    public static class WsAgentStatusData {
        private Long agentId;
        private Integer onlineStatus;
    }
}
