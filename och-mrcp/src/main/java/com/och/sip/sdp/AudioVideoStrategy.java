package com.och.sip.sdp;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.ListMultimap;
import com.och.config.MrcpConfig;
import com.och.mrcp.MrcpSession;
import com.och.mrcp.MrcpSessionManager;
import com.och.rtp.PortPoolManager;
import com.och.rtp.RtpServer;
import com.och.rtp.RtpServerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class AudioVideoStrategy implements SdpStrategy {

    private static final String MRCP_FORMAT = "1"; // MRCPv2 使用格式参数1

    @Override
    public SdpAnswer negotiate(String callId, SdpOffer offer) {
        SdpAnswer answer = new SdpAnswer();
        copyCommonFields(offer, answer);
        for (SdpMessage.MediaDescription md : offer.getMediaDescriptions()) {
            if ("application".equals(md.getMediaType()) && "TCP/MRCPv2".equals(md.getProtocol())) {
                // 处理MRCPv2
                SdpMessage.MediaDescription mrcpMd = processMrcp(callId,md);
                answer.addMediaDescription(mrcpMd);
            } else if ("audio".equals(md.getMediaType())) {
                // 处理音频编解码器
                SdpMessage.MediaDescription audioMd = processAudio(callId, md);
                answer.addMediaDescription(audioMd);
            }
        }
        return answer;
    }

    /**
     * 复制 SDP Offer 的通用字段到 Answer
     */
    private void copyCommonFields(SdpOffer offer, SdpAnswer answer) {
        // 复制基类 SdpMessage 的公共字段
        answer.setVersion(offer.getVersion());
        answer.setOrigin(offer.getOrigin());
        answer.setSessionName(offer.getSessionName());
        // 应答中的连接地址必须用本机（och-mrcp）可达 IP，不能反射 Offer 的地址
        // （Offer 里是 FreeSWITCH 的 IP，反射回去会导致客户端连不上 MRCP/RTP 通道）
        String advertiseIp = com.och.config.MrcpConfig.getAdvertiseIp();
        String offerConn = offer.getConnectionInfo();
        String answerConn = (offerConn != null && offerConn.contains("IP4"))
                ? offerConn.replaceAll("IP4\\s+\\S+", "IP4 " + advertiseIp)
                : "IN IP4 " + advertiseIp;
        answer.setConnectionInfo(answerConn);

        // 设置 Answer 特有的字段
        answer.setTimingInfo("0 0"); // 默认时间范围（t=0 0）

    }

    /**
     * 处理 MRCP 资源描述
     */
    private SdpMessage.MediaDescription processMrcp(String callId, SdpMessage.MediaDescription offerMd) {
        // 检查Offer是否支持MRCPv2
        List<String> offerFormats = offerMd.getFormats();
        if (!offerFormats.contains(MRCP_FORMAT)) {
            return null; // 不支持MRCPv2，拒绝该媒体流
        }
        String channelId = System.currentTimeMillis() + "@";
        if(CollectionUtil.isNotEmpty(offerMd.getAttributes().get("resource"))){
            channelId = channelId + offerMd.getAttributes().get("resource").get(0);;
        }
        // 生成Answer媒体描述
        SdpMessage.MediaDescription answerMd = new SdpMessage.MediaDescription();
        answerMd.setMediaType("application");
        answerMd.setPort(MrcpConfig.getServerPort());
        answerMd.setProtocol("TCP/MRCPv2");
        answerMd.setFormats(Collections.singletonList(MRCP_FORMAT));
        answerMd.addAttribute("setup", "passive");
        answerMd.addAttribute("connection", "new");
        answerMd.addAttribute("channel",channelId) ;
        answerMd.addAttribute("cmid", "1");
        MrcpSession session = MrcpSessionManager.getInstance().createSession(channelId);
        if(Objects.nonNull(session)){
            session.setCallId(callId);
        }
        return answerMd;
    }

    private SdpMessage.MediaDescription processAudio(String callId, SdpMessage.MediaDescription offerMd) {
        SdpMessage.MediaDescription answerMd = new SdpMessage.MediaDescription();
        answerMd.setMediaType("audio");
        int selectedPort = PortPoolManager.getInstance().allocatePort();
        answerMd.setPort(selectedPort);

        // SDP 协商分配本端 RTP 端口时，立即创建 RtpServer 并按 callId 暂存。
        // dialog 创建（sendFinalResponse）晚于此，故先缓存，再由 TransactionContext 绑定到 dialog。
        try {
            RtpServer rtpServer = new RtpServer(selectedPort);
            rtpServer.start();
            RtpServerRegistry.put(callId, rtpServer);
        } catch (Exception e) {
            log.error("Failed to start RtpServer on port {} for callId {}: {}", selectedPort, callId, e.getMessage());
        }

        answerMd.setProtocol(offerMd.getProtocol());

        // 获取支持的编解码器负载类型
        List<String> supportedPayloads = MrcpConfig.getCodecs().stream()
                .map(codec -> String.valueOf(codec.getPayloadType()))
                .toList();

        // 取交集（客户端Offer与服务器支持的编解码器）
        List<String> acceptedPayloads = offerMd.getFormats().stream()
                .filter(supportedPayloads::contains)
                .collect(Collectors.toList());

        // 添加telephone-event支持
        if (!acceptedPayloads.contains("101")) {
            acceptedPayloads.add("101");
        }

        answerMd.setFormats(acceptedPayloads);

        // 添加rtpmap属性
        MrcpConfig.getCodecs().forEach(codec -> {
            if (acceptedPayloads.contains(String.valueOf(codec.getPayloadType()))) {
                answerMd.addAttribute("rtpmap",
                        codec.getPayloadType() + " " + codec.getName() + "/" + codec.getRate()
                );
            }
        });
        answerMd.addAttribute("rtpmap", "101 telephone-event/8000");
        answerMd.addAttribute("fmtp", "101 0-15");
        ListMultimap<String, String> attributes = offerMd.getAttributes();
        if (CollectionUtil.isNotEmpty(attributes.get("recvonly"))){
            answerMd.addAttribute("sendonly", "");
        }else if (CollectionUtil.isNotEmpty(attributes.get("sendonly"))){
            answerMd.addAttribute("recvonly", "");
        }
        answerMd.addAttribute("ptime", "20");
        answerMd.addAttribute("mid", "1");

        return answerMd;
    }

}
