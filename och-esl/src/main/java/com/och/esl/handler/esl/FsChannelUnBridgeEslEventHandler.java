package com.och.esl.handler.esl;

import com.och.common.constant.FlowDataContext;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.ProcessEnum;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.service.IBridgeStateCache;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 终止桥接
 * uuid_bridge breaks hangup_after_bridge behavior - channels return to park instead of hanging up.
 * Handle cleanup here by explicitly hanging up all channels in the call.
 * @author danmo
 * @date 2023-09-18 19:03
 */
@Slf4j
@EslEventName(EslEventNames.CHANNEL_UNBRIDGE)
@Component
public class FsChannelUnBridgeEslEventHandler extends AbstractFsEslEventHandler {

    private final IBridgeStateCache bridgeStateCache;
    private final IFlowNoticeService flowNoticeService;

    public FsChannelUnBridgeEslEventHandler(IBridgeStateCache bridgeStateCache, IFlowNoticeService flowNoticeService) {
        this.bridgeStateCache = bridgeStateCache;
        this.flowNoticeService = flowNoticeService;
    }

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        String uniqueId = EslEventUtil.getUniqueId(event);
        String otherUniqueId = EslEventUtil.getOtherUniqueId(event);
        log.info("CHANNEL_UNBRIDGE: uuid={}, otherUuid={}", uniqueId, otherUniqueId);

        CallInfo callInfo = resolveCallInfo(event);
        if (callInfo == null) {
            return;
        }
        ChannelInfo unbridgedChannel = callInfo.getChannelMap().get(uniqueId);
        ChannelInfo peerChannel = callInfo.getChannelMap().get(otherUniqueId);
        FlowDataContext flowData = callInfo.getFlowDataContext();
        boolean bridgeEstablished = bridgeStateCache.isBridged(callInfo.getCallId())
                || hasActualBridgeEvidence(event)
                || (unbridgedChannel != null && unbridgedChannel.getBridgeTime() != null)
                || (peerChannel != null && peerChannel.getBridgeTime() != null);

        if (bridgeStateCache.isBridging(callInfo.getCallId()) && !bridgeEstablished) {
            log.info("桥接尚未建立，忽略解桥接清理 callId:{}, uuid:{}, otherUuid:{}",
                    callInfo.getCallId(), uniqueId, otherUniqueId);
            return;
        }

        boolean agentBridgeEnded = (unbridgedChannel != null && Objects.equals(unbridgedChannel.getDirectionType(), 2))
                || (peerChannel != null && Objects.equals(peerChannel.getDirectionType(), 2));

        // 坐席侧解桥接 + 仍在桥接/满意度状态 + 有流程上下文 => 进入满意度评价
        // 无论 markSatisfaction 返回 true/false 都要进入(挂断处理器可能已经标记过)
        if (agentBridgeEnded && flowData != null
                && (bridgeEstablished
                    || bridgeStateCache.isSatisfaction(callInfo.getCallId()))) {
            if (!bridgeStateCache.isBridged(callInfo.getCallId()) && hasActualBridgeEvidence(event)) {
                bridgeStateCache.markBridged(callInfo.getCallId());
            }
            boolean firstMark = bridgeStateCache.markSatisfaction(callInfo.getCallId());
            log.info("坐席解桥接，进入满意度评价 callId:{}, agentUuid:{}, isBridged:{}, isSatisfaction:{}, firstMark:{}",
                    callInfo.getCallId(), uniqueId,
                    bridgeStateCache.isBridged(callInfo.getCallId()),
                    bridgeStateCache.isSatisfaction(callInfo.getCallId()),
                    firstMark);
            if (!firstMark) {
                log.info("满意度流程已由挂机事件推进，忽略重复解桥接事件 callId:{}", callInfo.getCallId());
                return;
            }
            ChannelInfo callerChannel = callInfo.getChannelMap().values().stream()
                    .filter(Objects::nonNull)
                    .filter(channel -> Objects.equals(channel.getDirectionType(), 1))
                    .findFirst()
                    .orElse(null);
            if (callerChannel != null && StringUtils.isNotBlank(callerChannel.getUniqueId())) {
                fsClient.sendSyncMsg(address, "uuid_park", callerChannel.getUniqueId());
                // 仅挂断坐席侧通道，保留主叫通道继续满意度评价
                callInfo.getChannelMap().values().stream()
                        .filter(Objects::nonNull)
                        .filter(channel -> StringUtils.isNotBlank(channel.getUniqueId()))
                        .filter(channel -> !StringUtils.equals(channel.getUniqueId(), callerChannel.getUniqueId()))
                        .forEach(channel -> fsClient.killCall(address, callInfo.getCallId(), channel.getUniqueId()));
                flowData.setUniqueId(callerChannel.getUniqueId());
                callInfo.setProcess(ProcessEnum.CALL_ROUTE);
                callInfo.setFlowDataContext(flowData);
                ifsCallCacheService.saveCallInfo(callInfo);
                log.info("坐席通话结束，进入满意度 callId:{}, callerUuid:{}", callInfo.getCallId(), callerChannel.getUniqueId());
                flowNoticeService.notice(2, "next", flowData);
                return;
            }
            log.warn("进入满意度评价但未找到主叫通道 callId:{}, channelMap:{}", callInfo.getCallId(), callInfo.getChannelMap().keySet());
        }

        // 如果已经在满意度阶段，不做强制挂断
        if (bridgeStateCache.isSatisfaction(callInfo.getCallId())) {
            log.info("满意度评价阶段，跳过强制挂断 callId:{}, uuid:{}", callInfo.getCallId(), uniqueId);
            return;
        }

        // 仍在桥接状态 + 有流程上下文 => 不强制挂断，等待挂断处理器处理满意度逻辑
        if (flowData != null && bridgeEstablished) {
            log.info("桥接中解桥接，跳过强制挂断等待挂断处理器 callId:{}, uuid:{}", callInfo.getCallId(), uniqueId);
            return;
        }

        if (StringUtils.isNotBlank(otherUniqueId) && !StringUtils.equals(uniqueId, otherUniqueId)) {
            log.info("UNBRIDGE force kill peer: uuid={}, otherUuid={}", uniqueId, otherUniqueId);
            fsClient.killCall(address, null, otherUniqueId);
        }

        // Hang up all channels in this call explicitly
        for (ChannelInfo ch : callInfo.getChannelMap().values()) {
            if (ch == null || StringUtils.isBlank(ch.getUniqueId())) {
                continue;
            }
            log.info("UNBRIDGE kill: callId={}, uuid={}", callInfo.getCallId(), ch.getUniqueId());
            fsClient.killCall(address, callInfo.getCallId(), ch.getUniqueId());
        }
    }

    private boolean hasActualBridgeEvidence(EslEvent event) {
        if (event == null || event.getEventHeaders() == null) {
            return false;
        }
        String bridgedTime = firstNotBlank(
                event.getEventHeaders().get("Caller-Channel-Bridged-Time"),
                event.getEventHeaders().get("Other-Leg-Channel-Bridged-Time"),
                event.getEventHeaders().get("variable_bridge_stamp"),
                event.getEventHeaders().get("variable_bridge_epoch"),
                event.getEventHeaders().get("variable_bridge_uepoch"));
        if (StringUtils.isBlank(bridgedTime)) {
            return false;
        }
        String value = StringUtils.trim(bridgedTime);
        return !StringUtils.equals(value, "0") && !StringUtils.equals(value, "_undef_");
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
