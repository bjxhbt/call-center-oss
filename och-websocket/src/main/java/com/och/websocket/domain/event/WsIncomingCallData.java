package com.och.websocket.domain.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 来电通知事件 {@code INCOMING_CALL} 的 data 结构（任务 9.7）。
 *
 * <p>用于来电弹屏：坐席端收到后展示客户资料与历史通话。客户资料字段允许为空（未命中客户时）。</p>
 *
 * @author och
 */
@Schema
@Data
public class WsIncomingCallData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主叫号码（外线号码）")
    private String caller;

    @Schema(description = "主叫显号")
    private String callerDisplay;

    @Schema(description = "被叫号码（坐席分机或 DID）")
    private String callee;

    @Schema(description = "呼叫方向 1-呼出 2-呼入")
    private Integer direction;

    @Schema(description = "技能组ID")
    private Long skillId;

    @Schema(description = "坐席ID")
    private Long agentId;

    @Schema(description = "坐席分机号")
    private String agentNumber;

    @Schema(description = "客户ID（命中客户时返回，否则为空）")
    private Long customerId;

    @Schema(description = "客户名称（命中客户时返回）")
    private String customerName;

    @Schema(description = "号码归属地")
    private String numberLocation;
}
