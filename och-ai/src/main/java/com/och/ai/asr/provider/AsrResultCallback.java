package com.och.ai.asr.provider;

import com.och.ai.asr.model.AsrRecognitionResult;

/**
 * ASR 结果回调（方案 7.6）。
 *
 * <p>Provider 在收到云端临时/最终结果或异常时回调本接口；由会话管理器实现，
 * 转发到 {@code AsrResultHandler}（增量覆盖/最终持久化）与 WebSocket 推送。</p>
 */
public interface AsrResultCallback {

    /** 收到一条识别结果（临时或最终）。 */
    void onResult(AsrRecognitionResult result);

    /** 连接/鉴权/限流等异常。code 取 {@code AsrErrorCode}。 */
    void onError(String code, String message);

    /** 云端会话已关闭（正常或异常）。 */
    void onClosed();
}
