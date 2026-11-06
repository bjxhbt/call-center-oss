package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.ai.tts.playback.EslTtsPlaybackPort;
import com.och.esl.client.FsClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ESL 播放端口测试：命令下发、未连接、缺参、异常容错（不抛穿）。
 * <p>不连接真实 FreeSWITCH；FsClient 为 Mockito mock。
 */
class EslTtsPlaybackPortTest {

    private TtsProperties props() {
        TtsProperties p = new TtsProperties();
        p.getPlayback().setMethod("execute");
        return p;
    }

    private TtsPlaybackRequest req(String uuid, String file) {
        return TtsPlaybackRequest.builder().taskId("t1").cacheKey("k").filePath(file)
                .channelUuid(uuid).callId("c1").format("WAV").sampleRate(8000).build();
    }

    @Test
    void play_executeDispatchesPlayFile() {
        FsClient fs = mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("1.2.3.4:8021");
        EslTtsPlaybackPort port = new EslTtsPlaybackPort(fs, props());

        PlaybackAck ack = port.play(req("uuid-1", "/data/tts/a.wav"));

        assertTrue(ack.isAccepted());
        verify(fs).playFile("1.2.3.4:8021", "uuid-1", "/data/tts/a.wav");
    }

    @Test
    void play_broadcastUsesUuidBroadcast() {
        FsClient fs = mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("1.2.3.4:8021");
        when(fs.sendAsyncMsg(anyString(), anyString(), anyString())).thenReturn("job-1");
        TtsProperties p = props();
        p.getPlayback().setMethod("broadcast");
        EslTtsPlaybackPort port = new EslTtsPlaybackPort(fs, p);

        PlaybackAck ack = port.play(req("uuid-1", "/data/tts/a.wav"));

        assertTrue(ack.isAccepted());
        verify(fs).sendAsyncMsg("1.2.3.4:8021", "uuid_broadcast", "uuid-1 playback::/data/tts/a.wav");
        verify(fs, never()).playFile(anyString(), anyString(), anyString());
    }

    @Test
    void play_rejectedWhenFsNotConnected() {
        FsClient fs = mock(FsClient.class);
        when(fs.anyAddress()).thenReturn(null);
        EslTtsPlaybackPort port = new EslTtsPlaybackPort(fs, props());

        PlaybackAck ack = port.play(req("uuid-1", "/data/tts/a.wav"));
        assertFalse(ack.isAccepted());
        verify(fs, never()).playFile(anyString(), anyString(), anyString());
    }

    @Test
    void play_rejectedWhenMissingChannelOrFile() {
        FsClient fs = mock(FsClient.class);
        EslTtsPlaybackPort port = new EslTtsPlaybackPort(fs, props());

        assertFalse(port.play(req(null, "/x/a.wav")).isAccepted());
        assertFalse(port.play(req("uuid-1", null)).isAccepted());
        verifyNoInteractions(fs);
    }

    @Test
    void play_swallowsExceptionAndKeepsChannelAlive() {
        FsClient fs = mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("1.2.3.4:8021");
        doThrow(new RuntimeException("fs down")).when(fs).playFile(anyString(), anyString(), anyString());
        EslTtsPlaybackPort port = new EslTtsPlaybackPort(fs, props());

        PlaybackAck ack = port.play(req("uuid-1", "/data/tts/a.wav"));
        assertFalse(ack.isAccepted());
        // 不抛出
    }

    @Test
    void stop_dispatchesPlayBreak() {
        FsClient fs = mock(FsClient.class);
        when(fs.anyAddress()).thenReturn("1.2.3.4:8021");
        EslTtsPlaybackPort port = new EslTtsPlaybackPort(fs, props());

        PlaybackAck ack = port.stop("uuid-1");
        assertTrue(ack.isAccepted());
        verify(fs).playBreak("1.2.3.4:8021", "uuid-1");
    }
}
