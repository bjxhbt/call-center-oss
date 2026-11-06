package com.och.ai.asr.ingest;

import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.session.AsrSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 音频接入实现：直接委托会话管理器按 callId 路由到活动 ASR 会话（方案 9.5）。
 *
 * <p>音频帧处理同步且快速（归一化 + WS 二进制发送均为非阻塞），无需异步；
 * 一切异常被会话管理器吞掉，绝不影响通话。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsrAudioIngestServiceImpl implements IAsrAudioIngestService {

    private final AsrSessionManager sessionManager;

    @Override
    public void onFrame(CallAudioFrame frame) {
        if (frame == null) {
            return;
        }
        sessionManager.onAudioFrame(frame);
    }

    @Override
    public void onFrames(List<CallAudioFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        for (CallAudioFrame frame : frames) {
            sessionManager.onAudioFrame(frame);
        }
    }
}
