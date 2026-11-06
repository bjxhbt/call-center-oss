package com.och.esl.queue;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author danmo
 * @date 2024-11-16 14:22
 **/
@Builder
@Data
public class CallQueue implements Comparable<CallQueue>, Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 1-普通 2-VIP
     */
    private Integer type;

    /**
     * 通话ID
     */
    private Long callId;

    /**
     * 进入时间
     */
    private Long startTime;

    /**
     * 技能组ID
     */
    private Long skillId;

    /**
     * 腿Id
     */
    private String uniqueId;

    /**
     * fs地址
     */
    private String address;

    /**
     * 播放排队音标识
     */
    private Boolean playFlag;

    @Override
    public int compareTo(@NotNull CallQueue o) {
        int startTimeComparison = this.startTime.compareTo(o.startTime);
        if (startTimeComparison != 0) {
            return startTimeComparison;
        }
        return this.callId.compareTo(o.callId);
    }
}
