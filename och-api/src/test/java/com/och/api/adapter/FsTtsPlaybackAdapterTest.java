package com.och.api.adapter;

import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.ai.tts.model.TtsPlaybackStatus;
import com.och.ai.tts.service.TtsBusinessService;
import com.och.api.service.IFsPlaybackService;
import com.och.esl.event.ChannelPlaybackEvent;
import com.och.system.domain.vo.playback.PlaybackStatusInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证真实播放适配器：play 下发 + ChannelPlaybackEvent → TtsPlaybackEvent 事件回写(P0)。
 */
@ExtendWith(MockitoExtension.class)
class FsTtsPlaybackAdapterTest {

    @Mock
    private IFsPlaybackService fsPlaybackService;

    @Mock
    private TtsBusinessService ttsBusinessService;

    @InjectMocks
    private FsTtsPlaybackAdapter adapter;

    private TtsPlaybackRequest request(String taskId, String uuid, String file) {
        return TtsPlaybackRequest.builder()
                .taskId(taskId).callId("call-1").cacheKey("k1")
                .channelUuid(uuid).filePath(file).build();
    }

    private PlaybackStatusInfo info(String state) {
        PlaybackStatusInfo i = new PlaybackStatusInfo();
        i.setUuid("uuid-1");
        i.setState(state);
        return i;
    }

    @Test
    void play_accepted_whenNotFailed() {
        when(fsPlaybackService.play(eq("uuid-1"), eq("/a.wav"), any())).thenReturn(info(PlaybackStatusInfo.STATE_REQUESTED));
        PlaybackAck ack = adapter.play(request("task-1", "uuid-1", "/a.wav"));
        assertTrue(ack.isAccepted());
        verify(fsPlaybackService).play("uuid-1", "/a.wav", null);
    }

    @Test
    void play_rejected_whenMissingChannelOrFile() {
        PlaybackAck ack = adapter.play(TtsPlaybackRequest.builder().taskId("t").build());
        assertFalse(ack.isAccepted());
        verify(fsPlaybackService, never()).play(any(), any(), any());
    }

    @Test
    void play_rejected_whenFsReturnsFailed() {
        when(fsPlaybackService.play(any(), any(), any())).thenReturn(info(PlaybackStatusInfo.STATE_FAILED));
        PlaybackAck ack = adapter.play(request("task-1", "uuid-1", "/a.wav"));
        assertFalse(ack.isAccepted());
    }

    @Test
    void eventPlayback_writesBackStartThenComplete() {
        when(fsPlaybackService.play(any(), any(), any())).thenReturn(info(PlaybackStatusInfo.STATE_REQUESTED));
        adapter.play(request("task-1", "uuid-1", "/a.wav"));

        // PLAYBACK_START -> START
        adapter.onChannelPlayback(event("uuid-1", PlaybackStatusInfo.STATE_PLAYING, null));
        // PLAYBACK_STOP -> COMPLETE，并清理上下文
        adapter.onChannelPlayback(event("uuid-1", PlaybackStatusInfo.STATE_COMPLETED, null));
        // 终态后再发 COMPLETE：上下文已清理，不再回写
        adapter.onChannelPlayback(event("uuid-1", PlaybackStatusInfo.STATE_COMPLETED, null));

        ArgumentCaptor<TtsPlaybackEvent> captor = ArgumentCaptor.forClass(TtsPlaybackEvent.class);
        verify(ttsBusinessService, org.mockito.Mockito.times(2)).onPlaybackEvent(captor.capture());
        java.util.List<TtsPlaybackEvent> events = captor.getAllValues();
        assertEquals(TtsPlaybackStatus.START, events.get(0).getStatus());
        assertEquals("task-1", events.get(0).getTaskId());
        assertEquals(TtsPlaybackStatus.COMPLETE, events.get(1).getStatus());
    }

    @Test
    void eventPlayback_failed_writesBackFailedWithCode() {
        when(fsPlaybackService.play(any(), any(), any())).thenReturn(info(PlaybackStatusInfo.STATE_FAILED));
        adapter.play(request("task-2", "uuid-2", "/b.wav"));

        adapter.onChannelPlayback(event("uuid-2", PlaybackStatusInfo.STATE_FAILED, "wav 校验失败"));
        ArgumentCaptor<TtsPlaybackEvent> captor = ArgumentCaptor.forClass(TtsPlaybackEvent.class);
        verify(ttsBusinessService).onPlaybackEvent(captor.capture());
        assertEquals(TtsPlaybackStatus.FAILED, captor.getValue().getStatus());
        assertEquals("TTS_013", captor.getValue().getErrorCode());
        assertNotNull(captor.getValue().getErrorMsg());
    }

    @Test
    void eventPlayback_nonTtsPlayback_ignored() {
        // 未经过 play() 登记的通道事件(如 IVR 放音)不应回写 TTS 任务
        adapter.onChannelPlayback(event("other-uuid", PlaybackStatusInfo.STATE_PLAYING, null));
        verify(ttsBusinessService, never()).onPlaybackEvent(any());
    }

    @Test
    void stop_delegates() {
        adapter.stop("uuid-1");
        verify(fsPlaybackService).stop("uuid-1");
    }

    private ChannelPlaybackEvent event(String uuid, String state, String reason) {
        return ChannelPlaybackEvent.builder()
                .uuid(uuid).state(state).reason(reason).timestamp(System.currentTimeMillis()).build();
    }
}
