package com.och.esl.factory;

import com.alibaba.fastjson2.JSONObject;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.AgentStateEnum;
import com.och.common.enums.DirectionEnum;
import com.och.esl.client.FsClient;
import com.och.esl.service.AgentTransferScheduleService;
import com.och.esl.service.IFsCallCacheService;
import com.och.esl.utils.EslEventUtil;
import com.och.system.service.ICallDisplayService;
import com.och.system.service.ICallRecordService;
import com.och.system.service.IPhoneLocationService;
import com.och.system.service.ISipAgentService;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.websocket.domain.WsEventEnum;
import com.och.websocket.domain.WsMsgPayload;
import com.och.websocket.service.IWebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author danmo
 * @date 2023年09月28日 14:21
 */
@Slf4j
@Component
public abstract class AbstractFsEslEventHandler implements FsEslEventHandler {

    private static final Object[] CALL_LOCKS = new Object[64];

    static {
        for (int index = 0; index < CALL_LOCKS.length; index++) {
            CALL_LOCKS[index] = new Object();
        }
    }

    public final String JSSIP_SIP_USER_AGENT = "JsSIP";

    @Autowired
    protected IFsCallCacheService ifsCallCacheService;

    @Autowired
    protected FsEslProcessFactory fsEslProcessFactory;

    @Autowired
    protected ISipAgentService iSipAgentService;

    @Autowired
    protected ICallDisplayService iCallDisplayService;

    @Autowired
    protected RedisService redisService;

    @Lazy
    @Autowired
    protected FsClient fsClient;

    @Autowired
    protected ICallRecordService iCallRecordService;
    @Autowired
    protected IPhoneLocationService iPhoneLocationService;

    @Autowired
    protected IWebSocketService webSocketService;

    @Autowired
    protected AgentTransferScheduleService agentTransferScheduleService;


    @Override
    public abstract void handleEslEvent(String address, EslEvent event);

    protected CallInfo resolveCallInfo(EslEvent event) {
        String uniqueId = EslEventUtil.getUniqueId(event);
        if (StringUtils.isBlank(uniqueId)) {
            return null;
        }
        CallInfo callInfo = ifsCallCacheService.getCallInfoByUniqueId(uniqueId);
        String eventCallId = EslEventUtil.getOchCallId(event);
        if (callInfo != null && (callInfo.getChannelMap() == null
                || !callInfo.getChannelMap().containsKey(uniqueId))) {
            return bindEventChannel(event, callInfo.getCallId(), uniqueId);
        }
        if (callInfo != null || StringUtils.isBlank(eventCallId)) {
            return callInfo;
        }
        try {
            return bindEventChannel(event, Long.valueOf(eventCallId), uniqueId);
        } catch (NumberFormatException exception) {
            log.warn("Invalid och_call_id in FreeSWITCH event, value:{}, uniqueId:{}", eventCallId, uniqueId);
            return null;
        }
    }

