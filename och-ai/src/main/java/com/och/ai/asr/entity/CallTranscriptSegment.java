package com.och.ai.asr.entity;

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
 * 通话转写明细段落表（方案 7.11 / 7.14）：每句最终结果一行。
 *
 * <p>仅持久化最终(final)段落；临时(temporary)结果不落库。通过 call_id 与摘要/话单关联。</p>
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = false)
@SuppressWarnings("serial")
@TableName("call_transcript_segment")
public class CallTranscriptSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 摘要主键（call_transcript.id）。 */
    @Schema(description = "转写摘要ID")
    @TableField("transcript_id")
    private Long transcriptId;

    /** 业务呼叫 ID（冗余，便于直接按 callId 查段落）。 */
    @Schema(description = "呼叫唯一ID")
    @TableField("call_id")
    private String callId;

    /** 段落稳定标识。 */
    @Schema(description = "段落标识")
    @TableField("segment_id")
    private String segmentId;

    /** 段内/全局序号。 */
    @Schema(description = "序号")
    @TableField("seq")
    private Integer seq;

    /** 说话人 customer/agent/unknown。 */
    @Schema(description = "说话人")
    @TableField("speaker")
    private String speaker;

    /** 最终文本。 */
    @Schema(description = "文本")
    @TableField("text")
    private String text;

    /** 是否最终（明细恒为 1，保留字段）。 */
    @Schema(description = "是否最终")
    @TableField("is_final")
    private Integer isFinal;

    /** 相对开始时间（毫秒）。 */
    @Schema(description = "相对开始时间(毫秒)")
    @TableField("begin_ms")
    private Long beginMs;

    /** 相对结束时间（毫秒）。 */
    @Schema(description = "相对结束时间(毫秒)")
    @TableField("end_ms")
    private Long endMs;

    /** 置信度。 */
    @Schema(description = "置信度")
    @TableField("confidence")
    private Double confidence;

    /** 来源 Provider。 */
    @Schema(description = "ASR服务商标识")
    @TableField("provider")
    private String provider;
}
