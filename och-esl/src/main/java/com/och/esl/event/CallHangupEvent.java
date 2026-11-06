package com.och.esl.event;

import lombok.Builder;
import lombok.Data;

/**
 * 通话挂断事件（渠道层 → 业务/AI 层）。
 *
 * <p>由 {@code FsChannelHangUpCompleteEslEventHandler} 在最后一腿挂断（话单落库点）
 * 发布。ASR 模块据此结束会话、等待最终结果并持久化。<b>不侵入</b>挂断核心逻辑。</p>
 *
 * @author och
 */
@Data
@Builder
public class CallHangupEvent {

    /** 业务呼叫 ID。 */
    private Long callId;

    /** 最后挂断通道 FS UUID。 */
    private String uniqueId;

    /** 主叫号码。 */
    private String caller;

    /** 被叫号码。 */
    private String callee;

    /** 坐席 ID。 */
    private Long agentId;

    /** 坐席号码。 */
    private String agentNumber;

    /** 呼叫方向：1-呼出 2-呼入。 */
    private Integer direction;

    /** 挂断原因（FS Hangup-Cause 文本）。 */
    private String hangupCause;

    /** 结束时间（毫秒）。 */
    private Long endTime;

    /** 录音文件路径（用于离线转写补偿）。 */
    private String record;

    /** 录音时长（秒）。 */
    private Integer recordDurationSec;
}
