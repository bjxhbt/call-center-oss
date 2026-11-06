package com.och.system.domain.query.number;

import com.och.system.domain.query.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 号码转换规则查询条件
 *
 * @author och
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
public class NumberTransformRuleQuery extends BaseQuery {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "ID集合")
    private List<Long> ids;

    @Schema(description = "规则名称(模糊)")
    private String name;

    @Schema(description = "方向 1-呼入 2-呼出(与DirectionEnum一致)")
    private Integer direction;

    @Schema(description = "绑定网关ID")
    private Long gatewayId;

    @Schema(description = "匹配类型 1-前缀 2-正则 3-长度 4-全匹配")
    private Integer matchType;

    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status;
}
