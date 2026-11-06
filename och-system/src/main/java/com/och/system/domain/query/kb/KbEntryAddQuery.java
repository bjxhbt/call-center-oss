package com.och.system.domain.query.kb;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "新增知识库词条入参")
@Data
public class KbEntryAddQuery {

    @Schema(description = "主键ID", hidden = true)
    private Long id;

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @NotEmpty(message = "标题不能为空")
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "关键词,多个用逗号分隔")
    private String keywords;

    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status = 1;
}
