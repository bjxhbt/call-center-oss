package com.och.api.controller.fs;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.och.esl.client.FsClient;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.entity.SipAgent;
import com.och.system.service.ICallRecordService;
import com.och.system.service.ISipAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorControllerTest {

    private MonitorController controller;
    private FsClient fsClient;
    private ICallRecordService callRecordService;
    private ISipAgentService sipAgentService;

    @BeforeEach
    void setUp() {
        controller = new MonitorController();
        fsClient = mock(FsClient.class);
        callRecordService = mock(ICallRecordService.class);
        sipAgentService = mock(ISipAgentService.class);
        ReflectionTestUtils.setField(controller, "fsClient", fsClient);
        ReflectionTestUtils.setField(controller, "callRecordService", callRecordService);
        ReflectionTestUtils.setField(controller, "sipAgentService", sipAgentService);
        ReflectionTestUtils.setField(controller, "staleChannelTimeoutSeconds", 21600L);
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(callRecordService.list(any(QueryWrapper.class))).thenReturn(List.of());
    }

    @Test
    void groupsInboundAndOutboundLegsByBusinessCallId() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(
                        row("agent-uuid", "outbound", "CS_EXECUTE", "ACTIVE", "6005", "18610915226"),
                        row("caller-uuid", "inbound", "CS_EXECUTE", "ACTIVE", "18610915226", "88659000")));
        when(fsClient.getChannelCallId("127.0.0.1:8021", "agent-uuid")).thenReturn("10001");
        when(fsClient.getChannelCallId("127.0.0.1:8021", "caller-uuid")).thenReturn("10001");

        List<Map<String, Object>> calls = controller.listLiveCalls();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0))
                .containsEntry("callId", "10001")
                .containsEntry("callerNumber", "18610915226")
                .containsEntry("calleeNumber", "88659000")
                .containsEntry("direction", 1);
    }

    @Test
    void ignoresUncorrelatedOutboundForkLeg() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(
                        row("orphan-agent", "outbound", "CS_CONSUME_MEDIA", "RINGING", "6005", "6005"),
                        row("caller-uuid", "inbound", "CS_EXECUTE", "ACTIVE", "18610915226", "88659000")));

        List<Map<String, Object>> calls = controller.listLiveCalls();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).containsEntry("callId", "caller-uuid");
    }

    @Test
    void groupsLinkedChannelsWhenOnlyOneLegHasBusinessCallId() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(
                        linkedRow("agent-uuid", "outbound", "CS_EXECUTE", "ACTIVE",
                                "6005", "13311762726", "caller-uuid"),
                        linkedRow("caller-uuid", "inbound", "CS_EXECUTE", "ACTIVE",
                                "13311762726", "88659000", "agent-uuid")));
        when(fsClient.getChannelCallId("127.0.0.1:8021", "agent-uuid")).thenReturn("10003");

        List<Map<String, Object>> calls = controller.listLiveCalls();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0))
                .containsEntry("callId", "10003")
                .containsEntry("callerNumber", "13311762726")
                .containsEntry("direction", 1);
    }

    @Test
    void excludesDeletedAndDisabledAgents() {
        SipAgent active = agent(1L, "6005", 1, 0);
        SipAgent deleted = agent(2L, "1000", 1, 1);
        SipAgent disabled = agent(3L, "6006", 0, 0);
        when(sipAgentService.list(any(QueryWrapper.class)))
                .thenReturn(List.of(active, deleted, disabled));

        assertThat(controller.listActiveAgents()).containsExactly(active);
    }

    @Test
    void filtersChannelWhoseBusinessCallAlreadyEnded() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(
                        row("stale-uuid", "inbound", "CS_EXECUTE", "ACTIVE", "18610915226", "88659000")));
        when(fsClient.getChannelCallId("127.0.0.1:8021", "stale-uuid")).thenReturn("10002");
        CallRecord ended = new CallRecord();
        ended.setCallId("10002");
        ended.setCallEndTime(new Date());
        when(callRecordService.list(any(QueryWrapper.class))).thenReturn(List.of(ended));

        assertThat(controller.listLiveCalls()).isEmpty();
    }

    @Test
    void filtersStaleBusinessChannelEvenWhenCallRecordHasNoEndTime() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(rowAtEpoch(
                        "stale-business", "inbound", "CS_EXECUTE", "ACTIVE",
                        "13311762726", "6005", epochSecondsAgo(86400))));
        when(fsClient.getChannelCallId("127.0.0.1:8021", "stale-business"))
                .thenReturn("2089255339145875456");

        assertThat(controller.listLiveCalls()).isEmpty();
    }

    @Test
    void filtersStaleInboundChannelWithoutBusinessCallId() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(rowAtEpoch(
                        "7ccfe582-6fb3-42cd-bd55-07aca9f0e04f", "inbound", "CS_EXECUTE", "ACTIVE",
                        "13311762726", "88659000", epochSecondsAgo(86400))));

        assertThat(controller.listLiveCalls()).isEmpty();
    }

    @Test
    void keepsRecentInboundChannel() {
        when(fsClient.sendApiText("127.0.0.1:8021", "show", "channels as json"))
                .thenReturn(channels(rowAtEpoch(
                        "recent-inbound", "inbound", "CS_EXECUTE", "ACTIVE",
                        "13311762726", "88659000", epochSecondsAgo(60))));

        assertThat(controller.listLiveCalls()).hasSize(1);
    }

    private String channels(String... rows) {
        return "{\"rows\":[" + String.join(",", rows) + "]}";
    }

    private String row(String uuid, String direction, String state, String callState,
                       String caller, String destination) {
        return rowAtEpoch(uuid, direction, state, callState, caller, destination, epochSecondsAgo(60));
    }

    private String rowAtEpoch(String uuid, String direction, String state, String callState,
                              String caller, String destination, long createdEpoch) {
        return "{\"uuid\":\"" + uuid + "\","
                + "\"direction\":\"" + direction + "\","
                + "\"state\":\"" + state + "\","
                + "\"callstate\":\"" + callState + "\","
                + "\"cid_num\":\"" + caller + "\","
                + "\"initial_dest\":\"" + destination + "\","
                + "\"created_epoch\":\"" + createdEpoch + "\","
                + "\"created\":\"2026-08-21 10:00:00\"}";
    }

    private String linkedRow(String uuid, String direction, String state, String callState,
                             String caller, String destination, String linkedUuid) {
        String row = row(uuid, direction, state, callState, caller, destination);
        return row.substring(0, row.length() - 1) + ",\"b_uuid\":\"" + linkedUuid + "\"}";
    }

    private SipAgent agent(long id, String number, int status, int delFlag) {
        SipAgent agent = new SipAgent();
        agent.setId(id);
        agent.setAgentNumber(number);
        agent.setStatus(status);
        agent.setDelFlag(delFlag);
        return agent;
    }

    private long epochSecondsAgo(long seconds) {
        return System.currentTimeMillis() / 1000L - seconds;
    }
}
