package com.och.system.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 录音状态字典，对应 {@code CallRecord.record_status} 字段。
 *
 * <p>录音文件由方案三通信链路在 FreeSWITCH 录音停止后上传，方案四 ASR 依赖该状态决定是否可转写。
 * 状态只能按未上传 -&gt; 上传中 -&gt; 已上传/上传失败 推进，禁止逆向覆盖。</p>
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum RecordStatusEnum {

    /** 未上传 */
    NOT_UPLOADED(0, "未上传"),
    /** 上传中 */
    UPLOADING(1, "上传中"),
    /** 已上传 */
    UPLOADED(2, "已上传"),
    /** 上传失败 */
    UPLOAD_FAILED(3, "上传失败");

    private final Integer code;
    private final String desc;

    public static RecordStatusEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> Objects.equals(e.code, code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否终态。终态录音状态不可被覆盖。
     */
    public boolean isTerminal() {
        return this == UPLOADED || this == UPLOAD_FAILED;
    }
}
