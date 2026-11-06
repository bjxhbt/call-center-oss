package com.och.ai.tts.file;

import com.och.ai.tts.config.TtsProperties;
import com.och.common.constant.SysSettingConfig;
import com.och.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * TTS 音频文件落盘管理。
 *
 * <p>存储根目录：优先 {@code och.tts.storage-dir}，否则 {@code SysSettingConfig.baseProfile}/tts
 * （与系统既有上传目录一致，便于 FreeSWITCH 与文件服务读取）。
 * 按日期分目录，文件名用 UUID，扩展名随格式（wav/pcm）。
 *
 * <p>不把生成文件提交进 Git；产物由本类在运行时写入磁盘。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsFileManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TtsProperties properties;
    private final SysSettingConfig sysSettingConfig;

    /** 解析存储根目录并确保存在。 */
    public String resolveStorageDir() {
        String dir = properties.getStorageDir();
        if (StringUtils.isBlank(dir)) {
            String base = sysSettingConfig.getBaseProfile();
            dir = (StringUtils.isBlank(base) ? System.getProperty("java.io.tmpdir") : base) + "/tts";
        }
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            log.warn("创建 TTS 存储目录失败 {}: {}", dir, e.getMessage());
        }
        return dir;
    }

    /** 写入音频并返回 (fileName, filePath, listenUrl 相对路径)。 */
    public Written write(String cacheKey, byte[] audio, String format) throws IOException {
        String storageDir = resolveStorageDir();
        String datePart = LocalDate.now().format(DATE_FMT);
        String ext = "WAV".equalsIgnoreCase(format) || "wav".equalsIgnoreCase(format) ? "wav" : "pcm";
        String fileName = cacheKey + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "." + ext;
        Path subDir = Paths.get(storageDir, datePart);
        Files.createDirectories(subDir);
        Path target = subDir.resolve(fileName);
        Files.write(target, audio);
        String relative = "tts/" + datePart + "/" + fileName;
        return new Written(fileName, target.toAbsolutePath().toString(), relative);
    }

    /** 删除磁盘文件（忽略不存在）。 */
    public boolean delete(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        try {
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("删除 TTS 文件失败 {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /** 文件是否存在。 */
    public boolean exists(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return false;
        }
        return new File(filePath).exists();
    }

    /** 读取字节（试听/FS 播放）。 */
    public byte[] read(String filePath) throws IOException {
        return Files.readAllBytes(Paths.get(filePath));
    }

    public record Written(String fileName, String filePath, String relativePath) {
    }
}
