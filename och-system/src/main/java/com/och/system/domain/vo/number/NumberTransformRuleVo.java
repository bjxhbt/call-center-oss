package com.och.system.domain.vo.number;

import com.och.system.domain.vo.BaseVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 号码转换规则展示对象
 *
 * @author och
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
public class NumberTransformRuleVo extends BaseVo {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "规则名称")
    private String name;

    @Schema(description = "方向 1-呼入 2-呼出(与DirectionEnum一致)")
    private Integer direction;

    @Schema(description = "绑定网关ID")
    private Long gatewayId;

    @Schema(description = "绑定网关名称")
    private String gatewayName;

    @Schema(description = "匹配类型 1-前缀 2-正则 3-长度 4-全匹配")
    private Integer matchType;

    @Schema(description = "匹配值")
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

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
