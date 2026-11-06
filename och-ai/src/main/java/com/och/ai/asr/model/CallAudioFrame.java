package com.och.ai.asr.model;

import lombok.Builder;
import lombok.Data;

/**
 * 通话音频帧（方案 7.2 / 7.3）。
 *
 * <p>FreeSWITCH {@code mod_audio_fork}/{@code mod_audio_stream} 输出的音频通过本结构送入 ASR：
 * 携带 callId + UUID，支持 PCMA/PCMU/PCM，标注采样率、声道与说话人。
 * 统一接口在入口解码为线性 PCM 后再分片发送给云端。</p>
 */
@Data
@Builder
public class CallAudioFrame {

    /** 业务呼叫 ID。 */
    private Long callId;

    /** FS 通道 UUID。 */
    private String uniqueId;

    /** 音频编码。 */
    private AudioCodec codec;

    /** 采样率（8000/16000）。 */
    private Integer sampleRate;

    /** 声道数（电话一般为 1）。 */
    private Integer channels;

    /** 原始音频字节（PCMA/PCMU 编码字节或线性 PCM）。 */
    private byte[] data;

    /** 说话人（分腿可知，混合声道为 UNKNOWN）。 */
    private Speaker speaker;

    /** 该 callId 内单调递增的帧序号。 */
    private Long sequence;

    /** 帧产生时间（毫秒）。 */
    private Long timestampMs;

    /**
     * 是否为静音/空帧（采集层标注，便于 ASR 跳过，节省配额）。
     * 采集层若未计算则保持 false。
     */
    private boolean silent;
}
