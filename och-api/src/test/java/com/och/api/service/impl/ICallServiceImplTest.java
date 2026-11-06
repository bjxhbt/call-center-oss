package com.och.api.service.impl;

import com.och.common.exception.CommonException;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFsCallCacheService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.call.CallQuery;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.domain.vo.display.CallDisplayVo;
import com.och.system.domain.vo.route.CallRouteVo;
import com.och.system.service.ICallDisplayService;
import com.och.system.service.IFsSipGatewayService;
import com.och.system.service.ISipAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ICallServiceImplTest {

    private FsClient fsClient;
    private ISipAgentService sipAgentService;
    private ICallDisplayService callDisplayService;
    private IFsCallCacheService callCacheService;
    private IFsSipGatewayService sipGatewayService;
    private ICallServiceImpl callService;

    @BeforeEach
    void setUp() {
        fsClient = mock(FsClient.class);
        sipAgentService = mock(ISipAgentService.class);
        callDisplayService = mock(ICallDisplayService.class);
        callCacheService = mock(IFsCallCacheService.class);
        sipGatewayService = mock(IFsSipGatewayService.class);
        callService = new ICallServiceImpl();
        ReflectionTestUtils.setField(callService, "fsClient", fsClient);
        ReflectionTestUtils.setField(callService, "iSipAgentService", sipAgentService);
        ReflectionTestUtils.setField(callService, "iCallDisplayService", callDisplayService);
        ReflectionTestUtils.setField(callService, "iFsCallCacheService", callCacheService);
        ReflectionTestUtils.setField(callService, "iFsSipGatewayService", sipGatewayService);
    }

    @Test
    void rejectsOutboundCallBeforeCustomerLegWhenBrowserPhoneIsNotRegistered() {
        CallQuery query = callQuery();
        SipAgentVo agent = agent();
        when(sipAgentService.getInfoByQuery(any())).thenReturn(List.of(agent));
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(false);

        assertThatThrownBy(() -> callService.makeCall(query))
                .isInstanceOf(CommonException.class)
                .hasMessage("浏览器软电话未注册，请先注册后再拨号");

        verifyNoInteractions(callDisplayService, callCacheService, sipGatewayService);
        verify(fsClient, never()).makeCall(anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any(FsSipGateway.class));
    }

    @Test
    void usesTheSameFreeSwitchForRegistrationCheckAndCustomerLeg() {
        CallQuery query = callQuery();
        SipAgentVo agent = agent();
        CallDisplayVo display = new CallDisplayVo();
        display.setPhone("01058882999");
        CallRouteVo route = new CallRouteVo();
        route.setRouteValue("9");
        FsSipGateway gateway = new FsSipGateway();

        when(sipAgentService.getInfoByQuery(any())).thenReturn(List.of(agent));
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).thenReturn(true);
        when(callDisplayService.getList(any())).thenReturn(List.of(display));
        when(callCacheService.getCallRoute("13800138000", 2)).thenReturn(route);
        when(sipGatewayService.getDetail(9L)).thenReturn(gateway);

        callService.makeCall(query);

        verify(fsClient).makeCall(eq("127.0.0.1:8021"), anyLong(), eq("13800138000"),
                eq("01058882999"), anyString(), eq(10), same(gateway));
    }

    private CallQuery callQuery() {
        CallQuery query = new CallQuery();
        query.setAgentId(1L);
        query.setCallee("13800138000");
        return query;
    }

    private SipAgentVo agent() {
        SipAgentVo agent = new SipAgentVo();
        agent.setId(1L);
        agent.setAgentNumber("6005");
        agent.setName("agent-6005");
        return agent;
    }
}
