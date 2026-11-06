package com.och.system.domain.query.holiday;

import com.och.system.domain.query.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 假期日历列表查询
 **/
@Schema
@Data
public class CallHolidayQuery extends BaseQuery {
    @Schema(description = "主键")
    private Long id;

    private List<Long> ids;

    @Schema(description = "日期(yyyy-MM-dd)")
    private String holidayDate;

    @Schema(description = "类型 1-假期 2-调休上班")
    private Integer type;

    @Schema(description = "名称")
    private String name;
}
