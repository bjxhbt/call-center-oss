package com.och.system.domain.query.kb;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "新增知识库分类入参")
@Data
public class KbCategoryAddQuery {

    @Schema(description = "主键ID", hidden = true)
    private Long id;

    @NotEmpty(message = "分类名称不能为空")
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "父分类ID")
    private Long parentId = 0L;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "可删除标志 0 可删除 1 不可删除")
    private Integer flag = 0;
}
