package com.och.ai.asr.push;

import com.alibaba.fastjson2.JSONObject;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.websocket.domain.WsEventEnum;
import com.och.websocket.domain.WsMsgPayload;
import com.och.websocket.service.IWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ASR 结果 WebSocket 推送（方案 9.5 / 9.7）。
 *
 * <p>复用平台统一 {@link WsMsgPayload}（{sessionId,timestamp,event,data}），事件名与前端
 * {@code CallSyncClient} 路由一致：{@code TRANSCRIPT_PARTIAL}/{@code TRANSCRIPT_FINAL}/{@code TRANSCRIPT_STATUS}。
 * 推送目标：坐席 agentId → Redis sessionId → WS session；坐席未连接时静默跳过，绝不抛异常。</p>
 *
 * <p>data 载荷字段与前端 {@code toSegment} 解析对齐：callId、segmentId、seq、text、speaker、timestamp、confidence。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrWsPushService {

    private final IWebSocketService webSocketService;

    /** 推送一条临时增量结果。 */
    public void pushPartial(AsrSessionContext ctx, AsrSegment segment) {
        push(ctx, WsEventEnum.TRANSCRIPT_PARTIAL, segmentPayload(ctx, segment, false));
    }

    /** 推送一条最终结果。 */
    public void pushFinal(AsrSessionContext ctx, AsrSegment segment) {
        push(ctx, WsEventEnum.TRANSCRIPT_FINAL, segmentPayload(ctx, segment, true));
    }

    /**
     * 推送会话状态/异常。status ∈ active/connecting/idle/unavailable/error。
     */
    public void pushStatus(AsrSessionContext ctx, String status, String code, String message) {
        if (ctx == null) {
            return;
        }
        JSONObject data = new JSONObject();
        data.put("callId", String.valueOf(ctx.getCallId()));
        if (ctx.getAgentId() != null) {
            data.put("agentId", ctx.getAgentId());
        }
        data.put("status", status);
        if (code != null) {
            data.put("code", code);
        }
        if (message != null) {
            data.put("message", message);
        }
        push(ctx, WsEventEnum.TRANSCRIPT_STATUS, data);
    }

    private JSONObject segmentPayload(AsrSessionContext ctx, AsrSegment segment, boolean isFinal) {
        JSONObject data = new JSONObject();
        data.put("callId", String.valueOf(ctx.getCallId()));
        data.put("segmentId", segment.getSegmentId());
        if (segment.getSequence() != null) {
            data.put("seq", segment.getSequence());
        }
        data.put("text", segment.getText() == null ? "" : segment.getText());
        data.put("isFinal", isFinal);
        if (segment.getSpeaker() != null) {
            data.put("speaker", segment.getSpeaker().code());
        }
        data.put("timestamp", System.currentTimeMillis());
        if (segment.getConfidence() != null) {
            data.put("confidence", segment.getConfidence());
        }
        return data;
    }

    private void push(AsrSessionContext ctx, WsEventEnum event, Object data) {
        if (ctx == null || ctx.getAgentId() == null) {
            // 无坐席（如模拟器/离线补偿）时静默跳过，不阻塞转写主流程
            return;
        }
        try {
            String sessionKey = webSocketService.getSessionId(String.valueOf(ctx.getAgentId()));
            if (sessionKey == null || sessionKey.isBlank()) {
                log.debug("坐席未连接 WS，跳过推送 agentId:{} callId:{} event:{}",
                        ctx.getAgentId(), ctx.getCallId(), event);
                return;
            }
            WsMsgPayload payload = WsMsgPayload.builder()
                    .sessionId(sessionKey)
                    .timestamp(System.currentTimeMillis())
                    .event(event)
                    .data(data instanceof String s ? s : JSONObject.toJSONString(data))
                    .build();
            webSocketService.sendMsg(sessionKey, payload.toString());
        } catch (Exception e) {
            // 推送失败绝不影响转写与通话（方案 7.15）
            log.warn("ASR 推送失败(忽略) callId:{} event:{} err:{}", ctx.getCallId(), event, e.getMessage());
        }
    }
}
