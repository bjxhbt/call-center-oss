package com.och.system.domain.query.call;

import com.och.system.domain.query.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CallRecordQuery extends BaseQuery {

    private Long callId;
    private String agentNumber;
    private String callerNumber;
    private String calleeNumber;
    private Integer recordStatus;
    private String satisfaction;
    /** 呼叫方式 1-呼出 2-呼入 */
    private Integer direction;
    /** 目标网关ID(呼出) */
    private Long gatewayId;
    /** 来源网关ID(呼入) */
    private Long fromGatewayId;
    /** DID(呼入接入号) */
    private String did;
    /** 呼叫状态 1-成功 2-失败 */
    private Integer callState;
}
