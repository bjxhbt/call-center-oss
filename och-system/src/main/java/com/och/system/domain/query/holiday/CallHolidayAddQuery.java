package com.och.system.domain.query.holiday;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 假期日历新增/编辑查询
 **/
@Schema
@Data
public class CallHolidayAddQuery {
    @Schema(description = "主键", hidden = true)
    private Long id;

    @Schema(description = "日期(yyyy-MM-dd),单日期新增时用")
    private String holidayDate;

    @Schema(description = "多日期批量新增(yyyy-MM-dd数组),优先于单日期")
    private java.util.List<String> holidayDates;

    @NotNull(message = "类型不能为空")
    @Schema(description = "类型 1-假期 2-调休上班", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer type;

    @Schema(description = "名称")
    private String name;
}
