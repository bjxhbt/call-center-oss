package com.och.ai.asr.lifecycle;

import com.och.ai.asr.session.AsrSessionManager;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * Ensures bridge/hangup wiring keeps ASR stream control ordered and call-safe.
 */
@ExtendWith(MockitoExtension.class)
class AsrCallLifecycleListenerTest {

    @Mock
    private AsrSessionManager sessionManager;

    @Mock
    private AsrFsAudioStreamService audioStreamService;

    @InjectMocks
    private AsrCallLifecycleListener listener;

    @Test
    void onBridgeStartsSessionThenAudioStream() {
        CallBridgeEvent event = CallBridgeEvent.builder()
                .callId(1001L)
                .uniqueId("uuid-a")
                .otherUniqueId("uuid-b")
                .build();

        listener.onBridge(event);

        InOrder order = inOrder(sessionManager, audioStreamService);
        order.verify(sessionManager).onBridge(event);
        order.verify(audioStreamService).startOnBridge(event);
    }

    @Test
    void onHangupStopsStreamThenFinalizesSession() {
        CallHangupEvent event = CallHangupEvent.builder()
                .callId(1001L)
                .uniqueId("uuid-a")
                .build();

        listener.onHangup(event);

        InOrder order = inOrder(audioStreamService, sessionManager);
        order.verify(audioStreamService).stopOnHangup(event);
        order.verify(sessionManager).onHangup(event);
    }

    @Test
    void sessionFailureDoesNotBlockStreamStart() {
        CallBridgeEvent event = CallBridgeEvent.builder()
                .callId(2002L)
                .uniqueId("uuid-x")
                .build();
        doThrow(new RuntimeException("session boom")).when(sessionManager).onBridge(any());

        assertDoesNotThrow(() -> listener.onBridge(event));
        verify(audioStreamService).startOnBridge(event);
    }

    @Test
    void streamStopFailureDoesNotBlockHangupFinalize() {
        CallHangupEvent event = CallHangupEvent.builder()
                .callId(2002L)
                .uniqueId("uuid-x")
                .build();
        doThrow(new RuntimeException("stream boom")).when(audioStreamService).stopOnHangup(any());

        assertDoesNotThrow(() -> listener.onHangup(event));
        verify(sessionManager).onHangup(event);
    }
}
