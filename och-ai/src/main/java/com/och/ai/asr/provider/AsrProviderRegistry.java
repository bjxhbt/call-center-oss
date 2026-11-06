package com.och.ai.asr.provider;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrProviderType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ASR Provider 注册表（方案 7.6 / 7.15）。
 *
 * <p>启动时收集所有 {@link IAsrProvider} Bean（Mock 始终存在；云 Provider 按配置装配），
 * 提供：按类型查找、列出就绪 Provider、缺凭证/未装配时回退到 Mock（降级，方案 7.15）。</p>
 *
 * <p>选用顺序：请求指定且就绪 → 主用配置且就绪 → MOCK 兜底。
 * 不抛异常，保证业务层总能在“某 Provider”上得到结果（与 TTS 注册表策略一致）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrProviderRegistry {

    private final List<IAsrProvider> providers;
    private final AsrProperties properties;

    private final Map<AsrProviderType, IAsrProvider> table = new EnumMap<>(AsrProviderType.class);

    @PostConstruct
    public void init() {
        for (IAsrProvider p : providers) {
            table.put(p.type(), p);
        }
        log.info("ASR Provider 注册完成: 装配={} 主用={} 就绪={}",
                table.keySet(), properties.getProvider(), readyTypes());
        if (!table.containsKey(AsrProviderType.MOCK)) {
            log.warn("Mock Provider 未注册，无凭证兜底将不可用");
        }
        if (!isReady(properties.getProvider())) {
            log.warn("配置的主用 ASR Provider [{}] 未就绪（可能缺凭证或未启用），将回退到 MOCK", properties.getProvider());
        }
    }

    /** 某 Provider 是否就绪。 */
    public boolean isReady(AsrProviderType type) {
        IAsrProvider p = table.get(type);
        return p != null && p.ready();
    }

    private List<AsrProviderType> readyTypes() {
        return table.values().stream().filter(IAsrProvider::ready).map(IAsrProvider::type).toList();
    }

    /** 获取指定 Provider（无论是否就绪）；不存在返回 null。 */
    public IAsrProvider get(AsrProviderType type) {
        return table.get(type);
    }

    /**
     * 选择可用 Provider：优先请求指定且就绪 → 主用配置且就绪 → MOCK 兜底。
     */
    public IAsrProvider select(AsrProviderType requested) {
        if (requested != null && isReady(requested)) {
            return table.get(requested);
        }
        if (isReady(properties.getProvider())) {
            return table.get(properties.getProvider());
        }
        return table.get(AsrProviderType.MOCK);
    }

    /** 实际选用并解析类型（含降级判定），供日志/审计。 */
    public AsrProviderType resolveType(AsrProviderType requested) {
        return select(requested).type();
    }
}
