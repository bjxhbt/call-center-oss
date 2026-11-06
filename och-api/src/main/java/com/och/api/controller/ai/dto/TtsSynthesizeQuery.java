package com.och.api.controller.ai.dto;

import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * TTS 合成请求（前端/接口入口），映射到统一 {@code TtsRequest}。
 *
 * <p>前端 TTS 参数与试听页面（方案二）通过本契约联调，本方案不修改前端页面。
 */
@Data
@Schema(description = "TTS 合成请求")
public class TtsSynthesizeQuery {

    @Schema(description = "合成文本")
    private String text;

    @Schema(description = "服务商，为空用主用配置 MOCK/ALIYUN/TENCENT/COSYVOICE")
    private TtsProviderType provider;

    @Schema(description = "音色")
    private String voice;

    @Schema(description = "语速 [-1.0,1.0]")
    private Double speed;

    @Schema(description = "音调 [-1.0,1.0]")
    private Double pitch;

    @Schema(description = "音量 [0.0,1.0]")
    private Double volume;

    @Schema(description = "采样率，默认 8000")
    private Integer sampleRate;

    @Schema(description = "输出格式，默认 WAV")
    private TtsAudioFormat format;

    @Schema(description = "通话标识（可选，携带则下发通道播放）")
    private String callId;

    @Schema(description = "通道 UUID（可选，携带则下发通道播放）")
    private String channelUuid;

    @Schema(description = "强制刷新忽略缓存")
    private Boolean forceRefresh;

    @Schema(description = "跳过缓存写入")
    private Boolean skipCache;
}
