package com.och.ai.asr.provider;

import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSessionContext;

/**
 * 统一 ASR 服务 Provider SPI（方案 7.6）。
 *
 * <p>业务代码（会话管理、音频接入、生命周期监听）只依赖本接口与 {@link AsrProviderRegistry}，
 * 不直接依赖任一云厂商 SDK，满足方案 7.6 要求。</p>
 *
 * <p>每个实现负责：鉴权 → 建立长连接 → 分片发送 PCM → 解析增量/最终结果 → 回调。
 * 会话创建/去重/超时/重连由 {@code AsrSessionManager} 统一编排，Provider 只关注“一条云端会话”。</p>
 *
 * <p>音频输入约定：{@code sendAudio} 收到的 {@code pcm} 为 <b>16bit 小端线性 PCM</b>，
 * 采样率为 {@link AsrSessionContext#getSampleRate()}（由配置统一为目标采样率）。</p>
 */
public interface IAsrProvider {

    /** Provider 标识。 */
    AsrProviderType type();

    /**
     * 是否就绪：Mock 恒为 true；云 Provider 要求 enabled 且凭证齐全。
     * 未就绪的 Provider 不会被选用（降级到 Mock，方案 7.15）。
     */
    boolean ready();

    /**
     * 创建云端会话并建立长连接；成功后回填 {@code ctx.cloudSessionId}。
     *
     * @param ctx      会话上下文
     * @param callback 结果回调
     * @return 云端会话 ID（可为空，表示该 Provider 不需要显式 id）
     */
    String createSession(AsrSessionContext ctx, AsrResultCallback callback);

    /**
     * 发送一帧线性 PCM（16bit 小端，目标采样率）。
     */
    void sendAudio(AsrSessionContext ctx, byte[] pcm);

    /**
     * 通知音频结束，触发最终结果（方案 7.11）。
     * 实现应尽量在有限时间内产出最终结果并回调，最终等待上限由会话管理器保证。
     */
    void finishSession(AsrSessionContext ctx);

    /**
     * 取消会话，不等待最终结果（如异常降级）。
     */
    void cancelSession(AsrSessionContext ctx);
}
