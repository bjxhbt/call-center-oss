package com.och.esl.factory;

import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.AgentStateEnum;
import com.och.common.enums.DirectionEnum;
import com.och.esl.service.AgentTransferScheduleService;
import com.och.esl.service.IFsCallCacheService;
import com.och.system.domain.entity.CallSchedule;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.service.ISipAgentService;
import com.och.websocket.domain.WsMsgPayload;
import com.och.websocket.service.IWebSocketService;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractFsEslEventHandlerTest {

    @Test
    void sendsAnsweredInboundPopupOnlyToAssignedAgentUserAtCallEnd() throws Exception {
        TestHandler handler = new TestHandler();
        IFsCallCacheService cacheService = mock(IFsCallCacheService.class);
        ISipAgentService agentService = mock(ISipAgentService.class);
        IWebSocketService webSocketService = mock(IWebSocketService.class);
        AgentTransferScheduleService scheduleService = mock(AgentTransferScheduleService.class);
        ReflectionTestUtils.setField(handler, "ifsCallCacheService", cacheService);
        ReflectionTestUtils.setField(handler, "iSipAgentService", agentService);
        ReflectionTestUtils.setField(handler, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(handler, "agentTransferScheduleService", scheduleService);

        Long callId = 100L;
        CallInfo callInfo = CallInfo.builder()
                .callId(callId)
                .agentId(20L)
                .direction(DirectionEnum.INBOUND.getType())
                .build();
        callInfo.setAnswerTime(System.currentTimeMillis());
        callInfo.setChannelInfoMap("agent-uuid", ChannelInfo.builder()
                .uniqueId("agent-uuid")
                .directionType(2)
                .build());
        SipAgentVo agent = new SipAgentVo();
        agent.setId(20L);
        agent.setUserId(30L);
        CallSchedule schedule = new CallSchedule();

        when(cacheService.getCallInfo(callId)).thenReturn(callInfo);
        when(agentService.getDetail(20L)).thenReturn(agent);
        when(scheduleService.findCurrentScheduleForNotification()).thenReturn(schedule);
        when(scheduleService.isWorkingSchedule(schedule)).thenReturn(true);
        when(webSocketService.sendToUser(eq(30L), any(WsMsgPayload.class))).thenReturn(true);

        handler.send(callId, AgentStateEnum.CALL_END);

        verify(webSocketService).sendToUser(eq(30L), any(WsMsgPayload.class));
        verify(webSocketService, never()).sendBroadcastMsg(any());
    }

    @Test
    void doesNotSendInboundPopupWhenAgentAnswers() {
        TestHandler handler = new TestHandler();
        IWebSocketService webSocketService = mock(IWebSocketService.class);
        ReflectionTestUtils.setField(handler, "webSocketService", webSocketService);

        handler.send(100L, AgentStateEnum.TALKING);

        verify(webSocketService, never()).sendToUser(any(), any());
    }

    private static class TestHandler extends AbstractFsEslEventHandler {
        @Override
        public void handleEslEvent(String address, EslEvent event) {
        }

        void send(Long callId, AgentStateEnum status) {
            sendAgentStatus(callId, "18610915226", "6005", DirectionEnum.INBOUND.getType(), status);
        }
    }
}
