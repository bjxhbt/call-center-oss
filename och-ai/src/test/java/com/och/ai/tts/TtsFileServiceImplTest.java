package com.och.ai.tts;

import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.mapper.TtsFileMapper;
import com.och.ai.tts.service.impl.TtsFileServiceImpl;
import com.och.common.exception.CommonException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 文件删除引用检查测试（方案 8.7）：refCount>0 拒绝删除；=0 软删记录并删磁盘文件。
 */
class TtsFileServiceImplTest {

    @Test
    void deleteBlockedWhenReferenced() {
        TtsFileMapper mapper = mock(TtsFileMapper.class);
        TtsFileManager fileManager = mock(TtsFileManager.class);
        TtsFileServiceImpl svc = new TtsFileServiceImpl(mapper, fileManager);

        com.och.ai.tts.entity.TtsFile file = new com.och.ai.tts.entity.TtsFile();
        file.setId(1L);
        file.setFilePath("/x/a.wav");
        file.setRefCount(2);
        when(mapper.selectById(1L)).thenReturn(file);

        CommonException ex = assertThrows(CommonException.class, () -> svc.delete(1L));
        assertTrue(ex.getMessage().contains("引用"));
        verify(fileManager, never()).delete(anyString());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void deleteSucceedsWhenNotReferenced() {
        TtsFileMapper mapper = mock(TtsFileMapper.class);
        TtsFileManager fileManager = mock(TtsFileManager.class);
        TtsFileServiceImpl svc = new TtsFileServiceImpl(mapper, fileManager);

        com.och.ai.tts.entity.TtsFile file = new com.och.ai.tts.entity.TtsFile();
        file.setId(1L);
        file.setFilePath("/x/a.wav");
        file.setRefCount(0);
        when(mapper.selectById(1L)).thenReturn(file);
        when(fileManager.delete("/x/a.wav")).thenReturn(true);
        when(mapper.updateById(any())).thenReturn(1);

        boolean removed = svc.delete(1L);

        assertTrue(removed);
        verify(fileManager).delete("/x/a.wav");
        verify(mapper).updateById(any());
    }

    @Test
    void adjustRefCountNeverNegative() {
        TtsFileMapper mapper = mock(TtsFileMapper.class);
        TtsFileManager fileManager = mock(TtsFileManager.class);
        TtsFileServiceImpl svc = new TtsFileServiceImpl(mapper, fileManager);

        com.och.ai.tts.entity.TtsFile file = new com.och.ai.tts.entity.TtsFile();
        file.setId(1L);
        file.setRefCount(0);
        when(mapper.selectById(1L)).thenReturn(file);

        int next = svc.adjustRefCount(1L, -5);
        assertEquals(0, next);
    }
}
