package com.och.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.och.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 基础客户信息表(Customer)（任务 6.2）。
 *
 * <p>面向来电弹屏与基础客户维护的主数据：保存客户姓名、规范化电话、来源、状态，
 * 以及与历史通话关联所需的最近通话字段。与 och-call-task 中的 CustomerSeas（公海/活动客户）区别：
 * Customer 是稳定的主档，CustomerSeas 面向外呼活动并引用主档信息。</p>
 *
 * @author och
 */
@Schema
@Data
@SuppressWarnings("serial")
@TableName("customer")
public class Customer extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "客户编号(业务编号，可空)")
    @TableField("customer_no")
    private String customerNo;

    @Schema(description = "客户名称")
    @TableField("name")
    private String name;

    /** 主电话(已规范化，建唯一索引用于重复检查与来电弹屏查询) */
    @Schema(description = "主电话(规范化后)")
    @TableField("phone")
    private String phone;

    @Schema(description = "备用电话(规范化后)")
    @TableField("backup_phone")
    private String backupPhone;

    /** 性别 0-未知 1-男 2-女（与 CustomerSeas.sex 保持一致） */
    @Schema(description = "性别 0-未知 1-男 2-女")
    @TableField("gender")
    private Integer gender;

    /** 来源 0-手动 1-文件导入 2-API导入（见 CustomerSourceEnum） */
    @Schema(description = "来源 0-手动 1-文件导入 2-API导入")
    @TableField("source")
    private Integer source;

    /** 状态 1-正常 0-停用 */
    @Schema(description = "状态 1-正常 0-停用")
    @TableField("status")
    private Integer status;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;

    /** 最近一次通话话单ID，用于历史通话关联（任务 6.2 保留字段） */
    @Schema(description = "最近一次通话话单ID")
    @TableField("last_call_record_id")
    private Long lastCallRecordId;

    @Schema(description = "最近一次通话时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("last_call_time")
    private Date lastCallTime;

    @Schema(description = "历史通话次数")
    @TableField("call_count")
    private Integer callCount;
}