    private CallInfo bindEventChannel(EslEvent event, Long callId, String uniqueId) {
        Object callLock = CALL_LOCKS[Math.floorMod(callId.hashCode(), CALL_LOCKS.length)];
        synchronized (callLock) {
            CallInfo callInfo = ifsCallCacheService.getCallInfo(callId);
            if (callInfo == null) {
                return null;
            }
            ifsCallCacheService.saveCallRel(uniqueId, callId);
            if (callInfo.getChannelMap() == null || callInfo.getChannelMap().containsKey(uniqueId)) {
                return callInfo;
            }

            int directionType = inferChannelDirectionType(callInfo, event);
            ChannelInfo generatedChannel = directionType == 2
                    ? findGeneratedAgentChannel(callInfo) : null;
            ChannelInfo eventChannel;
            if (generatedChannel != null) {
                String generatedUniqueId = generatedChannel.getUniqueId();
                generatedChannel.setUniqueId(uniqueId);
                callInfo.removeChannelInfoMap(generatedUniqueId);
                callInfo.removeUniqueIdList(generatedUniqueId);
                redisService.delCacheMapValue(CacheConstants.CALL_REL_MAP_CACHE_KEY, generatedUniqueId);
                eventChannel = generatedChannel;
            } else {
                ChannelInfo template = directionType == 2
                        ? findAgentChannel(callInfo) : findCallerChannel(callInfo);
                eventChannel = ChannelInfo.builder()
                        .callId(callId)
                        .uniqueId(uniqueId)
                        .cdrType(template == null ? 1 : template.getCdrType())
                        .type(template == null ? 1 : template.getType())
                        .directionType(directionType)
                        .agentId(callInfo.getAgentId())
                        .agentNumber(callInfo.getAgentNumber())
                        .agentName(callInfo.getAgentName())
                        .callTime(System.currentTimeMillis())
                        .caller(template == null ? event.getEventHeaders().get("Caller-Caller-ID-Number") : template.getCaller())
                        .called(template == null ? event.getEventHeaders().get("Caller-Destination-Number") : template.getCalled())
                        .display(template == null ? callInfo.getCallerDisplay() : template.getDisplay())
                        .build();
            }

            String peerUniqueId = findCallerUniqueId(callInfo, uniqueId);
            eventChannel.setOtherUniqueId(peerUniqueId);
            callInfo.setChannelInfoMap(uniqueId, eventChannel);
            callInfo.addUniqueIdList(uniqueId);
            linkPeerChannel(callInfo, peerUniqueId, uniqueId);
            ifsCallCacheService.saveCallInfo(callInfo);
            log.info("Bound FreeSWITCH channel to call, callId:{}, uniqueId:{}, peerUniqueId:{}",
                    callId, uniqueId, peerUniqueId);
            return callInfo;
        }
    }

