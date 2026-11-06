
package com.och.esl.handler.esl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslConstant;
import com.och.common.constant.EslEventNames;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.AgentStateEnum;
import com.och.common.enums.DirectionEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.utils.StringUtils;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.handler.call.FsAbstractCallProcess;
import com.och.esl.utils.EslEventUtil;
import com.och.esl.utils.InitialCallDirectionResolver;
import com.och.system.domain.entity.CallDisplay;
import com.och.system.domain.entity.CorpInfo;
import com.och.system.domain.query.display.CallDisplayQuery;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.domain.vo.display.CallDisplayVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.och.common.enums.DirectionEnum.INBOUND;
import static com.och.common.enums.DirectionEnum.OUTBOUND;

/**
 * 话机振铃
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.CHANNEL_PARK)
@Component
public class FsChannelParkEslEventHandler extends AbstractFsEslEventHandler {

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("ChannelParkEslEventHandler EslEvent:{}.", JSONObject.toJSONString(event));
        if (EslConstant.OK.equals(EslEventUtil.getSipHangupPhrase(event))) {
            return;
        }
        String uniqueId = EslEventUtil.getUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);

        if (callInfo == null) {
            boolean inboundRouteMatched = hasInboundRoute(event);
            boolean knownAgent = isKnownAgent(event);
            InitialCallDirectionResolver.Decision decision = InitialCallDirectionResolver.resolve(
                    event.getEventHeaders(), inboundRouteMatched, knownAgent);
            log.info("初始呼叫方向 callId关联:{} caller:{} callee:{} profile:{} context:{} userAgent:{} inboundRoute:{} knownAgent:{} decision:{}",
                    EslEventUtil.getOchCallId(event),
                    EslEventUtil.getCallerCallerIdNumber(event),
                    EslEventUtil.getCallerDestinationNumber(event),
                    EslEventUtil.getSofiaProfileName(event),
                    EslEventUtil.getCallerContext(event),
                    EslEventUtil.getVariableSipUserAgent(event),
                    inboundRouteMatched,
                    knownAgent,
                    decision);
            if (decision == InitialCallDirectionResolver.Decision.INBOUND) {
                inboundCall(address, event);
                return;
            }
            if (decision == InitialCallDirectionResolver.Decision.OUTBOUND) {
                outboundCall(address, event);
                return;
            }
            if (decision == InitialCallDirectionResolver.Decision.CORRELATED) {
                log.info("初始通道已携带业务关联标识，等待后续事件完成关联 uniqueId:{}", uniqueId);
            }
            return;
        }

        if (Objects.isNull(callInfo) || callInfo.getHangupDir() != null) {
            return;
        }

        ChannelInfo channelInfo = callInfo.getChannelMap().get(uniqueId);
        if (Objects.isNull(channelInfo)) {
            return;
        }
        if (StringUtils.isNotEmpty(EslEventUtil.getSipHangupPhrase(event))) {
            return;
        }
        if (channelInfo.getAnswerTime() != null && channelInfo.getState() != null) {
            return;
        }
        channelInfo.setRingStartTime(event.getEventDateTimestamp() / 1000);
        if(Objects.equals(1,channelInfo.getDirectionType())){
            callInfo.setCallerRingStartTime(channelInfo.getRingStartTime());
        }
        if(Objects.equals(2,channelInfo.getDirectionType())){
            callInfo.setCalleeRingStartTime(channelInfo.getRingStartTime());
        }

        DirectionEnum directionEnum = DirectionEnum.getByType(callInfo.getDirection());
        if (directionEnum != null){
            switch (directionEnum){
                case INBOUND -> {
                    // 弹屏时机:坐席接听后(FsChannelAnswerEslEventHandler),振铃阶段一律不推。
                    // IVR 节点间、排队、满意度回 park、转接失败(繁忙/无坐席/非工作时间)都会
                    // 让腿 park——任何 park 推送都会造成误弹。工单由转人工入口静默落库。
                    log.info("Skip incoming-call push on park (ringing), callId:{}, uniqueId:{}, directionType:{}",
                            callInfo.getCallId(), uniqueId, channelInfo.getDirectionType());
                }
                case OUTBOUND -> {
                    sendAgentStatus(callInfo.getCallId(),callInfo.getCaller(),callInfo.getCallee(), OUTBOUND.getType(), AgentStateEnum.CALL_INT_RING);
                }
                default -> {
                }
            }
        }
        log.info("ChannelParkEslEventHandler after callId:{}, caller:{}, called:{}, uniqueId:{}", callInfo.getCallId(),callInfo.getCaller(), callInfo.getCallee(), uniqueId);
        callInfo.setChannelInfoMap(uniqueId,channelInfo);
        ifsCallCacheService.saveCallInfo(callInfo);

    }

    /**
     * 呼入
     * @param event
     */
    private void inboundCall(String address, EslEvent event) {
        Long callId = IdUtil.getSnowflakeNextId();
        String uniqueId = EslEventUtil.getUniqueId(event);
        String callerNumber = EslEventUtil.getCallerCallerIdNumber(event);
        String rawCalleeNumber = EslEventUtil.getCallerDestinationNumber(event);
        String calleeNumber = resolveInboundCallee(event);
        String sipContactUri = EslEventUtil.getSipContactUri(event);
        log.info("park >>>>>>>inbound callId:{}, caller:{}, called:{}, rawCalled:{}, uniqueId:{}, sipContactUri:{}",
                callId, callerNumber, calleeNumber, rawCalleeNumber, uniqueId, sipContactUri);
        String location = iPhoneLocationService.getLocation(callerNumber);
        //构建呼叫总线
        CallInfo callInfo = CallInfo.builder().callId(callId)
                .caller(callerNumber).callee(calleeNumber).calleeDisplay(calleeNumber).direction(INBOUND.getType())
                .callTime(DateUtil.current()).callerDisplay(callerNumber).numberLocation(location).routeType(2).build();
        callInfo.addUniqueIdList(uniqueId);
        //构建主叫通道
        ChannelInfo channelInfo = ChannelInfo.builder().callId(callId).uniqueId(uniqueId).cdrType(1).type(2).directionType(1).callTime(DateUtil.current())
                .caller(callerNumber).called(calleeNumber).display(calleeNumber).build();
        callInfo.setChannelInfoMap(uniqueId,channelInfo);


        callInfo.setProcess(ProcessEnum.CALL_ROUTE);

        ifsCallCacheService.saveCallInfo(callInfo);
        ifsCallCacheService.saveCallRel(uniqueId,callId);
        // 入站主叫腿也写入业务 callId。RECORD_STOP/挂机事件可能在 Redis 关系清理后到达，
        // FreeSWITCH 仍可通过该变量把录音和残留通道关联回同一条话单。
        fsClient.sendSyncMsg(address, "uuid_setvar", uniqueId + " och_call_id " + callId);
        fsClient.ringReady(address, uniqueId);
        FsAbstractCallProcess callProcess = fsEslProcessFactory.factory(callInfo.getProcess());
        if (callProcess == null) {
            log.error("Inbound call process not found, callId:{}, process:{}", callId, callInfo.getProcess());
            fsClient.hangupCall(address, callId, uniqueId);
            return;
        }
        try {
            callProcess.handler(address, event, callInfo, channelInfo);
        } catch (Exception e) {
            log.error("Inbound call route failed, callId:{}, caller:{}, callee:{}", callId, callerNumber, calleeNumber, e);
            fsClient.hangupCall(address, callId, uniqueId);
        }
    }

    /**
     * 呼出
     * @param address
     * @param event
     */
    private void outboundCall(String address,EslEvent event) {
        if (InitialCallDirectionResolver.hasExternalInboundEvidence(event.getEventHeaders())
                || hasInboundRoute(event)) {
            log.warn("外部来电被误判为外呼，已切回呼入流程 caller:{} callee:{}",
                    EslEventUtil.getCallerCallerIdNumber(event),
                    EslEventUtil.getCallerDestinationNumber(event));
            inboundCall(address, event);
            return;
        }
        String callerNumber = EslEventUtil.getCallerCallerIdNumber(event);
        String calleeNumber = EslEventUtil.getCallerDestinationNumber(event);
        String uniqueId = EslEventUtil.getUniqueId(event);
        Long callId = IdUtil.getSnowflakeNextId();
        SipAgentVo sipAgent = iSipAgentService.getInfoByAgent(callerNumber);
        if(Objects.isNull(sipAgent)){
            log.error("软电话外呼未查询到坐席信息 callId:{}  callerNumber:{} calleeNumber:{}", callId, callerNumber, calleeNumber);
            fsClient.hangupCall(address,callId,uniqueId);
            return;
        }
        String location = iPhoneLocationService.getLocation(callerNumber);
        //构建呼叫总线
        CallInfo callInfo = CallInfo.builder().callId(callId)
                .agentId(sipAgent.getId()).agentNumber(sipAgent.getAgentNumber()).agentName(sipAgent.getName())
                .caller(callerNumber).callee(calleeNumber).numberLocation(location).direction(OUTBOUND.getType())
                .callTime(DateUtil.current()).build();
        callInfo.addUniqueIdList(uniqueId);
        callInfo.setProcess(ProcessEnum.CALL_ROUTE);

        //获取被叫显号
        CallDisplayQuery displayQuery = new CallDisplayQuery();
        displayQuery.setType(2);
        List<CallDisplayVo> displayList = iCallDisplayService.getList(displayQuery);
        if(CollectionUtil.isEmpty(displayList)){
            log.error("电话外呼未查询到显号信息 callId:{}  callerNumber:{} calleeNumber:{}", callId, callerNumber, calleeNumber);
            fsClient.hangupCall(address,callId,uniqueId);
            return;
        }

        CallDisplayVo displaySimple = RandomUtil.randomEle(displayList);
        callInfo.setCallerDisplay(sipAgent.getAgentNumber());
        callInfo.setCalleeDisplay(displaySimple.getPhone());


        //构建主叫通道
        ChannelInfo channelInfo = ChannelInfo.builder().callId(callId).uniqueId(uniqueId).cdrType(2).type(1).directionType(1)
                .agentId(sipAgent.getId()).agentNumber(sipAgent.getAgentNumber()).agentName(sipAgent.getName())
                .callTime(DateUtil.current())
                .caller(callInfo.getCallee()).called(callInfo.getCaller()).display(callInfo.getCalleeDisplay()).build();
        callInfo.setChannelInfoMap(uniqueId,channelInfo);

        ifsCallCacheService.saveCallInfo(callInfo);
        ifsCallCacheService.saveCallRel(uniqueId,callId);
        fsClient.answer(address, uniqueId);
    }

    private boolean hasInboundRoute(EslEvent event) {
        for (String candidate : inboundRouteCandidates(event)) {
            try {
                if (ifsCallCacheService.getCallRoute(candidate, INBOUND.getType()) != null) {
                    return true;
                }
            } catch (Exception exception) {
                log.warn("查询呼入路由失败 candidate:{}", candidate, exception);
            }
        }
        return false;
    }

    private String resolveInboundCallee(EslEvent event) {
        Set<String> candidates = inboundRouteCandidates(event);
        for (String candidate : candidates) {
            try {
                if (ifsCallCacheService.getCallRoute(candidate, INBOUND.getType()) != null) {
                    return candidate;
                }
            } catch (Exception exception) {
                log.warn("解析呼入DID路由失败 candidate:{}", candidate, exception);
            }
        }
        return candidates.stream().findFirst().orElse(EslEventUtil.getCallerDestinationNumber(event));
    }

    private Set<String> inboundRouteCandidates(EslEvent event) {
        Set<String> candidates = new LinkedHashSet<>();
        addRouteCandidate(candidates, EslEventUtil.getCallerDestinationNumber(event));
        addUriUser(candidates, EslEventUtil.getCallerDestinationNumber(event));
        addRouteCandidate(candidates, event.getEventHeaders().get("variable_sip_req_user"));
        addUriUser(candidates, event.getEventHeaders().get("variable_sip_req_user"));
        addRouteCandidate(candidates, event.getEventHeaders().get("variable_sip_to_user"));
        addUriUser(candidates, event.getEventHeaders().get("variable_sip_to_user"));
        addRouteCandidate(candidates, event.getEventHeaders().get("variable_requested_user_name"));
        addRouteCandidate(candidates, event.getEventHeaders().get("variable_number_alias"));
        addUriUser(candidates, event.getEventHeaders().get("variable_sip_req_uri"));
        addUriUser(candidates, event.getEventHeaders().get("variable_sip_to_uri"));
        return candidates;
    }

    private boolean isKnownAgent(EslEvent event) {
        String callerNumber = EslEventUtil.getCallerCallerIdNumber(event);
        if (StringUtils.isBlank(callerNumber)) {
            return false;
        }
        try {
            return iSipAgentService.getInfoByAgent(callerNumber) != null;
        } catch (Exception exception) {
            log.warn("查询主叫坐席失败 caller:{}", callerNumber, exception);
            return false;
        }
    }

    private void addRouteCandidate(Set<String> candidates, String value) {
        if (StringUtils.isNotBlank(value)) {
            String normalized = value.trim();
            candidates.add(normalized);
            if (normalized.startsWith("+")) {
                candidates.add(normalized.substring(1));
            }
        }
    }

    private void addUriUser(Set<String> candidates, String uri) {
        if (StringUtils.isBlank(uri)) {
            return;
        }
        String value = uri.trim();
        int atIndex = value.indexOf('@');
        if (atIndex > 0) {
            value = value.substring(0, atIndex);
        }
        int colonIndex = value.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < value.length()) {
            value = value.substring(colonIndex + 1);
        }
        addRouteCandidate(candidates, value);
    }


}
