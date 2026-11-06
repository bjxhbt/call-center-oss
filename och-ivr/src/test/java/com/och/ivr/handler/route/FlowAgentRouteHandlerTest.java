package com.och.ivr.handler.route;

import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.esl.client.FsClient;
import com.och.esl.service.AgentUnavailablePromptService;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.properties.FlowTransferNodeProperties;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.service.ISipAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowAgentRouteHandlerTest {

    private IFsCallCacheService callCacheService;
    private ISipAgentService sipAgentService;
    private FsClient fsClient;
    private AgentUnavailablePromptService unavailablePromptService;
    private FlowAgentRouteHandler handler;

    @BeforeEach
    void setUp() {
        callCacheService = mock(IFsCallCacheService.class);
        sipAgentService = mock(ISipAgentService.class);
        fsClient = mock(FsClient.class);
        unavailablePromptService = mock(AgentUnavailablePromptService.class);
        handler = new FlowAgentRouteHandler(callCacheService, sipAgentService, fsClient,
                unavailablePromptService, mock(IFlowNoticeService.class));
    }

    @Test
    void playsBusyPromptWithoutCreatingAgentLegWhenBrowserIsNotRegistered() {
        FlowDataContext flowData = flowData();
        CallInfo callInfo = callInfo();
        when(callCacheService.getCallInfo(1001L)).thenReturn(callInfo);
        when(sipAgentService.getInfoByAgent("1")).thenReturn(agent());
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(false);

        handler.handler(flowData, properties());

        verify(unavailablePromptService).playAndHangup(
                "127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.FULLBUSY);
        verify(callCacheService, never()).saveCallRel(anyString(), anyLong());
        verify(fsClient, never()).makeWebRtcCall(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any());
        assertThat(callInfo.getProcess()).isNotEqualTo(ProcessEnum.CALL_BRIDGE);
        assertThat(callInfo.getAgentId()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesBrowserOnlyOriginateAndPlaysPromptIfRegistrationDisappears() {
        FlowDataContext flowData = flowData();
        CallInfo callInfo = callInfo();
        when(callCacheService.getCallInfo(1001L)).thenReturn(callInfo);
        when(sipAgentService.getInfoByAgent("1")).thenReturn(agent());
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(true);
        ArgumentCaptor<Consumer<String>> failureCaptor = ArgumentCaptor.forClass(Consumer.class);

        handler.handler(flowData, properties());

        verify(fsClient).makeWebRtcCall(eq("127.0.0.1:8021"), eq(1001L), eq("6005"),
                eq("88659000"), anyString(), eq(10), failureCaptor.capture());
        failureCaptor.getValue().accept("browser softphone not registered: 6005");
        verify(unavailablePromptService).playAndHangup(
                "127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.FULLBUSY);
    }

    private FlowDataContext flowData() {
        FlowDataContext flowData = new FlowDataContext();
        flowData.setAddress("127.0.0.1:8021");
        flowData.setCallId(1001L);
        flowData.setUniqueId("caller-uuid");
        return flowData;
    }

    private FlowTransferNodeProperties properties() {
        FlowTransferNodeProperties properties = new FlowTransferNodeProperties();
        properties.setRouteValue("1");
        return properties;
    }

    private CallInfo callInfo() {
        return CallInfo.builder()
                .callId(1001L)
                .caller("13800138000")
                .callerDisplay("88659000")
                .calleeTimeOut(10)
                .build();
    }

    private SipAgentVo agent() {
        SipAgentVo agent = new SipAgentVo();
        agent.setId(1L);
        agent.setAgentNumber("6005");
        agent.setName("agent-6005");
        return agent;
    }
}
