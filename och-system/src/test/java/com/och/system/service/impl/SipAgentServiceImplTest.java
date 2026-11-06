package com.och.system.service.impl;

import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.system.domain.entity.SipAgent;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.mapper.SipAgentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SipAgentServiceImplTest {

    private static final Long AGENT_ID = 6005L;
    private static final Long STATUS_TIME = 1786636800000L;

    private RedisService redisService;
    private SipAgentMapper sipAgentMapper;
    private SipAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        redisService = Mockito.mock(RedisService.class);
        sipAgentMapper = Mockito.mock(SipAgentMapper.class);
        service = new SipAgentServiceImpl(redisService);
        ReflectionTestUtils.setField(service, "baseMapper", sipAgentMapper);
        when(sipAgentMapper.updateById(any(SipAgent.class))).thenReturn(1);
    }

    @Test
    void resetIdleCreatesReadyCacheWhenAgentStatusIsMissing() {
        SipAgentVo detail = new SipAgentVo();
        detail.setId(AGENT_ID);
        detail.setAgentNumber("6005");
        detail.setOnlineStatus(2);
        when(redisService.getCacheMapHasKey(CacheConstants.AGENT_CURRENT_STATUS_KEY, AGENT_ID.toString()))
                .thenReturn(false);
        when(sipAgentMapper.getDetail(AGENT_ID)).thenReturn(detail);

        service.updateOnlineStatus(AGENT_ID, 1, STATUS_TIME);

        SipAgentStatusVo cachedStatus = captureCachedStatus();
        assertThat(cachedStatus.getOnlineStatus()).isEqualTo(1);
        assertThat(cachedStatus.getStatus()).isEqualTo(SipAgentStatusEnum.READY.getCode());
        assertThat(cachedStatus.getStatusTime()).isEqualTo(STATUS_TIME);
    }

    @Test
    void resetIdleOverridesStaleCachedCallStatus() {
        SipAgentStatusVo existingStatus = new SipAgentStatusVo();
        existingStatus.setId(AGENT_ID);
        existingStatus.setOnlineStatus(6);
        existingStatus.setStatus(SipAgentStatusEnum.RINGING.getCode());
        when(redisService.getCacheMapHasKey(CacheConstants.AGENT_CURRENT_STATUS_KEY, AGENT_ID.toString()))
                .thenReturn(true);
        when(redisService.getCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, AGENT_ID.toString()))
                .thenReturn(existingStatus);

        service.updateOnlineStatus(AGENT_ID, 1, STATUS_TIME);

        SipAgentStatusVo cachedStatus = captureCachedStatus();
        assertThat(cachedStatus.getOnlineStatus()).isEqualTo(1);
        assertThat(cachedStatus.getStatus()).isEqualTo(SipAgentStatusEnum.READY.getCode());
        assertThat(cachedStatus.getStatusTime()).isEqualTo(STATUS_TIME);
    }

    private SipAgentStatusVo captureCachedStatus() {
        ArgumentCaptor<SipAgentStatusVo> captor = ArgumentCaptor.forClass(SipAgentStatusVo.class);
        verify(redisService).setCacheMapValue(
                eq(CacheConstants.AGENT_CURRENT_STATUS_KEY), eq(AGENT_ID.toString()), captor.capture());
        return captor.getValue();
    }
}
