package com.och.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.och.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 号码转换规则表(number_transform_rule)
 * <p>
 * 用于在呼出/呼入路由阶段对被叫号码与主叫号码进行去前缀、加前缀、整体替换和主叫替换等变换。
 * 所有具体号码规则均由数据库配置驱动，不在 Java 代码中硬编码。
 *
 * @author och
 */
@Schema
@Data
@SuppressWarnings("serial")
@TableName("number_transform_rule")
public class NumberTransformRule extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "规则名称")
    @TableField("name")
    private String name;

    @Schema(description = "方向 1-呼入 2-呼出(与DirectionEnum一致)")
    @TableField("direction")
    private Integer direction;

    @Schema(description = "绑定网关ID(为空表示适用于所有网关)")
    @TableField("gateway_id")
    private Long gatewayId;

    @Schema(description = "匹配类型 1-前缀 2-正则 3-长度 4-全匹配")
    @TableField("match_type")
    private Integer matchType;

    @Schema(description = "匹配值(前缀字符串/正则表达式/号码长度/完整号码)")
    @TableField("match_value")
    private String matchValue;

    @Schema(description = "去前缀(被叫号码去除该前缀，为空不去除)")
    @TableField("strip_prefix")
    private String stripPrefix;

    @Schema(description = "加前缀(变换后号码前置该字符串，为空不加)")
    @TableField("add_prefix")
    private String addPrefix;

    @Schema(description = "整体替换为(非空时将变换结果直接替换为该值)")
    @TableField("replace_to")
    private String replaceTo;

    @Schema(description = "主叫替换为(非空时主叫号码替换为该值)")
    @TableField("caller_replace")
    private String callerReplace;

    @Schema(description = "号码最小长度约束(为空不限制)")
    @TableField("min_length")
    private Integer minLength;

    @Schema(description = "号码最大长度约束(为空不限制)")
    @TableField("max_length")
    private Integer maxLength;

    @Schema(description = "优先级(数值小的优先匹配)")
    @TableField("\"priority\"")
    private Integer priority;

    @Schema(description = "状态 0-禁用 1-启用")
    @TableField("status")
    private Integer status;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
