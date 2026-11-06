package com.och.websocket.domain.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 挂断事件 {@code HANGUP} 的 data 结构（任务 9.7）。
 *
 * <p>一次业务呼叫只会下发一次有效挂断事件（见 9.8 幂等控制）。前端收到后清理通话态并落本地话单缓存。</p>
 *
 * @author och
 */
@Schema
@Data
public class WsHangupData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "挂机方向 1-主叫挂机 2-被叫挂机 3-系统挂机")
    private Integer hangupDir;

    @Schema(description = "挂断原因码，见 FsHangupCauseEnum")
    private Integer hangupCause;

    @Schema(description = "挂断原因描述")
    private String hangupCauseDesc;

    @Schema(description = "呼叫最终结果 1-成功 2-失败")
    private Integer callState;

    @Schema(description = "通话时长(秒)")
    private Integer duration;
}
