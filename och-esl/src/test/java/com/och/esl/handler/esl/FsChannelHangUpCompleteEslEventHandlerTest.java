package com.och.esl.handler.esl;

import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.esl.client.FsClient;
import com.och.esl.service.IBridgeStateCache;
import com.och.common.enums.AgentStateEnum;
import com.och.esl.service.IFsCallCacheService;
import com.och.esl.service.IFlowNoticeService;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FsChannelHangUpCompleteEslEventHandlerTest {

    @Test
    void finalizesWhenCallerHangsUpWithResidualForkLegs() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.shouldFinalizeCall(3, true, false)).isTrue();
    }

    @Test
    void keepsCallerAliveWhenAgentEntersSatisfaction() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.shouldFinalizeCall(1, false, true)).isFalse();
    }

    @Test
    void finalizesTheLastNormalChannel() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.shouldFinalizeCall(1, false, false)).isTrue();
    }

    @Test
    void keepsSatisfactionActiveWhenUnbridgeMarkedItFirst() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.continuesSatisfaction(true, false)).isTrue();
    }

    @Test
    void continuesWhenHangupEventMarksSatisfactionFirst() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.continuesSatisfaction(false, true)).isTrue();
    }

    @Test
    void ignoresLosingParallelRingLegBeforeSatisfactionHandling() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.isForkRaceHangup("LOSE_RACE")).isTrue();
        assertThat(FsChannelHangUpCompleteEslEventHandler.isForkRaceHangup(" lose_race ")).isTrue();
    }

    @Test
    void keepsRealAgentHangupEligibleForSatisfaction() {
        assertThat(FsChannelHangUpCompleteEslEventHandler.isForkRaceHangup("NORMAL_CLEARING")).isFalse();
    }

    @Test
    void losingParallelLegOnlyRemovesItsOwnCachedChannel() {
        CallInfo callInfo = CallInfo.builder().callId(123L).build();
        ChannelInfo caller = ChannelInfo.builder()
                .callId(123L).uniqueId("caller-uuid").directionType(1).otherUniqueId("loser-uuid").build();
        ChannelInfo loser = ChannelInfo.builder()
                .callId(123L).uniqueId("loser-uuid").directionType(2).otherUniqueId("caller-uuid").build();
        callInfo.setChannelInfoMap("caller-uuid", caller);
        callInfo.setChannelInfoMap("loser-uuid", loser);
        callInfo.addUniqueIdList("caller-uuid");
        callInfo.addUniqueIdList("loser-uuid");

        IFsCallCacheService callCache = mock(IFsCallCacheService.class);
        RedisService redisService = mock(RedisService.class);
        TestHangupHandler handler = new TestHangupHandler(callInfo, callCache, redisService);
        EslEvent event = mock(EslEvent.class);
        when(event.getEventHeaders()).thenReturn(Map.of(
                "Unique-ID", "loser-uuid",
                "Hangup-Cause", "LOSE_RACE"));

        handler.handleEslEvent("127.0.0.1:8021", event);

        assertThat(callInfo.getChannelMap()).containsOnlyKeys("caller-uuid");
        assertThat(callInfo.getUniqueIdList()).containsExactly("caller-uuid");
        assertThat(caller.getOtherUniqueId()).isNull();
        verify(redisService).delCacheMapValue(CacheConstants.CALL_REL_MAP_CACHE_KEY, "loser-uuid");
        verify(callCache).saveCallInfo(callInfo);
    }

    @Test
    void realAgentHangupStillParksCallerAndStartsSatisfaction() {
        CallInfo callInfo = CallInfo.builder().callId(456L).build();
        FlowDataContext flowData = new FlowDataContext();
        flowData.setCallId(456L);
        flowData.setAddress("127.0.0.1:8021");
        flowData.setUniqueId("caller-uuid");
        callInfo.setFlowDataContext(flowData);
        ChannelInfo caller = ChannelInfo.builder()
                .callId(456L).uniqueId("caller-uuid").directionType(1).otherUniqueId("agent-uuid").build();
        ChannelInfo agent = ChannelInfo.builder()
                .callId(456L).uniqueId("agent-uuid").directionType(2).otherUniqueId("caller-uuid")
                .answerTime(1000L).bridgeTime(1001L).build();
        callInfo.setChannelInfoMap("caller-uuid", caller);
        callInfo.setChannelInfoMap("agent-uuid", agent);
        callInfo.addUniqueIdList("caller-uuid");
        callInfo.addUniqueIdList("agent-uuid");

        IFsCallCacheService callCache = mock(IFsCallCacheService.class);
        RedisService redisService = mock(RedisService.class);
        IBridgeStateCache bridgeStateCache = mock(IBridgeStateCache.class);
        IFlowNoticeService flowNoticeService = mock(IFlowNoticeService.class);
        FsClient fsClient = mock(FsClient.class);
        when(bridgeStateCache.isBridged(456L)).thenReturn(true);
        when(bridgeStateCache.isSatisfaction(456L)).thenReturn(false);
        when(bridgeStateCache.markSatisfaction(456L)).thenReturn(true);

        TestHangupHandler handler = new TestHangupHandler(callInfo, callCache, redisService);
        ReflectionTestUtils.setField(handler, "bridgeStateCache", bridgeStateCache);
        ReflectionTestUtils.setField(handler, "flowNoticeService", flowNoticeService);
        ReflectionTestUtils.setField(handler, "fsClient", fsClient);
        EslEvent event = mock(EslEvent.class);
        when(event.getEventHeaders()).thenReturn(Map.of(
                "Unique-ID", "agent-uuid",
                "Hangup-Cause", "NORMAL_CLEARING"));
        when(event.getEventDateTimestamp()).thenReturn(2_000_000L);

        handler.handleEslEvent("127.0.0.1:8021", event);

        verify(fsClient).sendSyncMsg("127.0.0.1:8021", "uuid_park", "caller-uuid");
        verify(flowNoticeService).notice(2, "next", flowData);
        assertThat(callInfo.getProcess()).isEqualTo(com.och.common.enums.ProcessEnum.CALL_ROUTE);
        assertThat(callInfo.getChannelMap()).containsKey("caller-uuid");
        assertThat(handler.callEndNotificationCount).isEqualTo(1);
    }

    private static final class TestHangupHandler extends FsChannelHangUpCompleteEslEventHandler {
        private final CallInfo callInfo;
        private int callEndNotificationCount;

        private TestHangupHandler(CallInfo callInfo, IFsCallCacheService callCache, RedisService redisService) {
            this.callInfo = callInfo;
            this.ifsCallCacheService = callCache;
            this.redisService = redisService;
        }

        @Override
        protected void sendAgentStatus(Long callId, String caller, String callee, Integer direction,
                                       AgentStateEnum status) {
            if (AgentStateEnum.CALL_END.equals(status)) {
                callEndNotificationCount++;
            }
        }

        @Override
        protected CallInfo resolveCallInfo(EslEvent event) {
            return callInfo;
        }
    }
}
