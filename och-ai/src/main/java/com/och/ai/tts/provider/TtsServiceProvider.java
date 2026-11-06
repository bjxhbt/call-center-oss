package com.och.ai.tts.provider;

import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;

/**
 * 统一 TTS 服务 Provider SPI。
 *
 * <p>业务代码（{@code TtsService}、{@code TtsBusinessService}）只依赖本接口与
 * {@link TtsProviderRegistry}，不直接依赖任一云厂商 SDK，满足方案 8.1 要求。
 *
 * <p>每个实现负责：鉴权 → 参数映射 → 文本合成 → 输出归一化 PCM/WAV 字节，
 * 并返回带 {@code audioBytes/format/sampleRate/voice/errorCode} 的 {@link TtsResult}。
 * 文件落盘、缓存、引用计数由 {@code TtsFileManager}/{@code TtsCacheService} 统一处理，
 * Provider 不关心文件路径。
 */
public interface TtsServiceProvider {

    /**
     * Provider 标识。
     *
     * @return 服务商标识
     */
    TtsProviderType type();

    /**
     * Whether this provider has the minimum local configuration required to attempt a real
     * synthesis request. This method must not call a cloud service or expose credentials.
     * A true result proves configuration readiness only, not cloud acceptance.
     */
    default boolean ready() {
        return true;
    }

    /**
     * 执行一次文本合成。
     *
     * @param request 统一请求
     * @return 合成结果（成功时携带 audioBytes，失败时携带 errorCode）
     */
    TtsResult synthesize(TtsRequest request);
}
