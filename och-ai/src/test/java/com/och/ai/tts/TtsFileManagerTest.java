package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.file.TtsFileManager;
import com.och.common.constant.SysSettingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 文件落盘测试：真实 IO（临时目录），验证写/存在/读/删，产物可被 FreeSWITCH/浏览器读取。
 */
class TtsFileManagerTest {

    @TempDir
    Path tempDir;

    private TtsFileManager newManager() {
        TtsProperties props = new TtsProperties();
        props.setStorageDir(tempDir.toString());
        return new TtsFileManager(props, mock(SysSettingConfig.class));
    }

    @Test
    void writeReadDeleteRoundtrip() throws Exception {
        TtsFileManager mgr = newManager();
        byte[] audio = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00}; // fake wav header

        TtsFileManager.Written w = mgr.write("cachekey1", audio, "WAV");
        assertNotNull(w.fileName());
        assertTrue(w.filePath().contains("cachekey1"));
        assertTrue(mgr.exists(w.filePath()));
        assertArrayEquals(audio, mgr.read(w.filePath()));

        assertTrue(mgr.delete(w.filePath()));
        assertFalse(mgr.exists(w.filePath()));
        assertFalse(mgr.delete(w.filePath()));
    }

    @Test
    void resolveStorageDir_createsDirectory() {
        TtsFileManager mgr = newManager();
        assertEquals(tempDir.toString(), mgr.resolveStorageDir());
        assertTrue(java.nio.file.Files.exists(tempDir));
    }

    @Test
    void exists_handlesNullAndMissing() {
        TtsFileManager mgr = newManager();
        assertFalse(mgr.exists(null));
        assertFalse(mgr.exists(tempDir.resolve("nope.wav").toString()));
    }
}
