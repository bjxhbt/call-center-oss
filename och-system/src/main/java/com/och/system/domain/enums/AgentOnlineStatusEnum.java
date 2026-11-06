package com.och.system.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 坐席在线状态字典。
 *
 * <p>对应数据库表 {@code sip_agent.online_status} 字段。该字段在系统中以整型存储，
 * 此枚举提供统一的取值说明，供前后端及 WebSocket 坐席状态事件共用。</p>
 *
 * <p>与 och-common 中已有枚举的关系（避免重复造概念）：</p>
 * <ul>
 *     <li>{@code com.och.common.enums.AgentStateEnum}：坐席呼叫状态机的细粒度状态
 *         (READY/CALL_START/TALKING/CALL_END 等)，用于通话流程编排；</li>
 *     <li>{@code com.och.common.enums.SipAgentStatusEnum}：SIP 注册/ACD 角度状态；</li>
 *     <li>本枚举：业务展示用的坐席在线状态，面向前端坐席条与会话面板。</li>
 * </ul>
 * 三者通过 {@link #code} 与业务状态映射，不在数据库中混用。
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum AgentOnlineStatusEnum {

    IDLE(1, "空闲"),
    BUSY(2, "忙碌"),
    DO_NOT_DISTURB(3, "勿扰"),
    OFFLINE(4, "离线"),
    TALKING(5, "通话中"),
    RINGING(6, "振铃中"),
    AFTER_CALL(7, "话后");

    private final Integer code;
    private final String desc;

    public static AgentOnlineStatusEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }
}
