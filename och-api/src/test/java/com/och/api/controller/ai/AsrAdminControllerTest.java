package com.och.api.controller.ai;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.ingest.IAsrAudioIngestService;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.ai.asr.session.AsrSessionManager;
import com.och.ai.asr.simulator.MockRecordingAudioFeeder;
import com.och.common.base.ResResult;
import com.och.esl.client.FsClient;
import com.och.esl.client.FsConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsrAdminControllerTest {

    @Mock
    private AsrProviderRegistry providerRegistry;
    @Mock
    private AsrSessionManager sessionManager;
    @Mock
    private IAsrAudioIngestService audioIngest;
    @Mock
    private MockRecordingAudioFeeder feeder;
    @Mock
    private IAsrTranscriptService transcriptService;
    @Mock
    private FsClient fsClient;

    @InjectMocks
    private AsrAdminController controller;

    private AsrProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AsrProperties();
        properties.setEnabled(true);
        AsrProperties.AudioStream stream = new AsrProperties.AudioStream();
        stream.setEnabled(true);
        stream.setModule("audio_fork");
        stream.setEndpoint("ws://127.0.0.1:4320/asr/v1/audio/ws");
        properties.setAudioStream(stream);
        ReflectionTestUtils.setField(controller, "properties", properties);
    }

    @Test
    void status_marks_audio_stream_blocked_when_mod_audio_fork_is_missing() {
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_fork"))
                .thenReturn("false");

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertTrue((Boolean) freeSwitch.get("eslConnected"));
        assertTrue((Boolean) freeSwitch.get("audioStreamEnabled"));
        assertFalse((Boolean) freeSwitch.get("audioForkAvailable"));
        assertFalse((Boolean) freeSwitch.get("audioStreamOperational"));
        assertTrue((Boolean) freeSwitch.get("streamEndpointCompatible"));
        verify(fsClient).sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_fork");
    }

    @Test
    void status_marks_audio_stream_operational_when_mod_audio_fork_is_loaded() {
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_fork"))
                .thenReturn("true\n");

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertTrue((Boolean) freeSwitch.get("audioForkAvailable"));
        assertTrue((Boolean) freeSwitch.get("audioStreamOperational"));
        assertEquals("127.0.0.1:8021", freeSwitch.get("eslAddress"));
        assertEquals("mod_audio_fork", freeSwitch.get("audioStreamFsModule"));
        assertEquals("ws", freeSwitch.get("streamEndpointTransport"));
    }

    @Test
    void status_marks_audio_stream_blocked_when_esl_is_disconnected() {
        when(fsClient.anyAddress()).thenReturn(null);

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertFalse((Boolean) freeSwitch.get("eslConnected"));
        assertNull(freeSwitch.get("eslAddress"));
        assertNull(freeSwitch.get("audioForkAvailable"));
        assertFalse((Boolean) freeSwitch.get("audioStreamOperational"));
    }

    @Test
    void status_reports_live_event_channel_without_claiming_command_or_stream_readiness() {
        when(fsClient.connectionStatus()).thenReturn(new FsConnectionStatus(
                null, "127.0.0.1:8021", 1_700_000_000_000L, 1_000L, true));
        when(fsClient.anyAddress()).thenReturn(null);

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertTrue((Boolean) freeSwitch.get("eslConnected"));
        assertFalse((Boolean) freeSwitch.get("eslCommandReady"));
        assertEquals("127.0.0.1:8021", freeSwitch.get("eslAddress"));
        assertNull(freeSwitch.get("audioStreamModuleAvailable"));
        assertFalse((Boolean) freeSwitch.get("audioStreamOperational"));
    }

    @Test
    void status_does_not_report_stream_ready_when_module_probe_fails() {
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_fork"))
                .thenThrow(new IllegalStateException("probe failed"));

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertFalse((Boolean) freeSwitch.get("audioForkAvailable"));
        assertFalse((Boolean) freeSwitch.get("audioStreamOperational"));
    }

    @Test
    void status_rejects_http_endpoint_for_native_websocket_module() {
        properties.getAudioStream().setEndpoint("http://127.0.0.1:4320/asr/v1/audio/frame");
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_fork"))
                .thenReturn("true");

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertTrue((Boolean) freeSwitch.get("audioStreamModuleAvailable"));
        assertFalse((Boolean) freeSwitch.get("streamEndpointCompatible"));
        assertFalse((Boolean) freeSwitch.get("audioStreamOperational"));
    }

    @Test
    void status_probes_mod_audio_stream_when_selected() {
        properties.getAudioStream().setModule("audio_stream");
        when(fsClient.anyAddress()).thenReturn("127.0.0.1:8021");
        when(fsClient.sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_stream"))
                .thenReturn("true");

        Map<String, Object> freeSwitch = freeSwitchStatus(controller.status());

        assertEquals("mod_audio_stream", freeSwitch.get("audioStreamFsModule"));
        assertNull(freeSwitch.get("audioForkAvailable"));
        assertTrue((Boolean) freeSwitch.get("audioStreamModuleAvailable"));
        assertTrue((Boolean) freeSwitch.get("audioStreamOperational"));
        verify(fsClient).sendApiText("127.0.0.1:8021", "module_exists", "mod_audio_stream");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> freeSwitchStatus(ResResult<Map<String, Object>> result) {
        return (Map<String, Object>) result.getData().get("freeSwitch");
    }
}
