package com.och.rtp;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RTP server 暂存注册表。
 * 在 SDP 协商期间（AudioVideoStrategy.processAudio）创建 RtpServer 并按 callId 暂存，
 * 待 SipDialog 创建后（TransactionContext.sendFinalResponse）再绑定到 dialog 上。
 * 解决 RtpServer 创建与 dialog 创建时序不一致导致 getRtpServer() 返回 null 的问题。
 */
@Slf4j
public final class RtpServerRegistry {

    private static final ConcurrentHashMap<String, RtpServer> PENDING = new ConcurrentHashMap<>();

    private RtpServerRegistry() {
    }

    /**
     * 按 callId 暂存尚未绑定到 dialog 的 RtpServer。
     * 若同一 callId 已存在暂存实例，先关闭旧的，避免端口泄漏。
     */
    public static void put(String callId, RtpServer rtpServer) {
        if (callId == null || rtpServer == null) {
            return;
        }
        RtpServer old = PENDING.put(callId, rtpServer);
        if (old != null && old != rtpServer) {
            try {
                old.stop();
            } catch (Exception e) {
                log.warn("Failed to stop stale RtpServer for callId {}: {}", callId, e.getMessage());
            }
        }
    }

    /**
     * 取出并移除暂存的 RtpServer（dialog 创建后调用）。
     */
    public static RtpServer remove(String callId) {
        if (callId == null) {
            return null;
        }
        return PENDING.remove(callId);
    }
}
