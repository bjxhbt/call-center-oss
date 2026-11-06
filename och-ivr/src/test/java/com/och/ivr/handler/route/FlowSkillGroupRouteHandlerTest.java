package com.och.ivr.handler.route;

import com.och.common.config.redis.RedisService;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.esl.client.FsClient;
import com.och.esl.service.CallPlaybackCompletionService;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.properties.FlowTransferNodeProperties;
import com.och.ivr.service.IFlowInstancesService;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.skill.CallSkillAgentRelVo;
import com.och.system.domain.vo.skill.CallSkillVo;
import com.och.system.service.ICallSkillService;
import com.och.system.service.ISipAgentService;
import com.och.system.service.IVoiceFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowSkillGroupRouteHandlerTest {

    private IFsCallCacheService callCacheService;
    private ICallSkillService callSkillService;
    private FsClient fsClient;
    private RedisService redisService;
    private CallPlaybackCompletionService playbackCompletionService;
    private FlowSkillGroupRouteHandler handler;

    @BeforeEach
    void setUp() {
        callCacheService = mock(IFsCallCacheService.class);
        callSkillService = mock(ICallSkillService.class);
        fsClient = mock(FsClient.class);
        redisService = mock(RedisService.class);
        playbackCompletionService = mock(CallPlaybackCompletionService.class);
        handler = new FlowSkillGroupRouteHandler(
                callCacheService,
                callSkillService,
                fsClient,
                redisService,
                mock(IVoiceFileService.class),
                mock(ISipAgentService.class),
                mock(IFlowNoticeService.class),
                mock(IFlowInstancesService.class),
                playbackCompletionService);
        ReflectionTestUtils.setField(handler, "queueBusyPrompt", "say:正在排队，请稍候。");
        ReflectionTestUtils.setField(handler, "agentBusyPrompt", "say:当前坐席繁忙，请稍后重试。");
    }

    @AfterEach
    void tearDown() {
        handler.destroy();
    }

    @Test
    void keepsCallInConfiguredQueueWhenNoBrowserPhoneIsRegistered() {
        FlowDataContext flowData = flowData();
        CallInfo callInfo = CallInfo.builder().callId(1001L).caller("13800138000").build();
        CallSkillVo skill = queueSkill();
        SipAgentStatusVo status = new SipAgentStatusVo();
        status.setId(1L);
        status.setAgentNumber("6005");
        status.setStatus(SipAgentStatusEnum.READY.getCode());

        when(callCacheService.getCallInfo(1001L)).thenReturn(callInfo);
        when(callSkillService.getDetail(9L)).thenReturn(skill);
        doReturn(status).when(redisService).getCacheMapValue(anyString(), eq("1"));
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(false);

        handler.handler(flowData, properties());

        verify(fsClient).playFile("127.0.0.1:8021", "caller-uuid",
                "say:正在排队，请稍候。!silence_stream://5000", -1);
        verify(fsClient, never()).makeWebRtcCall(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any());
        verify(redisService, atLeastOnce()).setCacheMapValue(
                eq("och:ivr:skill:queue"), eq("1001"), any());
    }

    @Test
    void reappliesQueueStrategyAndRollsBackBridgeWhenOriginateFails() {
        FlowDataContext flowData = flowData();
        CallInfo callInfo = CallInfo.builder()
                .callId(1001L)
                .caller("13800138000")
                .callerDisplay("88659000")
                .callee("ivr")
                .process(ProcessEnum.CALL_ROUTE)
                .calleeTimeOut(10)
                .build();
        CallSkillVo skill = queueSkill();
        SipAgentStatusVo status = readyStatus();
        when(callCacheService.getCallInfo(1001L)).thenReturn(callInfo);
        when(callSkillService.getDetail(9L)).thenReturn(skill);
        doReturn(status).when(redisService).getCacheMapValue(anyString(), eq("1"));
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(true);

        var failure = failureCaptor();
        handler.handler(flowData, properties());
        verify(fsClient).makeWebRtcCall(eq("127.0.0.1:8021"), eq(1001L), eq("6005"),
                eq("88659000"), anyString(), eq(10), failure.capture());

        failure.getValue().accept("browser softphone not registered: 6005");
        failure.getValue().accept("late duplicate failure");

        assertThat(callInfo.getProcess()).isEqualTo(ProcessEnum.CALL_ROUTE);
        assertThat(callInfo.getAgentId()).isNull();
        assertThat(callInfo.getAgentNumber()).isNull();
        assertThat(callInfo.getChannelMap()).isEmpty();
        assertThat(callInfo.getUniqueIdList()).isEmpty();
        verify(callCacheService, times(1)).removeCallRel(anyString());
        verify(redisService, atLeastOnce()).setCacheMapValue(
                eq("och:ivr:skill:queue"), eq("1001"), any());
        verify(playbackCompletionService, never()).armHangupAfterPlayback(anyLong(), anyString());
    }

    @Test
    void appliesOverflowStrategyAfterOriginateFailure() {
        FlowDataContext flowData = flowData();
        CallInfo callInfo = CallInfo.builder()
                .callId(1001L)
                .caller("13800138000")
                .callerDisplay("88659000")
                .callee("ivr")
                .process(ProcessEnum.CALL_ROUTE)
                .calleeTimeOut(10)
                .build();
        CallSkillVo skill = queueSkill();
        skill.setFullBusyType(1);
        skill.setOverflowType(0);
        SipAgentStatusVo status = readyStatus();
        when(callCacheService.getCallInfo(1001L)).thenReturn(callInfo);
        when(callSkillService.getDetail(9L)).thenReturn(skill);
        doReturn(status).when(redisService).getCacheMapValue(anyString(), eq("1"));
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(true);

        var failure = failureCaptor();
        handler.handler(flowData, properties());
        verify(fsClient).makeWebRtcCall(anyString(), eq(1001L), eq("6005"), anyString(),
                anyString(), eq(10), failure.capture());
        failure.getValue().accept("originate rejected");

        assertThat(callInfo.getProcess()).isEqualTo(ProcessEnum.CALL_ROUTE);
        assertThat(callInfo.getAgentId()).isNull();
        verify(playbackCompletionService).armHangupAfterPlayback(eq(1001L), eq("caller-uuid"));
        verify(fsClient).playFile("127.0.0.1:8021", "caller-uuid",
                "say:当前坐席繁忙，请稍后重试。");
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
        properties.setRouteValue("9");
        return properties;
    }

    private CallSkillVo queueSkill() {
        CallSkillAgentRelVo relation = new CallSkillAgentRelVo();
        relation.setAgentId(1L);
        relation.setLevel(1);
        CallSkillVo skill = new CallSkillVo();
        skill.setId(9L);
        skill.setFullBusyType(0);
        skill.setQueueLength(10);
        skill.setTimeOut(120);
        skill.setAgentList(List.of(relation));
        return skill;
    }

    private SipAgentStatusVo readyStatus() {
        SipAgentStatusVo status = new SipAgentStatusVo();
        status.setId(1L);
        status.setAgentNumber("6005");
        status.setStatus(SipAgentStatusEnum.READY.getCode());
        return status;
    }

    @SuppressWarnings("unchecked")
    private org.mockito.ArgumentCaptor<Consumer<String>> failureCaptor() {
        Class<Consumer<String>> consumerType = (Class<Consumer<String>>) (Class<?>) Consumer.class;
        return org.mockito.ArgumentCaptor.forClass(consumerType);
    }
}
