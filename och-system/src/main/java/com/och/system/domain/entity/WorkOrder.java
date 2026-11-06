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
 * 工单表(WorkOrder)实体类
 */
@Schema
@Data
@SuppressWarnings("serial")
@TableName("work_order")
public class WorkOrder extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "工单标题")
    @TableField("title")
    private String title;

    @Schema(description = "工单描述")
    @TableField("description")
    private String description;

    @Schema(description = "客户ID")
    @TableField("customer_id")
    private Long customerId;

    @Schema(description = "客户名称")
    @TableField("customer_name")
    private String customerName;

    @Schema(description = "手机1")
    @TableField("mobile_1")
    private String mobile1;

    @Schema(description = "手机2")
    @TableField("mobile_2")
    private String mobile2;

    @Schema(description = "固定电话")
    @TableField("fixed_phone")
    private String fixedPhone;

    @Schema(description = "客户住址")
    @TableField("customer_address")
    private String customerAddress;

    @Schema(description = "客户类型")
    @TableField("customer_type")
    private String customerType;

    @Schema(description = "单位名称")
    @TableField("company_name")
    private String companyName;

    @Schema(description = "工单类型")
    @TableField("work_order_type")
    private String workOrderType;

    @Schema(description = "号码归属地")
    @TableField("number_location")
    private String numberLocation;

    @Schema(description = "业务类型")
    @TableField("business_type")
    private String businessType;

    @Schema(description = "问题类型")
    @TableField("issue_type")
    private String issueType;

    @Schema(description = "受理内容")
    @TableField("acceptance_content")
    private String acceptanceContent;

    @Schema(description = "附件元数据JSON")
    @TableField("attachments_json")
    private String attachmentsJson;

    @Schema(description = "关联呼叫记录ID")
    @TableField("call_record_id")
    private Long callRecordId;

    @Schema(description = "关联呼叫唯一ID")
    @TableField("call_id")
    private String callId;

    @Schema(description = "主叫号码")
    @TableField("caller_number")
    private String callerNumber;

    @Schema(description = "被叫号码")
    @TableField("callee_number")
    private String calleeNumber;

    @Schema(description = "坐席ID")
    @TableField("agent_id")
    private Long agentId;

    @Schema(description = "坐席名称")
    @TableField("agent_name")
    private String agentName;

    @Schema(description = "满意度评价")
    @TableField("satisfaction")
    private String satisfaction;

    @Schema(description = "满意度评价时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("satisfaction_time")
    private Date satisfactionTime;

    @Schema(description = "优先级 1-低 2-中 3-高 4-紧急")
    @TableField("priority")
    private Integer priority;

    @Schema(description = "状态 0-待处理 1-处理中 2-已完成 3-已关闭")
    @TableField("status")
    private Integer status;

    @Schema(description = "处理人ID")
    @TableField("handler_id")
    private Long handlerId;

    @Schema(description = "处理人名称")
    @TableField("handler_name")
    private String handlerName;

    @Schema(description = "处理备注")
    @TableField("handle_remark")
    private String handleRemark;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("finish_time")
    private Date finishTime;
}
