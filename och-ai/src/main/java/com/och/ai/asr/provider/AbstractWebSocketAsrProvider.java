package com.och.ai.asr.provider;

import com.och.ai.asr.AsrErrorCode;
import com.och.ai.asr.AsrException;
import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrRecognitionResult;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 JDK {@link java.net.http.WebSocket} 的云 ASR Provider 公共基类（方案 7.7 / 7.8 / 7.9）。
 *
 * <p>统一处理：建立长连接、二进制分片发送 PCM、文本结果分发、关闭/异常回调。
 * 子类只实现差异：{@link #buildUri}（鉴权 URL）、{@link #buildStartFrame}（首帧配置 JSON）、
 * {@link #parseTextMessage}（结果解析）。</p>
 *
 * <p>使用 JDK 内置 WebSocket，不引入额外 Netty/SDK 依赖。所有真实云 Provider 均受
 * {@link #ready()} 守卫：缺凭证时 ready=false，注册表不选用，自动降级 Mock（方案 7.15）。</p>
 *
 * <p>协议依据各云公开文档实现；真实联调受凭证/网络限制，列为可选集成测试，不在仓库内置凭证。</p>
 */
@Slf4j
public abstract class AbstractWebSocketAsrProvider implements IAsrProvider {

    protected final AsrProperties properties;

    /** sessionId → WebSocket。 */
    protected final ConcurrentHashMap<String, java.net.http.WebSocket> sockets = new ConcurrentHashMap<>();
    /** sessionId → 回调。 */
    protected final ConcurrentHashMap<String, AsrResultCallback> callbacks = new ConcurrentHashMap<>();
    /** sessionId → 上下文。 */
    protected final ConcurrentHashMap<String, AsrSessionContext> contexts = new ConcurrentHashMap<>();

    protected AbstractWebSocketAsrProvider(AsrProperties properties) {
        this.properties = properties;
    }

    @Override
    public abstract AsrProviderType type();

    @Override
    public abstract boolean ready();

    /** 构造鉴权后的 WebSocket URI。 */
    protected abstract URI buildUri(AsrSessionContext ctx) throws Exception;

    /** 连接建立后发送的首帧配置 JSON；返回 null 表示无需首帧（如直接发二进制）。 */
    protected abstract String buildStartFrame(AsrSessionContext ctx);

    /** 结束信号帧；返回 null 表示用关闭连接方式结束。 */
    protected abstract String buildEndFrame(AsrSessionContext ctx);

    /** 解析一条文本消息为识别结果并回调。 */
    protected abstract void parseTextMessage(AsrSessionContext ctx, AsrResultCallback callback, String text);

    @Override
    public String createSession(AsrSessionContext ctx, AsrResultCallback callback) {
        if (!ready()) {
            throw new AsrException(AsrErrorCode.PROVIDER_NOT_READY, type() + " 未就绪（缺凭证或未启用）");
        }
        callbacks.put(ctx.getSessionId(), callback);
        contexts.put(ctx.getSessionId(), ctx);
        try {
            URI uri = buildUri(ctx);
            java.net.http.WebSocket.Listener listener = buildListener(ctx, callback);
            java.net.http.WebSocket socket = java.net.http.HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .buildAsync(uri, listener)
                    .get(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
            sockets.put(ctx.getSessionId(), socket);
            ctx.setCloudSessionId(uri.toString());
            String start = buildStartFrame(ctx);
            if (start != null && !start.isBlank()) {
                socket.sendText(start, true);
            }
            log.info("[{}] 已建立 ASR 长连接 callId:{} uri:{}", type(), ctx.getCallId(), redact(uri));
            return ctx.getCloudSessionId();
        } catch (Exception e) {
            log.error("[{}] 建立 ASR 连接失败 callId:{} err:{}", type(), ctx.getCallId(), e.getMessage());
            cleanup(ctx.getSessionId());
            callback.onError(AsrErrorCode.CONNECTION_BROKEN, "connect failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void sendAudio(AsrSessionContext ctx, byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        java.net.http.WebSocket socket = sockets.get(ctx.getSessionId());
        if (socket == null) {
            return;
        }
        try {
            socket.sendBinary(ByteBuffer.wrap(pcm), true);
        } catch (Exception e) {
            log.warn("[{}] 发送音频失败 callId:{} err:{}", type(), ctx.getCallId(), e.getMessage());
            callbacks.get(ctx.getSessionId()).onError(AsrErrorCode.SEND_FAILED, e.getMessage());
        }
    }

    @Override
    public void finishSession(AsrSessionContext ctx) {
        java.net.http.WebSocket socket = sockets.get(ctx.getSessionId());
        AsrResultCallback callback = callbacks.get(ctx.getSessionId());
        try {
            if (socket != null) {
                String end = buildEndFrame(ctx);
                if (end != null && !end.isBlank()) {
                    socket.sendText(end, true);
                }
                // 关闭连接触发服务端 flush 最终结果；onClose 回调 onClosed 释放最终等待闩
                socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "finish");
            }
        } catch (Exception e) {
            log.warn("[{}] 结束会话异常 callId:{} err:{}", type(), ctx.getCallId(), e.getMessage());
            cleanup(ctx.getSessionId());
            if (callback != null) {
                callback.onClosed();
            }
        }
    }

    @Override
    public void cancelSession(AsrSessionContext ctx) {
        java.net.http.WebSocket socket = sockets.remove(ctx.getSessionId());
        try {
            if (socket != null) {
                socket.abort();
            }
        } catch (Exception ignored) {
            // ignore
        }
        cleanup(ctx.getSessionId());
    }

    protected void cleanup(String sessionId) {
        sockets.remove(sessionId);
        callbacks.remove(sessionId);
        contexts.remove(sessionId);
    }

    /** 组装 WebSocket 监听器：文本→解析；关闭→onClosed；错误→onError。 */
    protected java.net.http.WebSocket.Listener buildListener(AsrSessionContext ctx, AsrResultCallback callback) {
        return new java.net.http.WebSocket.Listener() {
            final StringBuilder buf = new StringBuilder();

            @Override
            public void onOpen(java.net.http.WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                buf.append(data);
                if (last) {
                    String msg = buf.toString();
                    buf.setLength(0);
                    try {
                        parseTextMessage(ctx, callback, msg);
                    } catch (Exception e) {
                        log.warn("[{}] 解析结果失败 callId:{} err:{}", type(), ctx.getCallId(), e.getMessage());
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                log.warn("[{}] WS 错误 callId:{} err:{}", type(), ctx.getCallId(), error.getMessage());
                callback.onError(AsrErrorCode.CONNECTION_BROKEN, error.getMessage());
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
                cleanup(ctx.getSessionId());
                callback.onClosed();
                return null;
            }
        };
    }

    /** 子类解析后调用：组装统一结果并回调。 */
    protected void emit(AsrSessionContext ctx, AsrResultCallback callback, AsrSegment segment, boolean firstText, Long finalLatencyMs) {
        long now = System.currentTimeMillis();
        AsrRecognitionResult.AsrRecognitionResultBuilder b = AsrRecognitionResult.builder()
                .provider(type())
                .sessionId(ctx.getSessionId())
                .callId(ctx.getCallId())
                .uniqueId(ctx.getUniqueId())
                .segment(segment)
                .success(true)
                .receivedAt(now);
        if (firstText && ctx.getFirstTextAt() == 0) {
            ctx.setFirstTextAt(now);
            b.firstTextLatencyMs(now - ctx.getFirstAudioSentAt());
        }
        if (finalLatencyMs != null) {
            b.finalLatencyMs(finalLatencyMs);
        } else if (segment.isFinalResult()) {
            b.finalLatencyMs(now - ctx.getFirstAudioSentAt());
        }
        callback.onResult(b.build());
    }

    /** 解析 JSON 工具。 */
    protected JSONObject parse(String text) {
        return JSONObject.parseObject(text);
    }

    /** 隐藏 URI 中可能的敏感签名片段，避免日志泄露。 */
    protected String redact(URI uri) {
        if (uri == null) {
            return null;
        }
        String s = uri.toString();
        int idx = s.indexOf("signature=");
        if (idx > 0) {
            return s.substring(0, idx) + "signature=***";
        }
        return s;
    }
}
