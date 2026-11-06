package com.och.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.och.common.base.BaseEntity;
import com.och.system.domain.query.schedule.CallScheduleAddQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


/**
 * 日程安排表(CallSchedule)表实体类
 *
 * @author danmo
 * @since 2024-08-09 17:14:50
 */
@EqualsAndHashCode(callSuper = false)
@Schema
@Data
@SuppressWarnings("serial")
@TableName("call_schedule")
public class CallSchedule extends BaseEntity implements Serializable {
    private static final long serialVersionUID = -83484748238063743L;

    /**
     * 主键
     */

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * 日程名称
     */
    @Schema(description = "日程名称")
    @TableField("name")
    private String name;


    /**
     * 优先级 0-10
     */
    @Schema(description = "优先级 0-10")
    @TableField("priority_level")
    private Integer level;


    /**
     * 0-指定时间 1-相对时间
     */
    @Schema(description = "0-指定时间 1-相对时间")
    @TableField("type")
    private Integer type;


    /**
     * 开始日期
     */
    @Schema(description = "开始日期")
    @TableField("start_day")
    private String startDay;


    /**
     * 结束日期
     */
    @Schema(description = "结束日期")
    @TableField("end_day")
    private String endDay;


    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    @TableField("start_time")
    private String startTime;


    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @TableField("end_time")
    private String endTime;


    /**
     * 周期时间
     */
    @Schema(description = "周期时间")
    @TableField("work_cycle")
    private String workCycle;





    /**
     * 时段类型 1-上班 2-下班 3-休息
     * 上班(1): 命中时走IVR/座席; 下班(2)/休息(3): 命中时播放对应voiceText提示语后挂断
     */
    @Schema(description = "时段类型 1-上班 2-下班 3-休息")
    @TableField("time_type")
    private Integer timeType;

    /**
     * 提示语(该时段命中的语音文本,留空则用默认)
     * 上班时段一般留空(走IVR); 下班/休息时段填写提示语
     * 注意: 优先级低于 voice_file_id, 配了语音文件则播放文件而非TTS
     */
    @Schema(description = "提示语文本(TTS合成,优先级低于语音文件)")
    @TableField("voice_text")
    private String voiceText;

    /**
     * 语音文件ID(关联 voice_file 表)
     * 下班/休息时段: 指定后播放该录音文件(优先于 voice_text 的 TTS)
     */
    @Schema(description = "语音文件ID(指定播放上传的录音,优先于TTS文本)")
    @TableField("voice_file_id")
    private Long voiceFileId;

    /**
     * IVR流程ID(关联 flow_info 表)
     * 上班时段: 指定后走该IVR流程(不填则用路由默认的 route_value)
     */
    @Schema(description = "IVR流程ID(上班时段指定走哪个流程,可选)")
    @TableField("flow_id")
    private Long flowId;
    public void setQuery2Entity(CallScheduleAddQuery query) {
        this.id = query.getId();
        this.name = query.getName();
        this.level = query.getLevel();
        this.type = query.getType();
        this.startDay = query.getStartDay();
        this.endDay = query.getEndDay();
        this.startTime = query.getStartTime();
        this.endTime = query.getEndTime();
        this.workCycle = query.getWorkCycle();
        this.voiceText = query.getVoiceText();
        this.timeType = query.getTimeType();
        this.voiceFileId = query.getVoiceFileId();
        this.flowId = query.getFlowId();
    }
}

