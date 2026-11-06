package com.och.ai.tts.config;

import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 统一 TTS 配置（前缀 {@code och.tts}）。
 *
 * 通过配置切换主用 Provider，业务代码不感知具体云厂商。
 * 不在配置或代码中硬编码云密钥；云密钥仍沿用 {@code SysSettingConfig}
 * （由系统设置/配置中心注入），本配置只承载非敏感开关与策略。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "och.tts")
public class TtsProperties {

    /** 主用 Provider，默认 MOCK（无凭证可联调）。 */
    private TtsProviderType provider = TtsProviderType.MOCK;

    /** 单次合成文本最大长度（中文按字符计），防止超长文本打爆上游。 */
    private int maxTextLength = 2000;

    /** Provider 调用超时（毫秒）。 */
    private long timeoutMs = 8000L;

    /** 默认采样率，电话通道 8000。 */
    private int defaultSampleRate = 8000;

    /** 默认输出格式。 */
    private TtsAudioFormat defaultFormat = TtsAudioFormat.WAV;

    /** 缓存开关。 */
    private boolean cacheEnabled = true;

    /** 缓存 TTL（秒），默认 7 天。 */
    private long cacheTtlSeconds = 7 * 24 * 3600L;

    /** 文件存储根目录；为空时取 SysSettingConfig.baseProfile/tts。 */
    private String storageDir;

    /** 阿里云 Provider 开关（凭证由 SysSettingConfig 注入）。 */
    private Aliyun aliyun = new Aliyun();

    /** 腾讯云 Provider 开关（第二云，验证用）。 */
    private Tencent tencent = new Tencent();

    /** CosyVoice 本地服务（验证用）。 */
    private CosyVoice cosyvoice = new CosyVoice();

    /** 兜底音色（主用失败或为空时使用）。 */
    private String fallbackVoice;

    /** 通道播放引擎：留空/noop=占位，esl=走 FreeSWITCH ESL 真实播放。 */
    private Playback playback = new Playback();

    @Data
    public static class Aliyun {
        /** 是否启用阿里云 Provider。 */
        private boolean enabled = false;
        /** 默认音色。 */
        private String voice = "xiaoyun";
        /** 接入点，为空走默认。 */
        private String endpoint;
    }

    @Data
    public static class Tencent {
        /** 是否启用腾讯云 Provider。 */
        private boolean enabled = false;
        /** 默认音色（数字 ID，如 101001）。 */
        private String voice = "101001";
        /** 地域，如 ap-beijing。 */
        private String region = "ap-beijing";
        /** 接入点。 */
        private String endpoint = "tts.tencentcloudapi.com";
    }

    @Data
    public static class CosyVoice {
        /** 是否启用 CosyVoice 本地 Provider。 */
        private boolean enabled = false;
        /** 本地服务地址，如 http://127.0.0.1:50031 。 */
        private String baseUrl;
        /** 默认音色。 */
        private String voice = "longxiaochun";
    }

    @Data
    public static class Playback {
        /**
         * 通道播放引擎：
         * <ul>
         *   <li>esl —— {@code FsTtsPlaybackAdapter}，生产默认，经统一播放服务下发到 FreeSWITCH。</li>
         *   <li>noop —— {@code NoopTtsPlaybackPort}，仅在明确关闭通道播放时使用。</li>
         *   <li>direct-esl —— {@code EslTtsPlaybackPort}，绕过统一播放服务的诊断兜底。</li>
         * </ul>
         */
        private String engine = "esl";

        /**
         * ESL 播放命令方式：
         * <ul>
         *   <li>execute —— sendmsg execute playback（与 FsPlaybackServiceImpl 一致，默认）。</li>
         *   <li>broadcast —— uuid_broadcast（bgapi）。</li>
         * </ul>
         */
        private String method = "execute";
    }
}
