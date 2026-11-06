package com.och.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.och.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema
@Data
@SuppressWarnings("serial")
@TableName("kb_entry")
public class KbEntry extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "分类ID")
    @TableField("category_id")
    private Long categoryId;

    @Schema(description = "标题")
    @TableField("title")
    private String title;

    @Schema(description = "内容")
    @TableField("content")
    private String content;

    @Schema(description = "关键词,多个用逗号分隔")
    @TableField("keywords")
    private String keywords;

    @Schema(description = "状态 0-禁用 1-启用")
    @TableField("status")
    private Integer status;
}
