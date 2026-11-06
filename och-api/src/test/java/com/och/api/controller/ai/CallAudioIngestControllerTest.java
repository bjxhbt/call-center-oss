package com.och.api.controller.ai;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.ingest.IAsrAudioIngestService;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.common.base.ResResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CallAudioIngestControllerTest {

    @Mock
    private IAsrAudioIngestService audioIngest;

    @InjectMocks
    private CallAudioIngestController controller;

    @BeforeEach
    void setUp() {
        AsrProperties props = new AsrProperties();
        AsrProperties.AudioStream stream = new AsrProperties.AudioStream();
        stream.setStreamToken("");
        props.setAudioStream(stream);
        ReflectionTestUtils.setField(controller, "asrProperties", props);
    }

    @Test
    void frame_accepts_callId_from_query_when_header_missing() {
        byte[] pcm = new byte[] {0x01, 0x02, 0x03, 0x04};
        ResResult<String> res = controller.frame(
                null, null, null, null, null, null, null,
                1001L, "uuid-query", "PCMA", 8000, 7L, "agent", null,
                pcm);
        assertEquals("ok", res.getData());
        ArgumentCaptor<CallAudioFrame> cap = ArgumentCaptor.forClass(CallAudioFrame.class);
        verify(audioIngest).onFrame(cap.capture());
        assertEquals(1001L, cap.getValue().getCallId());
        assertEquals("uuid-query", cap.getValue().getUniqueId());
        assertEquals(7L, cap.getValue().getSequence());
    }

    @Test
    void frame_prefers_header_over_query() {
        byte[] pcm = new byte[] {0x0a};
        ResResult<String> res = controller.frame(
                2002L, "uuid-header", "PCM", 16000, 1L, "customer", null,
                1001L, "uuid-query", "PCMA", 8000, 7L, "agent", null,
                pcm);
        assertEquals("ok", res.getData());
        ArgumentCaptor<CallAudioFrame> cap = ArgumentCaptor.forClass(CallAudioFrame.class);
        verify(audioIngest).onFrame(cap.capture());
        assertEquals(2002L, cap.getValue().getCallId());
        assertEquals("uuid-header", cap.getValue().getUniqueId());
    }

    @Test
    void frame_missing_callId_returns_2xx_marker() {
        ResResult<String> res = controller.frame(
                null, null, null, null, null, null, null,
                null, "uuid", null, null, null, null, null,
                new byte[] {1});
        assertTrue(res.getData().contains("no-callId"));
    }
}
