
package com.och.esl.handler.esl;

import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.CacheConstants;
import com.och.common.constant.EslEventNames;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.AgentStateEnum;
import com.och.common.enums.FsHangupCauseEnum;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.common.thread.ThreadFactoryImpl;
import com.och.esl.event.CallHangupEvent;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.service.IBridgeStateCache;
import com.och.esl.service.RecordingUploadService;
import com.och.esl.utils.EslEventUtil;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.call.CallTrace;
import com.och.system.service.ICallRecordService;
import com.och.system.service.ICallTraceStore;
import com.och.system.service.IWorkOrderService;
import com.och.esl.service.IFlowNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 渠道挂机处理类
 *
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.CHANNEL_HANGUP_COMPLETE)
@Component
public class FsChannelHangUpCompleteEslEventHandler extends AbstractFsEslEventHandler implements DisposableBean {

    private final ScheduledExecutorService agentStatusExecutor = new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryImpl("agent-after-call-pool-%d"));

    @Autowired
    private ICallTraceStore callTraceStore;

    /**
     * ASR 等模块通过 Spring 事件接收“挂断结束”信号，不侵入挂断核心逻辑（仅一行发布）。
     * 发布失败仅记日志，绝不影响通话挂断/话单（方案 7.15 / 9.5）。
     */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private IBridgeStateCache bridgeStateCache;

    @Autowired
    private IFlowNoticeService flowNoticeService;

    @Autowired
    private IWorkOrderService workOrderService;

    @Autowired
    private RecordingUploadService recordingUploadService;

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("ChannelHangUpCompleteEslEventHandler EslEvent:{}.", JSONObject.toJSONString(event));
        String uniqueId = EslEventUtil.getUniqueId(event);
        CallInfo callInfo = resolveCallInfo(event);
        String eventHangupCause = EslEventUtil.getHangupCause(event);
        if (isForkRaceHangup(eventHangupCause)) {
            if (callInfo != null) {
                callInfo.removeUniqueIdList(uniqueId);
                if (callInfo.getChannelMap() != null) {
                    callInfo.getChannelMap().remove(uniqueId);
                    callInfo.getChannelMap().values().stream()
                            .filter(Objects::nonNull)
                            .filter(channel -> StringUtils.equals(channel.getOtherUniqueId(), uniqueId))
                            .forEach(channel -> channel.setOtherUniqueId(null));
                }
                redisService.delCacheMapValue(CacheConstants.CALL_REL_MAP_CACHE_KEY, uniqueId);
                ifsCallCacheService.saveCallInfo(callInfo);
            }
            log.info("Ignore cancelled parallel ringing leg, callId:{}, uuid:{}, hangupCause:{}",
                    callInfo == null ? null : callInfo.getCallId(), uniqueId, eventHangupCause);
            return;
        }
        ChannelInfo cachedChannel = callInfo == null ? null : callInfo.getChannelMap().get(uniqueId);
        boolean bridgeEstablished = isBridgeEstablished(callInfo, cachedChannel, event);
        boolean satisfactionContinuation = false;
        boolean workOrderNotified = false;
        if (callInfo != null && cachedChannel != null && Objects.equals(cachedChannel.getDirectionType(), 2)
              && bridgeEstablished) {
            boolean alreadyInSatisfaction = bridgeStateCache.isSatisfaction(callInfo.getCallId());
            if (!alreadyInSatisfaction && !bridgeStateCache.isBridged(callInfo.getCallId())) {
                bridgeStateCache.markBridged(callInfo.getCallId());
            }
            boolean newlyEnteredSatisfaction = !alreadyInSatisfaction
                    && bridgeStateCache.markSatisfaction(callInfo.getCallId());
            satisfactionContinuation = continuesSatisfaction(
                    alreadyInSatisfaction, newlyEnteredSatisfaction);
            if (newlyEnteredSatisfaction) {
                log.info("Agent hung up, entering satisfaction survey callId:{}", callInfo.getCallId());
                // 推进IVR流程到满意度节点：将flowData指向主叫通道并发送next事件
                enterSatisfaction(address, callInfo);
                // 工单在坐席挂断瞬间弹出；主叫满意度流程结束后不再重复打开同一工单弹窗。
                sendAgentStatus(callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(),
                        callInfo.getDirection(), AgentStateEnum.CALL_END);
                workOrderNotified = true;
            }
        } else if (callInfo != null && bridgeStateCache.isSatisfaction(callInfo.getCallId())
              && cachedChannel != null && Objects.equals(cachedChannel.getDirectionType(), 2)) {
            satisfactionContinuation = true;
        }

        // If this channel was bridged, hang up the other leg immediately via FreeSWITCH event variables.
        // This works even when Redis UUID mapping is missing (e.g., multi-contact fork race condition).
        String bridgeUuid = firstNotBlank(
                event.getEventHeaders().get("variable_bridge_uuid"),
                EslEventUtil.getOtherUniqueId(event)
        );
        if (!satisfactionContinuation && bridgeEstablished
                && StringUtils.isNotBlank(bridgeUuid) && !StringUtils.equals(uniqueId, bridgeUuid)) {
            log.info("Bridge peer kill: uuid={}, bridge_uuid={}", uniqueId, bridgeUuid);
            fsClient.killCall(address, null, bridgeUuid);
        }

        if (Objects.isNull(callInfo)) {
            if (bridgeEstablished || isCallerLegEvent(event)) {
                // callId 关联丢失(多腿并发写竞态):仅在确认主叫腿结束或真实桥接后清理残留腿。
                killChannelsByEventCallId(address, event);
            } else {
                log.info("Skip cleanup for unbridged agent leg without callInfo, uuid:{}", uniqueId);
            }
            return;
        }
        log.info("挂机 callId:{} uniqueId:{}", callInfo.getCallId(), uniqueId);
        int count = callInfo.getUniqueIdList().size();
        callInfo.removeUniqueIdList(uniqueId);
        ChannelInfo channelInfo = callInfo.getChannelMap().get(uniqueId);
        if (Objects.isNull(channelInfo)) {
            log.warn("channelInfo lost for uuid, fallback cleanup, callId:{}, uuid:{}", callInfo.getCallId(), uniqueId);
            boolean callerGone = looksLikeCallerLeg(callInfo, uniqueId) || isCallerLegEvent(event);
            if (bridgeEstablished || callerGone) {
                callInfo.getChannelMap().forEach((key, value) -> {
                    if (StringUtils.isNotBlank(key) && !StringUtils.equals(key, uniqueId)) {
                        log.info("Lost-channel fallback kill: callId={}, uuid={}", callInfo.getCallId(), key);
                        fsClient.killCall(address, callInfo.getCallId(), key);
                    }
                });
                if (callerGone) {
                    fsClient.killCallsByCallId(callInfo.getCallId(), true);
                }
            } else {
                log.info("Skip fallback cleanup for unbridged agent leg, callId:{}, uuid:{}",
                        callInfo.getCallId(), uniqueId);
            }
            if (count <= 1) {
                ifsCallCacheService.removeCallInfo(callInfo.getCallId());
            } else {
                ifsCallCacheService.saveCallInfo(callInfo);
            }
            return;
        }
        channelInfo.setHangupCause(EslEventUtil.getHangupCause(event));
        channelInfo.setSipProtocol(EslEventUtil.getSipViaProtocol(event));
        channelInfo.setSipStatus(EslEventUtil.getSipTermStatus(event));
        channelInfo.setChannelName(EslEventUtil.getChannelName(event));
        channelInfo.setEndTime(event.getEventDateTimestamp() / 1000);
        bridgeEstablished = bridgeEstablished || channelInfo.getBridgeTime() != null;
        if (Objects.equals(channelInfo.getDirectionType(), 2)
                && !isForkRaceHangup(channelInfo.getHangupCause())
                && (channelInfo.getAnswerTime() != null || channelInfo.getBridgeTime() != null)) {
            changeAgentStatus(address, callInfo, channelInfo.getEndTime());
        } else if (Objects.equals(channelInfo.getDirectionType(), 2)
                && !isForkRaceHangup(channelInfo.getHangupCause())) {
            restoreAgentAfterFailedRing(address, callInfo.getAgentId(), callInfo.getAgentNumber());
        }
        // 设置挂机方向
        if(Objects.isNull(callInfo.getHangupDir())){
            if(Objects.equals(1,channelInfo.getDirectionType())){
                callInfo.setHangupDir(1);
            }
            if(Objects.equals(2,channelInfo.getDirectionType())){
                callInfo.setHangupDir(2);

            }
            String hangupCause = EslEventUtil.getHangupCause(event);
            FsHangupCauseEnum causeEnum = FsHangupCauseEnum.getByValue(hangupCause);
            if(Objects.nonNull(causeEnum)){
                callInfo.setHangupCause(causeEnum.getCode());
            }
        }

        // Cancelled fork channels must not trigger cleanup of the active caller leg.
        boolean forkRaceHangup = isForkRaceHangup(channelInfo.getHangupCause());
        boolean callerLegHungUp = Objects.equals(channelInfo.getDirectionType(), 1);
        if (!satisfactionContinuation && (bridgeEstablished || callerLegHungUp)) {
            callInfo.getChannelMap().forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && !StringUtils.equals(key, uniqueId)) {
                    log.info("Kill other channel: callId={}, uuid={}", callInfo.getCallId(), key);
                    fsClient.killCall(address, callInfo.getCallId(), key);
                }
            });
            if (callerLegHungUp) {
                // 主叫挂断:强清理(跳过满意度保护)。满意度保护只服务"坐席挂断、主叫存活"场景;
                // 主叫已挂时坐席 originate 腿(独立 park)无人取消会一直振铃(线上实测)。
                log.info("Caller hung up, force cleanup all legs, callId:{}", callInfo.getCallId());
                fsClient.killCallsByCallId(callInfo.getCallId(), true);
            }
        } else if (!satisfactionContinuation && !callerLegHungUp && !bridgeEstablished) {
            log.info("Skip hangup other channels (cancelled fork): callId={}, uuid={}, hangupCause={}",
                    callInfo.getCallId(), uniqueId, channelInfo.getHangupCause());
        }

        if (shouldFinalizeCall(count, callerLegHungUp, satisfactionContinuation)) {
            callInfo.setEndTime(channelInfo.getEndTime());
            sendAgentStatus(callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(), callInfo.getDirection(), AgentStateEnum.CALL_END);

            boolean recordingStopMissing = StringUtils.isNotBlank(callInfo.getRecord())
                    && callInfo.getRecordEndTime() == null;
            completeRecordingTiming(callInfo);

            CallRecord callRecord = new CallRecord();
            callRecord.transfer(callInfo);
            String satisfactionKey = "och:call:satisfaction:" + callInfo.getCallId();
            String satisfaction = redisService.getCacheObject(satisfactionKey);
            if (StringUtils.isNotBlank(satisfaction)) {
                callRecord.setSatisfaction(satisfaction);
                callRecord.setSatisfactionTime(new Date());
            }
            // 录音起止时间(CallInfo 中已记录，但 transfer 未搬运)；结束时间为空时按挂断时间回填
            if (callInfo.getRecordStartTime() != null) {
                callRecord.setRecordStartTime(new Date(callInfo.getRecordStartTime()));
            }
            if (callInfo.getRecordEndTime() != null) {
                callRecord.setRecordEndTime(new Date(callInfo.getRecordEndTime()));
            } else if (callRecord.getRecordStartTime() != null && callRecord.getCallEndTime() != null) {
                callRecord.setRecordEndTime(callRecord.getCallEndTime());
            }
            // 合并链路可追溯信息(原始/转换号码、网关、DID、路由、失败原因)与挂断通道 SIP 状态
            CallTrace trace = callTraceStore.get(callInfo.getCallId());
            if (trace == null) {
                trace = new CallTrace();
            }
            if (trace.getSipStatus() == null && channelInfo.getSipStatus() != null) {
                trace.setSipStatus(channelInfo.getSipStatus());
            }
            iCallRecordService.mergeTrace(callRecord, trace);
            // 幂等落库: 已存在则守卫式更新，避免重复事件/失败预记录产生重复话单
            iCallRecordService.saveOrUpdateByCallId(callRecord);
            if (recordingStopMissing) {
                recordingUploadService.uploadRecording(
                        String.valueOf(callInfo.getCallId()),
                        callInfo.getRecord(),
                        recordDurationSeconds(callInfo));
                log.warn("未收到 RECORD_STOP，已在最后一腿挂机时补偿录音上传 callId:{} record:{}",
                        callInfo.getCallId(), callInfo.getRecord());
            }
            try {
                workOrderService.updateSatisfactionByCallId(
                        String.valueOf(callInfo.getCallId()),
                        callRecord.getSatisfaction(),
                        callRecord.getSatisfactionTime());
            } catch (Exception e) {
                log.error("同步工单满意度失败 callId:{} satisfaction:{}",
                        callInfo.getCallId(), callRecord.getSatisfaction(), e);
            }
            redisService.deleteObject(satisfactionKey);
            if (callInfo.getFlowDataContext() != null) {
                flowNoticeService.notice(5, "hangup", callInfo.getFlowDataContext());
            }
            callTraceStore.remove(callInfo.getCallId());
            bridgeStateCache.remove(callInfo.getCallId());

            // 通知 ASR 等模块：通话结束，结束转写会话并等待最终结果（非侵入，失败不影响挂断）。
            publishHangupEvent(callInfo, channelInfo, callRecord);

            ifsCallCacheService.removeCallInfo(callInfo.getCallId());
            return;
        }
        callInfo.setChannelInfoMap(uniqueId, channelInfo);
        ifsCallCacheService.saveCallInfo(callInfo);

    }

    private void completeRecordingTiming(CallInfo callInfo) {
        if (callInfo == null || callInfo.getRecordStartTime() == null) {
            return;
        }
        if (callInfo.getRecordEndTime() == null && callInfo.getEndTime() != null) {
            callInfo.setRecordEndTime(callInfo.getEndTime());
        }
        if (callInfo.getRecordTime() == null && callInfo.getRecordEndTime() != null) {
            callInfo.setRecordTime(Math.max(0L, callInfo.getRecordEndTime() - callInfo.getRecordStartTime()));
        }
    }

    private Integer recordDurationSeconds(CallInfo callInfo) {
        if (callInfo == null || callInfo.getRecordTime() == null) {
            return null;
        }
        return (int) Math.max(0L, callInfo.getRecordTime() / 1000L);
    }

    static boolean shouldFinalizeCall(int channelCount, boolean callerLegHungUp,
                                      boolean satisfactionContinuation) {
        return !satisfactionContinuation && (channelCount <= 1 || callerLegHungUp);
    }

    static boolean continuesSatisfaction(boolean alreadyInSatisfaction,
                                         boolean newlyEnteredSatisfaction) {
        return alreadyInSatisfaction || newlyEnteredSatisfaction;
    }

    private void enterSatisfaction(String address, CallInfo callInfo) {
        FlowDataContext flowData = callInfo.getFlowDataContext();
        if (flowData == null) {
            log.warn("满意度评价失败：flowData为空 callId:{}", callInfo.getCallId());
            return;
        }
        // 找到主叫通道(directionType=1)，保留它继续满意度评价
        ChannelInfo callerChannel = null;
        for (ChannelInfo ch : callInfo.getChannelMap().values()) {
            if (ch != null && Objects.equals(ch.getDirectionType(), 1) && StringUtils.isNotBlank(ch.getUniqueId())) {
                callerChannel = ch;
                break;
            }
        }
        if (callerChannel == null) {
            log.warn("满意度评价失败：未找到主叫通道 callId:{} channelMap:{}", callInfo.getCallId(), callInfo.getChannelMap().keySet());
            return;
        }
        fsClient.sendSyncMsg(address, "uuid_park", callerChannel.getUniqueId());
        flowData.setUniqueId(callerChannel.getUniqueId());
        callInfo.setProcess(ProcessEnum.CALL_ROUTE);
        callInfo.setFlowDataContext(flowData);
        ifsCallCacheService.saveCallInfo(callInfo);
        log.info("发送满意度评价通知 callId:{}, callerUuid:{}", callInfo.getCallId(), callerChannel.getUniqueId());
        flowNoticeService.notice(2, "next", flowData);
    }

    private void publishHangupEvent(CallInfo callInfo, ChannelInfo channelInfo, CallRecord callRecord) {
        try {
            Integer durationSec = callRecord == null ? null : callRecord.getRecordDuration();
            CallHangupEvent hangupEvent = CallHangupEvent.builder()
                    .callId(callInfo.getCallId())
                    .uniqueId(channelInfo == null ? null : channelInfo.getUniqueId())
                    .caller(callInfo.getCaller())
                    .callee(callInfo.getCallee())
                    .agentId(callInfo.getAgentId())
                    .agentNumber(callInfo.getAgentNumber())
                    .direction(callInfo.getDirection())
                    .hangupCause(channelInfo == null ? null : channelInfo.getHangupCause())
                    .endTime(callInfo.getEndTime())
                    .record(callInfo.getRecord())
                    .recordDurationSec(durationSec)
                    .build();
            eventPublisher.publishEvent(hangupEvent);
        } catch (Exception e) {
            log.warn("发布 CallHangupEvent 失败(忽略，不影响挂断) callId:{} err:{}", callInfo.getCallId(), e.getMessage());
        }
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * callInfo 丢失时,从事件头取 och_call_id / X-OCH-Call-ID 强杀该通话全部残留通道。
     * originate 命令已把 och_call_id 写入坐席腿 channel var,坐席振铃腿可被命中。
     */
    private void killChannelsByEventCallId(String address, EslEvent event) {
        try {
            String callIdStr = firstNotBlank(
                    event.getEventHeaders().get("variable_och_call_id"),
                    event.getEventHeaders().get("variable_sip_h_X-OCH-Call-ID"),
                    EslEventUtil.getOchCallId(event));
            if (StringUtils.isBlank(callIdStr)) {
                log.warn("Hangup with no callInfo and no och_call_id in event, uuid:{}", EslEventUtil.getUniqueId(event));
                return;
            }
            log.info("callInfo lost, force cleanup by event och_call_id:{}, hung uuid:{}", callIdStr, EslEventUtil.getUniqueId(event));
            fsClient.killCallsByCallId(Long.valueOf(callIdStr), true);
        } catch (Exception e) {
            log.warn("killChannelsByEventCallId failed (ignored): {}", e.getMessage());
        }
    }

    /**
     * ChannelInfo 丢失时判定挂断腿是否主叫:channelMap 里除自己外只剩 directionType=2 的坐席腿,
     * 视为用户(主叫)挂断,需强清坐席腿。
     */
    private boolean looksLikeCallerLeg(CallInfo callInfo, String uniqueId) {
        if (callInfo == null || callInfo.getChannelMap() == null) {
            return false;
        }
        boolean hasCallerLeg = false;
        boolean hasAgentLeg = false;
        for (ChannelInfo ch : callInfo.getChannelMap().values()) {
            if (ch == null || StringUtils.equals(ch.getUniqueId(), uniqueId)) {
                continue;
            }
            if (Objects.equals(ch.getDirectionType(), 1)) {
                hasCallerLeg = true;
            } else if (Objects.equals(ch.getDirectionType(), 2)) {
                hasAgentLeg = true;
            }
        }
        return hasAgentLeg && !hasCallerLeg;
    }

    static boolean isForkRaceHangup(String hangupCause) {
        return StringUtils.equalsIgnoreCase("LOSE_RACE", StringUtils.trim(hangupCause));
    }

    private boolean isBridgeEstablished(CallInfo callInfo, ChannelInfo channelInfo, EslEvent event) {
        return (callInfo != null && bridgeStateCache.isBridged(callInfo.getCallId()))
                || (channelInfo != null && channelInfo.getBridgeTime() != null)
                || hasActualBridgeEvidence(event);
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

    private boolean isCallerLegEvent(EslEvent event) {
        if (event == null || event.getEventHeaders() == null) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(event.getEventHeaders().get("Call-Direction"), "inbound")
                || StringUtils.equalsIgnoreCase(event.getEventHeaders().get("Caller-Logical-Direction"), "inbound")
                || StringUtils.equalsIgnoreCase(event.getEventHeaders().get("Caller-Direction"), "inbound")
                || StringUtils.equalsIgnoreCase(event.getEventHeaders().get("variable_direction"), "inbound");
    }


    private void changeAgentStatus(String address, CallInfo callInfo, Long endTime) {
        if (Objects.isNull(callInfo) || callInfo.getAgentId() == null) {
            return;
        }
        String agentKey = String.valueOf(callInfo.getAgentId());
        SipAgentStatusVo agentStatus = redisService.getCacheMapValue(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey);
        if (agentStatus == null) {
            return;
        }
        long statusTime = endTime == null ? System.currentTimeMillis() : endTime;
        int skillAfterTime = Objects.requireNonNullElse(callInfo.gainSkillAfterTime(), 1);
        skillAfterTime = Math.max(skillAfterTime, 0);
        agentStatus.setStatusTime(statusTime);
        agentStatus.setCallEndTime(statusTime);
        agentStatus.setStatus(SipAgentStatusEnum.TALKING_OUT.getCode());
        redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey, agentStatus);
        agentStatusExecutor.schedule(
                () -> restoreAgentReady(address, callInfo.getAgentId(), callInfo.getAgentNumber(), statusTime),
                skillAfterTime, TimeUnit.SECONDS);
    }

    private void restoreAgentReady(String address, Long agentId, String agentNumber, long expectedStatusTime) {
        String agentKey = String.valueOf(agentId);
        try {
            SipAgentStatusVo currentStatus = redisService.getCacheMapValue(
                    CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey);
            if (currentStatus == null
                    || !Objects.equals(currentStatus.getStatus(), SipAgentStatusEnum.TALKING_OUT.getCode())
                    || !Objects.equals(currentStatus.getStatusTime(), expectedStatusTime)) {
                return;
            }
            if (!fsClient.isUserRegistered(address, agentNumber)) {
                currentStatus.setOnlineStatus(0);
                currentStatus.setStatus(SipAgentStatusEnum.OFF_ON.getCode());
                currentStatus.setStatusTime(System.currentTimeMillis());
                redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey, currentStatus);
                log.info("Agent left offline after call, agentId:{}, agentNumber:{}", agentId, agentNumber);
                return;
            }
            currentStatus.setOnlineStatus(1);
            currentStatus.setStatus(SipAgentStatusEnum.READY.getCode());
            currentStatus.setStatusTime(System.currentTimeMillis());
            redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey, currentStatus);
            log.info("Agent restored to ready after call, agentId:{}, agentNumber:{}", agentId, agentNumber);
        } catch (Exception exception) {
            log.error("Restore agent status failed, agentId:{}, agentNumber:{}", agentId, agentNumber, exception);
        }
    }

    private void restoreAgentAfterFailedRing(String address, Long agentId, String agentNumber) {
        if (agentId == null) {
            return;
        }
        agentStatusExecutor.schedule(() -> {
            String agentKey = String.valueOf(agentId);
            try {
                SipAgentStatusVo currentStatus = redisService.getCacheMapValue(
                        CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey);
                if (currentStatus == null
                        || !Objects.equals(currentStatus.getStatus(), SipAgentStatusEnum.RINGING.getCode())
                        || fsClient.hasActiveCallForAgent(address, agentNumber)) {
                    return;
                }
                boolean registered = fsClient.isUserRegistered(address, agentNumber);
                currentStatus.setOnlineStatus(registered ? 1 : 0);
                currentStatus.setStatus(registered
                        ? SipAgentStatusEnum.READY.getCode() : SipAgentStatusEnum.OFF_ON.getCode());
                currentStatus.setStatusTime(System.currentTimeMillis());
                redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey, currentStatus);
                log.info("Agent restored after failed ring, agentId:{}, agentNumber:{}, registered:{}",
                        agentId, agentNumber, registered);
            } catch (Exception exception) {
                log.error("Restore failed-ring agent status failed, agentId:{}, agentNumber:{}",
                        agentId, agentNumber, exception);
            }
        }, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        agentStatusExecutor.shutdownNow();
    }
}
