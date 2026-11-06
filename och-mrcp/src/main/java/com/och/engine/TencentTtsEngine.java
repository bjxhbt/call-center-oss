package com.och.engine;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tts.v20190823.TtsClient;
import com.tencentcloudapi.tts.v20190823.models.TextToVoiceRequest;
import com.tencentcloudapi.tts.v20190823.models.TextToVoiceResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

/**
 * 腾讯云 TTS 语音合成引擎
 * 采用同步 HTTP 接口 TextToVoice，合成完成后一次性返回完整音频（Base64 编码）。
 * 输出格式固定为 8k 采样率 PCM，便于直接送入 FreeSWITCH / MRCP 语音通道。
 *
 * 配置来源：engine.conf 中 platform = "tencent" 的 CloudConfig
 *   - apiKey    -> SecretId
 *   - apiSecret -> SecretKey
 *   - appKey    -> AppId（腾讯云账号 APPID，可留空）
 *   - endpoint  -> 接入地域域名，默认 tts.tencentcloudapi.com
 */
@Slf4j
public class TencentTtsEngine implements TtsEngine {

    private final CloudConfig config;

    private static final String DEFAULT_ENDPOINT = "tts.tencentcloudapi.com";
    private static final String DEFAULT_REGION = "ap-beijing";

    /**
     * 腾讯云 SessionId 缓存键前缀（用于计费 / 日志关联，非必需）
     */
    private static final String SESSION_ID_CACHE_KEY = "tencent_tts_session_id";

    public TencentTtsEngine() {
        this.config = CloudConfigManager.getConfig("tencent");
        if (config == null) {
            log.error("腾讯云 TTS 配置未找到，请在 engine.conf 中配置 platform=tencent 的相关信息");
        }
    }

    @Override
    public void synthesize(String text, String voice, TtsCallback callback) {
        if (config == null) {
            callback.onComplete((byte[]) null, new IllegalStateException("腾讯云 TTS 配置缺失"));
            return;
        }
        if (text == null || text.isEmpty()) {
            callback.onComplete((byte[]) null, new IllegalArgumentException("合成文本不能为空"));
            return;
        }
        try {
            byte[] audioData = doSynthesize(text, voice);
            log.info("腾讯云语音合成成功，文本长度={}，音频字节数={}", text.length(), audioData.length);
            callback.onComplete(audioData, null);

            // 合成完成事件回调（与 AliyunTtsEngine 保持一致的两段式回调）
            JSONObject result = new JSONObject();
            result.put("status", 200);
            result.put("statusText", "success");
            result.put("sessionId", IdUtil.fastSimpleUUID());
            callback.onComplete(result);
        } catch (Exception e) {
            log.error("腾讯云语音合成失败: {}", e.getMessage(), e);
            callback.onComplete((byte[]) null, e);
        }
    }

    /**
     * 调用腾讯云 TextToVoice 接口完成一次合成。
     *
     * @param text  待合成文本（一次调用上限 150 字，长文本需在外部分片）
     * @param voice 发音人 ID，为空时使用默认发音人 101001（智瑜，女，普通话）
     * @return PCM 8k 音频字节
     */
    private byte[] doSynthesize(String text, String voice) throws TencentCloudSDKException {
        // 1. 鉴权凭证：apiKey=SecretId，apiSecret=SecretKey
        Credential cred = new Credential(config.getApiKey(), config.getApiSecret());

        // 2. HTTP 选项
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(config.getEndpoint() == null || config.getEndpoint().isEmpty()
                ? DEFAULT_ENDPOINT : config.getEndpoint());

        // 3. 客户端配置
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        String region = config.getCustomParams() == null
                ? DEFAULT_REGION
                : config.getCustomParams().getOrDefault("region", DEFAULT_REGION);

        TtsClient client = new TtsClient(cred, region, clientProfile);

        // 4. 构造请求
        TextToVoiceRequest req = new TextToVoiceRequest();
        req.setText(text);
        req.setSessionId(IdUtil.fastSimpleUUID());
        // 发音人：voice 参数对应 MRCP 头部 Voice-Name；为空走默认音色
        req.setVoiceType(parseVoiceType(voice));
        // 音频格式：8 = PCM；Volume/Speed 取默认值
        req.setCodec("pcm");
        // 采样率：8000Hz，匹配 FreeSWITCH 电话语音通道
        req.setSampleRate(8000L);
        // 音量 [0,100]，默认 0 表示平台默认值
        req.setVolume(0F);
        // 语速 [-2,6]，默认 0
        req.setSpeed(0F);
        // 启用按字符返回时间戳（不影响音频流，仅用于调试）
        req.setEnableSubtitle(false);

        // 5. 发起调用并解码 Base64 音频
        TextToVoiceResponse resp = client.TextToVoice(req);
        String audioBase64 = resp.getAudio();
        if (audioBase64 == null || audioBase64.isEmpty()) {
            throw new IllegalStateException("腾讯云返回音频为空");
        }
        return Base64.getDecoder().decode(audioBase64);
    }

    /**
     * 解析腾讯云 VoiceType。
     * FreeSWITCH 的 speak(unimrcp|och|text) 传来的 voice 实际是 MRCP profile 名（如 "och"），
     * 不是腾讯云数字音色 ID。这里只接受纯数字字符串，否则回退到默认音色 101001（智瑶）。
     */
    private long parseVoiceType(String voice) {
        if (voice == null || voice.isEmpty()) {
            return 101001L;
        }
        try {
            return Long.parseLong(voice.trim());
        } catch (NumberFormatException e) {
            log.warn("Voice 参数 [{}] 不是数字，使用默认音色 101001", voice);
            return 101001L;
        }
    }
}