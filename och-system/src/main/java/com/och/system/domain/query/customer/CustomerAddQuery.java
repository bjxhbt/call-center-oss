package com.och.system.domain.query.customer;

import com.och.system.constant.ValidationConstants;
import com.och.system.domain.query.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户新增/修改参数（任务 6.2）。
 *
 * @author och
 */
@Schema
@Data
public class CustomerAddQuery extends BaseQuery {

    @Schema(description = "主键ID（修改时必填）", hidden = true)
    private Long id;

    @Schema(description = "客户编号")
    @Size(max = 64, message = "客户编号长度不能超过64")
    private String customerNo;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 64, message = "客户名称长度不能超过64")
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 主电话，提交时允许带分隔符，服务端统一规范化后入库与去重 */
    @NotBlank(message = "客户电话不能为空")
    @Pattern(regexp = ValidationConstants.PHONE_PATTERN, message = ValidationConstants.PHONE_MSG)
    @Schema(description = "主电话(支持手机号或带区号座机)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Pattern(regexp = ValidationConstants.PHONE_PATTERN + "|^$", message = ValidationConstants.PHONE_MSG)
    @Schema(description = "备用电话(可空)")
    private String backupPhone;

    @Schema(description = "性别 0-未知 1-男 2-女")
    private Integer gender;

    @Schema(description = "来源 0-手动 1-文件导入 2-API导入")
    private Integer source;

    @Schema(description = "状态 1-正常 0-停用")
    private Integer status;

    @Size(max = 500, message = ValidationConstants.REMARK_MSG)
    @Schema(description = "备注")
    private String remark;
}
