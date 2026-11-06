package com.och.ivr.handler.route;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.och.common.annotation.EslRouteName;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.RouteTypeEnum;
import com.och.common.exception.FlowNodeException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.handler.route.FsAbstractRouteHandler;
import com.och.esl.service.AgentUnavailablePromptService;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.properties.FlowNodeProperties;
import com.och.ivr.properties.FlowTransferNodeProperties;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.service.ISipAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author danmo
 * @date 2024-12-31 17:20
 **/
@RequiredArgsConstructor
@Component
@Slf4j
public class FlowAgentRouteHandler {

    private final IFsCallCacheService fsCallCacheService;
    private final ISipAgentService iSipAgentService;
    private final FsClient fsClient;
    private final AgentUnavailablePromptService agentUnavailablePromptService;
    protected final IFlowNoticeService iFlowNoticeService;



    public void handler(FlowDataContext flowData, FlowTransferNodeProperties properties) {
        String address = flowData.getAddress();
        CallInfo callInfo = fsCallCacheService.getCallInfo(flowData.getCallId());
        if(Objects.isNull(callInfo)){
            throw new FlowNodeException("路由节点未查询到呼叫信息");
        }
        String agentId = properties.getRouteValue();
        SipAgentVo sipAgent = iSipAgentService.getInfoByAgent(agentId);
        if(Objects.isNull(sipAgent)){
            log.error("转坐席未查询到坐席信息 callId:{}  callerNumber:{} calleeNumber:{},agentId:{}", callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(),agentId);
            fsClient.hangupCall(address,callInfo.getCallId(),flowData.getUniqueId());
            iFlowNoticeService.notice(2, "end", flowData);
            return;
        }

        //坐席分机号码
        String calleeNumber = sipAgent.getAgentNumber();
        if(StringUtils.isEmpty(calleeNumber)){
            log.error("坐席未配置sip号码 callId:{}  callerNumber:{} calleeNumber:{},agentId:{}", callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(),agentId);
            fsClient.hangupCall(address,callInfo.getCallId(),flowData.getUniqueId());
            iFlowNoticeService.notice(2, "end", flowData);
            return;
        }
        if (!fsClient.isWebRtcUserRegistered(address, calleeNumber)) {
            log.warn("浏览器软电话未注册 callId:{} callerNumber:{} agentId:{} agentNumber:{}",
                    callInfo.getCallId(), callInfo.getCaller(), agentId, calleeNumber);
            agentUnavailablePromptService.playAndHangup(
                    address, callInfo, flowData.getUniqueId(), HangupCauseEnum.FULLBUSY);
            return;
        }
        String otherUniqueId = RandomUtil.randomNumbers(32);
        log.info("转坐席 callId:{}, agent:{}", callInfo.getCallId(), agentId);

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

        //构建被叫通道
        ChannelInfo otherChannelInfo = ChannelInfo.builder().callId(callInfo.getCallId()).uniqueId(otherUniqueId).cdrType(1).type(1)
                .directionType(2)
                .agentId(sipAgent.getId()).agentNumber(sipAgent.getAgentNumber()).agentName(sipAgent.getName())
                .callTime(DateUtil.current()).otherUniqueId(flowData.getUniqueId())
                .called(calleeNumber).caller(callInfo.getCaller()).display(callInfo.getCallerDisplay()).build();
        callInfo.setChannelInfoMap(otherUniqueId,otherChannelInfo);
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
        fsCallCacheService.saveCallRel(otherUniqueId,callInfo.getCallId());
        fsClient.makeWebRtcCall(address, callInfo.getCallId(), calleeNumber, callInfo.getCallerDisplay(),
                otherUniqueId, callInfo.getCalleeTimeOut(), failure -> {
                    log.error("转坐席呼叫失败 callId:{} callerNumber:{} calleeNumber:{} agentId:{} reason:{}",
                            callInfo.getCallId(), callInfo.getCaller(), calleeNumber, agentId, failure);
                    CallInfo currentCall = fsCallCacheService.getCallInfo(callInfo.getCallId());
                    fsCallCacheService.removeCallRel(otherUniqueId);
                    if (!isCurrentAttempt(currentCall, otherUniqueId, flowData.getUniqueId(), sipAgent.getId())) {
                        log.info("忽略过期坐席呼叫失败回调 callId:{} attempt:{}",
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
                            address, currentCall, flowData.getUniqueId(), HangupCauseEnum.FULLBUSY);
                });
        //iFlowNoticeService.notice(2, "next", flowData);
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
