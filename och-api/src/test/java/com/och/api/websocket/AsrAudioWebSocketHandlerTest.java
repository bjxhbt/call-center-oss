package com.och.api.websocket;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.ingest.IAsrAudioIngestService;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsrAudioWebSocketHandlerTest {

    @Test
    void acceptsAudioForkSubprotocolAndForwardsBinaryPcm() throws Exception {
        IAsrAudioIngestService ingest = mock(IAsrAudioIngestService.class);
        AsrProperties properties = new AsrProperties();
        AsrAudioWebSocketHandler handler = new AsrAudioWebSocketHandler(ingest, properties);
        WebSocketSession session = session(
                "ws://127.0.0.1/asr/v1/audio/ws?callId=9001&uniqueId=u-1&codec=PCM&sampleRate=8000",
                new HttpHeaders());

        handler.afterConnectionEstablished(session);
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[]{1, 2, 3, 4}));

        ArgumentCaptor<CallAudioFrame> frame = ArgumentCaptor.forClass(CallAudioFrame.class);
        verify(ingest).onFrame(frame.capture());
        assertEquals(9001L, frame.getValue().getCallId());
        assertEquals("u-1", frame.getValue().getUniqueId());
        assertEquals(AudioCodec.PCM, frame.getValue().getCodec());
        assertEquals(8000, frame.getValue().getSampleRate());
        assertEquals(1L, frame.getValue().getSequence());
        assertEquals(AsrAudioWebSocketHandler.AUDIO_FORK_SUBPROTOCOL,
                handler.getSubProtocols().get(0));
        assertEquals(1L, handler.snapshot().get("acceptedFrames"));
        assertEquals(4L, handler.snapshot().get("acceptedBytes"));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertEquals(0, handler.snapshot().get("activeConnections"));
    }

    @Test
    void rejectsMissingCallIdWithoutForwardingAudio() throws Exception {
        IAsrAudioIngestService ingest = mock(IAsrAudioIngestService.class);
        AsrAudioWebSocketHandler handler = new AsrAudioWebSocketHandler(ingest, new AsrProperties());
        WebSocketSession session = session("ws://127.0.0.1/asr/v1/audio/ws", new HttpHeaders());

        handler.afterConnectionEstablished(session);
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[]{1, 2}));

        verify(session).close(any(CloseStatus.class));
        verify(ingest, never()).onFrame(any());
        assertEquals(1L, handler.snapshot().get("rejectedConnections"));
    }

    @Test
    void enforcesConfiguredStreamTokenDuringHandshake() throws Exception {
        IAsrAudioIngestService ingest = mock(IAsrAudioIngestService.class);
        AsrProperties properties = new AsrProperties();
        properties.getAudioStream().setStreamToken("expected-token");
        AsrAudioWebSocketHandler handler = new AsrAudioWebSocketHandler(ingest, properties);
        WebSocketSession rejected = session(
                "ws://127.0.0.1/asr/v1/audio/ws?callId=2&streamToken=wrong", new HttpHeaders());

        handler.afterConnectionEstablished(rejected);

        verify(rejected).close(any(CloseStatus.class));
        assertEquals(1L, handler.snapshot().get("rejectedConnections"));

        WebSocketSession accepted = session(
                "ws://127.0.0.1/asr/v1/audio/ws?callId=3&streamToken=expected-token", new HttpHeaders());
        handler.afterConnectionEstablished(accepted);
        assertEquals(1, handler.snapshot().get("activeConnections"));
        assertTrue((Boolean) handler.snapshot().get("registered"));
    }

    private WebSocketSession session(String uri, HttpHeaders headers) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getUri()).thenReturn(URI.create(uri));
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("test-session-" + uri.hashCode());
        return session;
    }
}
