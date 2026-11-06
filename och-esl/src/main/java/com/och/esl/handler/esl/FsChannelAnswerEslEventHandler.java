
package com.och.esl.handler.esl;

import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.ProcessEnum;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.handler.call.FsAbstractCallProcess;
import com.och.esl.service.CallRecordingService;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 渠道回答处理类
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.CHANNEL_ANSWER)
@Component
public class FsChannelAnswerEslEventHandler extends AbstractFsEslEventHandler {

    @org.springframework.beans.factory.annotation.Autowired
    private CallRecordingService callRecordingService;

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("ChannelAnswerEslEventHandler handle address:{} EslEvent:{}.", address, JSONObject.toJSONString(event));
        String uniqueId = EslEventUtil.getUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);
        if(Objects.isNull(callInfo)){
            return;
        }
        ChannelInfo channelInfo = callInfo.getChannelMap().get(uniqueId);
        if (channelInfo == null) {
            String otherUniqueId = findPeerUniqueId(callInfo, uniqueId);
            // Multi-contact fork race: this UUID's ChannelInfo was lost due to concurrent writes.
            // Create a new entry so subsequent events (bridge, hangup) can find it.
            channelInfo = ChannelInfo.builder()
                    .callId(callInfo.getCallId())
                    .uniqueId(uniqueId)
                    .cdrType(1).type(1).directionType(2)
                    .callTime(System.currentTimeMillis())
                    .called(event.getEventHeaders().get("Caller-Destination-Number"))
                    .caller(event.getEventHeaders().get("Caller-Caller-ID-Number"))
                    .otherUniqueId(otherUniqueId)
                    .build();
            callInfo.setChannelInfoMap(uniqueId, channelInfo);
            callInfo.addUniqueIdList(uniqueId);
            ifsCallCacheService.saveCallRel(uniqueId, callInfo.getCallId());
            linkPeerChannel(callInfo, otherUniqueId, uniqueId);
            log.info("Created missing ChannelInfo for forked channel: callId={}, uuid={}", callInfo.getCallId(), uniqueId);
        } else if (StringUtils.isBlank(channelInfo.getOtherUniqueId())) {
            String otherUniqueId = findPeerUniqueId(callInfo, uniqueId);
            if (StringUtils.isNotBlank(otherUniqueId)) {
                channelInfo.setOtherUniqueId(otherUniqueId);
                callInfo.setChannelInfoMap(uniqueId, channelInfo);
                linkPeerChannel(callInfo, otherUniqueId, uniqueId);
            }
        }
        long answerTime = event.getEventDateTimestamp() > 0
                ? event.getEventDateTimestamp() / 1000
                : System.currentTimeMillis();
        boolean agentLeg = Objects.equals(channelInfo.getDirectionType(), 2);
        if (channelInfo.getAnswerTime() != null) {
            if (agentLeg && (callInfo.getAnswerTime() == null || callInfo.getAnswerTime() == 0L)) {
                // 话单的接通时间代表坐席实际接听，不把 IVR 主叫腿的 answer 当成接通。
                callInfo.setAnswerTime(channelInfo.getAnswerTime());
                if (callInfo.getAnswerCount() == null) {
                    callInfo.setAnswerCount(1);
                }
                ifsCallCacheService.saveCallInfo(callInfo);
            }
            log.info("Skip duplicate channel answer event, callId:{}, uniqueId:{}", callInfo.getCallId(), uniqueId);
            return;
        }
        channelInfo.setAnswerTime(answerTime);
        channelInfo.setRingEndTime(answerTime);
        if (agentLeg && (callInfo.getAnswerTime() == null || callInfo.getAnswerTime() == 0L)) {
            // 仅记录坐席腿的接听，避免“进入 IVR 即接通”把未转人工电话算入接通率。
            callInfo.setAnswerTime(answerTime);
        }
        callInfo.setAnswerCount(Objects.requireNonNullElse(callInfo.getAnswerCount(), 0) + 1);
        callInfo.setChannelInfoMap(uniqueId, channelInfo);
        ifsCallCacheService.saveCallInfo(callInfo);

        if (agentLeg && Objects.equals(com.och.common.enums.DirectionEnum.INBOUND.getType(), callInfo.getDirection())) {
            boolean recordingStarted = callRecordingService.startRecordingOnAgentAnswer(address, callInfo, uniqueId);
            log.info("坐席接听时启动呼入录音 callId:{} agentUuid:{} started:{}",
                    callInfo.getCallId(), uniqueId, recordingStarted);
        }

        if (Objects.equals(channelInfo.getDirectionType(), 2)
                && Objects.equals(com.och.common.enums.DirectionEnum.INBOUND.getType(), callInfo.getDirection())) {
            log.info("Agent answered inbound call, defer work-order popup until call end, callId:{}, agentUuid:{}",
                    callInfo.getCallId(), uniqueId);
        }

        if (Objects.equals(ProcessEnum.CALL_BRIDGE, callInfo.getProcess())
                && !Objects.equals(channelInfo.getDirectionType(), 2)) {
            log.info("Skip duplicate bridge trigger from caller leg, callId:{}, uniqueId:{}, directionType:{}",
                    callInfo.getCallId(), uniqueId, channelInfo.getDirectionType());
            return;
        }

        FsAbstractCallProcess factory = fsEslProcessFactory.factory(callInfo.getProcess());
        if (factory == null) {
            log.error("Answer process not found, callId:{}, process:{}", callInfo.getCallId(), callInfo.getProcess());
            return;
        }
        try {
            factory.handler(address,event,callInfo,channelInfo);
        } catch (Exception e) {
            log.error("应答处理类执行异常 callId:{}", callInfo.getCallId(), e);
        }
    }

    private String findPeerUniqueId(CallInfo callInfo, String uniqueId) {
        if (callInfo.getChannelMap() == null || callInfo.getChannelMap().isEmpty()) {
            return null;
        }
        String relatedUniqueId = callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .map(ChannelInfo::getOtherUniqueId)
                .filter(otherUniqueId -> isUsablePeer(callInfo, otherUniqueId, uniqueId))
                .findFirst()
                .orElse(null);
        if (StringUtils.isNotBlank(relatedUniqueId)) {
            return relatedUniqueId;
        }
        String inboundUniqueId = callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> Objects.equals(channel.getDirectionType(), 1))
                .map(ChannelInfo::getUniqueId)
                .filter(otherUniqueId -> isUsablePeer(callInfo, otherUniqueId, uniqueId))
                .findFirst()
                .orElse(null);
        if (StringUtils.isNotBlank(inboundUniqueId)) {
            return inboundUniqueId;
        }
        return callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .map(ChannelInfo::getUniqueId)
                .filter(otherUniqueId -> isUsablePeer(callInfo, otherUniqueId, uniqueId))
                .filter(otherUniqueId -> !isGeneratedUuid(otherUniqueId))
                .findFirst()
                .orElseGet(() -> callInfo.getChannelMap().values().stream()
                        .filter(Objects::nonNull)
                        .map(ChannelInfo::getUniqueId)
                        .filter(otherUniqueId -> isUsablePeer(callInfo, otherUniqueId, uniqueId))
                        .findFirst()
                        .orElse(null));
    }

    private boolean isUsablePeer(CallInfo callInfo, String otherUniqueId, String uniqueId) {
        return StringUtils.isNotBlank(otherUniqueId)
                && !StringUtils.equals(otherUniqueId, uniqueId)
                && callInfo.getChannelMap().containsKey(otherUniqueId);
    }

    private void linkPeerChannel(CallInfo callInfo, String otherUniqueId, String uniqueId) {
        if (StringUtils.isBlank(otherUniqueId)) {
            return;
        }
        ChannelInfo peerChannel = callInfo.getChannelMap().get(otherUniqueId);
        if (peerChannel != null && StringUtils.isBlank(peerChannel.getOtherUniqueId())) {
            peerChannel.setOtherUniqueId(uniqueId);
            callInfo.setChannelInfoMap(otherUniqueId, peerChannel);
        }
    }

    private boolean isGeneratedUuid(String uniqueId) {
        return StringUtils.length(uniqueId) == 32 && StringUtils.isNumeric(uniqueId);
    }
}
