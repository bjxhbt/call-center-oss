package com.och.esl.handler.route;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.och.common.annotation.EslRouteName;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.RouteTypeEnum;
import com.och.common.utils.StringUtils;
import com.och.esl.service.AgentUnavailablePromptService;
import com.och.system.domain.vo.agent.SipAgentVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@EslRouteName(RouteTypeEnum.AGENT)
@Component
@Slf4j
public class FsAgentRouteHandler extends FsAbstractRouteHandler {

    @Autowired
    private AgentUnavailablePromptService agentUnavailablePromptService;

    @Override
    public void handler(String address, CallInfo callInfo, String uniqueId, String agentId) {
        SipAgentVo sipAgent = iSipAgentService.getDetail(Long.valueOf(agentId));
        if (Objects.isNull(sipAgent)) {
            log.error("Agent not found, callId:{}, caller:{}, callee:{}, agentId:{}",
                    callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(), agentId);
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }

        String calleeNumber = sipAgent.getAgentNumber();
        if (StringUtils.isEmpty(calleeNumber)) {
            log.error("Agent SIP number is empty, callId:{}, caller:{}, callee:{}, agentId:{}",
                    callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(), agentId);
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }

        if (!fsClient.isWebRtcUserRegistered(address, calleeNumber)) {
            log.warn("Browser softphone is not registered, callId:{}, agentId:{}, agentNumber:{}",
                    callInfo.getCallId(), agentId, calleeNumber);
            agentUnavailablePromptService.playAndHangup(
                    address, callInfo, uniqueId, HangupCauseEnum.FULLBUSY);
            return;
        }

        String otherUniqueId = RandomUtil.randomNumbers(32);
        log.info("Route call to agent, callId:{}, agentId:{}, agentNumber:{}", callInfo.getCallId(), agentId, calleeNumber);

        String previousCallee = callInfo.getCallee();
        Long previousAgentId = callInfo.getAgentId();
        String previousAgentNumber = callInfo.getAgentNumber();
        String previousAgentName = callInfo.getAgentName();
        ProcessEnum previousProcess = callInfo.getProcess();
        Long previousQueueEndTime = callInfo.getQueueEndTime();
        callInfo.setCallee(calleeNumber);
        callInfo.setAgentId(sipAgent.getId());
        callInfo.setAgentName(sipAgent.getName());
        callInfo.setAgentNumber(sipAgent.getAgentNumber());

        ChannelInfo otherChannelInfo = ChannelInfo.builder()
                .callId(callInfo.getCallId())
                .uniqueId(otherUniqueId)
                .cdrType(1)
                .type(1)
                .directionType(2)
                .agentId(sipAgent.getId())
                .agentNumber(sipAgent.getAgentNumber())
                .agentName(sipAgent.getName())
                .callTime(DateUtil.current())
                .otherUniqueId(uniqueId)
                .called(calleeNumber)
                .caller(callInfo.getCaller())
                .display(callInfo.getCallerDisplay())
                .build();
        callInfo.setChannelInfoMap(otherUniqueId, otherChannelInfo);
        callInfo.addUniqueIdList(otherUniqueId);
        callInfo.setProcess(ProcessEnum.CALL_BRIDGE);

        CallInfoDetail detail = new CallInfoDetail();
        detail.setCallId(callInfo.getCallId());
        detail.setStartTime(DateUtil.current());
        detail.setOrderNum(callInfo.getDetailList() == null ? 0 : callInfo.getDetailList().size() + 1);
        detail.setTransferType(1);

        detail.setEndTime(DateUtil.current());
        callInfo.addDetailList(detail);
        fsCallCacheService.saveCallInfo(callInfo);
        fsCallCacheService.saveCallRel(otherUniqueId, callInfo.getCallId());
        fsClient.makeWebRtcCall(address, callInfo.getCallId(), calleeNumber, callInfo.getCallerDisplay(),
                otherUniqueId, callInfo.getCalleeTimeOut(), failure -> {
                    log.error("Internal agent originate failed, callId:{}, agentId:{}, agentNumber:{}, reason:{}",
                            callInfo.getCallId(), agentId, calleeNumber, failure);
                    CallInfo currentCall = fsCallCacheService.getCallInfo(callInfo.getCallId());
                    fsCallCacheService.removeCallRel(otherUniqueId);
                    if (!isCurrentAttempt(currentCall, otherUniqueId, uniqueId, sipAgent.getId())) {
                        log.info("Ignore stale agent originate failure, callId:{}, attempt:{}",
                                callInfo.getCallId(), otherUniqueId);
                        return;
                    }
                    currentCall.removeChannelInfoMap(otherUniqueId);
                    currentCall.removeUniqueIdList(otherUniqueId);
                    currentCall.setCallee(previousCallee);
                    currentCall.setAgentId(previousAgentId);
                    currentCall.setAgentNumber(previousAgentNumber);
                    currentCall.setAgentName(previousAgentName);
                    currentCall.setProcess(previousProcess);
                    currentCall.setQueueEndTime(previousQueueEndTime);
                    fsCallCacheService.saveCallInfo(currentCall);
                    agentUnavailablePromptService.playAndHangup(
                            address, currentCall, uniqueId, HangupCauseEnum.FULLBUSY);
                });
    }

    private boolean isCurrentAttempt(CallInfo callInfo, String attemptUniqueId,
                                     String callerUniqueId, Long agentId) {
        ChannelInfo attemptChannel = callInfo == null || callInfo.getChannelMap() == null
                ? null : callInfo.getChannelMap().get(attemptUniqueId);
        return callInfo != null
                && attemptChannel != null
                && Objects.equals(attemptChannel.getOtherUniqueId(), callerUniqueId)
                && Objects.equals(callInfo.getProcess(), ProcessEnum.CALL_BRIDGE)
                && Objects.equals(callInfo.getAgentId(), agentId);
    }
}
