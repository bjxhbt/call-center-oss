package com.och.websocket.domain.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * ASR 识别结果事件 {@code ASR_RESULT}(增量) / {@code ASR_FINAL}(最终) 的 data 结构（任务 9.7）。
 *
 * <p>增量结果用于实时转写面板滚动展示；最终结果用于落库转写记录（7.11）。
 * 同一句的增量结果通过 {@code sentenceId} 关联，最终结果与最后一次增量 sentenceId 一致。</p>
 *
 * @author och
 */
@Schema
@Data
public class WsAsrResultData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "句子ID，同一句增量与最终结果一致")
    private String sentenceId;

    @Schema(description = "识别文本")
    private String text;

    @Schema(description = "说话角色 1-坐席 2-客户")
    private Integer role;

    @Schema(description = "是否最终结果")
    private Boolean isFinal;

    @Schema(description = "置信度 0-1")
    private Float confidence;
}
