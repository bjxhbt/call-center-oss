package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

/**
 * 统一 TTS 合成结果。
 *
 * 无论同步还是异步生成、缓存命中还是新生成，业务侧拿到的都是本结构。
 * {@code audioBytes} 仅在内存流转时携带；持久化与对外暴露只使用文件地址。
 */
@Data
@Builder
public class TtsResult {

    /** 是否成功。 */
    private boolean success;

    /** 实际使用的服务商。 */
    private TtsProviderType provider;

    /** 实际使用的音色。 */
    private String voice;

    /** 实际输出格式。 */
    private TtsAudioFormat format;

    /** 实际采样率。 */
    private Integer sampleRate;

    /** 音频时长（毫秒）。 */
    private Long durationMs;

    /** 音频字节数。 */
    private Long sizeBytes;

    /** 落盘文件名（相对于存储根目录）。 */
    private String fileName;

    /** 落盘文件绝对/可读路径（供 FreeSWITCH 读取）。 */
    private String filePath;

    /** 可通过 HTTP 试听的相对路径（供前端/浏览器）。 */
    private String listenUrl;

    /** 缓存键（文本+参数指纹），命中时复用同一文件。 */
    private String cacheKey;

    /** 是否命中缓存。 */
    private boolean cacheHit;

    /** 文件记录主键（tts_file.id），用于引用计数与删除前检查。 */
    private Long fileId;

    /** 任务标识（tts_task.task_id）。 */
    private String taskId;

    /** 内存中的音频字节（仅在 Provider→Service 之间传递，不序列化）。 */
    private byte[] audioBytes;

    /** 错误码（失败时），取值见 {@code TtsErrorCode}。 */
    private String errorCode;

    /** 错误信息（失败时），不含密钥与完整敏感文本。 */
    private String errorMsg;

    /** Provider 侧原始状态文本（调试用，脱敏后）。 */
    private String providerStatus;

    /** 合成耗时（毫秒）。 */
    private Long elapsedMs;
}
