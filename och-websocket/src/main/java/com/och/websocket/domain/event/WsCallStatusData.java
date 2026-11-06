package com.och.websocket.domain.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通话状态变更事件 {@code CALL_STATUS} 的 data 结构（任务 9.7）。
 *
 * <p>状态取值见 {@code com.och.system.domain.enums.BusinessCallStatusEnum}（INITIATING/RINGING/ANSWERED/HANGUP/FAILED...）。
 * 增量推送通话生命周期，前端据此刷新坐席条与通话面板。</p>
 *
 * @author och
 */
@Schema
@Data
public class WsCallStatusData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务呼叫状态码，见 BusinessCallStatusEnum")
    private Integer status;

    @Schema(description = "业务呼叫状态描述")
    private String statusDesc;

    @Schema(description = "呼叫方向 1-呼出 2-呼入")
    private Integer direction;

    @Schema(description = "主叫号码")
    private String caller;

    @Schema(description = "被叫号码")
    private String callee;

    @Schema(description = "坐席ID")
    private Long agentId;

    @Schema(description = "坐席分机号")
    private String agentNumber;
}
