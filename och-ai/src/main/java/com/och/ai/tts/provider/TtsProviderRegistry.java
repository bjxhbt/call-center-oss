package com.och.ai.tts.provider;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsProviderType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * TTS Provider 注册表。
 *
 * <p>启动时收集所有 {@link TtsServiceProvider} Bean（Mock 始终存在；云 Provider 按配置装配），
 * 提供：按类型查找、列出可用 Provider、缺凭证/未装配时回退到 Mock（降级，方案 8.10）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsProviderRegistry {

    private final List<TtsServiceProvider> providers;
    private final TtsProperties properties;

    private final Map<TtsProviderType, TtsServiceProvider> table = new EnumMap<>(TtsProviderType.class);

    @PostConstruct
    public void init() {
        for (TtsServiceProvider p : providers) {
            table.put(p.type(), p);
        }
        log.info("TTS Provider 注册完成: 可用={} 主用={}", table.keySet(), properties.getProvider());
        if (!table.containsKey(TtsProviderType.MOCK)) {
            log.warn("Mock Provider 未注册，无凭证兜底将不可用");
        }
        if (!table.containsKey(properties.getProvider())) {
            log.warn("配置的主用 Provider [{}] 未装配（可能缺凭证或未启用），将回退到 MOCK", properties.getProvider());
        }
    }

    /** 是否存在某 Provider。 */
    public boolean isAvailable(TtsProviderType type) {
        return table.containsKey(type);
    }

    /**
     * Registered and locally configured for a real request. This intentionally performs no
     * cloud call, so it must not be treated as a cloud acceptance result.
     */
    public boolean isReady(TtsProviderType type) {
        TtsServiceProvider provider = table.get(type);
        return provider != null && provider.ready();
    }

    /** 获取指定 Provider；不存在返回 null。 */
    public TtsServiceProvider get(TtsProviderType type) {
        return table.get(type);
    }

    /**
     * 选择可用 Provider：优先请求指定 → 主用配置 → MOCK 兜底。
     * 不抛异常，保证业务层总能在“某 Provider”上得到一个结果。
     */
    public TtsServiceProvider select(TtsProviderType requested) {
        if (requested != null && table.containsKey(requested)) {
            return table.get(requested);
        }
        TtsServiceProvider primary = table.get(properties.getProvider());
        if (primary != null) {
            return primary;
        }
        return table.get(TtsProviderType.MOCK);
    }

    /** 实际选用的 Provider 类型（含降级判定），供日志/审计。 */
    public TtsProviderType resolveType(TtsProviderType requested) {
        return select(requested).type();
    }
}
