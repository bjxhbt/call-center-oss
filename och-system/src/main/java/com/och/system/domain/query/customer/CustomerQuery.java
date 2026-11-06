package com.och.system.domain.query.customer;

import com.och.system.domain.query.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 客户查询参数（任务 6.2）。
 *
 * @author och
 */
@Schema
@Data
public class CustomerQuery extends BaseQuery {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "主键ID集合")
    private List<Long> ids;

    @Schema(description = "客户编号")
    private String customerNo;

    @Schema(description = "客户名称(模糊)")
    private String name;

    @Schema(description = "电话号码(精确，服务端自动规范化)")
    private String phone;

    @Schema(description = "性别 0-未知 1-男 2-女")
    private Integer gender;

    @Schema(description = "来源")
    private Integer source;

    @Schema(description = "状态 1-正常 0-停用")
    private Integer status;
}
