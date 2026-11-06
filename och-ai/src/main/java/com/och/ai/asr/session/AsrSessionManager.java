package com.och.ai.asr.session;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.audio.AudioNormalizer;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.model.AsrSessionState;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.provider.AsrResultCallback;
import com.och.ai.asr.provider.IAsrProvider;
import com.och.ai.asr.result.AsrResultHandler;
import com.och.ai.asr.push.AsrWsPushService;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ASR 会话管理器（方案 7.5 / 7.15 / 9.5）。
 *
 * <p>编排一通通话的转写生命周期：桥接→创建会话（幂等）→音频推送→挂断→结束等待最终结果→持久化摘要→清理。
 * 统一负责：云端 sessionId 与 callId/UUID 映射、有限重连、重连后防文本重复、最终结果等待超时。</p>
 *
 * <p><b>铁律</b>：本类所有方法吞掉一切异常，绝不让 ASR 失败冒泡到通话链路（方案 7.15：
 * ASR 失败不得挂断电话）。连接/发送失败仅停止当前转写并记录，保留录音供离线补偿。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrSessionManager {

    private final AsrProperties properties;
    private final AsrProviderRegistry providerRegistry;
    private final AudioNormalizer audioNormalizer;
    private final AsrResultHandler resultHandler;
    private final AsrWsPushService pushService;
    private final IAsrTranscriptService transcriptService;

    /** callId → 会话上下文。 */
    private final ConcurrentHashMap<Long, AsrSessionContext> byCallId = new ConcurrentHashMap<>();
    /** uniqueId → callId（音频按 UUID 路由）。 */
    private final ConcurrentHashMap<String, Long> callIdByUuid = new ConcurrentHashMap<>();
    /** sessionId → 实际生效 Provider。 */
    private final ConcurrentHashMap<String, IAsrProvider> providers = new ConcurrentHashMap<>();
    /** sessionId → 最终等待闩（挂断时设置，Provider onClosed 时释放）。 */
    private final ConcurrentHashMap<String, CountDownLatch> finalLatches = new ConcurrentHashMap<>();

    /** 可触发有限重连的错误码（瞬时类：连接断开/超时/发送失败/限流）。鉴权失败等配置类错误不重连。 */
    private static final Set<String> RECONNECTABLE = Set.of(
            AsrErrorCode.CONNECTION_BROKEN, AsrErrorCode.TIMEOUT, AsrErrorCode.SEND_FAILED, AsrErrorCode.RATE_LIMITED);

    /**
     * 桥接开始：创建 ASR 会话（幂等，同 callId 重复直接返回）。
     */
    public void onBridge(CallBridgeEvent event) {
        if (event == null || event.getCallId() == null) {
            return;
        }
        if (!properties.isEnabled()) {
            log.debug("ASR 未启用，跳过桥接创建 callId:{}", event.getCallId());
            return;
        }
        Long callId = event.getCallId();
        AsrSessionContext existing = byCallId.get(callId);
        if (existing != null) {
            log.info("ASR 会话已存在，跳过重复创建 callId:{} state:{}", callId, existing.getState());
            return;
        }
        try {
            IAsrProvider provider = providerRegistry.select(properties.getProvider());
            AsrSessionContext ctx = new AsrSessionContext();
            ctx.setSessionId("asr-" + callId);
            ctx.setCallId(callId);
            ctx.setUniqueId(event.getUniqueId());
            ctx.setOtherUniqueId(event.getOtherUniqueId());
            ctx.setCaller(event.getCaller());
            ctx.setCallee(event.getCallee());
            ctx.setAgentId(event.getAgentId());
            ctx.setAgentNumber(event.getAgentNumber());
            ctx.setAgentName(event.getAgentName());
            ctx.setDirection(event.getDirection());
            ctx.setProvider(provider.type());
            ctx.setSampleRate(properties.getSampleRate());
            ctx.setCodec(properties.getInputCodec());
            ctx.setBridgeTime(event.getBridgeTime());
            ctx.setRecord(event.getRecord());
            ctx.setState(AsrSessionState.CREATED);

            byCallId.put(callId, ctx);
            if (event.getUniqueId() != null) {
                callIdByUuid.put(event.getUniqueId(), callId);
            }
            providers.put(ctx.getSessionId(), provider);

            // 创建摘要（失败不阻断，最终段落到达时兜底创建）
            try {
                ctx.setTranscriptId(transcriptService.getOrCreateSummary(ctx).getId());
            } catch (Exception e) {
                log.warn("创建转写摘要失败(忽略，将兜底) callId:{} err:{}", callId, e.getMessage());
            }

            AsrResultCallback callback = buildCallback(ctx);
            provider.createSession(ctx, callback);
            ctx.setState(AsrSessionState.ACTIVE);
            pushService.pushStatus(ctx, "active", null, null);
            log.info("ASR 会话已创建 callId:{} uniqueId:{} provider:{} cloudSession:{}",
                    callId, event.getUniqueId(), provider.type(), ctx.getCloudSessionId());
        } catch (Exception e) {
            log.error("ASR 会话创建失败(忽略，不影响通话) callId:{} err:{}", callId, e.getMessage(), e);
            // 失败不留残骸
            byCallId.remove(callId);
        }
    }

    /**
     * 收到一帧音频：连续性检测 → 归一化 → 推送 Provider。
     *
     * <p>连续性：按 frame.sequence 检测丢帧(序号跳变)/重复/乱序，仅统计与告警，不阻断发送
     * （方案 11.2 P0：核对采样率、分片、持续性与通话内容；无明显丢帧可量化）。</p>
     */
    public void onAudioFrame(CallAudioFrame frame) {
        if (frame == null || frame.getCallId() == null) {
            return;
        }
        AsrSessionContext ctx = byCallId.get(frame.getCallId());
        if (ctx == null) {
            return;
        }
        if (ctx.getState() == null || ctx.getState().isTerminal()) {
            return;
        }
        IAsrProvider provider = providers.get(ctx.getSessionId());
        if (provider == null) {
            return;
        }
        // 连续性检测（不抛出、不影响发送）
        trackContinuity(ctx, frame.getSequence());
        try {
            if (ctx.getFirstAudioSentAt() == 0) {
                ctx.setFirstAudioSentAt(System.currentTimeMillis());
            }
            AudioNormalizer.NormalizedAudio na = audioNormalizer.normalize(frame);
            if (!na.valid() || na.pcm().length == 0) {
                return;
            }
            ctx.setAudioFramesSent(ctx.getAudioFramesSent() + 1);
            provider.sendAudio(ctx, na.pcm());
        } catch (Exception e) {
            log.warn("音频推送失败(忽略) callId:{} err:{}", frame.getCallId(), e.getMessage());
            handleProviderError(ctx, AsrErrorCode.SEND_FAILED, e.getMessage());
        }
    }

    /**
     * 帧序号连续性统计：建立基准后，跳变计丢帧，回退计重复/乱序。
     */
    private void trackContinuity(AsrSessionContext ctx, Long sequence) {
        ctx.setFramesReceived(ctx.getFramesReceived() + 1);
        if (sequence == null) {
            return;
        }
        if (ctx.getLastFrameSequence() < 0) {
            ctx.setLastFrameSequence(sequence);
            return;
        }
        long last = ctx.getLastFrameSequence();
        if (sequence == last) {
            ctx.setDuplicateOrOutOfOrder(ctx.getDuplicateOrOutOfOrder() + 1);
        } else if (sequence < last) {
            ctx.setDuplicateOrOutOfOrder(ctx.getDuplicateOrOutOfOrder() + 1);
            log.warn("音频乱序/回退 callId:{} seq:{} < last:{} (累计{})",
                    ctx.getCallId(), sequence, last, ctx.getDuplicateOrOutOfOrder());
        } else if (sequence > last + 1) {
            long missing = sequence - last - 1;
            ctx.setGapsDetected(ctx.getGapsDetected() + missing);
            log.warn("音频疑似丢帧 callId:{} missing:{} seq:{} last:{} (累计丢{})",
                    ctx.getCallId(), missing, sequence, last, ctx.getGapsDetected());
        }
        ctx.setLastFrameSequence(sequence);
    }

    /**
     * 挂断：结束会话，等待最终结果（有超时上限），落库摘要并清理。
     */
    public void onHangup(CallHangupEvent event) {
        if (event == null || event.getCallId() == null) {
            return;
        }
        Long callId = event.getCallId();
        AsrSessionContext ctx = byCallId.get(callId);
        if (ctx == null) {
            // 未启用/未创建会话：可选离线补偿（方案 7.15），此处仅记录
            if (properties.isOfflineCompensationEnabled()) {
                log.info("转写会话不存在，可触发离线补偿 callId:{} record:{}", callId, event.getRecord());
            }
            return;
        }
        IAsrProvider provider = providers.get(ctx.getSessionId());
        boolean finalized = false;
        try {
            ctx.setState(AsrSessionState.FINALIZING);
            CountDownLatch latch = new CountDownLatch(1);
            finalLatches.put(ctx.getSessionId(), latch);
            if (provider != null) {
                provider.finishSession(ctx);
            }
            // 等待 onClosed（Provider 产出全部最终结果）或超时
            finalized = latch.await(properties.getFinalWaitTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!finalized) {
                log.warn("等待 ASR 最终结果超时 callId:{} timeoutMs:{}", callId, properties.getFinalWaitTimeoutMs());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("等待 ASR 最终结果被中断 callId:{}", callId);
        } catch (Exception e) {
            log.warn("结束 ASR 会话异常(忽略) callId:{} err:{}", callId, e.getMessage());
        } finally {
            finishSummary(ctx, finalized, event);
            resultHandler.clear(ctx.getSessionId());
            finalLatches.remove(ctx.getSessionId());
            byCallId.remove(callId);
            if (ctx.getUniqueId() != null) {
                callIdByUuid.remove(ctx.getUniqueId());
            }
            providers.remove(ctx.getSessionId());
        }
    }

    /** 计算/落库结束状态。 */
    private void finishSummary(AsrSessionContext ctx, boolean finalized, CallHangupEvent event) {
        String status;
        if (ctx.getState() == AsrSessionState.ERROR) {
            status = IAsrTranscriptService.STATUS_FAILED;
        } else if (!finalized) {
            status = IAsrTranscriptService.STATUS_PARTIAL;
        } else {
            status = IAsrTranscriptService.STATUS_SUCCESS;
        }
        try {
            IAsrTranscriptService.FinalizeRequest req = IAsrTranscriptService.FinalizeRequest.of(status);
            req.endMs = event.getEndTime() == null ? System.currentTimeMillis() : event.getEndTime();
            req.reconnectCount = ctx.getReconnectCount();
            if (ctx.getState() == AsrSessionState.ERROR && ctx.getLastErrorCode() != null) {
                req.errorCode = ctx.getLastErrorCode();
            }
            req.recordUrl = event.getRecord();
            transcriptService.finalizeSummary(String.valueOf(ctx.getCallId()), req);
        } catch (Exception e) {
            log.warn("落库转写摘要失败(忽略) callId:{} err:{}", ctx.getCallId(), e.getMessage());
        }
        try {
            pushService.pushStatus(ctx, "idle", null, null);
        } catch (Exception ignored) {
            // 推送失败不影响落库
        }
    }

    /**
     * 处理 Provider 错误：可恢复错误有限重连，否则标记错误并停止当前转写。
     */
    private void handleProviderError(AsrSessionContext ctx, String code, String message) {
        if (ctx == null) {
            return;
        }
        synchronized (ctx) {
            if (ctx.getState() != null && ctx.getState().isTerminal()) {
                return;
            }
            boolean reconnectable = RECONNECTABLE.contains(code);
            if (reconnectable && ctx.getReconnectCount() < properties.getMaxReconnect()) {
                ctx.setReconnectCount(ctx.getReconnectCount() + 1);
                int attempt = ctx.getReconnectCount();
                log.warn("ASR 瞬时错误，尝试重连 callId:{} attempt:{}/{} code:{}",
                        ctx.getCallId(), attempt, properties.getMaxReconnect(), code);
                try {
                    transcriptService.updateReconnectCount(String.valueOf(ctx.getCallId()), attempt);
                    pushService.pushStatus(ctx, "connecting", code, message);
                    IAsrProvider provider = providers.get(ctx.getSessionId());
                    // 重连：取消旧云端会话再新建（去重游标在 resultHandler 保留，防文本重复）
                    if (provider != null) {
                        provider.cancelSession(ctx);
                        AsrResultCallback callback = buildCallback(ctx);
                        provider.createSession(ctx, callback);
                        ctx.setState(AsrSessionState.ACTIVE);
                        pushService.pushStatus(ctx, "active", null, null);
                    }
                    return;
                } catch (Exception e) {
                    log.error("ASR 重连失败 callId:{} err:{}", ctx.getCallId(), e.getMessage(), e);
                    // 落入下方错误终止
                }
            }
            // 不可恢复或重连耗尽：终止当前转写（不挂电话）
            ctx.markError(code == null ? AsrErrorCode.RECONNECT_EXHAUSTED : code);
            log.error("ASR 终止当前转写 callId:{} code:{} msg:{}", ctx.getCallId(), code, message);
            try {
                resultHandler.handleError(ctx, code, message);
            } catch (Exception ignored) {
            }
            try {
                IAsrProvider provider = providers.get(ctx.getSessionId());
                if (provider != null) {
                    provider.cancelSession(ctx);
                }
            } catch (Exception ignored) {
            }
            // 推送 unavailable，但保留会话上下文到挂断（便于统计/录音补偿）
            pushService.pushStatus(ctx, "unavailable", code, message);
        }
    }

    /**
     * 构建会话回调：结果→结果处理器；错误→重连/终止；关闭→释放最终等待闩。
     */
    private AsrResultCallback buildCallback(AsrSessionContext ctx) {
        return new AsrResultCallback() {
            @Override
            public void onResult(AsrRecognitionResult result) {
                resultHandler.handle(ctx, result);
            }

            @Override
            public void onError(String code, String message) {
                handleProviderError(ctx, code, message);
            }

            @Override
            public void onClosed() {
                CountDownLatch latch = finalLatches.get(ctx.getSessionId());
                if (latch != null) {
                    latch.countDown();
                }
            }
        };
    }

    /* ===== 测试/诊断可见 ===== */

    public AsrSessionContext getSession(Long callId) {
        return byCallId.get(callId);
    }

    public int activeCount() {
        return byCallId.size();
    }

    public Long callIdByUuid(String uniqueId) {
        return uniqueId == null ? null : callIdByUuid.get(uniqueId);
    }
}
