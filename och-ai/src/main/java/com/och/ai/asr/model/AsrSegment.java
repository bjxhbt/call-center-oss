package com.och.ai.asr.model;

import lombok.Builder;
import lombok.Data;

/**
 * 统一识别结果片段（方案 7.6 / 7.10 / 7.11）。
 *
 * <p>统一字段：segmentId、sequence、temporary/final、speaker、时间、文本。
 * 临时结果按 {@code segmentId + sequence} 覆盖更新；最终结果替换临时结果。</p>
 */
@Data
@Builder
public class AsrSegment {

    /** 段落稳定标识（同一句反复更新使用同一 segmentId）。 */
    private String segmentId;

    /** 段内结果序号（部分 ASR 用 result_index 表达增量覆盖）。 */
    private Integer sequence;

    /** 文本内容。 */
    private String text;

    /** 是否最终结果（true=最终，替换临时；false=临时增量）。 */
    private boolean finalResult;

    /** 说话人。 */
    private Speaker speaker;

    /** 相对会话开始的开始时间（毫秒），可空。 */
    private Long beginMs;

    /** 相对会话开始的结束时间（毫秒），可空。 */
    private Long endMs;

    /** 置信度 0~1，可空。 */
    private Double confidence;
}
