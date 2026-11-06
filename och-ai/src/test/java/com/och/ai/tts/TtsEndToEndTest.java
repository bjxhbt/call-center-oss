package com.och.ai.tts;

import com.och.ai.tts.cache.TtsCacheService;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.entity.TtsTask;
import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.model.PlaybackAck;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsPlaybackRequest;
import com.och.ai.tts.model.TtsPlaybackStatus;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsStatusChangedEvent;
import com.och.ai.tts.model.TtsTaskStatus;
import com.och.ai.tts.model.TtsTaskView;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.provider.MockTtsProvider;
import com.och.ai.tts.provider.TtsProviderRegistry;
import com.och.ai.tts.service.TtsFileService;
import com.och.ai.tts.service.TtsTaskService;
import com.och.ai.tts.service.impl.TtsBusinessServiceImpl;
import com.och.ai.tts.service.impl.TtsServiceImpl;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.SysSettingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TTS 子系统端到端测试（任务 10.4 / 9.6 闭环）。
 *
 * <p>不启动 Spring、不连 DB、不依赖 FreeSWITCH、不需云凭证：
 * 使用真实 {@code TtsBusinessService}+{@code TtsServiceImpl}+真实 Mock Provider+
 * 真实文件落盘(临时目录)+内存版 Redis 缓存+录制型播放端口，仅 DB 边界(TtsFileService/TtsTaskService)为桩。
 *
 * <p>验证整条：合成→落盘→缓存命中→下发播放→事件回写(PLAYING→PLAYED)，以及强制刷新与通话异常容错。
 */
class TtsEndToEndTest {

    @TempDir
    Path tempDir;

    private TtsProperties properties;
    private TtsFileManager fileManager;
    private TtsCacheService cacheService;
    private TtsFileService fileService;
    private TtsTaskService taskService;
    private RecordingPlaybackPort playbackPort;
    private TtsServiceImpl ttsService;
    private TtsBusinessServiceImpl businessService;

    /** 内存版 Redis，保证缓存读写真实。 */
    private final Map<String, Object> redisStore = new HashMap<>();

    /** 记录的业务事件。 */
    private final java.util.List<TtsStatusChangedEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        properties = new TtsProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setCacheEnabled(true);
        properties.setCacheTtlSeconds(3600);

        fileManager = new TtsFileManager(properties, mock(SysSettingConfig.class));

