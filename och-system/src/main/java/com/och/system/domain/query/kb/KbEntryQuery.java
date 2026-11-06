package com.och.system.domain.query.kb;

import com.och.system.domain.query.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库词条入参")
@Data
public class KbEntryQuery extends BaseQuery {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status;

    @Schema(description = "需要删除的ID列表")
    private Long[] ids;
}
