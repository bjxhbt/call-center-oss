package com.och.system.domain.query.agent;

import com.och.system.constant.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @author danmo
 * @date 2023年09月23日 11:03
 */
@Schema
@Data
public class SipAgentAddQuery {

    /**
     * 主键ID
     */
    @Schema(description = "主键ID", hidden = true)
    private Long id;

    /**
     * 坐席名称
     */
    @NotBlank(message = "坐席名称不能为空")
    @Pattern(regexp = ValidationConstants.NAME_PATTERN, message = ValidationConstants.NAME_MSG)
    @Schema(description = "坐席名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /**
     * 主键ID
     */
    @NotNull(message = "员工ID不能为空")
    @Schema(description = "员工ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;


    /**
     * 坐席账号（SIP号码，同一SIP账号只能绑定一个有效坐席）
     */
    @NotBlank(message = "坐席账号不能为空")
    @Pattern(regexp = ValidationConstants.NUMBER_PATTERN, message = ValidationConstants.NUMBER_MSG)
    @Schema(description = "坐席账号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentNumber;


    /**
     * 状态 0-未开通 1-开通
     */
    @Schema(description = "状态 0-未开通 1-开通")
    private Integer status;

}
