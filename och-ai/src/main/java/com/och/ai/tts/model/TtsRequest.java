package com.och.ai.tts.model;

import lombok.Builder;
import lombok.Data;

/**
 * 统一 TTS 合成请求。
 *
 * 业务模块只构造本对象并交给 {@code TtsService}，不感知具体云厂商。
 * 字段对齐方案要求：文本、服务商、音色、语速、音调、音量、采样率、输出格式。
 */
@Data
@Builder
public class TtsRequest {

    /** 待合成文本（必填，长度受 {@code och.tts.max-text-length} 约束）。 */
    private String text;

    /** 指定服务商；为空时使用配置的主用 Provider。 */
    private TtsProviderType provider;

    /** 发音人/音色 ID。为空时各 Provider 使用自身默认音色。 */
    private String voice;

    /**
     * 语速。归一化到 [-1.0, 1.0]，0 为正常。
     * 各 Provider 在适配层映射为自身刻度（阿里 -500~500、腾讯 -2~6 等）。
     */
    private Double speed;

    /** 音调，归一化到 [-1.0, 1.0]，0 为正常。 */
    private Double pitch;

    /** 音量，归一化到 [0.0, 1.0] 或 [0,100]，适配层按厂商映射。 */
    private Double volume;

    /** 采样率，电话通道默认 8000。 */
    @Builder.Default
    private Integer sampleRate = 8000;

    /** 输出格式，默认 WAV（兼容浏览器试听与 FreeSWITCH 播放）。 */
    @Builder.Default
    private TtsAudioFormat format = TtsAudioFormat.WAV;

    /** 业务关联：通话/通道标识，用于业务流转与播放事件回写，可空。 */
    private String callId;

    /** 业务关联：通道 UUID（FreeSWITCH Channel），可空。 */
    private String channelUuid;

    /** 是否强制刷新（忽略缓存重新合成），默认 false。 */
    @Builder.Default
    private boolean forceRefresh = false;

    /** 是否跳过缓存写入（一次性合成），默认 false。 */
    @Builder.Default
    private boolean skipCache = false;
}
