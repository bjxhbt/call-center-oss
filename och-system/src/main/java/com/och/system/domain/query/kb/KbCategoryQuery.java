package com.och.system.domain.query.kb;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "知识库分类入参")
@Data
public class KbCategoryQuery {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "需要删除的ID列表")
    private Long[] ids;
}
