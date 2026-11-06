package com.och.calltask.service.impl;

import com.och.calltask.domain.entity.CallTaskAssignment;
import com.och.calltask.domain.vo.CallTaskVo;
import com.och.common.config.redis.RedisService;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFsCallCacheService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.domain.vo.display.CallDisplayPoolVo;
import com.och.system.domain.vo.display.CallDisplaySimpleVo;
import com.och.system.domain.vo.route.CallRouteVo;
import com.och.system.service.ICallDisplayPoolService;
import com.och.system.service.IFsSipGatewayService;
import com.och.system.service.ISipAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PredictiveDialingServiceImplTest {

    private FsClient fsClient;
    private IFsCallCacheService callCacheService;
    private ISipAgentService sipAgentService;
    private IFsSipGatewayService sipGatewayService;
    private ICallDisplayPoolService displayPoolService;
    private PredictiveDialingServiceImpl service;

    @BeforeEach
    void setUp() {
        fsClient = mock(FsClient.class);
        callCacheService = mock(IFsCallCacheService.class);
        sipAgentService = mock(ISipAgentService.class);
        sipGatewayService = mock(IFsSipGatewayService.class);
        displayPoolService = mock(ICallDisplayPoolService.class);
        service = new PredictiveDialingServiceImpl(
                fsClient, callCacheService, sipAgentService, callCacheService,
                sipGatewayService, displayPoolService, mock(RedisService.class));
    }

    @Test
    void skipsBeforeCacheAndCustomerOriginateWhenBrowserPhoneIsNotRegistered() {
        SipAgentVo agent = agent();
        when(sipAgentService.getDetail(1L)).thenReturn(agent);
        when(fsClient.anyAddress()).thenReturn("fs-a:8021");
        when(fsClient.isWebRtcUserRegistered("fs-a:8021", "6005")).thenReturn(false);

        assertThat(service.executePredictiveCall(assignment(), new CallTaskVo())).isFalse();

        verify(callCacheService, never()).saveCallInfo(any());
        verify(callCacheService, never()).saveCallRel(anyString(), anyLong());
        verify(fsClient, never()).makeCall(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any(FsSipGateway.class));
    }

    @Test
    void originatesCustomerOnTheSameFreeSwitchAddressUsedForRegistrationCheck() {
        SipAgentVo agent = agent();
        when(sipAgentService.getDetail(1L)).thenReturn(agent);
        when(fsClient.anyAddress()).thenReturn("fs-a:8021");
        when(fsClient.isWebRtcUserRegistered("fs-a:8021", "6005")).thenReturn(true);
        FsSipGateway gateway = stubGateway();

        assertThat(service.executePredictiveCall(assignment(), taskWithDisplayPool())).isTrue();

        verify(fsClient).makeCall(eq("fs-a:8021"), anyLong(), eq("13800138000"),
                eq("01058882999"), anyString(), eq(30), eq(gateway));
        verify(fsClient, times(2)).isWebRtcUserRegistered("fs-a:8021", "6005");
    }

    @Test
    void requeuesWithoutCachingOrDialingWhenPhoneDisconnectsDuringPreflight() {
        when(sipAgentService.getDetail(1L)).thenReturn(agent());
        when(fsClient.anyAddress()).thenReturn("fs-a:8021");
        when(fsClient.isWebRtcUserRegistered("fs-a:8021", "6005")).thenReturn(true, false);
        stubGateway();
        CallTaskAssignment assignment = assignment();

        assertThat(service.executePredictiveCall(assignment, taskWithDisplayPool())).isFalse();

        assertThat(assignment.getAttemptCount()).isZero();
        verify(callCacheService, never()).saveCallInfo(any());
        verify(callCacheService, never()).saveCallRel(anyString(), anyLong());
        verify(fsClient, never()).makeCall(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any(FsSipGateway.class));
    }

    @Test
    void clearsCallCacheWhenCustomerOriginateThrows() {
        when(sipAgentService.getDetail(1L)).thenReturn(agent());
        when(fsClient.anyAddress()).thenReturn("fs-a:8021");
        when(fsClient.isWebRtcUserRegistered("fs-a:8021", "6005")).thenReturn(true);
        FsSipGateway gateway = stubGateway();
        doThrow(new IllegalStateException("ESL unavailable")).when(fsClient).makeCall(
                eq("fs-a:8021"), anyLong(), eq("13800138000"), eq("01058882999"),
                anyString(), eq(30), eq(gateway));

        assertThatThrownBy(() -> service.executePredictiveCall(assignment(), taskWithDisplayPool()))
                .hasMessageContaining("ESL unavailable");

        verify(callCacheService).removeCallInfo(anyLong());
        verify(callCacheService).removeCallRel(anyString());
    }

    private FsSipGateway stubGateway() {
        CallDisplaySimpleVo display = new CallDisplaySimpleVo();
        display.setDisplayNumber("01058882999");
        CallDisplayPoolVo pool = new CallDisplayPoolVo();
        pool.setType(0);
        pool.setPhoneList(List.of(display));
        when(displayPoolService.getPoolDetail(9L)).thenReturn(pool);

        CallRouteVo route = new CallRouteVo();
        route.setRouteValue("7");
        when(callCacheService.getCallRoute("01058882999", 2)).thenReturn(route);
        FsSipGateway gateway = new FsSipGateway();
        gateway.setName("gw-1");
        when(sipGatewayService.getDetail(7L)).thenReturn(gateway);
        return gateway;
    }

    private CallTaskAssignment assignment() {
        CallTaskAssignment assignment = new CallTaskAssignment();
        assignment.setId(101L);
        assignment.setAgentId(1L);
        assignment.setPhone("13800138000");
        assignment.setAttemptCount(0);
        return assignment;
    }

    private CallTaskVo taskWithDisplayPool() {
        CallTaskVo task = new CallTaskVo();
        task.setPhonePoolId(9L);
        return task;
    }

    private SipAgentVo agent() {
        SipAgentVo agent = new SipAgentVo();
        agent.setId(1L);
        agent.setAgentNumber("6005");
        agent.setName("agent-6005");
        return agent;
    }
}
