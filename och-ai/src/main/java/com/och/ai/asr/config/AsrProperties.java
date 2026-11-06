package com.och.ai.asr.config;

import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AudioCodec;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一 ASR 配置（前缀 {@code och.asr}，方案 7.1 / 7.6）。
 *
 * <p>通过配置切换主用 Provider，业务代码不感知具体云厂商。
 * <b>不在配置或代码中硬编码云密钥</b>：appId/secretId/secretKey/appKey/token 等敏感字段
 * 默认留空，由系统设置/配置中心/环境变量注入；为空时对应 Provider 自动降级为 Mock。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "och.asr")
public class AsrProperties {

    /** ASR 总开关；false 时桥接不创建会话、不消耗配额（方案 7.15 降级）。 */
    private boolean enabled = true;

    /** 主用 Provider，默认 MOCK（无凭证可联调整条链路）。 */
    private AsrProviderType provider = AsrProviderType.MOCK;

    /** 发送给 ASR 的目标采样率（电话 8k 升采样到 16k 以提升准确率）。 */
    private int sampleRate = 16000;

    /** 入口默认编码（FreeSWITCH 电话通道通常 PCMA）。 */
    private AudioCodec inputCodec = AudioCodec.PCMA;

    /** 声道数。 */
    private int channels = 1;

    /** 音频分片时长（毫秒）。 */
    private int frameDurationMs = 20;

    /** 单次发送间隔上限（毫秒，模拟实时流用）。 */
    private int sendIntervalMs = 20;

    /** 每帧字节数上限（保护切片过大）。 */
    private int maxFrameBytes = 6400;

    /** 会话结束后等待最终结果的最长时间（毫秒，方案 7.5）。 */
    private long finalWaitTimeoutMs = 5000L;

    /** 云端连接超时（毫秒）。 */
    private long connectTimeoutMs = 5000L;

    /** 单帧发送超时（毫秒）。 */
    private long sendTimeoutMs = 3000L;

    /** 最大重连次数（方案 7.15，防止无限重试）。 */
    private int maxReconnect = 2;

    /** 重连退避基数（毫秒）。 */
    private long reconnectBackoffMs = 1000L;

    /** 最大并发会话数（保护内存/配额）。 */
    private int maxConcurrentSessions = 256;

    /** 是否启用标点。 */
    private boolean punctuation = true;

    /** 是否启用数字转换（如“一百”→“100”）。 */
    private boolean convertDigit = true;

    /** 业务热词（不含隐私，统一注入各 Provider）。 */
    private List<String> hotWords = new ArrayList<>();

    /** 录音文件根目录（模拟器/离线补偿读取本地录音）。 */
    private String recordingDir;

        /**
     * FreeSWITCH 真实音频推流（方案 7.2 / 11.2 P0）。
     * 桥接后由业务下发 uuid_audio_fork，挂断后 stop。
     */
    private AudioStream audioStream = new AudioStream();

    /** 腾讯云实时 ASR（可配置为主用）。 */
    private Tencent tencent = new Tencent();

    /** 阿里云 NLS 实时 ASR（第二云，验证用）。 */
    private Aliyun aliyun = new Aliyun();

    /** FunASR 本地 runtime（验证用，不进主链路）。 */
    private FunAsr funasr = new FunAsr();

    /** Mock 配置（无凭证联调）。 */
    private Mock mock = new Mock();

    /** 自动从录音补偿：会话异常或未启用 ASR 时，挂断后尝试离线转写。 */
    private boolean offlineCompensationEnabled = false;

    @Data
    public static class Tencent {
        private boolean enabled = false;
        /** AppId（敏感，配置中心/环境变量注入，留空则降级 Mock）。 */
        private String appId;
        /** SecretId（敏感）。 */
        private String secretId;
        /** SecretKey（敏感）。 */
        private String secretKey;
        /** 引擎模型类型，如 16k_zh。 */
        private String engineModelType = "16k_zh";
        /** 语音数据格式，1-PCM/4-PCMA/6-PCMU 等。 */
        private int voiceFormat = 1;
        /** 热词词表 ID（可选）。 */
        private String hotWordId;
        /** 接入点 wss。 */
        private String endpoint = "wss://asr.cloud.tencent.com/asr/v2/";
    }

    @Data
    public static class Aliyun {
        private boolean enabled = false;
        /** AppKey（敏感）。 */
        private String appKey;
        /** AccessKeyId（敏感，仅本地换 token 可选）。 */
        private String accessKeyId;
        /** AccessKeySecret（敏感，仅本地换 token 可选）。 */
        private String accessKeySecret;
        /** NLS AccessToken（敏感，由 AccessKey 调 CreateToken 获取后注入；运维缓存）。 */
        private String accessToken;
        /** NLS 网关 wss。 */
        private String endpoint = "wss://nls-gateway.aliyuncs.com/ws/v1";
        /** 模型。 */
        private String model = "chat";
    }

    @Data
    public static class FunAsr {
        private boolean enabled = false;
        /** 本地 runtime WebSocket 地址。 */
        private String wsUrl = "ws://127.0.0.1:10095";
        /** 模式：2pass(实时+最终) / online(纯增量) / offline(纯最终)。 */
        private String mode = "2pass";
        /** 热词 JSON 路径或内联。 */
        private String hotWordsJson;
        /** 采样率（FunASR 默认 16000）。 */
        private int sampleRate = 16000;
    }

    @Data
    public static class Mock {
        /** Mock 脚本文件（classpath 或绝对路径），为空用内置脚本。 */
        private String scriptFile;
        /** 每段 partial 间隔（毫秒）。 */
        private long intervalMs = 400L;
        /** 每句拆成多少个 partial。 */
        private int partialSteps = 3;
    }

    @Data
    public static class AudioStream {
        /** 是否在桥接后自动启动 FreeSWITCH 音频推流。 */
        private boolean enabled = true;
        /**
         * 推流目标 URL。
         * <p>mod_audio_fork/mod_audio_stream 必须使用 WebSocket（ws:// 或 wss://）。
         * {@code /asr/v1/audio/frame} 仅供另行实现的 HTTP 适配器调用，不能直接作为
         * 这两个 FreeSWITCH 模块的目标。
         * 支持占位符：{callId}、{uniqueId}。
         */
        private String endpoint = "ws://127.0.0.1:4320/asr/v1/audio/ws?callId={callId}&uniqueId={uniqueId}&codec=PCM&sampleRate=8000";
        /** 模块：audio_fork（uuid_audio_fork）| audio_stream（uuid_audio_stream）| none。 */
        private String module = "audio_fork";
        /** mono | mixed | stereo */
        private String mixType = "mono";
        /** 推流采样率（电话默认 8k） */
        private int sampleRate = 8000;
        /** 是否对桥接双方腿都启动推流；false 时仅 uniqueId 一侧 */
        private boolean bothLegs = false;
        /**
         * 可选共享密钥，FS/脚本在 Header {@code X-Asr-Stream-Token} 携带；
         * 为空则不校验（内网部署）。
         */
        private String streamToken = "";
    }
}