        RedisService redis = mock(RedisService.class);
        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(redis).setCacheObject(anyString(), any(Object.class), anyLong(), any(TimeUnit.class));
        when(redis.getCacheObject(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        when(redis.keyIsExists(anyString())).thenAnswer(inv -> redisStore.containsKey(inv.getArgument(0)));
        when(redis.deleteObject(anyString())).thenAnswer(inv -> redisStore.remove(inv.getArgument(0)) != null);
        cacheService = new TtsCacheService(redis);

        // DB 边界桩：保存即落内存，getById/save/adjustRefCount 行为真实
        Map<Long, TtsFile> fileStore = new HashMap<>();
        AtomicLong fileIdSeq = new AtomicLong(100);
        fileService = mock(TtsFileService.class);
        when(fileService.save(any())).thenAnswer(inv -> {
            TtsFile f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(fileIdSeq.incrementAndGet());
            }
            fileStore.put(f.getId(), f);
            return f.getId();
        });
        when(fileService.getById(anyLong())).thenAnswer(inv -> fileStore.get(inv.getArgument(0)));
        when(fileService.findSuccessByCacheKey(anyString())).thenReturn(null);
        when(fileService.adjustRefCount(anyLong(), anyInt())).thenReturn(1);

        // 任务记录桩
        Map<String, TtsTask> taskStore = new HashMap<>();
        taskService = mock(TtsTaskService.class);
        when(taskService.create(any())).thenAnswer(inv -> {
            TtsTask t = inv.getArgument(0);
            taskStore.put(t.getTaskId(), t);
            return t.getTaskId();
        });
        when(taskService.getByTaskId(anyString())).thenAnswer(inv -> taskStore.get(inv.getArgument(0)));
        when(taskService.updateStatus(anyString(), anyString(), any(), any(), any())).thenAnswer(inv -> {
            TtsTask t = taskStore.get(inv.getArgument(0));
            if (t != null) {
                t.setStatus(inv.getArgument(1));
                Long fid = inv.getArgument(2);
                if (fid != null) {
                    t.setFileId(fid);
                }
            }
            return 1;
        });

        MockTtsProvider mockProvider = new MockTtsProvider(properties);
        TtsProviderRegistry registry = new TtsProviderRegistry(List.of(mockProvider), properties);
        registry.init();
        ttsService = new TtsServiceImpl(properties, registry, cacheService, fileService, fileManager);

        playbackPort = new RecordingPlaybackPort();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof TtsStatusChangedEvent e) {
                events.add(e);
            }
        };
        businessService = new TtsBusinessServiceImpl(ttsService, taskService, fileService, playbackPort, publisher);
    }

    @Test
    void fullLifecycle_synthesizeCachePlayComplete() {
        TtsRequest req = TtsRequest.builder()
                .text("欢迎致电呼叫中心")
                .voice("mock-default")
                .provider(TtsProviderType.MOCK)
                .sampleRate(8000).format(TtsAudioFormat.WAV)
                .callId("call-1").channelUuid("uuid-1")
                .build();

        // 1) 首次合成 + 下发播放
        TtsTaskView v1 = businessService.submit(req);
        assertEquals(TtsTaskStatus.PLAYING.name(), v1.getStatus());
        assertTrue(v1.getFileId() >= 100);
        assertNotNull(v1.getFilePath());
        assertTrue(new java.io.File(v1.getFilePath()).exists(), "音频应真实落盘");
        assertEquals("call-1", playbackPort.last.getCallId());
        assertEquals("uuid-1", playbackPort.last.getChannelUuid());
        assertEquals(v1.getFilePath(), playbackPort.last.getFilePath());

        // 2) 通信链路回写播放完成 → PLAYED
        businessService.onPlaybackEvent(TtsPlaybackEvent.builder()
                .taskId(v1.getTaskId()).status(TtsPlaybackStatus.COMPLETE).build());
        assertTrue(latestEvent().getStatus().equals(TtsTaskStatus.PLAYED.name()));

        // 3) 相同请求再次合成 → 缓存命中，不重复合成（不下发播放）
        TtsRequest same = TtsRequest.builder()
                .text("欢迎致电呼叫中心").voice("mock-default").provider(TtsProviderType.MOCK)
                .sampleRate(8000).format(TtsAudioFormat.WAV).build();
        com.och.ai.tts.model.TtsResult cached = ttsService.synthesize(same);
        assertTrue(cached.isSuccess());
        assertTrue(cached.isCacheHit());
        assertEquals(v1.getFileId(), cached.getFileId());
    }

    @Test
    void forceRefreshProducesNewFileAndBypassesCache() {
        TtsRequest req = TtsRequest.builder().text("重试文本").provider(TtsProviderType.MOCK)
                .sampleRate(8000).format(TtsAudioFormat.WAV).build();
        com.och.ai.tts.model.TtsResult first = ttsService.synthesize(req);
        assertTrue(first.isSuccess());

        req.setForceRefresh(true);
        com.och.ai.tts.model.TtsResult refreshed = ttsService.synthesize(req);
        assertTrue(refreshed.isSuccess());
        assertFalse(refreshed.isCacheHit());
    }

    @Test
    void playbackFailureKeepsChannelAliveAndMarksFailed() {
        playbackPort.fail = true;
        TtsRequest req = TtsRequest.builder().text("播放将失败").provider(TtsProviderType.MOCK)
                .channelUuid("uuid-x").callId("call-x").sampleRate(8000).format(TtsAudioFormat.WAV).build();
        // 不应抛出（通话不受影响）
        TtsTaskView view = businessService.submit(req);
        assertEquals(TtsTaskStatus.FAILED.name(), view.getStatus());
        assertEquals("TTS_013", view.getErrorCode());
    }

    @Test
    void replayUsesExistingFile() {
        TtsRequest req = TtsRequest.builder().text("先合成").provider(TtsProviderType.MOCK)
                .sampleRate(8000).format(TtsAudioFormat.WAV).build();
        TtsTaskView v = businessService.submit(req);
        assertEquals(TtsTaskStatus.SUCCESS.name(), v.getStatus());

        TtsTaskView r = businessService.replay(v.getTaskId(), "call-2", "uuid-2");
        assertEquals(TtsTaskStatus.PLAYING.name(), r.getStatus(),
                () -> "status=" + r.getStatus() + " code=" + r.getErrorCode() + " msg=" + r.getErrorMsg()
                        + " fileId=" + v.getFileId());
        assertEquals("uuid-2", playbackPort.last.getChannelUuid());
    }

    private TtsStatusChangedEvent latestEvent() {
        return events.get(events.size() - 1);
    }

    /** 录制型播放端口：记录最近请求，可选置为失败以模拟 FS 播放异常。 */
    static class RecordingPlaybackPort implements TtsPlaybackPort {
        TtsPlaybackRequest last;
        boolean fail;

        @Override
        public PlaybackAck play(TtsPlaybackRequest request) {
            this.last = request;
            if (fail) {
                throw new RuntimeException("simulated FS playback error");
            }
            return PlaybackAck.builder().accepted(true).message("ok").build();
        }
    }
}
