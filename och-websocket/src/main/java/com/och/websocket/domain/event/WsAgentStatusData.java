package com.och.websocket.domain.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 坐席状态变更事件 {@code AGENT_STATUS} / {@code AGENT_REGISTER} 的 data 结构（任务 9.7）。
 *
 * <p>状态取值见 {@code com.och.system.domain.enums.AgentOnlineStatusEnum}（1-空闲 2-忙碌 3-勿扰 4-离线 5-通话中 6-振铃中 7-话后）。</p>
 *
 * @author och
 */
@Schema
@Data
public class WsAgentStatusData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "坐席ID")
    private Long agentId;

    @Schema(description = "坐席分机号")
    private String agentNumber;

    @Schema(description = "在线状态码，见 AgentOnlineStatusEnum")
    private Integer onlineStatus;

    @Schema(description = "状态变更时间戳")
    private Long statusTime;
}
