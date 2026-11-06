package com.och.esl.handler.esl;

import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.AgentStateEnum;
import com.och.common.enums.DirectionEnum;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.service.IBridgeStateCache;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import org.apache.commons.lang3.StringUtils;
import java.util.Objects;

/**
 * 桥接调用
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.CHANNEL_BRIDGE)
@Component
public class FsChannelBridgeEslEventHandler extends AbstractFsEslEventHandler {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private IBridgeStateCache bridgeStateCache;

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("ChannelBridgeEslEventHandler EslEvent:{}.", JSONObject.toJSONString(event));
        String uniqueId = EslEventUtil.getUniqueId(event);
        String otherUniqueId = EslEventUtil.getOtherUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);
        if(Objects.isNull(callInfo)){
            return;
        }
        if (callInfo.getAnswerTime() == null || callInfo.getAnswerTime() == 0L) {
            callInfo.setAnswerTime(event.getEventDateTimestamp() / 1000);
        }
        log.info("桥接成功 callId:{}, uniqueId:{}, otherUniqueId:{}", callInfo.getCallId(), uniqueId, otherUniqueId);
        ChannelInfo channelInfo = callInfo.getChannelMap().get(uniqueId);
        ChannelInfo otherChannelInfo = callInfo.getChannelMap().get(otherUniqueId);
        if (channelInfo != null && channelInfo.getBridgeTime() == null) {
            channelInfo.setBridgeTime(event.getEventDateTimestamp() / 1000);
        }
        if (otherChannelInfo != null && otherChannelInfo.getBridgeTime() == null) {
            otherChannelInfo.setBridgeTime(event.getEventDateTimestamp() / 1000);
        }

        if (Objects.equals(callInfo.getDirection(), DirectionEnum.OUTBOUND.getType())) {
            fsClient.detectSpeech(address,callInfo.getCallId(),uniqueId);
            fsClient.detectSpeech(address,callInfo.getCallId(),otherUniqueId);
        }

        callInfo.setChannelInfoMap(uniqueId,channelInfo);
        callInfo.setChannelInfoMap(otherUniqueId,otherChannelInfo);
        ifsCallCacheService.saveCallInfo(callInfo);

        // 呼入弹屏已在坐席接听瞬间推送(FsChannelAnswerEslEventHandler),桥接处再推会二次弹窗;
        // 呼出场景接听方应答不经过坐席腿 answer,仍在此推送。
        if (Objects.equals(callInfo.getDirection(), DirectionEnum.OUTBOUND.getType())) {
            sendAgentStatus(callInfo.getCallId(),callInfo.getCaller(),callInfo.getCallee(),callInfo.getDirection(), AgentStateEnum.TALKING);
        }

        bridgeStateCache.markBridged(callInfo.getCallId());
        publishBridgeEvent(callInfo, uniqueId, otherUniqueId, event);
    }

    private void publishBridgeEvent(CallInfo callInfo, String uniqueId, String otherUniqueId, EslEvent event) {
        String recordingUniqueId = findChannelUniqueId(callInfo, uniqueId, otherUniqueId, 1);
        if (StringUtils.isBlank(recordingUniqueId)) {
            log.warn("真实桥接事件未找到主叫通道，跳过录音提示事件 callId:{} uniqueId:{} otherUniqueId:{}",
                    callInfo.getCallId(), uniqueId, otherUniqueId);
            return;
        }
        try {
            CallBridgeEvent bridgeEvent = CallBridgeEvent.builder()
                    .callId(callInfo.getCallId())
                    .uniqueId(uniqueId)
                    .otherUniqueId(otherUniqueId)
                    .recordingUniqueId(recordingUniqueId)
                    .caller(callInfo.getCaller())
                    .callerDisplay(callInfo.getCallerDisplay())
                    .callee(callInfo.getCallee())
                    .calleeDisplay(callInfo.getCalleeDisplay())
                    .agentId(callInfo.getAgentId())
                    .agentNumber(callInfo.getAgentNumber())
                    .agentName(callInfo.getAgentName())
                    .direction(callInfo.getDirection())
                    .bridgeTime(event.getEventDateTimestamp() / 1000)
                    .record(callInfo.getRecord())
                    .build();
            eventPublisher.publishEvent(bridgeEvent);
            log.info("真实桥接成功事件已发布 callId:{} callerUuid:{} agentUuid:{}",
                    callInfo.getCallId(), recordingUniqueId, findChannelUniqueId(callInfo, uniqueId, otherUniqueId, 2));
        } catch (Exception exception) {
            log.warn("发布真实桥接事件失败(忽略，不影响通话) callId:{} err:{}",
                    callInfo.getCallId(), exception.getMessage());
        }
    }

    private String findChannelUniqueId(CallInfo callInfo, String uniqueId, String otherUniqueId, int directionType) {
        if (callInfo == null || callInfo.getChannelMap() == null) {
            return null;
        }
        ChannelInfo current = callInfo.getChannelMap().get(uniqueId);
        if (current != null && Objects.equals(current.getDirectionType(), directionType)) {
            return uniqueId;
        }
        ChannelInfo other = callInfo.getChannelMap().get(otherUniqueId);
        if (other != null && Objects.equals(other.getDirectionType(), directionType)) {
            return otherUniqueId;
        }
        return callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> Objects.equals(channel.getDirectionType(), directionType))
                .map(ChannelInfo::getUniqueId)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
