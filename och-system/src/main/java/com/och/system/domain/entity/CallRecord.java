package com.och.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.och.common.base.BaseEntity;
import com.och.common.domain.CallInfo;
import com.och.common.enums.FsHangupCauseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;


/**
 * 呼叫记录表(CallRecord)表实体类
 *
 * @author danmo
 * @since 2024-11-21 11:23:02
 */
@Schema
@Data
@SuppressWarnings("serial")
@TableName("call_record")
public class CallRecord extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 373517321891756184L;

    /**
     * 主键ID
     */

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * 呼叫唯一ID
     */
    @Schema(description = "呼叫唯一ID")
    @TableField("call_id")
    private String callId;


    /**
     * 主叫号码
     */
    @Schema(description = "主叫号码")
    @TableField("caller_number")
    private String callerNumber;


    /**
     * 主叫显号
     */
    @Schema(description = "主叫显号")
    @TableField("caller_display_number")
    private String callerDisplayNumber;


    /**
     * 被叫号码
     */
    @Schema(description = "被叫号码")
    @TableField("callee_number")
    private String calleeNumber;


    /**
     * 被叫显号
     */
    @Schema(description = "被叫显号")
    @TableField("callee_display_number")
    private String calleeDisplayNumber;


    /**
     * 号码归属地
     */
    @Schema(description = "号码归属地")
    @TableField("number_location")
    private String numberLocation;


    /**
     * 坐席ID
     */
    @Schema(description = "坐席ID")
    @TableField("agent_id")
    private Long agentId;


    /**
     * 坐席号码
     */
    @Schema(description = "坐席号码")
    @TableField("agent_number")
    private String agentNumber;


    /**
     * 坐席名称
     */
    @Schema(description = "坐席名称")
    @TableField("agent_name")
    private String agentName;


    /**
     * 呼叫状态 1-成功 2-失败
     */
    @Schema(description = "呼叫状态 1-成功 2-失败")
    @TableField("call_state")
    private Integer callState;


    /**
     * 呼叫方式 1-呼入 2-呼出(与 DirectionEnum.getType() 一致:INBOUND=1/OUTBOUND=2)
     */
    @Schema(description = "呼叫方式 1-呼入 2-呼出")
    @TableField("direction")
    private Integer direction;


    /**
     * 呼叫开始时间
     */
    @Schema(description = "呼叫开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("call_start_time")
    private Date callStartTime;


    /**
     * 呼叫结束时间
     */
    @Schema(description = "呼叫结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("call_end_time")
    private Date callEndTime;


    /**
     * 应答标识 0-接通 1-坐席未接用户未接 2-坐席接通用户未接通 3-用户接通坐席未接通
     */
    @Schema(description = "应答标识 0-接通 1-坐席未接用户未接 2-坐席接通用户未接通 3-用户接通坐席未接通")
    @TableField("answer_flag")
    private Integer answerFlag;


    /**
     * 呼叫接通时间
     */
    @Schema(description = "呼叫接通时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("answer_time")
    private Date answerTime;


    /**
     * 振铃时间
     */
    @Schema(description = "振铃时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("ringing_time")
    private Date ringingTime;


    /**
     * 挂机方向 1-主叫挂机 2-被叫挂机 3-系统挂机
     */
    @Schema(description = "挂机方向 1-主叫挂机 2-被叫挂机 3-系统挂机")
    @TableField("hangup_dir")
    private Integer hangupDir;


    /**
     * 挂机原因
     */
    @Schema(description = "挂机原因 ")
    @TableField("hangup_cause_code")
    private Integer hangupCauseCode;


    /**
     * 录音文件地址
     */
    @Schema(description = "录音文件地址")
    @TableField("file_path")
    private String filePath;


    /**
     * 振铃文件地址
     */
    @Schema(description = "振铃文件地址")
    @TableField("ringing_path")
    private String ringingPath;

    @Schema(description = "MinIO录音文件URL")
    @TableField("record_url")
    private String recordUrl;

    @Schema(description = "录音时长(秒)")
    @TableField("record_duration")
    private Integer recordDuration;

    @Schema(description = "录音状态 0-未上传 1-上传中 2-已上传 3-上传失败")
    @TableField("record_status")
    private Integer recordStatus;

    @Schema(description = "满意度评价")
    @TableField("satisfaction")
    private String satisfaction;

    @Schema(description = "满意度评价时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("satisfaction_time")
    private Date satisfactionTime;

    // ====== 呼叫链路可追溯字段(关联网关、路由、号码变换与失败原因) ======

    @Schema(description = "目标网关ID(呼出)")
    @TableField("gateway_id")
    private Long gatewayId;

    @Schema(description = "目标网关名称(呼出)")
    @TableField("gateway_name")
    private String gatewayName;

    @Schema(description = "来源网关ID(呼入)")
    @TableField("from_gateway_id")
    private Long fromGatewayId;

    @Schema(description = "来源网关名称(呼入)")
    @TableField("from_gateway_name")
    private String fromGatewayName;

    @Schema(description = "DID(呼入接入号/被叫号码)")
    @TableField("did")
    private String did;

    @Schema(description = "原始被叫号码(号码变换前)")
    @TableField("original_callee")
    private String originalCallee;

    @Schema(description = "转换后被叫号码(送入网关)")
    @TableField("converted_callee")
    private String convertedCallee;

    @Schema(description = "命中路由ID")
    @TableField("route_id")
    private Long routeId;

    @Schema(description = "命中路由名称")
    @TableField("route_name")
    private String routeName;

    @Schema(description = "录音开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("record_start_time")
    private Date recordStartTime;

    @Schema(description = "录音结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("record_end_time")
    private Date recordEndTime;

    @Schema(description = "SIP 状态码(文本)")
    @TableField("sip_status")
    private String sipStatus;

    @Schema(description = "呼叫失败原因(明确文本，便于排查)")
    @TableField("failure_reason")
    private String failureReason;


    public void transfer(CallInfo callInfo) {
        this.setCallId(String.valueOf(callInfo.getCallId()));
        this.setAgentId(callInfo.getAgentId());
        this.setAgentNumber(callInfo.getAgentNumber());
        this.setAgentName(callInfo.getAgentName());
        this.setCallerNumber(callInfo.getCaller());
        this.setCallerDisplayNumber(callInfo.getCallerDisplay());
        this.setCalleeNumber(callInfo.getCallee());
        this.setCalleeDisplayNumber(callInfo.getCalleeDisplay());
        this.setNumberLocation(callInfo.getNumberLocation());
        if(Objects.nonNull(callInfo.getCallTime())){
            this.setCallStartTime(new Date(callInfo.getCallTime()));
        }
        this.setCallEndTime(new Date());

        this.setDirection(callInfo.getDirection());
        Date answerTime = null;
        if (Objects.nonNull(callInfo.getAnswerTime()) && callInfo.getAnswerTime() > 0) {
            answerTime = new Date(callInfo.getAnswerTime());
            this.setAnswerTime(answerTime);
        }
        // 0-接通, 1-坐席和用户均未接通。不能在未接通时固定写 0,否则首页接通率失真。
        this.setAnswerFlag(answerTime == null ? 1 : 0);

        if (Objects.nonNull(callInfo.getCalleeRingStartTime())) {
            this.setRingingTime(new Date(callInfo.getCalleeRingStartTime()));
        }

        this.setHangupDir(callInfo.getHangupDir());
        this.setHangupCauseCode(callInfo.getHangupCause());
        this.setFilePath(callInfo.getRecord());
        FsHangupCauseEnum causeEnum = FsHangupCauseEnum.getByCode(callInfo.getHangupCause());
        if(Objects.nonNull(causeEnum) && Objects.equals("正常挂机",causeEnum.getDesc())){
            this.setCallState(1);
        }else {
            this.setCallState(2);
        }
        //this.setRingingPath(callInfo.getRecord());

    }

    /**
     * 创建录音开始阶段的话单快照。
     *
     * <p>录音停止事件可能早于最后一条通道的挂机事件到达，不能把话单的首次落库
     * 依赖在挂机处理上。快照只写入通话当前信息和录音路径，结束时间及最终结果由
     * 挂机事件通过守卫式合并补齐。</p>
     */
    public void transferForRecording(CallInfo callInfo) {
        if (callInfo == null || callInfo.getCallId() == null) {
            return;
        }
        this.setCallId(String.valueOf(callInfo.getCallId()));
        this.setAgentId(callInfo.getAgentId());
        this.setAgentNumber(callInfo.getAgentNumber());
        this.setAgentName(callInfo.getAgentName());
        this.setCallerNumber(callInfo.getCaller());
        this.setCallerDisplayNumber(callInfo.getCallerDisplay());
        this.setCalleeNumber(callInfo.getCallee());
        this.setCalleeDisplayNumber(callInfo.getCalleeDisplay());
        this.setNumberLocation(callInfo.getNumberLocation());
        if (callInfo.getCallTime() != null) {
            this.setCallStartTime(new Date(callInfo.getCallTime()));
        }
        this.setDirection(callInfo.getDirection());
        if (callInfo.getAnswerTime() != null && callInfo.getAnswerTime() > 0) {
            this.setAnswerTime(new Date(callInfo.getAnswerTime()));
        }
        this.setAnswerFlag(this.getAnswerTime() == null ? 1 : 0);
        if (callInfo.getCalleeRingStartTime() != null) {
            this.setRingingTime(new Date(callInfo.getCalleeRingStartTime()));
        }
        this.setFilePath(callInfo.getRecord());
        if (callInfo.getRecordStartTime() != null) {
            this.setRecordStartTime(new Date(callInfo.getRecordStartTime()));
        }
        if (callInfo.getRecordEndTime() != null) {
            this.setRecordEndTime(new Date(callInfo.getRecordEndTime()));
        }
        if (callInfo.getRecordTime() != null) {
            this.setRecordDuration((int) Math.max(0L, callInfo.getRecordTime() / 1000L));
        }
        // 2=未完成通话的临时结果，挂机事件到达后会按结束时间改成最终结果。
        this.setCallState(2);
        this.setRecordStatus(0);
    }
}
