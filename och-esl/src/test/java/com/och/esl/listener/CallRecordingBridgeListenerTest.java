package com.och.esl.listener;

import com.och.esl.client.FsClient;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.service.CallRecordingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallRecordingBridgeListenerTest {

    @Test
    void retriesRecordingAndBroadcastsNoticeToCallerLegOnly() {
        FsClient fsClient = mock(FsClient.class);
        CallRecordingService recordingService = mock(CallRecordingService.class);
        CallRecordingBridgeListener listener = new CallRecordingBridgeListener(fsClient, recordingService);
        ReflectionTestUtils.setField(listener, "recordingNoticePath", "/opt/och/ivr-audio/luyin.wav");
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(recordingService.startRecording("127.0.0.1:8021", 123L, "caller-uuid"))
                .thenReturn(false);
        when(recordingService.claimRecordingNotice(123L, "caller-uuid")).thenReturn(true);
        when(fsClient.broadcastFile("127.0.0.1:8021", "caller-uuid",
                "/opt/och/ivr-audio/luyin.wav", "aleg")).thenReturn(true);

        listener.onBridge(CallBridgeEvent.builder()
                .callId(123L)
                .direction(1)
                .uniqueId("agent-uuid")
                .otherUniqueId("caller-uuid")
                .recordingUniqueId("caller-uuid")
                .build());

        verify(recordingService).startRecording("127.0.0.1:8021", 123L, "caller-uuid");
        verify(recordingService).claimRecordingNotice(123L, "caller-uuid");
        verify(fsClient).broadcastFile("127.0.0.1:8021", "caller-uuid",
                "/opt/och/ivr-audio/luyin.wav", "aleg");
        verify(recordingService, never()).armAfterPlayback(123L, "caller-uuid");
    }
}
