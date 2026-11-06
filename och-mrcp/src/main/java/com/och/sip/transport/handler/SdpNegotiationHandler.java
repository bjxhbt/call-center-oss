package com.och.sip.transport.handler;

import com.och.engine.AsrEngine;
import com.och.engine.EngineFactory;
import com.och.mrcp.MrcpSessionManager;
import com.och.sip.core.dialog.DialogManager;
import com.och.sip.core.dialog.SipDialog;
import com.och.sip.core.message.SipMessage;
import com.och.sip.core.transaction.TransactionManager;
import com.och.sip.sdp.SdpAnswer;
import com.och.sip.sdp.SdpMessage;
import com.och.sip.sdp.SdpParser;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SdpNegotiationHandler implements SipMessageHandler {
    @Override
    public void handle(ChannelHandlerContext ctx, SipMessage message, HandlerChain chain) {
        if (isSdpMessage(message)) {
            try {
                SdpAnswer sdpAnswer = new SdpAnswer();
                SdpParser.parse(message.getBody(), sdpAnswer);
                String callId = message.getCallId(); // 新增：从 SIP 消息获取 Call-ID
                // 2. 遍历所有媒体描述，分别处理 MRCP 和媒体会话
                for (SdpMessage.MediaDescription mediaDesc : sdpAnswer.getMediaDescriptions()) {
                    if (isMrcpMedia(mediaDesc)) {
                        // 处理 MRCP 控制通道
                        handleMrcpSession(mediaDesc, callId);
                    } else if (isAudioMedia(mediaDesc)) {
                        // 处理音频媒体通道
                        handleMediaSession(mediaDesc, callId);
                    }
                }
            } catch (Exception e) {
                log.error("SDP negotiation failed: {}", e.getMessage());
                return;
            }
        }
        chain.process(ctx, message);
    }

    // ================== 核心逻辑方法 ==================

    /**
     * 处理 MRCP 控制通道会话
     */
    private void handleMrcpSession(SdpMessage.MediaDescription mediaDesc, String callId) {
        String channelId = null;
        // 提取或生成 MRCP 会话 ID（通过 SDP 属性或自定义规则）
        if (!mediaDesc.getAttributes().get("channel").isEmpty()) {
            channelId = mediaDesc.getAttributes().get("channel").get(0);
        } else {
            channelId = System.currentTimeMillis() + "@speechrecog";
        }
        // 获取当前事务关联的 Dialog
        SipDialog dialog = DialogManager.getInstance().findDialogByCallId(callId);
        if (dialog != null) {
            dialog.bindMrcpSession(channelId);
            log.info("MRCP session {} bound to dialog {}", channelId, dialog.getCallId());
        } else {
            log.warn("No dialog found for call ID: {}", callId);
        }
    }

    /**
     * 处理音频媒体会话
     */
    private void handleMediaSession(SdpMessage.MediaDescription mediaDesc, String callId) {
        // RtpServer 的创建与绑定已移至 SDP 协商阶段
        // (AudioVideoStrategy.processAudio 创建 -> RtpServerRegistry 暂存 ->
        //  TransactionContext.sendFinalResponse 绑定到 dialog)，以修复时序不一致导致
        // dialog.getRtpServer() 为 null 的问题。歌处仅保留日志。
        log.debug("Audio media observed for callId={} port={}; RTP handled during SDP negotiation", callId, mediaDesc.getPort());
    }


    /**
     * 判断是否为 MRCP 媒体描述
     */
    private boolean isMrcpMedia(SdpMessage.MediaDescription md) {
        return "application".equals(md.getMediaType()) && "TCP/MRCPv2".equals(md.getProtocol());
    }

    /**
     * 判断是否为音频媒体描述
     */
    private boolean isAudioMedia(SdpMessage.MediaDescription md) {
        return "audio".equals(md.getMediaType());
    }


    private boolean isSdpMessage(SipMessage msg) {
        return "application/sdp".equals(msg.getHeader("Content-Type"));
    }


}
