package com.och.system.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 挂机方向字典，对应 {@code CallRecord.hangup_dir} 字段。
 *
 * <p>用于明确“谁先挂断”，配合 {@code FsHangupCauseEnum} 还原完整挂断场景。</p>
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum HangupDirectionEnum {

    /** 主叫挂机 */
    CALLER(1, "主叫挂机"),
    /** 被叫挂机 */
    CALLEE(2, "被叫挂机"),
    /** 系统挂机（超时、无路由、坐席未绑定 SIP 等） */
    SYSTEM(3, "系统挂机");

    private final Integer code;
    private final String desc;

    public static HangupDirectionEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }
}
