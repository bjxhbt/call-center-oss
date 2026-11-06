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
import java.util.Date;

/**
 * 通话转写摘要表（方案 7.11 / 7.14）：每通通话一行，关联话单(call_record)与录音。
 *
 * <p>call_id 与 {@code call_record.call_id} 一致，用于关联话单与录音回放。
 * 段落明细见 {@link CallTranscriptSegment}。临时结果不落库，仅最终结果持久化。</p>
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("serial")
@TableName("call_transcript")
public class CallTranscript extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务呼叫 ID（与 call_record.call_id 一致）。 */
    @Schema(description = "呼叫唯一ID")
    @TableField("call_id")
    private String callId;

    /** FS 通道 UUID。 */
    @Schema(description = "通道UUID")
    @TableField("unique_id")
    private String uniqueId;

    /** 主叫号码。 */
    @Schema(description = "主叫号码")
    @TableField("caller_number")
    private String callerNumber;

    /** 被叫号码。 */
    @Schema(description = "被叫号码")
    @TableField("callee_number")
    private String calleeNumber;

    /** 坐席ID。 */
    @Schema(description = "坐席ID")
    @TableField("agent_id")
    private Long agentId;

    /** 坐席号码。 */
    @Schema(description = "坐席号码")
    @TableField("agent_number")
    private String agentNumber;

    /** 坐席名称。 */
    @Schema(description = "坐席名称")
    @TableField("agent_name")
    private String agentName;

    /** 呼叫方向 1-呼出 2-呼入。 */
    @Schema(description = "呼叫方向")
    @TableField("direction")
    private Integer direction;

    /** 产生本摘要的 ASR Provider（MOCK/TENCENT/ALIYUN/FUNASR）。 */
    @Schema(description = "ASR服务商标识")
    @TableField("provider")
    private String provider;

    /** 目标采样率。 */
    @Schema(description = "采样率")
    @TableField("sample_rate")
    private Integer sampleRate;

    /** 全量最终文本（按句顺序拼接）。 */
    @Schema(description = "全量最终文本")
    @TableField("full_text")
    private String fullText;

    /** 最终段落数。 */
    @Schema(description = "段落数")
    @TableField("segment_count")
    private Integer segmentCount;

    /** 总字符数。 */
    @Schema(description = "字符数")
    @TableField("char_count")
    private Integer charCount;

    /** 转写开始时间（首帧/桥接）。 */
    @Schema(description = "转写开始时间")
    @TableField("start_time")
    private Date startTime;

    /** 转写结束时间（挂断）。 */
    @Schema(description = "转写结束时间")
    @TableField("end_time")
    private Date endTime;

    /** 时长（毫秒）。 */
    @Schema(description = "时长(毫秒)")
    @TableField("duration_ms")
    private Long durationMs;

    /** 状态 RUNNING/SUCCESS/PARTIAL/FAILED/OFFLINE。 */
    @Schema(description = "转写状态")
    @TableField("status")
    private String status;

    /** 错误码（AsrErrorCode）。 */
    @Schema(description = "错误码")
    @TableField("error_code")
    private String errorCode;

    /** 错误描述。 */
    @Schema(description = "错误描述")
    @TableField("error_msg")
    private String errorMsg;

    /** 录音文件地址（关联录音，用于回放/离线补偿）。 */
    @Schema(description = "录音文件地址")
    @TableField("record_url")
    private String recordUrl;

    /** 首文本延迟（毫秒，效果评估用）。 */
    @Schema(description = "首文本延迟(毫秒)")
    @TableField("first_text_latency_ms")
    private Long firstTextLatencyMs;

    /** 最终文本延迟（毫秒，效果评估用）。 */
    @Schema(description = "最终文本延迟(毫秒)")
    @TableField("final_latency_ms")
    private Long finalLatencyMs;

    /** 重连次数（效果评估/排障用）。 */
    @Schema(description = "重连次数")
    @TableField("reconnect_count")
    private Integer reconnectCount;
}
