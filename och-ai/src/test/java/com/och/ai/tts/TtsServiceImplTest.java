package com.och.ai.tts;

import com.och.ai.tts.cache.TtsCacheService;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.provider.TtsProviderRegistry;
import com.och.ai.tts.provider.TtsServiceProvider;
import com.och.ai.tts.service.TtsFileService;
import com.och.ai.tts.service.impl.TtsServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TtsService 编排测试：参数校验、缓存命中、成功落盘、失败降级（方案 8.1/8.7/8.10）。
 *
 * <p>纯 Mockito，不启动 Spring、不连 DB、不需要云凭证。
 */
class TtsServiceImplTest {

    private TtsProperties properties;
    private TtsProviderRegistry registry;
    private TtsCacheService cacheService;
    private TtsFileService fileService;
    private TtsFileManager fileManager;
    private TtsServiceProvider provider;
    private TtsServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new TtsProperties();
        properties.setMaxTextLength(50);
        properties.setCacheEnabled(true);
        registry = mock(TtsProviderRegistry.class);
        cacheService = mock(TtsCacheService.class);
        fileService = mock(TtsFileService.class);
        fileManager = mock(TtsFileManager.class);
        provider = mock(TtsServiceProvider.class);
        when(registry.select(any())).thenReturn(provider);
        when(cacheService.buildKey(any())).thenReturn("KEY");
        when(cacheService.textDigest(anyString())).thenReturn("DIGEST");
        service = new TtsServiceImpl(properties, registry, cacheService, fileService, fileManager);
    }

    private TtsRequest req(String text) {
        return TtsRequest.builder().text(text).sampleRate(8000).format(TtsAudioFormat.WAV)
                .provider(TtsProviderType.MOCK).build();
    }

    @Test
    void emptyText_throws() {
        TtsException ex = assertThrows(TtsException.class, () -> service.synthesize(req("")));
        assertEquals(TtsErrorCode.EMPTY_TEXT, ex.getCode());
    }

    @Test
    void nullText_throws() {
        assertThrows(TtsException.class, () -> service.synthesize(TtsRequest.builder().build()));
    }

    @Test
    void tooLongText_throws() {
        String big = "a".repeat(properties.getMaxTextLength() + 1);
        TtsException ex = assertThrows(TtsException.class, () -> service.synthesize(req(big)));
        assertEquals(TtsErrorCode.TEXT_TOO_LONG, ex.getCode());
    }

    @Test
    void invalidSpeed_throws() {
        TtsRequest r = req("hi");
        r.setSpeed(5.0);
        TtsException ex = assertThrows(TtsException.class, () -> service.synthesize(r));
        assertEquals(TtsErrorCode.INVALID_PARAM, ex.getCode());
    }

    @Test
    void invalidVolume_throws() {
        TtsRequest r = req("hi");
        r.setVolume(2.0);
        assertThrows(TtsException.class, () -> service.synthesize(r));
    }

    @Test
    void cacheHit_returnsCachedWithoutCallingProvider() {
        TtsFile cached = new TtsFile();
        cached.setId(9L);
        cached.setFilePath("/data/tts/a.wav");
        cached.setStatus("SUCCESS");
        cached.setProvider(TtsProviderType.MOCK.name());
        cached.setFormat(TtsAudioFormat.WAV.name());
        cached.setSampleRate(8000);
        cached.setDelFlag(DeleteStatusEnum.DELETE_NO.getIndex());
        when(cacheService.getFileId("KEY")).thenReturn(null);
        when(fileService.findSuccessByCacheKey("KEY")).thenReturn(cached);
        when(fileManager.exists("/data/tts/a.wav")).thenReturn(true);

        TtsResult result = service.synthesize(req("你好"));

        assertTrue(result.isSuccess());
        assertTrue(result.isCacheHit());
        assertEquals(9L, result.getFileId());
        verify(provider, never()).synthesize(any());
        verify(fileService, never()).save(any());
    }

    @Test
    void cacheMiss_synthesizesAndPersists() throws Exception {
        when(cacheService.getFileId("KEY")).thenReturn(null);
        when(fileService.findSuccessByCacheKey("KEY")).thenReturn(null);
        byte[] audio = com.och.ai.tts.util.TtsAudioUtils.wrapWav(new byte[1600], 8000);
        when(provider.synthesize(any())).thenReturn(TtsResult.builder()
                .success(true).provider(TtsProviderType.MOCK).voice("mock-default")
                .format(TtsAudioFormat.WAV).sampleRate(8000).durationMs(100L).sizeBytes((long) audio.length)
                .audioBytes(audio).build());
        when(fileManager.write(eq("KEY"), any(), eq("WAV")))
                .thenReturn(new TtsFileManager.Written("a.wav", "/data/tts/a.wav", "tts/a.wav"));
        when(fileService.save(any())).thenReturn(7L);

        TtsResult result = service.synthesize(req("你好"));

        assertTrue(result.isSuccess());
        assertFalse(result.isCacheHit());
        assertEquals(7L, result.getFileId());
        assertEquals("/data/tts/a.wav", result.getFilePath());
        assertEquals("/tts/v1/file/KEY", result.getListenUrl());
        verify(cacheService).putFileId(eq("KEY"), eq(7L), anyLong());
        verify(cacheService).putLastSuccessByDigest(eq("DIGEST"), eq(7L), anyLong());
    }

    @Test
    void providerFailure_returnsErrorAndRecordsDiagnostic() throws Exception {
        when(cacheService.getFileId("KEY")).thenReturn(null);
        when(fileService.findSuccessByCacheKey("KEY")).thenReturn(null);
        when(provider.synthesize(any())).thenReturn(TtsResult.builder()
                .success(false).provider(TtsProviderType.ALIYUN)
                .errorCode(TtsErrorCode.AUTH_FAILED).errorMsg("凭证缺失").build());

        TtsResult result = service.synthesize(req("你好"));

        assertFalse(result.isSuccess());
        assertEquals(TtsErrorCode.AUTH_FAILED, result.getErrorCode());
        assertEquals("KEY", result.getCacheKey());
        verify(fileService).save(any()); // 诊断记录
        verify(fileManager, never()).write(anyString(), any(), anyString());
    }

    @Test
    void forceRefreshBypassesCache() throws Exception {
        TtsRequest r = req("你好");
        r.setForceRefresh(true);
        byte[] audio = com.och.ai.tts.util.TtsAudioUtils.wrapWav(new byte[1600], 8000);
        when(provider.synthesize(any())).thenReturn(TtsResult.builder()
                .success(true).provider(TtsProviderType.MOCK).format(TtsAudioFormat.WAV)
                .sampleRate(8000).sizeBytes((long) audio.length).audioBytes(audio).build());
        when(fileManager.write(anyString(), any(), anyString()))
                .thenReturn(new TtsFileManager.Written("a.wav", "/x/a.wav", "tts/a.wav"));
        when(fileService.save(any())).thenReturn(1L);

        TtsResult result = service.synthesize(r);

        // forceRefresh 不应查缓存
        verify(cacheService, never()).getFileId(anyString());
        verify(fileService, never()).findSuccessByCacheKey(anyString());
        assertTrue(result.isSuccess());
        assertFalse(result.isCacheHit());
    }
}
