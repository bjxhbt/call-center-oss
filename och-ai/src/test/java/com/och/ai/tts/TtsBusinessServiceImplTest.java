package com.och.ai.tts;

import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsPlaybackStatus;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsTaskStatus;
import com.och.ai.tts.model.TtsTaskView;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.service.TtsFileService;
import com.och.ai.tts.service.TtsService;
import com.och.ai.tts.service.TtsTaskService;
import com.och.ai.tts.service.impl.TtsBusinessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 业务流转测试（方案 9.6）：合成→任务状态→播放→事件回写。
 */
class TtsBusinessServiceImplTest {

    private TtsService ttsService;
    private TtsTaskService taskService;
    private TtsFileService fileService;
    private TtsPlaybackPort playbackPort;
    private ApplicationEventPublisher publisher;
    private TtsBusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        ttsService = mock(TtsService.class);
        taskService = mock(TtsTaskService.class);
        fileService = mock(TtsFileService.class);
        playbackPort = mock(TtsPlaybackPort.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new TtsBusinessServiceImpl(ttsService, taskService, fileService, playbackPort, publisher);
        when(taskService.create(any())).thenReturn("tid");
        when(taskService.updateStatus(anyString(), anyString(), any(), anyString(), anyString())).thenReturn(1);
    }

    private TtsResult okResult() {
        return TtsResult.builder()
                .success(true).provider(TtsProviderType.MOCK).voice("mock-default")
                .format(TtsAudioFormat.WAV).sampleRate(8000).durationMs(500L).sizeBytes(8400L)
                .fileId(5L).filePath("/x/a.wav").fileName("a.wav").listenUrl("/tts/v1/file/KEY")
                .cacheKey("KEY").build();
    }

    @Test
    void submitWithoutChannel_staysSuccessNoPlayback() {
        when(ttsService.synthesize(any())).thenReturn(okResult());
        TtsRequest req = TtsRequest.builder().text("你好").sampleRate(8000).format(TtsAudioFormat.WAV).build();

        TtsTaskView view = service.submit(req);

        assertEquals(TtsTaskStatus.SUCCESS.name(), view.getStatus());
        verify(playbackPort, never()).play(any());
        verify(taskService).updateStatus(eq("tid"), eq(TtsTaskStatus.SUCCESS.name()), eq(5L), any(), any());
    }

    @Test
    void submitWithChannel_dispatchesPlayback() {
        when(ttsService.synthesize(any())).thenReturn(okResult());
        when(playbackPort.play(any())).thenReturn(PlaybackAck.builder().accepted(true).message("ok").build());
        TtsRequest req = TtsRequest.builder().text("你好").channelUuid("uuid-1").callId("call-1")
                .sampleRate(8000).format(TtsAudioFormat.WAV).build();

        TtsTaskView view = service.submit(req);

        assertEquals(TtsTaskStatus.PLAYING.name(), view.getStatus());
        verify(playbackPort).play(any(TtsPlaybackRequest.class));
        verify(fileService).adjustRefCount(5L, 1);
    }

    @Test
    void submitSynthesisFailure_marksTaskFailed() {
        when(ttsService.synthesize(any())).thenReturn(TtsResult.builder()
                .success(false).provider(TtsProviderType.ALIYUN)
                .errorCode(TtsErrorCode.AUTH_FAILED).errorMsg("凭证缺失").cacheKey("KEY").build());
        TtsRequest req = TtsRequest.builder().text("你好").build();

        TtsTaskView view = service.submit(req);

        assertEquals(TtsTaskStatus.FAILED.name(), view.getStatus());
        assertEquals(TtsErrorCode.AUTH_FAILED, view.getErrorCode());
        verify(playbackPort, never()).play(any());
    }

    @Test
    void playbackCompleteEvent_marksPlayed() {
        when(taskService.getByTaskId("tid")).thenReturn(taskOf("tid", 5L));
        service.onPlaybackEvent(TtsPlaybackEvent.builder()
                .taskId("tid").status(TtsPlaybackStatus.COMPLETE).build());
        verify(taskService).updateStatus(eq("tid"), eq(TtsTaskStatus.PLAYED.name()), any(), any(), any());
    }

    @Test
    void playbackFailedEvent_releasesRefAndMarksFailed() {
        when(taskService.getByTaskId("tid")).thenReturn(taskOf("tid", 5L));
        service.onPlaybackEvent(TtsPlaybackEvent.builder()
                .taskId("tid").status(TtsPlaybackStatus.FAILED)
                .errorCode(TtsErrorCode.PLAYBACK_FAILED).build());
        verify(taskService).updateStatus(eq("tid"), eq(TtsTaskStatus.FAILED.name()), any(), eq(TtsErrorCode.PLAYBACK_FAILED), any());
        verify(fileService).adjustRefCount(5L, -1);
    }

    private com.och.ai.tts.entity.TtsTask taskOf(String taskId, Long fileId) {
        com.och.ai.tts.entity.TtsTask t = new com.och.ai.tts.entity.TtsTask();
        t.setTaskId(taskId);
        t.setFileId(fileId);
        return t;
    }

    @Test
    void playbackFailure_doesNotPropagate() {
        when(ttsService.synthesize(any())).thenReturn(okResult());
        when(playbackPort.play(any())).thenThrow(new RuntimeException("fs down"));
        TtsRequest req = TtsRequest.builder().text("你好").channelUuid("uuid-1").build();

        TtsTaskView view = service.submit(req); // 不应抛

        assertEquals(TtsTaskStatus.FAILED.name(), view.getStatus());
        assertEquals(TtsErrorCode.PLAYBACK_FAILED, view.getErrorCode());
    }
}
