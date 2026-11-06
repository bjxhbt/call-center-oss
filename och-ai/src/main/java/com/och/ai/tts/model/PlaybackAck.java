package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

/**
 * 通信链路方案对播放请求的即时回执。
 */
@Data
@Builder
public class PlaybackAck {

    /** 是否受理（已提交给 FreeSWITCH 通道）。 */
    private boolean accepted;

    private String message;
}
