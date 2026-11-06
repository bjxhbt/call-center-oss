package com.och.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 引擎工厂：懒加载实现。
 *
 * 说明：引擎构造时会读取 CloudConfigManager 的配置，而配置由 OchMrcpServer.main
 * 在启动时加载。若用静态字段初始化（如 Map.of("aliyun", new AliyunTtsEngine())），
 * 会在 CloudConfigManager.loadConfig() 之前触发，导致 getConfig() 返回 null 而抛
 * NullPointerException（ExceptionInInitializerError），进而使 MRCP 会话建立失败
 * （och-mrcp 回 503）。改为按需懒加载，确保首次 getTtsEngine 时配置已就绪。
 */
public class EngineFactory {

    private static final Map<String, TtsEngine> ttsEngines = new ConcurrentHashMap<>();
    private static final Map<String, AsrEngine> asrEngines = new ConcurrentHashMap<>();

    public static TtsEngine getTtsEngine(String platform) {
        return ttsEngines.computeIfAbsent(platform, p -> {
            if ("aliyun".equals(p)) return new AliyunTtsEngine();
            if ("tencent".equals(p)) return new TencentTtsEngine();
            if ("local".equals(p)) return new LocalTtsEngine();
            throw new IllegalArgumentException("不支持的 TTS 引擎: " + p);
        });
    }

    public static AsrEngine getAsrEngine(String platform) {
        return asrEngines.computeIfAbsent(platform, p -> {
            if ("aliyun".equals(p)) return new AliyunAsrEngine();
            if ("tencent".equals(p)) return new TencentAsrEngine();
            throw new IllegalArgumentException("不支持的 ASR 引擎: " + p);
        });
    }

}