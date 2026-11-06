package com.och.system.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 应答标识字典，对应 {@code CallRecord.answer_flag} 字段。
 *
 * <p>区分一次呼叫中坐席侧与用户侧各自的接通情况，用于话单统计与计费口径。</p>
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum AnswerFlagEnum {

    /** 双方接通 */
    BOTH_ANSWERED(0, "接通"),
    /** 坐席未接且用户未接 */
    NONE_ANSWERED(1, "坐席未接用户未接"),
    /** 坐席接通、用户未接通 */
    AGENT_ANSWERED_ONLY(2, "坐席接通用户未接通"),
    /** 用户接通、坐席未接通 */
    USER_ANSWERED_ONLY(3, "用户接通坐席未接通");

    private final Integer code;
    private final String desc;

    public static AnswerFlagEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }
}
