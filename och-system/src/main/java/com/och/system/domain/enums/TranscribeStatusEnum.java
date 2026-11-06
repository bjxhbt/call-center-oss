package com.och.system.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 转写状态字典，描述一次通话 ASR 转写任务的整体状态。
 *
 * <p>供方案四 ASR 与方案一 WebSocket 转写事件共用。增量/最终文本事件 (9.7) 使用本状态标识会话级别进度，
 * 单条增量结果本身不做落库幂等判断，只保证最终转写记录 (7.11) 不重复创建。</p>
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum TranscribeStatusEnum {

    /** 待转写（通话已建立，尚未开始推送音频） */
    PENDING(0, "待转写"),
    /** 转写中（已收到增量结果） */
    TRANSCRIBING(1, "转写中"),
    /** 转写完成（已收到最终结果并落库） */
    COMPLETED(2, "转写完成"),
    /** 转写失败 */
    FAILED(3, "转写失败"),
    /** 已跳过（通话未接通或未开启转写） */
    SKIPPED(4, "已跳过");

    private final Integer code;
    private final String desc;

    public static TranscribeStatusEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否终态。终态转写状态不可被覆盖，避免重复落库。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED;
    }
}
