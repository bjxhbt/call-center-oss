package com.och.api.controller.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 音色列表项。
 */
@Data
@Schema(description = "TTS 音色")
public class TtsVoiceVo {

    @Schema(description = "音色标识")
    private String voice;

    @Schema(description = "音色名称")
    private String name;

    @Schema(description = "语言")
    private String language;

    @Schema(description = "性别")
    private String gender;

    public TtsVoiceVo() {
    }

    public TtsVoiceVo(String voice, String name, String language, String gender) {
        this.voice = voice;
        this.name = name;
        this.language = language;
        this.gender = gender;
    }
}