    private ChannelInfo findGeneratedAgentChannel(CallInfo callInfo) {
        if (callInfo.getChannelMap() == null) {
            return null;
        }
        return callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> Objects.equals(channel.getDirectionType(), 2))
                .filter(channel -> isGeneratedUuid(channel.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    private ChannelInfo findAgentChannel(CallInfo callInfo) {
        if (callInfo.getChannelMap() == null) {
            return null;
        }
        return callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> Objects.equals(channel.getDirectionType(), 2))
                .findFirst()
                .orElse(null);
    }

    private ChannelInfo findCallerChannel(CallInfo callInfo) {
        if (callInfo.getChannelMap() == null) {
            return null;
        }
        return callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> Objects.equals(channel.getDirectionType(), 1))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从 FreeSWITCH 事件判断通道是主叫腿还是坐席腿。
     *
     * <p>事件关联短暂丢失时不能默认按坐席腿处理，否则主叫腿的 PARK/ANSWER
     * 会被误认为坐席振铃/接听，进而提前弹工单或弹屏。</p>
     */
    private int inferChannelDirectionType(CallInfo callInfo, EslEvent event) {
        String channelDirection = firstNotBlank(
                event.getEventHeaders().get("Call-Direction"),
                event.getEventHeaders().get("Caller-Logical-Direction"),
                event.getEventHeaders().get("Caller-Direction"),
                event.getEventHeaders().get("Presence-Call-Direction"),
                event.getEventHeaders().get("variable_direction"));
        if (StringUtils.equalsIgnoreCase(channelDirection, "inbound")) {
            return 1;
        }
        if (StringUtils.equalsIgnoreCase(channelDirection, "outbound")) {
            return 2;
        }

        if (StringUtils.isNotBlank(event.getEventHeaders().get("variable_originate_endpoint"))
                || StringUtils.isNotBlank(event.getEventHeaders().get("variable_och_call_id"))) {
            return 2;
        }
        if (callInfo != null && Objects.equals(callInfo.getDirection(), DirectionEnum.INBOUND.getType())
                && findCallerChannel(callInfo) != null) {
            return 1;
        }
        return 2;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String findCallerUniqueId(CallInfo callInfo, String uniqueId) {
        return callInfo.getChannelMap().values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> Objects.equals(channel.getDirectionType(), 1))
                .map(ChannelInfo::getUniqueId)
                .filter(StringUtils::isNotBlank)
                .filter(channelUniqueId -> !StringUtils.equals(channelUniqueId, uniqueId))
                .findFirst()
                .orElse(null);
    }

    private void linkPeerChannel(CallInfo callInfo, String peerUniqueId, String uniqueId) {
        if (StringUtils.isBlank(peerUniqueId)) {
            return;
        }
        ChannelInfo peerChannel = callInfo.getChannelMap().get(peerUniqueId);
        if (peerChannel != null && StringUtils.isBlank(peerChannel.getOtherUniqueId())) {
            peerChannel.setOtherUniqueId(uniqueId);
            callInfo.setChannelInfoMap(peerUniqueId, peerChannel);
        }
    }

    private boolean isGeneratedUuid(String uniqueId) {
        return StringUtils.length(uniqueId) == 32 && StringUtils.isNumeric(uniqueId);
    }


    protected void sendAgentStatus(Long callId, String caller, String callee, Integer direction, AgentStateEnum status) {
        try {
            if (AgentStateEnum.CALL_OUT_RING.equals(status) || AgentStateEnum.CALL_INT_RING.equals(status)) {
                log.info("[WebSocket推送-来电通知] 振铃阶段静默(工单在通话结束后弹出) callId:{} status:{}",
                        callId, status.getDes());
                return;
            }
            if (AgentStateEnum.TALKING.equals(status)
                    && Objects.equals(direction, com.och.common.enums.DirectionEnum.INBOUND.getType())) {
                log.info("[WebSocket推送-来电通知] 呼入接听阶段静默,等待通话结束 callId:{}", callId);
                return;
            }
            CallInfo callInfo = ifsCallCacheService.getCallInfo(callId);
            if (AgentStateEnum.CALL_END.equals(status)) {
                if (!Objects.equals(direction, com.och.common.enums.DirectionEnum.INBOUND.getType())) {
                    log.info("[WebSocket推送-来电通知] 非呼入通话结束不弹工单 callId:{} direction:{}", callId, direction);
                    return;
                }
                if (callInfo == null || callInfo.getAnswerTime() == null || callInfo.getAnswerTime() <= 0) {
                    log.info("[WebSocket推送-来电通知] 呼入未接通,结束时不弹工单 callId:{}", callId);
                    return;
                }
            }
            if (!AgentStateEnum.CALL_END.equals(status)) {
                com.och.system.domain.entity.CallSchedule currentSchedule =
                        agentTransferScheduleService.findCurrentScheduleForNotification();
                if (!agentTransferScheduleService.isWorkingSchedule(currentSchedule)) {
                    log.info("[WebSocket推送-来电通知] 非工作时间静默 callId:{} status:{}", callId, status.getDes());
                    return;
                }
            }
            Map<String, Object> callData = new HashMap<>();
            callData.put("callId", callId);
            callData.put("caller", caller);
            callData.put("callee", callee);
            callData.put("direction", direction);
            callData.put("status", status.getCode());
            callData.put("content", status.getDes());

            WsMsgPayload payload = WsMsgPayload.builder()
                    .event(WsEventEnum.INCOMING_CALL)
                    .timestamp(System.currentTimeMillis())
                    .data(JSONObject.toJSONString(callData))
                    .build();

            Long agentId = callInfo == null ? null : callInfo.getAgentId();
            SipAgentVo agent = agentId == null ? null : iSipAgentService.getDetail(agentId);
            Long userId = agent == null ? null : agent.getUserId();
            if (userId == null) {
                log.warn("[WebSocket推送-来电通知] 未找到分配坐席用户,取消全员广播 callId:{} agentId:{} status:{}",
                        callId, agentId, status.getDes());
                return;
            }
            boolean sent = webSocketService.sendToUser(userId, payload);
            log.info("[WebSocket推送-来电通知] callId:{}, agentId:{}, userId:{}, caller:{}, callee:{}, status:{}, sent:{}",
                    callId, agentId, userId, caller, callee, status.getDes(), sent);
        } catch (Exception e) {
            log.error("[WebSocket推送-来电通知]失败 callId:{}, error:{}", callId, e.getMessage());
        }
    }

}
