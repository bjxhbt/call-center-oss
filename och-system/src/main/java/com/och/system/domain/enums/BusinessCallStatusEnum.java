package com.och.system.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 业务呼叫状态字典（一次业务呼叫的全生命周期状态）。
 *
 * <p>与已有概念的区别：</p>
 * <ul>
 *     <li>{@code CallRecord.call_state}（1-成功 / 2-失败）：呼叫最终结果，在挂断后由
 *         {@link #isTerminal()} 与挂断原因共同推导得出，不与本枚举混用；</li>
 *     <li>{@code com.och.common.enums.ProcessEnum}：ESL/IVR 流程编排步骤，面向通信链路内部；</li>
 *     <li>本枚举：业务层对外暴露的呼叫生命周期状态，用于 WebSocket 通话状态事件与话单。</li>
 * </ul>
 *
 * <p>状态流转（仅允许向后兼容推进，禁止逆向覆盖，见 9.8 数据一致性控制）：</p>
 * <pre>
 * INITIATING -&gt; RINGING -&gt; ANSWERED -&gt; HANGUP
 *                 |           |
 *                 +-&gt; FAILED / NO_ANSWER / BUSY / REJECTED (终态)
 * </pre>
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum BusinessCallStatusEnum {

    /** 发起呼叫（坐席拨号 / 外线呼入进入路由） */
    INITIATING(10, "发起呼叫"),
    /** 振铃中 */
    RINGING(20, "振铃中"),
    /** 已接通 / 通话中 */
    ANSWERED(30, "已接通"),
    /** 已正常挂断（终态） */
    HANGUP(40, "已挂断"),
    /** 呼叫失败（终态） */
    FAILED(50, "呼叫失败"),
    /** 无人接听（终态） */
    NO_ANSWER(51, "无人接听"),
    /** 占线 / 用户忙（终态） */
    BUSY(52, "占线"),
    /** 被拒接（终态） */
    REJECTED(53, "拒接");

    private final Integer code;
    private final String desc;

    public static BusinessCallStatusEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否终态。终态一旦写入不可被非终态覆盖。
     */
    public boolean isTerminal() {
        return this == HANGUP || this == FAILED || this == NO_ANSWER
                || this == BUSY || this == REJECTED;
    }

    /**
     * 状态流转校验（任务 9.8）。
     *
     * <p>只允许向后兼容的状态推进，禁止逆向覆盖：</p>
     * <ul>
     *   <li>相同状态：允许（幂等重放）；</li>
     *   <li>当前为终态：只允许相同终态（即重复挂断事件），拒绝回退到非终态；</li>
     *   <li>目标状态 code 大于当前：允许推进；</li>
     *   <li>其它：拒绝。</li>
     * </ul>
     *
     * @param target 目标状态，null 时返回 false
     */
    public boolean canTransitTo(BusinessCallStatusEnum target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        if (this.isTerminal()) {
            return false;
        }
        return target.code > this.code;
    }
}
