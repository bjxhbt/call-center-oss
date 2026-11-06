package com.och.esl.client;

import com.och.esl.propeties.FsClientProperties;
import com.och.esl.service.IFsEslEventService;
import com.och.system.service.IFsConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class FsClientInternalDialEndpointTest {

    private FsClient fsClient;

    @BeforeEach
    void setUp() {
        fsClient = new FsClient(mock(IFsConfigService.class), new FsClientProperties());
        ReflectionTestUtils.setField(fsClient, "ILfsEslEventService", mock(IFsEslEventService.class));
    }

    @AfterEach
    void tearDown() {
        fsClient.destroy();
    }

    @Test
    void forksAllRegisteredContactsForTheSameSipNumber() {
        String webPhone = "sofia/internal/sip:6005@127.0.0.1:50096;transport=ws;fs_nat=yes";
        String deskPhone = "sofia/internal/sip:6005@127.0.0.1:5060;fs_nat=yes";

        String endpoint = fsClient.buildInternalDialEndpoint(webPhone + "," + deskPhone, "6005");

        assertThat(endpoint).isEqualTo(webPhone + "," + deskPhone);
    }

    @Test
    void removesDuplicateAndInvalidContacts() {
        String contact = "sofia/internal/sip:6005@127.0.0.1:5060;fs_nat=yes";

        String endpoint = fsClient.buildInternalDialEndpoint(
                contact + ", " + contact + ",error/user_not_registered,-ERR no contact", "6005");

        assertThat(endpoint).isEqualTo(contact);
    }

    @Test
    void fallsBackToDirectoryLookupWhenContactOutputCannotBeUsed() {
        assertThat(fsClient.buildInternalDialEndpoint("error/user_not_registered", "6005"))
                .isEqualTo("user/6005");
    }

    @Test
    void keepsOnlyBrowserContactsFromMixedRegistrations() {
        String webPhone = "sofia/internal/sip:6005@127.0.0.1:50096;transport=ws;fs_nat=yes";
        String secureWebPhone = "sofia/internal/sip:6005@127.0.0.1:50097;transport=wss;fs_nat=yes";
        String deskPhone = "sofia/internal/sip:6005@127.0.0.1:5060;transport=udp;fs_nat=yes";

        assertThat(fsClient.filterWebRtcContacts(deskPhone + "," + webPhone + "," + secureWebPhone))
                .isEqualTo(webPhone + "," + secureWebPhone);
    }

    @Test
    void doesNotTreatUdpSipPhoneAsBrowserRegistration() {
        FsClient checkedClient = spy(fsClient);
        doReturn("sofia/internal/sip:6005@127.0.0.1:5060;transport=udp;fs_nat=yes")
                .when(checkedClient).sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");

        assertThat(checkedClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).isFalse();
    }

    @Test
    void acceptsWsAndWssBrowserRegistrations() {
        FsClient checkedClient = spy(fsClient);
        doReturn("sofia/internal/sip:6005@127.0.0.1:50096;transport=wss;fs_nat=yes")
                .when(checkedClient).sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");

        assertThat(checkedClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).isTrue();
    }

    @Test
    void checksBothContactLookupFormsForBrowserRegistration() {
        FsClient checkedClient = spy(fsClient);
        doReturn("sofia/internal/sip:6005@127.0.0.1:5060;transport=udp;fs_nat=yes")
                .when(checkedClient).sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");
        doReturn("sofia/internal/sip:6005@127.0.0.1:50096;transport=ws;fs_nat=yes")
                .when(checkedClient).sendApiText("127.0.0.1:8021", "sofia_contact", "6005");

        assertThat(checkedClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).isTrue();
    }

    @Test
    void doesNotUseTransportFromFsPathAsBrowserRegistration() {
        String udpContactWithWebSocketPath = "sofia/internal/sip:6005@127.0.0.1:5060;transport=udp"
                + ";fs_path=sip:proxy.example.test;transport=ws";

        assertThat(fsClient.filterWebRtcContacts(udpContactWithWebSocketPath)).isEmpty();
    }

    @Test
    void handlesMissingEslResponseAsNotRegistered() {
        FsClient checkedClient = spy(fsClient);
        doReturn(null).when(checkedClient)
                .sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");
        doReturn(null).when(checkedClient)
                .sendApiText("127.0.0.1:8021", "sofia_contact", "6005");

        assertThat(checkedClient.isWebRtcUserRegistered("127.0.0.1:8021", "6005")).isFalse();
    }

    @Test
    void browserOriginateStopsBeforeFreeSwitchWhenSecondContactLookupIsEmpty() {
        FsClient checkedClient = spy(fsClient);
        doReturn(null).when(checkedClient)
                .sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");
        doReturn(null).when(checkedClient)
                .sendApiText("127.0.0.1:8021", "sofia_contact", "6005");
        AtomicReference<String> failure = new AtomicReference<>();

        checkedClient.makeWebRtcCall("127.0.0.1:8021", 1001L, "6005", "13800138000",
                "agent-attempt-uuid", 10, failure::set);

        assertThat(failure.get()).isEqualTo("browser softphone not registered: 6005");
        verify(checkedClient, never()).sendSyncMsg(
                eq("127.0.0.1:8021"), eq("originate"), anyString());
    }

    @Test
    void browserOriginateReportsContactLookupExceptionToRoute() {
        FsClient checkedClient = spy(fsClient);
        doThrow(new IllegalStateException("ESL disconnected")).when(checkedClient)
                .sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");
        AtomicReference<String> failure = new AtomicReference<>();

        checkedClient.makeWebRtcCall("127.0.0.1:8021", 1001L, "6005", "13800138000",
                "agent-attempt-uuid", 10, failure::set);

        assertThat(failure.get()).isEqualTo("ESL disconnected");
        verify(checkedClient, never()).sendSyncMsg(
                eq("127.0.0.1:8021"), eq("originate"), anyString());
    }

    @Test
    void browserOriginateTreatsMissingEslReplyAsFailure() throws InterruptedException {
        FsClient checkedClient = spy(fsClient);
        doReturn("sofia/internal/sip:6005@127.0.0.1:50096;transport=ws;fs_nat=yes")
                .when(checkedClient).sendApiText("127.0.0.1:8021", "sofia_contact", "user/6005");
        doReturn(null).when(checkedClient)
                .sendApiText("127.0.0.1:8021", "sofia_contact", "6005");
        doReturn(null).when(checkedClient)
                .sendSyncMsg(eq("127.0.0.1:8021"), eq("originate"), anyString());
        AtomicReference<String> failure = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);

        checkedClient.makeWebRtcCall("127.0.0.1:8021", 1001L, "6005", "13800138000",
                "agent-attempt-uuid", 10, error -> {
                    failure.set(error);
                    callback.countDown();
                });

        assertThat(callback.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isEqualTo("empty response from FreeSWITCH");
    }
}
