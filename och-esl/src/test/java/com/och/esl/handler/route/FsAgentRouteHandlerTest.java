package com.och.esl.handler.route;

import com.och.common.domain.CallInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.esl.client.FsClient;
import com.och.esl.service.AgentUnavailablePromptService;
import com.och.esl.service.IFsCallCacheService;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.service.ISipAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FsAgentRouteHandlerTest {

    private FsClient fsClient;
    private IFsCallCacheService callCacheService;
    private AgentUnavailablePromptService unavailablePromptService;
    private FsAgentRouteHandler handler;

    @BeforeEach
    void setUp() {
        fsClient = mock(FsClient.class);
        callCacheService = mock(IFsCallCacheService.class);
        unavailablePromptService = mock(AgentUnavailablePromptService.class);
        handler = new FsAgentRouteHandler();
        ReflectionTestUtils.setField(handler, "fsClient", fsClient);
        ReflectionTestUtils.setField(handler, "fsCallCacheService", callCacheService);
        ReflectionTestUtils.setField(handler, "iSipAgentService", agentService());
        ReflectionTestUtils.setField(handler, "agentUnavailablePromptService", unavailablePromptService);
    }

    @Test
    void doesNotCreateSecondLegWhenBrowserSoftphoneIsNotRegistered() {
        CallInfo callInfo = CallInfo.builder()
                .callId(1001L)
                .caller("13800138000")
                .callerDisplay("88659000")
                .calleeTimeOut(10)
                .build();
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(false);

        handler.handler("127.0.0.1:8021", callInfo, "caller-uuid", "1");

        verify(unavailablePromptService).playAndHangup(
                "127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.FULLBUSY);
        verify(callCacheService, never()).saveCallRel(anyString(), anyLong());
        verify(fsClient, never()).makeWebRtcCall(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any());
        assertThat(callInfo.getProcess()).isNotEqualTo(ProcessEnum.CALL_BRIDGE);
        assertThat(callInfo.getAgentId()).isNull();
    }

    private ISipAgentService agentService() {
        ISipAgentService agentService = mock(ISipAgentService.class);
        SipAgentVo agent = new SipAgentVo();
        agent.setId(1L);
        agent.setAgentNumber("6005");
        agent.setName("agent-6005");
        when(agentService.getDetail(1L)).thenReturn(agent);
        return agentService;
    }
}
