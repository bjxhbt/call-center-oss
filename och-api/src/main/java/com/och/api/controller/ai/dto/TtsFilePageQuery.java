package com.och.api.controller.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "TTS 文件分页查询")
public class TtsFilePageQuery {

    @Schema(description = "服务商")
    private String provider;

    @Schema(description = "文本关键词")
    private String keyword;

    @Schema(description = "页码，从1开始")
    private Integer pageIndex = 1;

    @Schema(description = "每页大小")
    private Integer pageSize = 20;
}
