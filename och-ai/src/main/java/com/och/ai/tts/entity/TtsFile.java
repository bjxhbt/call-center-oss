package com.och.ai.tts.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.och.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * TTS 文件表：记录每次合成产物的文本摘要、服务商、音色、参数、文件地址、时长、状态与引用计数。
 *
 * <p>缓存键 {@link #cacheKey} 由文本+参数指纹生成（MD5），相同请求命中同一行，避免重复合成。
 * 删除前检查 {@link #refCount}，&gt;0 禁止删除（方案 8.7）。
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("serial")
@TableName("tts_file")
public class TtsFile extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 缓存键（文本+参数 MD5），唯一。 */
    @TableField("cache_key")
    private String cacheKey;

    /** 文本指纹（仅文本 MD5），用于按文本聚合查询。 */
    @TableField("text_digest")
    private String textDigest;

    /** 原始合成文本。 */
    @TableField("speech_text")
    private String speechText;

    /** 服务商 MOCK/ALIYUN/TENCENT/COSYVOICE。 */
    @TableField("provider")
    private String provider;

    /** 音色。 */
    @TableField("voice")
    private String voice;

    /** 参数 JSON（speed/pitch/volume/sampleRate/format）。 */
    @TableField("params_json")
    private String paramsJson;

    /** 落盘文件名。 */
    @TableField("file_name")
    private String fileName;

    /** 可被 FreeSWITCH 读取的文件路径。 */
    @TableField("file_path")
    private String filePath;

    /** 试听 HTTP 相对路径。 */
    @TableField("listen_url")
    private String listenUrl;

    /** 文件大小（字节）。 */
    @TableField("file_size")
    private Long fileSize;

    /** 时长（毫秒）。 */
    @TableField("duration_ms")
    private Long durationMs;

    /** 采样率。 */
    @TableField("sample_rate")
    private Integer sampleRate;

    /** 音频格式 PCM/WAV。 */
    @TableField("format")
    private String format;

    /** SUCCESS/FAILED。 */
    @TableField("status")
    private String status;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_msg")
    private String errorMsg;

    /** 业务引用计数，&gt;0 禁止物理删除。 */
    @TableField("ref_count")
    private Integer refCount;
}
