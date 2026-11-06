package com.och.ai.tts.service;

import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;

/**
 * 统一 TTS 合成服务（方案 8.1/8.2/8.7/8.10 入口）。
 *
 * <p>职责：参数校验 → 缓存命中 → 选择 Provider → 合成 → 落盘 → 持久化 → 回写缓存。
 * 业务模块只依赖本接口，不感知云厂商。
 */
public interface TtsService {

    /**
     * 同步合成。成功返回带文件地址的结果；失败返回带错误码的结果（或抛 TtsException 表示参数非法）。
     */
    TtsResult synthesize(TtsRequest request);
}
