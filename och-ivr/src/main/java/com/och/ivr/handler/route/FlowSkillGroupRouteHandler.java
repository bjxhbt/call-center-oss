package com.och.ivr.handler.route;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.common.constant.EslConstant;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.common.thread.ThreadFactoryImpl;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.event.CallHangupEvent;
import com.och.esl.queue.CallQueue;
import com.och.esl.service.CallPlaybackCompletionService;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.properties.FlowTransferNodeProperties;
import com.och.ivr.domain.entity.FlowInstances;
import com.och.ivr.service.IFlowInstancesService;
import com.och.system.domain.query.skill.CallSkillQuery;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.domain.vo.file.VoiceFileVo;
import com.och.system.domain.vo.skill.CallSkillAgentRelVo;
import com.och.system.domain.vo.skill.CallSkillVo;
import com.och.system.service.ICallSkillService;
import com.och.system.service.ISipAgentService;
import com.och.system.service.IVoiceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class FlowSkillGroupRouteHandler implements InitializingBean, DisposableBean {

    private static final long AGENT_RESERVATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
    private static final long STALE_TRANSIENT_STATUS_MILLIS = TimeUnit.MINUTES.toMillis(2);
    private static final int DEFAULT_QUEUE_LENGTH = 10;
    private static final int DEFAULT_QUEUE_TIMEOUT_SECONDS = 120;
    private static final String QUEUE_CACHE_KEY = "och:ivr:skill:queue";

    private final IFsCallCacheService fsCallCacheService;
    private final ICallSkillService iCallSkillService;
    private final FsClient fsClient;
    private final RedisService redisService;
    private final IVoiceFileService iVoiceFileService;
    private final ISipAgentService iSipAgentService;
    private final IFlowNoticeService iFlowNoticeService;
    private final IFlowInstancesService iFlowInstancesService;
    private final CallPlaybackCompletionService callPlaybackCompletionService;

    @Value("${och.ivr.agent-busy-prompt:say:当前坐席繁忙，请稍后重试。}")
    private String agentBusyPrompt;

    @Value("${och.ivr.queue-busy-prompt:say:当前坐席繁忙，正在为您排队，请稍候。}")
    private String queueBusyPrompt;

    private final Map<Long, FlowDataContext> channelInfoMap = new ConcurrentHashMap<>();
    private final Map<Long, PriorityBlockingQueue<CallQueue>> callQueueMap = new ConcurrentHashMap<>();
    private final Set<Long> queuedCallIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, Long> agentReservations = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor callAgentExecutor = new ThreadPoolExecutor(
            5, 10, 60L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            new ThreadFactoryImpl("flow-agent-distribution-pool-%d"));
    private final ScheduledExecutorService fsAcdThread = new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryImpl("flow-acd-pool-%d"));

    public void handler(FlowDataContext flowData, FlowTransferNodeProperties properties) {
        channelInfoMap.put(flowData.getCallId(), flowData);
        CallInfo callInfo = fsCallCacheService.getCallInfo(flowData.getCallId());
        if (callInfo == null) {
            log.warn("call info not found, callId:{}", flowData.getCallId());
            channelInfoMap.remove(flowData.getCallId());
            return;
        }
        if (ProcessEnum.CALL_BRIDGE.equals(callInfo.getProcess()) && callInfo.getAgentId() != null) {
            log.info("call already routed to agent, callId:{}, agentId:{}", callInfo.getCallId(), callInfo.getAgentId());
            return;
        }
        if (queuedCallIds.contains(callInfo.getCallId())) {
            log.info("call already queued, callId:{}, skillId:{}", callInfo.getCallId(), callInfo.getSkillId());
            return;
        }

        String skillValue = properties.getRouteValue();
        log.info("transfer to skill group, callId:{}, skill:{}", flowData.getCallId(), skillValue);
        CallSkillVo callSkill = resolveSkill(skillValue);
        if (callSkill == null) {
            log.warn("skill group not found, callId:{}, skill:{}", callInfo.getCallId(), skillValue);
            playBusyAndHangup(flowData.getAddress(), callInfo, flowData.getUniqueId(), HangupCauseEnum.FULLBUSY);
            return;
        }

        callInfo.setSkillId(callSkill.getId());
        addSkillDetail(callInfo);
        List<CallSkillAgentRelVo> agentList = callSkill.getAgentList();
        if (CollectionUtil.isEmpty(agentList)) {
            log.warn("skill group has no agent, callId:{}, skillId:{}", callInfo.getCallId(), callSkill.getId());
            callInfo.setSkillHangUpReason(HangupCauseEnum.FULLBUSY.getDesc());
            playBusyAndHangup(flowData.getAddress(), callInfo, flowData.getUniqueId(), HangupCauseEnum.FULLBUSY);
            return;
        }

        List<SipAgentStatusVo> registeredAgentStatuses = getRegisteredAgentStatuses(
                flowData.getAddress(), loadAgentStatuses(agentList));
        if (CollectionUtil.isEmpty(registeredAgentStatuses)) {
            log.warn("skill group has no registered agent, callId:{}, skillId:{}",
                    callInfo.getCallId(), callSkill.getId());
            handleNoFreeAgent(flowData.getAddress(), callInfo, flowData.getUniqueId(), callSkill);
            return;
        }

        List<SipAgentStatusVo> freeAgentList = getFreeAgentList(registeredAgentStatuses);
        if (CollectionUtil.isEmpty(freeAgentList)) {
            handleNoFreeAgent(flowData.getAddress(), callInfo, flowData.getUniqueId(), callSkill);
            return;
        }

        applySkillLevels(agentList, freeAgentList);
        SipAgentVo agentInfo = getReservedAgent(callInfo, callSkill, freeAgentList);
        if (agentInfo == null) {
            handleNoFreeAgent(flowData.getAddress(), callInfo, flowData.getUniqueId(), callSkill);
            return;
        }
        if (!transferAgentHandler(flowData.getAddress(), agentInfo, callInfo, flowData.getUniqueId(),
                callSkill, null, callInfo.getQueueEndTime())) {
            releaseAgentReservation(agentInfo.getId());
        }
    }

    private void addSkillDetail(CallInfo callInfo) {
        CallInfoDetail detail = new CallInfoDetail();
        detail.setCallId(callInfo.getCallId());
        detail.setCreateTime(DateUtil.current());
        detail.setStartTime(DateUtil.current());
        detail.setOrderNum(callInfo.getDetailList() == null ? 0 : callInfo.getDetailList().size() + 1);
        detail.setTransferType(3);
        detail.setTransferId(callInfo.getSkillId());
        callInfo.addDetailList(detail);
    }

    private List<SipAgentStatusVo> loadAgentStatuses(List<CallSkillAgentRelVo> agentList) {
        List<SipAgentStatusVo> statuses = new LinkedList<>();
        for (CallSkillAgentRelVo relation : agentList) {
            if (relation == null || relation.getAgentId() == null) {
                continue;
            }
            Long agentId = relation.getAgentId();
            SipAgentStatusVo status = redisService.getCacheMapValue(
                    CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId));
            if (status == null) {
                SipAgentVo agent = iSipAgentService.getDetail(agentId);
                if (agent == null || !Objects.equals(agent.getStatus(), 1)
                        || StringUtils.isBlank(agent.getAgentNumber())) {
                    continue;
                }
                status = new SipAgentStatusVo();
                BeanUtils.copyProperties(agent, status);
                status.setStatus(SipAgentStatusEnum.READY.getCode());
                status.setOnlineStatus(1);
                status.setStatusTime(DateUtil.current());
                redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY,
                        String.valueOf(agentId), status);
                log.info("agent status cache restored from database, agentId:{}, agentNumber:{}",
                        agentId, agent.getAgentNumber());
            } else if (isLegacyAfterCallStatus(status)) {
                status.setStatus(SipAgentStatusEnum.READY.getCode());
                status.setOnlineStatus(1);
                status.setStatusTime(DateUtil.current());
                status.setCallEndTime(null);
                redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY,
                        String.valueOf(agentId), status);
                log.info("legacy stuck agent status restored, agentId:{}, agentNumber:{}",
                        agentId, status.getAgentNumber());
            }
            statuses.add(status);
        }
        return statuses;
    }

    private boolean isLegacyAfterCallStatus(SipAgentStatusVo status) {
        return Objects.equals(status.getStatus(), SipAgentStatusEnum.NOT_READY.getCode())
                && Objects.equals(status.getOnlineStatus(), 1)
                && status.getCallEndTime() != null
                && status.getCallEndTime() > 0
                && status.getCallEndTime() < TimeUnit.DAYS.toMillis(365);
    }

    private List<SipAgentStatusVo> getRegisteredAgentStatuses(String address,
                                                               List<SipAgentStatusVo> agentStatusList) {
        if (CollectionUtil.isEmpty(agentStatusList)) {
            return new LinkedList<>();
        }
        List<SipAgentStatusVo> registeredStatuses = new LinkedList<>();
        for (SipAgentStatusVo agentStatus : agentStatusList) {
            if (agentStatus == null || agentStatus.getId() == null
                    || !fsClient.isWebRtcUserRegistered(address, agentStatus.getAgentNumber())) {
                continue;
            }
            normalizeRegisteredAgentStatus(address, agentStatus);
            registeredStatuses.add(agentStatus);
        }
        return registeredStatuses;
    }

    private void normalizeRegisteredAgentStatus(String address, SipAgentStatusVo agentStatus) {
        boolean staleOffline = Objects.equals(agentStatus.getStatus(), SipAgentStatusEnum.OFF_ON.getCode());
        boolean transientStatus = Objects.equals(agentStatus.getStatus(), SipAgentStatusEnum.RINGING.getCode())
                || Objects.equals(agentStatus.getStatus(), SipAgentStatusEnum.TALKING_IN.getCode())
                || Objects.equals(agentStatus.getStatus(), SipAgentStatusEnum.TALKING_OUT.getCode());
        boolean staleTransient = transientStatus && agentStatus.getStatusTime() != null
                && DateUtil.current() - agentStatus.getStatusTime() > STALE_TRANSIENT_STATUS_MILLIS;
        if ((staleOffline || staleTransient)
                && !fsClient.hasActiveCallForAgent(address, agentStatus.getAgentNumber())) {
            agentStatus.setStatus(SipAgentStatusEnum.READY.getCode());
            agentStatus.setOnlineStatus(1);
            agentStatus.setStatusTime(DateUtil.current());
            agentStatus.setCallEndTime(null);
            redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY,
                    String.valueOf(agentStatus.getId()), agentStatus);
            log.info("registered agent stale status restored, agentId:{}, agentNumber:{}",
                    agentStatus.getId(), agentStatus.getAgentNumber());
        }
    }

    private List<SipAgentStatusVo> getFreeAgentList(List<SipAgentStatusVo> agentStatusList) {
        if (CollectionUtil.isEmpty(agentStatusList)) {
            return new LinkedList<>();
        }
        long current = DateUtil.current();
        List<SipAgentStatusVo> freeAgentList = new LinkedList<>();
        for (SipAgentStatusVo agentStatus : agentStatusList) {
            if (agentStatus == null || agentStatus.getId() == null) {
                continue;
            }
            if (!Objects.equals(SipAgentStatusEnum.READY.getCode(), agentStatus.getStatus())) {
                agentReservations.remove(agentStatus.getId());
                continue;
            }
            Long reservedAt = agentReservations.get(agentStatus.getId());
            if (reservedAt != null && current - reservedAt <= AGENT_RESERVATION_TIMEOUT_MILLIS) {
                continue;
            }
            if (reservedAt != null) {
                agentReservations.remove(agentStatus.getId(), reservedAt);
            }
            freeAgentList.add(agentStatus);
        }
        return freeAgentList;
    }

    private void applySkillLevels(List<CallSkillAgentRelVo> agentList, List<SipAgentStatusVo> freeAgentList) {
        Map<Long, Integer> skillLevelMap = agentList.stream()
                .filter(item -> item.getAgentId() != null)
                .collect(Collectors.toMap(
                        CallSkillAgentRelVo::getAgentId,
                        item -> item.getLevel() == null ? 0 : item.getLevel(),
                        (first, second) -> first));
        freeAgentList.forEach(item -> item.setLevel(skillLevelMap.getOrDefault(item.getId(), 0)));
    }

    private void handleNoFreeAgent(String address, CallInfo callInfo, String uniqueId, CallSkillVo skill) {
        handleNoFreeAgent(address, callInfo, uniqueId, skill, null);
    }

    /**
     * Applies the skill's full-busy policy. When a queue scanner has already
     * removed a call from its queue, retain that queue entry on a failed
     * registration/originate attempt instead of creating a second entry.
     */
    private void handleNoFreeAgent(String address, CallInfo callInfo, String uniqueId,
                                   CallSkillVo skill, CallQueue queuedCall) {
        int fullBusyType = skill.getFullBusyType() == null ? 2 : skill.getFullBusyType();
        switch (fullBusyType) {
            case 0 -> {
                if (queuedCall == null) {
                    callQueueStrategy(address, callInfo, uniqueId, skill);
                } else if (!requeueAfterOriginateFailure(callInfo, skill, queuedCall)) {
                    callInfo.setQueueEndTime(DateUtil.current());
                    overFlowStrategy(address, callInfo, uniqueId, skill);
                }
            }
            case 1 -> {
                if (queuedCall != null) {
                    callInfo.setQueueEndTime(DateUtil.current());
                }
                overFlowStrategy(address, callInfo, uniqueId, skill);
            }
            case 2 -> {
                if (queuedCall != null) {
                    callInfo.setQueueEndTime(DateUtil.current());
                }
                playBusyAndHangup(address, callInfo, uniqueId, HangupCauseEnum.OVERFLOW);
            }
            default -> {
                log.warn("unsupported fullBusyType:{}, skillId:{}", fullBusyType, skill.getId());
                playBusyAndHangup(address, callInfo, uniqueId, HangupCauseEnum.OVERFLOW);
            }
        }
    }

    private void callQueueStrategy(String address, CallInfo callInfo, String uniqueId, CallSkillVo skill) {
        if (!queuedCallIds.add(callInfo.getCallId())) {
            log.info("call already queued, callId:{}, skillId:{}", callInfo.getCallId(), callInfo.getSkillId());
            return;
        }
        PriorityBlockingQueue<CallQueue> callQueues = callQueueMap.computeIfAbsent(
                callInfo.getSkillId(), key -> new PriorityBlockingQueue<>());
        int queueLength = skill.getQueueLength() == null ? DEFAULT_QUEUE_LENGTH : skill.getQueueLength();
        callInfo.setQueueStartTime(DateUtil.current());
        if (callInfo.getFirstQueueTime() == null) {
            callInfo.setFirstQueueTime(callInfo.getQueueStartTime());
        }
        CallQueue callQueue = CallQueue.builder()
                .callId(callInfo.getCallId())
                .skillId(callInfo.getSkillId())
                .address(address)
                .type(1)
                .startTime(callInfo.getQueueStartTime())
                .uniqueId(uniqueId)
                .build();
        synchronized (callQueues) {
            if (callQueues.size() >= queueLength) {
                queuedCallIds.remove(callInfo.getCallId());
                overFlowStrategy(address, callInfo, uniqueId, skill);
                return;
            }
            callQueues.offer(callQueue);
        }
        persistQueuedCall(callQueue);
        fsCallCacheService.saveCallInfo(callInfo);
        startQueueVoice(callQueue, skill);
        persistQueuedCall(callQueue);
        log.info("call queued, callId:{}, skillId:{}, queueSize:{}", callInfo.getCallId(),
                callInfo.getSkillId(), callQueues.size());
    }

    private void overFlowStrategy(String address, CallInfo callInfo, String uniqueId, CallSkillVo skill) {
        callInfo.setOverflowCount(callInfo.getOverflowCount() == null ? 1 : callInfo.getOverflowCount() + 1);
        if (Objects.equals(skill.getOverflowType(), 1) && StringUtils.isNotBlank(skill.getOverflowValue())) {
            try {
                Long targetFlowId = Long.valueOf(skill.getOverflowValue());
                FlowDataContext currentFlow = channelInfoMap.remove(callInfo.getCallId());
                if (currentFlow == null) {
                    currentFlow = callInfo.getFlowDataContext();
                }
                if (currentFlow != null && Objects.equals(currentFlow.getFlowId(), targetFlowId)) {
                    log.warn("overflow target flow is current flow, callId:{}, flowId:{}",
                            callInfo.getCallId(), targetFlowId);
                } else {
                    if (currentFlow != null) {
                        FlowInstances completed = FlowInstances.builder()
                                .id(currentFlow.getInstanceId())
                                .status(2)
                                .endTime(new java.util.Date())
                                .currentNodeId(currentFlow.getCurrentNodeId())
                                .build();
                        if (completed.getId() != null) {
                            iFlowInstancesService.updateById(completed);
                        }
                        iFlowNoticeService.notice(3, "overflow", currentFlow);
                    }
                    callInfo.setQueueEndTime(DateUtil.current());
                    fsCallCacheService.saveCallInfo(callInfo);
                    iFlowNoticeService.notice(address, callInfo.getCallId(), uniqueId, targetFlowId);
                    log.info("call overflowed to IVR, callId:{}, skillId:{}, flowId:{}",
                            callInfo.getCallId(), skill.getId(), targetFlowId);
                    return;
                }
            } catch (NumberFormatException exception) {
                log.warn("invalid overflow IVR value, skillId:{}, value:{}",
                        skill.getId(), skill.getOverflowValue());
            }
        }
        callInfo.setSkillHangUpReason(HangupCauseEnum.FULLBUSY.getDesc());
        playBusyAndHangup(address, callInfo, uniqueId, HangupCauseEnum.FULLBUSY);
    }

    private SipAgentVo getReservedAgent(CallInfo callInfo, CallSkillVo skill,
                                        List<SipAgentStatusVo> freeAgentList) {
        List<SipAgentStatusVo> candidates = new LinkedList<>(freeAgentList);
        while (CollectionUtil.isNotEmpty(candidates)) {
            SipAgentVo agentInfo = getAgentStrategy(callInfo, skill, candidates);
            if (agentInfo == null || agentInfo.getId() == null) {
                return null;
            }
            if (agentReservations.putIfAbsent(agentInfo.getId(), DateUtil.current()) == null) {
                return agentInfo;
            }
            candidates.removeIf(item -> Objects.equals(item.getId(), agentInfo.getId()));
        }
        return null;
    }

    private SipAgentVo getAgentStrategy(CallInfo callInfo, CallSkillVo skill,
                                        List<SipAgentStatusVo> freeAgentList) {
        int strategyType = skill.getStrategyType() == null ? 0 : skill.getStrategyType();
        return switch (strategyType) {
            case 0 -> RandomUtil.randomEle(freeAgentList);
            case 1 -> getPollingAgent(skill, freeAgentList);
            case 2 -> freeAgentList.stream()
                    .max(Comparator.comparingLong(item -> DateUtil.current() - valueOrZero(item.getStatusTime())))
                    .orElse(null);
            case 3 -> freeAgentList.stream()
                    .min(Comparator.comparing(item -> item.getReceptionNum() == null ? 0 : item.getReceptionNum()))
                    .orElse(null);
            case 4 -> freeAgentList.stream()
                    .max(Comparator.comparingLong(item -> DateUtil.current() - valueOrZero(item.getCallEndTime())))
                    .orElse(null);
            default -> null;
        };
    }

    private SipAgentVo getPollingAgent(CallSkillVo skill, List<SipAgentStatusVo> freeAgentList) {
        freeAgentList.sort(Comparator.comparing(SipAgentStatusVo::getId));
        String pollingKey = StringUtils.format(CacheConstants.CALL_SKILL_POLLING_KEY, skill.getId());
        Integer pollingNumber = redisService.keyIsExists(pollingKey)
                ? redisService.getCacheObject(pollingKey) : 0;
        int index = Math.floorMod(pollingNumber == null ? 0 : pollingNumber, freeAgentList.size());
        redisService.setCacheObject(pollingKey, (index + 1) % freeAgentList.size());
        return freeAgentList.get(index);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private void fsAcd() {
        try {
            if (callQueueMap.isEmpty()) {
                return;
            }
            CallSkillQuery skillQuery = new CallSkillQuery();
            skillQuery.setIds(new ArrayList<>(callQueueMap.keySet()));
            List<CallSkillVo> callSkillList = iCallSkillService.getListByIds(skillQuery);
            if (CollectionUtil.isEmpty(callSkillList)) {
                return;
            }
            callSkillList.sort(Comparator.comparing(
                    CallSkillVo::getPriority, Comparator.nullsLast(Integer::compareTo)));
            for (CallSkillVo skill : callSkillList) {
                distributeSkillQueue(skill);
            }
        } catch (Exception exception) {
            log.error("queue distribution failed", exception);
        }
    }

    private void distributeSkillQueue(CallSkillVo skill) {
        PriorityBlockingQueue<CallQueue> callQueues = callQueueMap.get(skill.getId());
        if (callQueues == null) {
            return;
        }
        while (true) {
            CallQueue callQueue = callQueues.peek();
            if (callQueue == null) {
                callQueueMap.remove(skill.getId(), callQueues);
                return;
            }
            CallInfo callInfo = fsCallCacheService.getCallInfo(callQueue.getCallId());
            if (callInfo == null) {
                removeQueuedCall(callQueues, callQueue);
                channelInfoMap.remove(callQueue.getCallId());
                continue;
            }
            int timeoutSeconds = skill.getTimeOut() == null
                    ? DEFAULT_QUEUE_TIMEOUT_SECONDS : skill.getTimeOut();
            if (DateUtil.current() - callQueue.getStartTime() >= TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                if (removeQueuedCall(callQueues, callQueue)) {
                    callAgentExecutor.execute(() -> queueTimeout(callQueue));
                }
                continue;
            }
            List<CallSkillAgentRelVo> agentList = skill.getAgentList();
            if (CollectionUtil.isEmpty(agentList)) {
                return;
            }
            List<SipAgentStatusVo> registeredAgentStatuses = getRegisteredAgentStatuses(
                    callQueue.getAddress(), loadAgentStatuses(agentList));
            if (CollectionUtil.isEmpty(registeredAgentStatuses)) {
                return;
            }
            List<SipAgentStatusVo> freeAgentList = getFreeAgentList(registeredAgentStatuses);
            if (CollectionUtil.isEmpty(freeAgentList)) {
                return;
            }
            applySkillLevels(agentList, freeAgentList);
            SipAgentVo agentInfo = getReservedAgent(callInfo, skill, freeAgentList);
            if (agentInfo == null) {
                return;
            }
            if (!removeQueuedCall(callQueues, callQueue)) {
                releaseAgentReservation(agentInfo.getId());
                continue;
            }
            callAgentExecutor.execute(() -> transferQueuedCall(callQueue, callInfo, skill, agentInfo));
        }
    }

    private boolean removeQueuedCall(PriorityBlockingQueue<CallQueue> callQueues, CallQueue callQueue) {
        boolean removed = callQueues.remove(callQueue);
        if (removed) {
            queuedCallIds.remove(callQueue.getCallId());
            removePersistedQueuedCall(callQueue.getCallId());
        }
        return removed;
    }

    private void transferQueuedCall(CallQueue callQueue, CallInfo callInfo,
                                    CallSkillVo skill, SipAgentVo agentInfo) {
        try {
            Long previousQueueEndTime = callInfo.getQueueEndTime();
            stopQueueVoice(callQueue);
            callInfo.setQueueEndTime(DateUtil.current());
            fsCallCacheService.saveCallInfo(callInfo);
            playConfiguredVoice(callQueue.getAddress(), callQueue.getUniqueId(), skill.getAgentVoice());
            if (!transferAgentHandler(callQueue.getAddress(), agentInfo, callInfo, callQueue.getUniqueId(),
                    skill, callQueue, previousQueueEndTime)) {
                releaseAgentReservation(agentInfo.getId());
            }
        } catch (Exception exception) {
            releaseAgentReservation(agentInfo.getId());
            log.error("queued call transfer failed, callId:{}, agentId:{}",
                    callQueue.getCallId(), agentInfo.getId(), exception);
        }
    }

    private boolean transferAgentHandler(String address, SipAgentVo agentInfo,
                                         CallInfo callInfo, String uniqueId, CallSkillVo skill,
                                         CallQueue queuedCall, Long previousQueueEndTime) {
        String agentNumber = agentInfo.getAgentNumber();
        if (StringUtils.isBlank(agentNumber)) {
            log.error("agent has no SIP number, callId:{}, agentId:{}", callInfo.getCallId(), agentInfo.getId());
            callInfo.setSkillHangUpReason(HangupCauseEnum.AGENT_NO_BAND_SIP.getDesc());
            endCall(address, callInfo, uniqueId, HangupCauseEnum.FULLBUSY);
            return false;
        }
        // The queue scanner checks registration before reserving an agent, but the
        // browser can disconnect while the transfer task is waiting in the pool.
        // Recheck immediately before creating the second leg so a stale contact can
        // never reach CALL_BRIDGE or be connected to the caller.
        if (!fsClient.isWebRtcUserRegistered(address, agentNumber)) {
            log.warn("browser softphone disconnected before queued originate, callId:{}, agentId:{}, agentNumber:{}",
                    callInfo.getCallId(), agentInfo.getId(), agentNumber);
            callInfo.setQueueEndTime(previousQueueEndTime);
            handleNoFreeAgent(address, callInfo, uniqueId, skill, queuedCall);
            return false;
        }
        String otherUniqueId = RandomUtil.randomNumbers(32);
        CallRoutingSnapshot routingSnapshot = new CallRoutingSnapshot(
                callInfo.getAgentId(), callInfo.getAgentNumber(), callInfo.getAgentName(),
                callInfo.getCallee(), callInfo.getProcess(), previousQueueEndTime);
        callInfo.setAgentId(agentInfo.getId());
        callInfo.setAgentNumber(agentInfo.getAgentNumber());
        callInfo.setAgentName(agentInfo.getName());
        callInfo.setCallee(agentInfo.getAgentNumber());
        ChannelInfo otherChannelInfo = ChannelInfo.builder()
                .callId(callInfo.getCallId())
                .uniqueId(otherUniqueId)
                .cdrType(2)
                .type(2)
                .directionType(2)
                .agentId(agentInfo.getId())
                .agentNumber(agentInfo.getAgentNumber())
                .agentName(agentInfo.getName())
                .callTime(DateUtil.current())
                .otherUniqueId(uniqueId)
                .called(agentNumber)
                .caller(callInfo.getCaller())
                .display(callInfo.getCallerDisplay())
                .build();
        callInfo.setChannelInfoMap(otherUniqueId, otherChannelInfo);
        callInfo.addUniqueIdList(otherUniqueId);
        callInfo.setProcess(ProcessEnum.CALL_BRIDGE);
        fsCallCacheService.saveCallInfo(callInfo);
        fsCallCacheService.saveCallRel(otherUniqueId, callInfo.getCallId());
        boolean agentStatusMarked = markAgentRinging(agentInfo.getId());
        FlowDataContext mappedFlowData = channelInfoMap.remove(callInfo.getCallId());
        FlowDataContext flowData = mappedFlowData == null ? callInfo.getFlowDataContext() : mappedFlowData;
        fsClient.makeWebRtcCall(address, callInfo.getCallId(), agentNumber, callInfo.getCallerDisplay(),
                otherUniqueId, callInfo.getCalleeTimeOut(),
                failure -> handleAgentOriginateFailure(address, callInfo.getCallId(), uniqueId,
                        skill, queuedCall, agentInfo, otherUniqueId, routingSnapshot,
                        flowData, agentStatusMarked, failure));
        return true;
    }

    private void handleAgentOriginateFailure(String address, Long callId, String callerUniqueId,
                                              CallSkillVo skill, CallQueue queuedCall,
                                              SipAgentVo agentInfo, String attemptUniqueId,
                                              CallRoutingSnapshot routingSnapshot,
                                              FlowDataContext flowData, boolean agentStatusMarked,
                                              String failure) {
        CallInfo currentCall = fsCallCacheService.getCallInfo(callId);
        if (currentCall == null || !rollbackCurrentAgentAttempt(
                currentCall, agentInfo, attemptUniqueId, callerUniqueId, routingSnapshot)) {
            log.info("stale agent originate failure ignored, callId:{}, attemptUniqueId:{}, response:{}",
                    callId, attemptUniqueId, failure);
            return;
        }
        releaseAgentReservation(agentInfo.getId());
        restoreAgentStatusAfterOriginateFailure(address, agentInfo, agentStatusMarked);
        if (flowData != null) {
            channelInfoMap.put(callId, flowData);
        }
        log.warn("agent originate failed; applying skill full-busy strategy, callId:{}, agent:{}, response:{}",
                callId, agentInfo.getAgentNumber(), failure);
        int fullBusyType = skill.getFullBusyType() == null ? 2 : skill.getFullBusyType();
        if (fullBusyType == 0 && queuedCall != null) {
            if (requeueAfterOriginateFailure(currentCall, skill, queuedCall)) {
                return;
            }
            currentCall.setQueueEndTime(DateUtil.current());
            overFlowStrategy(address, currentCall, callerUniqueId, skill);
            return;
        }
        if (queuedCall != null) {
            currentCall.setQueueEndTime(DateUtil.current());
        }
        handleNoFreeAgent(address, currentCall, callerUniqueId, skill);
    }

    private boolean rollbackCurrentAgentAttempt(CallInfo callInfo, SipAgentVo attemptedAgent,
                                                String attemptUniqueId,
                                                String callerUniqueId,
                                                CallRoutingSnapshot routingSnapshot) {
        List<String> uniqueIds = callInfo.getUniqueIdList();
        ChannelInfo attemptChannel = callInfo.getChannelMap() == null
                ? null : callInfo.getChannelMap().get(attemptUniqueId);
        boolean currentAttempt = attemptChannel != null
                && Objects.equals(attemptChannel.getAgentId(), attemptedAgent.getId())
                && Objects.equals(attemptChannel.getOtherUniqueId(), callerUniqueId)
                && Objects.equals(callInfo.getAgentId(), attemptedAgent.getId())
                && Objects.equals(callInfo.getProcess(), ProcessEnum.CALL_BRIDGE)
                && CollectionUtil.isNotEmpty(uniqueIds)
                && uniqueIds.contains(attemptUniqueId);
        if (!currentAttempt) {
            return false;
        }
        callInfo.removeChannelInfoMap(attemptUniqueId);
        callInfo.removeUniqueIdList(attemptUniqueId);
        callInfo.setAgentId(routingSnapshot.agentId());
        callInfo.setAgentNumber(routingSnapshot.agentNumber());
        callInfo.setAgentName(routingSnapshot.agentName());
        callInfo.setCallee(routingSnapshot.callee());
        callInfo.setProcess(routingSnapshot.process());
        callInfo.setQueueEndTime(routingSnapshot.queueEndTime());
        fsCallCacheService.removeCallRel(attemptUniqueId);
        fsCallCacheService.saveCallInfo(callInfo);
        return true;
    }

    private boolean requeueAfterOriginateFailure(CallInfo callInfo, CallSkillVo skill,
                                                 CallQueue callQueue) {
        PriorityBlockingQueue<CallQueue> queues = callQueueMap.computeIfAbsent(
                skill.getId(), key -> new PriorityBlockingQueue<>());
        int queueLength = skill.getQueueLength() == null ? DEFAULT_QUEUE_LENGTH : skill.getQueueLength();
        synchronized (queues) {
            if (queuedCallIds.contains(callInfo.getCallId())) {
                log.info("originate failure ignored because call is already queued, callId:{}, skillId:{}",
                        callInfo.getCallId(), skill.getId());
                return true;
            }
            if (queues.size() >= queueLength) {
                log.info("queue became full while restoring failed originate, callId:{}, skillId:{}",
                        callInfo.getCallId(), skill.getId());
                return false;
            }
            if (!queuedCallIds.add(callInfo.getCallId())) {
                return true;
            }
            callInfo.setQueueStartTime(callQueue.getStartTime());
            callQueue.setPlayFlag(false);
            queues.offer(callQueue);
        }
        fsCallCacheService.saveCallInfo(callInfo);
        startQueueVoice(callQueue, skill);
        persistQueuedCall(callQueue);
        log.info("call requeued after agent originate failure, callId:{}, skillId:{}",
                callInfo.getCallId(), skill.getId());
        return true;
    }

    private void restoreAgentStatusAfterOriginateFailure(String address, SipAgentVo agentInfo,
                                                          boolean receptionIncremented) {
        if (agentInfo == null || agentInfo.getId() == null) {
            return;
        }
        String agentKey = String.valueOf(agentInfo.getId());
        SipAgentStatusVo agentStatus = redisService.getCacheMapValue(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey);
        if (agentStatus == null) {
            return;
        }
        if (fsClient.hasActiveCallForAgent(address, agentInfo.getAgentNumber())) {
            log.info("agent has another active call; status left unchanged after originate rejection, agentId:{}",
                    agentInfo.getId());
            return;
        }
        boolean registered = fsClient.isWebRtcUserRegistered(address, agentInfo.getAgentNumber());
        if (receptionIncremented && agentStatus.getReceptionNum() != null
                && agentStatus.getReceptionNum() > 0) {
            agentStatus.setReceptionNum(agentStatus.getReceptionNum() - 1);
        }
        agentStatus.setOnlineStatus(registered ? 1 : 0);
        agentStatus.setStatus(registered
                ? SipAgentStatusEnum.READY.getCode() : SipAgentStatusEnum.OFF_ON.getCode());
        agentStatus.setStatusTime(DateUtil.current());
        agentStatus.setCallEndTime(registered ? null : DateUtil.current());
        redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey, agentStatus);
        log.info("Agent status restored after originate rejection, agentId:{}, agentNumber:{}, registered:{}",
                agentInfo.getId(), agentInfo.getAgentNumber(), registered);
    }

    private boolean markAgentRinging(Long agentId) {
        if (agentId == null || !redisService.getCacheMapHasKey(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId))) {
            return false;
        }
        SipAgentStatusVo agentStatus = redisService.getCacheMapValue(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId));
        if (agentStatus == null) {
            return false;
        }
        agentStatus.setStatus(SipAgentStatusEnum.RINGING.getCode());
        agentStatus.setStatusTime(DateUtil.current());
        agentStatus.setReceptionNum(agentStatus.getReceptionNum() == null
                ? 1 : agentStatus.getReceptionNum() + 1);
        redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY,
                String.valueOf(agentId), agentStatus);
        return true;
    }

    private record CallRoutingSnapshot(Long agentId, String agentNumber, String agentName,
                                       String callee, ProcessEnum process, Long queueEndTime) {
    }

    private void releaseAgentReservation(Long agentId) {
        if (agentId != null) {
            agentReservations.remove(agentId);
        }
    }

    private void startQueueVoice(CallQueue callQueue, CallSkillVo skill) {
        String queueVoicePath = resolveVoicePath(skill.getQueueVoice());
        if (StringUtils.isBlank(queueVoicePath)) {
            queueVoicePath = queueBusyPrompt;
            log.warn("queue voice is not configured, using fallback prompt, skillId:{}, callId:{}",
                    skill.getId(), callQueue.getCallId());
        }
        fsClient.sendArgs(callQueue.getAddress(), callQueue.getUniqueId(), EslConstant.SET,
                EslConstant.PLAYBACK_TERMINATORS);
        fsClient.sendArgs(callQueue.getAddress(), callQueue.getUniqueId(), EslConstant.SET,
                EslConstant.PLAYBACK_DELIMITER);
        String loopAudio = queueVoicePath + EslConstant.EXCLAMATION + "silence_stream://5000";
        log.info("queue voice started, callId:{}, uniqueId:{}, voiceId:{}, audio:{}",
                callQueue.getCallId(), callQueue.getUniqueId(), skill.getQueueVoice(), loopAudio);
        fsClient.playFile(callQueue.getAddress(), callQueue.getUniqueId(), loopAudio, -1);
        callQueue.setPlayFlag(true);
    }

    private void persistQueuedCall(CallQueue callQueue) {
        redisService.setCacheMapValue(QUEUE_CACHE_KEY, String.valueOf(callQueue.getCallId()), callQueue);
    }

    private void removePersistedQueuedCall(Long callId) {
        if (callId != null) {
            redisService.delCacheMapValue(QUEUE_CACHE_KEY, String.valueOf(callId));
        }
    }

    @EventListener
    public void handleCallHangup(CallHangupEvent event) {
        if (event == null || event.getCallId() == null) {
            return;
        }
        Long callId = event.getCallId();
        for (Map.Entry<Long, PriorityBlockingQueue<CallQueue>> entry : callQueueMap.entrySet()) {
            PriorityBlockingQueue<CallQueue> queues = entry.getValue();
            queues.removeIf(item -> Objects.equals(callId, item.getCallId()));
            if (queues.isEmpty()) {
                callQueueMap.remove(entry.getKey(), queues);
            }
        }
        queuedCallIds.remove(callId);
        channelInfoMap.remove(callId);
        removePersistedQueuedCall(callId);
        log.info("removed hung-up call from queue, callId:{}", callId);
    }

    private void restoreQueuedCalls() {
        Map<String, CallQueue> persistedQueues = redisService.getCacheMap(QUEUE_CACHE_KEY);
        if (CollectionUtils.isEmpty(persistedQueues)) {
            return;
        }
        int restored = 0;
        for (CallQueue callQueue : persistedQueues.values()) {
            if (callQueue == null || callQueue.getCallId() == null || callQueue.getSkillId() == null) {
                continue;
            }
            CallInfo callInfo = fsCallCacheService.getCallInfo(callQueue.getCallId());
            if (callInfo == null) {
                removePersistedQueuedCall(callQueue.getCallId());
                continue;
            }
            callQueueMap.computeIfAbsent(callQueue.getSkillId(), key -> new PriorityBlockingQueue<>())
                    .offer(callQueue);
            queuedCallIds.add(callQueue.getCallId());
            if (callInfo.getFlowDataContext() != null) {
                channelInfoMap.put(callQueue.getCallId(), callInfo.getFlowDataContext());
            }
            if (!Boolean.TRUE.equals(callQueue.getPlayFlag())) {
                CallSkillVo skill = iCallSkillService.getDetail(callQueue.getSkillId());
                if (skill != null) {
                    startQueueVoice(callQueue, skill);
                    persistQueuedCall(callQueue);
                }
            }
            restored++;
        }
        log.info("restored queued calls from Redis, count:{}", restored);
    }

    private void stopQueueVoice(CallQueue callQueue) {
        if (Boolean.TRUE.equals(callQueue.getPlayFlag())) {
            fsClient.sendSyncMsg(callQueue.getAddress(), "uuid_break", callQueue.getUniqueId());
            callQueue.setPlayFlag(false);
        }
    }

    private void playConfiguredVoice(String address, String uniqueId, Long voiceId) {
        String voicePath = resolveVoicePath(voiceId);
        if (StringUtils.isNotBlank(voicePath)) {
            fsClient.playFile(address, uniqueId, voicePath);
        }
    }

    private String resolveVoicePath(Long voiceId) {
        if (voiceId == null) {
            return null;
        }
        VoiceFileVo voiceFile = iVoiceFileService.getDetail(voiceId);
        if (voiceFile == null) {
            log.warn("voice file not found, voiceId:{}", voiceId);
            return null;
        }
        return StringUtils.isNotBlank(voiceFile.getFilePath())
                ? voiceFile.getFilePath() : voiceFile.getFileName();
    }

    private void queueTimeout(CallQueue callQueue) {
        stopQueueVoice(callQueue);
        CallInfo callInfo = fsCallCacheService.getCallInfo(callQueue.getCallId());
        if (callInfo == null) {
            channelInfoMap.remove(callQueue.getCallId());
            return;
        }
        long current = DateUtil.current();
        callInfo.setQueueEndTime(current);
        callInfo.setHangupDir(3);
        callInfo.setHangupCause(HangupCauseEnum.QUEUE_TIME_OUT.getCode());
        callInfo.setSkillHangUpReason(HangupCauseEnum.QUEUE_TIME_OUT.getDesc());
        if (!CollectionUtils.isEmpty(callInfo.getDetailList())) {
            CallInfoDetail callDetail = callInfo.getDetailList().get(callInfo.getDetailList().size() - 1);
            if (callDetail != null) {
                callDetail.setEndTime(current);
            }
        }
        fsClient.hangupCall(callQueue.getAddress(), callQueue.getCallId(), callQueue.getUniqueId());
        fsCallCacheService.saveCallInfo(callInfo);
        removePersistedQueuedCall(callQueue.getCallId());
        FlowDataContext flowData = channelInfoMap.remove(callQueue.getCallId());
        if (flowData != null) {
            iFlowNoticeService.notice(2, "end", flowData);
        }
        log.info("queue timeout, callId:{}, queuedAt:{}", callQueue.getCallId(), callQueue.getStartTime());
    }

    private void noRegisteredAgent(CallQueue callQueue, CallInfo callInfo) {
        stopQueueVoice(callQueue);
        callInfo.setQueueEndTime(DateUtil.current());
        removePersistedQueuedCall(callQueue.getCallId());
        log.warn("queued call has no registered agent, callId:{}, skillId:{}",
                callQueue.getCallId(), callQueue.getSkillId());
        playBusyAndHangup(callQueue.getAddress(), callInfo, callQueue.getUniqueId(), HangupCauseEnum.FULLBUSY);
    }

    private void endCall(String address, CallInfo callInfo, String uniqueId, HangupCauseEnum cause) {
        callInfo.setHangupDir(3);
        callInfo.setHangupCause(cause.getCode());
        fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
        fsCallCacheService.saveCallInfo(callInfo);
        queuedCallIds.remove(callInfo.getCallId());
        removePersistedQueuedCall(callInfo.getCallId());
        FlowDataContext flowData = channelInfoMap.remove(callInfo.getCallId());
        if (flowData != null) {
            iFlowNoticeService.notice(2, "end", flowData);
        }
    }

    private void playBusyAndHangup(String address, CallInfo callInfo, String uniqueId, HangupCauseEnum cause) {
        callInfo.setHangupDir(3);
        callInfo.setHangupCause(cause.getCode());
        callInfo.setSkillHangUpReason(cause.getDesc());
        fsCallCacheService.saveCallInfo(callInfo);
        callPlaybackCompletionService.armHangupAfterPlayback(callInfo.getCallId(), uniqueId);
        fsClient.sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " playback_terminators none");
        fsClient.playFile(address, uniqueId, agentBusyPrompt);
        fsAcdThread.schedule(() -> {
            if (callPlaybackCompletionService.consumeHangupAfterPlayback(callInfo.getCallId(), uniqueId)) {
                log.warn("busy prompt completion event timed out, forcing hangup callId:{}, uniqueId:{}",
                        callInfo.getCallId(), uniqueId);
                fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            }
        }, 10, TimeUnit.SECONDS);
        log.info("no agent busy prompt started, callId:{}, uniqueId:{}", callInfo.getCallId(), uniqueId);
    }

    private CallSkillVo resolveSkill(String skillValue) {
        if (StringUtils.isBlank(skillValue)) {
            return null;
        }
        try {
            return iCallSkillService.getDetail(Long.valueOf(skillValue));
        } catch (NumberFormatException ignored) {
            CallSkillQuery query = new CallSkillQuery();
            query.setName(skillValue);
            List<CallSkillVo> skills = iCallSkillService.getList(query);
            if (CollectionUtil.isEmpty(skills)) {
                return null;
            }
            return skills.stream()
                    .filter(skill -> StringUtils.equals(skillValue, skill.getName()))
                    .findFirst()
                    .orElse(skills.get(0));
        }
    }

    @Override
    public void afterPropertiesSet() {
        log.info("starting inbound queue scanner");
        restoreQueuedCalls();
        fsAcdThread.scheduleAtFixedRate(this::fsAcd, 5, 2, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        log.info("stopping inbound queue scanner");
        fsAcdThread.shutdown();
        callAgentExecutor.shutdown();
    }
}
