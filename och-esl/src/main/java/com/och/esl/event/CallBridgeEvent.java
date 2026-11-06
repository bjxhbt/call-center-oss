package com.och.esl.event;

import lombok.Builder;
import lombok.Data;

/**
 * 通话桥接事件（渠道层 → 业务/AI 层）。
 *
 * <p>由真实 {@code CHANNEL_BRIDGE} 事件处理器在双路桥接成功后发布（Spring {@code ApplicationEvent}）。
 * ASR 等需要“通话正式开始”信号的模块通过 {@code @EventListener} 消费，<b>不侵入</b>
 * SIP/E1 路由、桥接与挂断核心逻辑——核心逻辑只多一行 {@code publishEvent}。</p>
 *
 * <p>载荷只携带业务字段，不携带 {@code EslEvent}，便于跨模块解耦。</p>
 *
 * @author och
 */
@Data
@Builder
public class CallBridgeEvent {

    /** 业务呼叫 ID（CallInfo.callId）。 */
    private Long callId;

    /** 当前通道 FS UUID。 */
    private String uniqueId;

    /** 对端通道 FS UUID。 */
    private String otherUniqueId;

    /** 主叫通道 FS UUID，录音提示和录音均在该通道执行。 */
    private String recordingUniqueId;

    /** 主叫号码。 */
    private String caller;

    /** 主叫显号。 */
    private String callerDisplay;

    /** 被叫号码。 */
    private String callee;

    /** 被叫显号。 */
    private String calleeDisplay;

    /** 坐席 ID（推送 WebSocket 时用作 userId 定位 sessionId）。 */
    private Long agentId;

    /** 坐席号码。 */
    private String agentNumber;

    /** 坐席名称。 */
    private String agentName;

    /** 呼叫方向：1-呼出 2-呼入。 */
    private Integer direction;

    /** 桥接时间（毫秒）。 */
    private Long bridgeTime;

    /** 录音文件路径（用于后续离线补偿/关联回放，可为空）。 */
    private String record;
}
