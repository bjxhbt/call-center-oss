package com.och.system.domain.query.number;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 号码转换规则新增/修改参数
 *
 * @author och
 */
@Schema
@Data
public class NumberTransformRuleAddQuery {

    @Schema(description = "ID(修改时必填)", hidden = true)
    private Long id;

    @NotEmpty(message = "规则名称不能为空")
    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "方向不能为空")
    @Schema(description = "方向 1-呼入 2-呼出(与DirectionEnum一致)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer direction;

    @Schema(description = "绑定网关ID(为空表示适用于所有网关)")
    private Long gatewayId;

    @NotNull(message = "匹配类型不能为空")
    @Schema(description = "匹配类型 1-前缀 2-正则 3-长度 4-全匹配", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer matchType;

    @Schema(description = "匹配值(前缀字符串/正则表达式/号码长度/完整号码)")
    private String matchValue;

    @Schema(description = "去前缀")
    private String stripPrefix;

    @Schema(description = "加前缀")
    private String addPrefix;

    @Schema(description = "整体替换为")
    private String replaceTo;

    @Schema(description = "主叫替换为")
    private String callerReplace;

    @Schema(description = "号码最小长度约束")
    private Integer minLength;

    @Schema(description = "号码最大长度约束")
    private Integer maxLength;

    @Schema(description = "优先级(数值小的优先)")
    private Integer priority;

    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
