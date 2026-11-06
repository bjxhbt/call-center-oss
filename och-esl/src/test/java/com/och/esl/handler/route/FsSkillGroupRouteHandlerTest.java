package com.och.esl.handler.route;

import com.och.common.config.redis.RedisService;
import com.och.common.domain.CallInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.esl.client.FsClient;
import com.och.esl.service.AgentUnavailablePromptService;
import com.och.esl.service.IFsCallCacheService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.route.CallRouteVo;
import com.och.system.domain.vo.skill.CallSkillAgentRelVo;
import com.och.system.domain.vo.skill.CallSkillVo;
import com.och.system.service.ICallSkillService;
import com.och.system.service.IFsSipGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.PriorityBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FsSkillGroupRouteHandlerTest {

    private FsClient fsClient;
    private IFsCallCacheService callCacheService;
    private RedisService redisService;
    private AgentUnavailablePromptService unavailablePromptService;
    private ICallSkillService callSkillService;
    private IFsSipGatewayService gatewayService;
    private FsSkillGroupRouteHandler handler;

    @BeforeEach
    void setUp() {
        fsClient = mock(FsClient.class);
        callCacheService = mock(IFsCallCacheService.class);
        redisService = mock(RedisService.class);
        unavailablePromptService = mock(AgentUnavailablePromptService.class);
        callSkillService = mock(ICallSkillService.class);
        gatewayService = mock(IFsSipGatewayService.class);
        handler = new FsSkillGroupRouteHandler();
        ReflectionTestUtils.setField(handler, "fsClient", fsClient);
        ReflectionTestUtils.setField(handler, "fsCallCacheService", callCacheService);
        ReflectionTestUtils.setField(handler, "redisService", redisService);
        ReflectionTestUtils.setField(handler, "iCallSkillService", callSkillService);
        ReflectionTestUtils.setField(handler, "iFsSipGatewayService", gatewayService);
        ReflectionTestUtils.setField(handler, "agentUnavailablePromptService", unavailablePromptService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void requeuesAndRollsBackBridgeWhenOriginateLosesRegistration() {
        CallInfo callInfo = callInfo();
        CallSkillVo skill = skill(0);
        prepare(skill, callInfo);

        var failure = failureCaptor();
        handler.handler("127.0.0.1:8021", callInfo, "caller-uuid", "9");
        verify(fsClient).makeWebRtcCall(eq("127.0.0.1:8021"), eq(1001L), eq("6005"),
                eq("88659000"), anyString(), eq(10), failure.capture());

        failure.getValue().accept("browser softphone not registered: 6005");
        failure.getValue().accept("late duplicate failure");

        assertRolledBack(callInfo);
        Map<Long, PriorityBlockingQueue<?>> queues =
                (Map<Long, PriorityBlockingQueue<?>>) ReflectionTestUtils.getField(handler, "callQueueMap");
        assertThat(queues.get(9L)).hasSize(1);
        verify(callCacheService, times(1)).removeCallRel(anyString());
        verify(unavailablePromptService, never()).playAndHangup(anyString(), any(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void followsOverflowStrategyAfterOriginateFailure() {
        CallInfo callInfo = callInfo();
        CallSkillVo skill = skill(1);
        skill.setOverflowType(0);
        prepare(skill, callInfo);

        var failure = failureCaptor();
        handler.handler("127.0.0.1:8021", callInfo, "caller-uuid", "9");
        verify(fsClient).makeWebRtcCall(anyString(), eq(1001L), eq("6005"), anyString(),
                anyString(), eq(10), failure.capture());
        failure.getValue().accept("originate rejected");

        assertRolledBack(callInfo);
        Map<Long, PriorityBlockingQueue<?>> queues =
                (Map<Long, PriorityBlockingQueue<?>>) ReflectionTestUtils.getField(handler, "callQueueMap");
        assertThat(queues).isEmpty();
        verify(unavailablePromptService).playAndHangup(
                "127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.FULLBUSY);
    }

    @Test
    void followsHangupStrategyAfterOriginateFailure() {
        CallInfo callInfo = callInfo();
        CallSkillVo skill = skill(2);
        prepare(skill, callInfo);

        var failure = failureCaptor();
        handler.handler("127.0.0.1:8021", callInfo, "caller-uuid", "9");
        verify(fsClient).makeWebRtcCall(anyString(), eq(1001L), eq("6005"), anyString(),
                anyString(), eq(10), failure.capture());
        failure.getValue().accept("originate rejected");

        assertRolledBack(callInfo);
        verify(unavailablePromptService).playAndHangup(
                "127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.OVERFLOW);
    }

    @Test
    void missingOverflowTypeFallsBackToBusyPromptWhenBrowserIsOffline() {
        assertInvalidOverflowFallsBack(null);
    }

    @Test
    void unsupportedOverflowTypeFallsBackToBusyPromptWhenBrowserIsOffline() {
        assertInvalidOverflowFallsBack(9);
    }

    private void assertInvalidOverflowFallsBack(Integer overflowType) {
        CallInfo callInfo = callInfo();
        CallSkillVo skill = skill(1);
        skill.setOverflowType(overflowType);
        prepare(skill, callInfo);
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(false);

        handler.handler("127.0.0.1:8021", callInfo, "caller-uuid", "9");

        verify(fsClient, never()).makeWebRtcCall(anyString(), any(), anyString(), anyString(),
                anyString(), any(), any());
        verify(unavailablePromptService).playAndHangup(
                "127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.FULLBUSY);
    }

    private void prepare(CallSkillVo skill, CallInfo callInfo) {
        when(callSkillService.getDetail(9L)).thenReturn(skill);
        when(redisService.getMultiCacheMapValue(anyString(), any())).thenReturn(List.of(agentStatus()));
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(true);
        when(callCacheService.getCallRoute("6005", 1)).thenReturn(new CallRouteVo());
        when(gatewayService.getList(any())).thenReturn(List.of(new FsSipGateway()));
        when(callCacheService.getCallInfo(1001L)).thenReturn(callInfo);
        doAnswer(invocation -> null).when(fsClient).makeWebRtcCall(
                anyString(), eq(1001L), eq("6005"), anyString(), anyString(), eq(10), any());
    }

    private CallInfo callInfo() {
        return CallInfo.builder()
                .callId(1001L)
                .caller("13800138000")
                .callerDisplay("88659000")
                .callee("ivr")
                .calleeTimeOut(10)
                .process(ProcessEnum.CALL_ROUTE)
                .build();
    }

    private CallSkillVo skill(int fullBusyType) {
        CallSkillAgentRelVo relation = new CallSkillAgentRelVo();
        relation.setAgentId(1L);
        relation.setLevel(1);
        CallSkillVo skill = new CallSkillVo();
        skill.setId(9L);
        skill.setStrategyType(0);
        skill.setFullBusyType(fullBusyType);
        skill.setOverflowType(0);
        skill.setQueueLength(10);
        skill.setTimeOut(120);
        skill.setAgentList(List.of(relation));
        return skill;
    }

    private SipAgentStatusVo agentStatus() {
        SipAgentStatusVo status = new SipAgentStatusVo();
        status.setId(1L);
        status.setAgentNumber("6005");
        status.setName("agent-6005");
        status.setStatus(SipAgentStatusEnum.READY.getCode());
        status.setReceptionNum(0);
        return status;
    }

    private void assertRolledBack(CallInfo callInfo) {
        assertThat(callInfo.getProcess()).isEqualTo(ProcessEnum.CALL_ROUTE);
        assertThat(callInfo.getAgentId()).isNull();
        assertThat(callInfo.getAgentNumber()).isNull();
        assertThat(callInfo.getAgentName()).isNull();
        assertThat(callInfo.getCallee()).isEqualTo("ivr");
        assertThat(callInfo.getChannelMap()).isEmpty();
        assertThat(callInfo.getUniqueIdList()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private org.mockito.ArgumentCaptor<Consumer<String>> failureCaptor() {
        Class<Consumer<String>> consumerType = (Class<Consumer<String>>) (Class<?>) Consumer.class;
        return org.mockito.ArgumentCaptor.forClass(consumerType);
    }
}
