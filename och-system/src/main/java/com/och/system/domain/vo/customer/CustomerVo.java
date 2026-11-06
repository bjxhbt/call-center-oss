package com.och.system.domain.vo.customer;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 客户展示对象（任务 6.2）。
 *
 * @author och
 */
@Schema
@Data
public class CustomerVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "客户编号")
    private String customerNo;

    @Schema(description = "客户名称")
    private String name;

    @Schema(description = "主电话")
    private String phone;

    @Schema(description = "备用电话")
    private String backupPhone;

    @Schema(description = "性别 0-未知 1-男 2-女")
    private Integer gender;

    @Schema(description = "来源 0-手动 1-文件导入 2-API导入")
    private Integer source;

    @Schema(description = "状态 1-正常 0-停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "最近一次通话话单ID")
    private Long lastCallRecordId;

    @Schema(description = "最近一次通话时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastCallTime;

    @Schema(description = "历史通话次数")
    private Integer callCount;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
