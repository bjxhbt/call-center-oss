package com.och.ai.asr.ingest;

import com.och.ai.asr.model.CallAudioFrame;

import java.util.List;

/**
 * ASR 音频接入服务（方案 7.2 / 9.5）。
 *
 * <p>定义 FreeSWITCH {@code mod_audio_fork}/{@code mod_audio_stream} 与 ASR 之间的音频投递接口：
 * 每帧携带 callId + UUID + 编码 + 采样率 + 数据。FS 侧音频模块、Mock 录音切片模拟器、
 * REST 触发接口与测试均通过本接口投递音频，业务侧不感知来源。</p>
 */
public interface IAsrAudioIngestService {

    /**
     * 投递一帧音频到对应 callId 的活动 ASR 会话。
     *
     * @param frame 音频帧
     */
    void onFrame(CallAudioFrame frame);

    /**
     * 批量投递（切片/回放场景）。
     *
     * @param frames 帧列表
     */
    void onFrames(List<CallAudioFrame> frames);
}
