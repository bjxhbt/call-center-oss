package com.och.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.och.common.base.BaseEntity;
import com.och.system.domain.query.holiday.CallHolidayAddQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 假期日历表(CallHoliday)表实体类
 * 记录国家法定假期和调休日, 用于覆盖日程的常规周期判断
 *
 * @author och
 **/
@EqualsAndHashCode(callSuper = false)
@Schema
@Data
@SuppressWarnings("serial")
@TableName("call_holiday")
public class CallHoliday extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 日期(yyyy-MM-dd), 单日
     */
    @Schema(description = "日期(yyyy-MM-dd)")
    @TableField("holiday_date")
    private String holidayDate;

    /**
     * 类型 1-假期(休息) 2-调休(上班)
     */
    @Schema(description = "类型 1-假期 2-调休上班")
    @TableField("type")
    private Integer type;

    /**
     * 名称(如:国庆节、中秋节调休)
     */
    @Schema(description = "名称")
    @TableField("name")
    private String name;

    public void setQuery2Entity(CallHolidayAddQuery query) {
        this.id = query.getId();
        this.holidayDate = query.getHolidayDate();
        this.type = query.getType();
        this.name = query.getName();
    }
}
