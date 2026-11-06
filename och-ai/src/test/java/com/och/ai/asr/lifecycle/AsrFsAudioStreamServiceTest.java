package com.och.ai.asr.lifecycle;

import com.och.ai.asr.config.AsrProperties;
import com.och.esl.client.FsClient;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FreeSWITCH audio stream start/stop wiring (plan 11.2 P0).
 */
class AsrFsAudioStreamServiceTest {

    private AsrProperties propsEnabled() {
        AsrProperties p = new AsrProperties();
        p.setEnabled(true);
        p.getAudioStream().setEnabled(true);
        p.getAudioStream().setModule("audio_fork");
        p.getAudioStream().setEndpoint("ws://127.0.0.1:4320/asr/v1/audio/ws?callId={callId}&uuid={uniqueId}");
        p.getAudioStream().setSampleRate(8000);
        p.getAudioStream().setMixType("mono");
        p.getAudioStream().setBothLegs(false);
        return p;
    }

    @Test
    void buildStreamUrlReplacesPlaceholdersAndAppendsToken() {
        AsrProperties p = propsEnabled();
        p.getAudioStream().setStreamToken("secret-1");
        FsClient fs = Mockito.mock(FsClient.class);
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);
        String url = svc.buildStreamUrl(p.getAudioStream().getEndpoint(), 99L, "u-99");
        assertTrue(url.contains("callId=99"));
        assertTrue(url.contains("uuid=u-99"));
        assertTrue(url.contains("uniqueId=u-99"));
        assertTrue(url.contains("streamToken=secret-1"));
    }

    @Test
    void buildStreamUrlAddsCorrelationWhenEndpointHasNoPlaceholders() {
        AsrProperties p = propsEnabled();
        p.getAudioStream().setEndpoint("ws://127.0.0.1:4320/asr/v1/audio/ws");
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, Mockito.mock(FsClient.class));

        String url = svc.buildStreamUrl(p.getAudioStream().getEndpoint(), 101L, "uuid-101");

        assertTrue(url.contains("callId=101"));
        assertTrue(url.contains("uniqueId=uuid-101"));
    }

    @Test
    void startOnBridgeInvokesFsClient() {
        AsrProperties p = propsEnabled();
        FsClient fs = Mockito.mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fs.startAudioStream(anyString(), anyLong(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(true);
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);

        CallBridgeEvent event = CallBridgeEvent.builder()
                .callId(1L)
                .uniqueId("uuid-a")
                .otherUniqueId("uuid-b")
                .build();
        svc.startOnBridge(event);

        verify(fs, times(1)).startAudioStream(
                eq("127.0.0.1:8021"), eq(1L), eq("uuid-a"), eq("audio_fork"),
                anyString(), eq(8000), eq("mono"));
        verify(fs, never()).startAudioStream(
                anyString(), anyLong(), eq("uuid-b"), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void bothLegsStartsOtherUuid() {
        AsrProperties p = propsEnabled();
        p.getAudioStream().setBothLegs(true);
        FsClient fs = Mockito.mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fs.startAudioStream(anyString(), anyLong(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(true);
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);

        CallBridgeEvent event = CallBridgeEvent.builder()
                .callId(2L)
                .uniqueId("uuid-a")
                .otherUniqueId("uuid-b")
                .build();
        svc.startOnBridge(event);

        verify(fs, times(1)).startAudioStream(anyString(), eq(2L), eq("uuid-a"), eq("audio_fork"),
                anyString(), anyInt(), anyString());
        verify(fs, times(1)).startAudioStream(anyString(), eq(2L), eq("uuid-b"), eq("audio_fork"),
                anyString(), anyInt(), anyString());
    }

    @Test
    void moduleNoneSkipsStart() {
        AsrProperties p = propsEnabled();
        p.getAudioStream().setModule("none");
        FsClient fs = Mockito.mock(FsClient.class);
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);
        assertFalse(svc.isStreamEnabled());
        svc.startOnBridge(CallBridgeEvent.builder().callId(3L).uniqueId("u").build());
        verify(fs, never()).anyAddress();
        verify(fs, never()).startAudioStream(anyString(), anyLong(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void httpEndpointSkipsNativeWebSocketModule() {
        AsrProperties p = propsEnabled();
        p.getAudioStream().setEndpoint("http://127.0.0.1:4320/asr/v1/audio/frame");
        FsClient fs = Mockito.mock(FsClient.class);
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);

        assertFalse(svc.isStreamEnabled());
        svc.startOnBridge(CallBridgeEvent.builder().callId(30L).uniqueId("u-http").build());
        verify(fs, never()).anyAddress();
    }

    @Test
    void stopOnHangupInvokesFsClient() {
        AsrProperties p = propsEnabled();
        FsClient fs = Mockito.mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fs.stopAudioStream(anyString(), anyLong(), anyString(), anyString())).thenReturn(true);
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);

        svc.stopOnHangup(CallHangupEvent.builder().callId(4L).uniqueId("uuid-x").build());
        verify(fs, times(1)).stopAudioStream(eq("127.0.0.1:8021"), eq(4L), eq("uuid-x"), eq("audio_fork"));
    }

    @Test
    void startNeverThrowsWhenFsFails() {
        AsrProperties p = propsEnabled();
        FsClient fs = Mockito.mock(FsClient.class);
        when(fs.anyAddress()).thenThrow(new RuntimeException("down"));
        AsrFsAudioStreamService svc = new AsrFsAudioStreamService(p, fs);
        svc.startOnBridge(CallBridgeEvent.builder().callId(5L).uniqueId("u").build());
        // no exception
        assertEquals(true, true);
    }
}
