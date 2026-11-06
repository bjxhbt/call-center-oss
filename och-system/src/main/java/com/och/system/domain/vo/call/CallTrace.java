package com.och.system.domain.vo.call;

import lombok.Data;

import java.io.Serializable;

/**
 * 通话链路可追溯信息(按 callId 暂存于 Redis，挂断落库时合并进 CallRecord)。
 * <p>
 * 由于 CallInfo/ChannelInfo 位于外部 och-common 依赖中无法扩展字段，
 * 此对象用于在路由阶段保存原始号码、转换后号码、匹配路由与网关等可追溯信息。
 *
 * @author och
 */
@Data
public class CallTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long callId;

    /** DID(呼入被叫号码/接入号) */
    private String did;

    /** 原始被叫号码(号码变换前) */
    private String originalCallee;

    /** 转换后被叫号码(送入网关) */
    private String convertedCallee;

    /** 原始主叫号码 */
    private String originalCaller;

    /** 转换后主叫号码 */
    private String convertedCaller;

    /** 命中路由ID */
    private Long routeId;

    /** 命中路由名称 */
    private String routeName;

    /** 路由类型 1-坐席 2-外呼 3-sip 4-技能组 5-放音 6-ivr */
    private Integer routeType;

    /** 目标网关ID */
    private Long gatewayId;

    /** 目标网关名称 */
    private String gatewayName;

    /** 来源网关ID(呼入) */
    private Long fromGatewayId;

    /** 来源网关名称(呼入) */
    private String fromGatewayName;

    /** 呼叫失败原因(明确文本，便于排查) */
    private String failureReason;

    /** SIP 状态码(文本) */
    private String sipStatus;
}
