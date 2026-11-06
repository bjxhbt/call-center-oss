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
 * TTS 任务表：记录一次业务流转（参数→合成→播放→完成）的状态机。
 *
 * <p>用于方案 9.6：接收前端参数 → 合成/缓存 → 下发通信链路播放 → 回写事件 → 前端状态。
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("serial")
@TableName("tts_task")
public class TtsTask extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务标识（UUID），对外暴露。 */
    @TableField("task_id")
    private String taskId;

    /** 缓存键/文件标识。 */
    @TableField("cache_key")
    private String cacheKey;

    /** 合成文本。 */
    @TableField("speech_text")
    private String speechText;

    /** 服务商。 */
    @TableField("provider")
    private String provider;

    /** 音色。 */
    @TableField("voice")
    private String voice;

    /** 参数 JSON。 */
    @TableField("params_json")
    private String paramsJson;

    /** 关联 tts_file.id。 */
    @TableField("file_id")
    private Long fileId;

    /** PENDING/SYNTHESIZING/SUCCESS/FAILED/PLAYING/PLAYED。 */
    @TableField("status")
    private String status;

    /** 关联通话。 */
    @TableField("call_id")
    private String callId;

    /** 关联通道 UUID。 */
    @TableField("channel_uuid")
    private String channelUuid;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_msg")
    private String errorMsg;
}
