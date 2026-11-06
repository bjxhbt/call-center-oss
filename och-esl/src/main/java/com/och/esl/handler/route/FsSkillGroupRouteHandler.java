package com.och.esl.handler.route;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.och.common.annotation.EslRouteName;
import com.och.common.constant.CacheConstants;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.RouteTypeEnum;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.common.thread.ThreadFactoryImpl;
import com.och.common.utils.SpringUtils;
import com.och.common.utils.StringUtils;
import com.och.esl.factory.FsEslRouteFactory;
import com.och.esl.queue.CallQueue;
import com.och.esl.service.AgentUnavailablePromptService;
import com.och.esl.service.IFlowNoticeService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.fssip.FsSipGatewayQuery;
import com.och.system.domain.query.skill.CallSkillQuery;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.domain.vo.route.CallRouteVo;
import com.och.system.domain.vo.skill.CallSkillAgentRelVo;
import com.och.system.domain.vo.skill.CallSkillVo;
import com.och.system.service.ICallDisplayPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author danmo
 * @date 2023-11-10 17:20
 **/
@RequiredArgsConstructor
@EslRouteName(RouteTypeEnum.SKILL_GROUP)
@Component
@Slf4j
public class FsSkillGroupRouteHandler extends FsAbstractRouteHandler implements InitializingBean, DisposableBean {

    @Autowired
    private AgentUnavailablePromptService agentUnavailablePromptService;

    private final Map<Long, PriorityBlockingQueue<CallQueue>> callQueueMap = new ConcurrentHashMap<>();

    private final Set<Long> queuedCallIds = ConcurrentHashMap.newKeySet();

    private ThreadPoolExecutor callAgentExecutor = new ThreadPoolExecutor(5, 10, 60L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadFactoryImpl("fs-agent-distribution-pool-%d"));
    /**
     * 定时线程组
     */
    private static ScheduledExecutorService fsAcdThread = new ScheduledThreadPoolExecutor(1, new ThreadFactoryImpl("fs-acd-pool-%d"));

    @Override
    public void handler(String address, CallInfo callInfo, String uniqueId, String skillId) {
        log.info("转技能组 callId:{} transfer to {}", callInfo.getCallId(), skillId);
        CallSkillVo callSkill = iCallSkillService.getDetail(Long.valueOf(skillId));
        if (Objects.isNull(callSkill)) {
            log.info("转技能组获取技能组失败 callee:{}, skillId:{}", callInfo.getCallee(), skillId);
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }
        callInfo.setSkillId(callSkill.getId());

        //电话经过技能组
        CallInfoDetail detail = new CallInfoDetail();
        detail.setCallId(callInfo.getCallId());
        detail.setCreateTime(DateUtil.current());
        detail.setStartTime(DateUtil.current());
        detail.setOrderNum(callInfo.getDetailList() == null ? 0 : callInfo.getDetailList().size() + 1);
        detail.setTransferType(3);
        detail.setTransferId(callInfo.getSkillId());
        callInfo.addDetailList(detail);

        if(Objects.nonNull(callSkill.getCallerPhonePool())){
            callInfo.setCallerDisplay(getNumberPoolNumber(callSkill.getCallerPhonePool()));
        }

        if(Objects.nonNull(callSkill.getCalleePhonePool())){
            callInfo.setCallerDisplay(getNumberPoolNumber(callSkill.getCalleePhonePool()));
        }

        List<CallSkillAgentRelVo> agentList = callSkill.getAgentList();
        if (CollectionUtil.isEmpty(agentList)) {
            log.info("转技能组未配置坐席失败 callee:{}, skillId:{}", callInfo.getCallee(), skillId);
            callInfo.setSkillHangUpReason(HangupCauseEnum.FULLBUSY.getDesc());
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }
        List<String> agentIds = agentList.stream().map(CallSkillAgentRelVo::getAgentId).map(String::valueOf).toList();

        List<SipAgentStatusVo> callSipAgentStatusList = redisService.getMultiCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentIds);

        List<SipAgentStatusVo> freeAgentList = new LinkedList<>();

        //获取空闲状态坐席列表
        if (CollectionUtil.isNotEmpty(callSipAgentStatusList)) {
            List<SipAgentStatusVo> sipAgentStatusVoList = callSipAgentStatusList.stream()
                    .filter(item -> Objects.equals(SipAgentStatusEnum.READY.getCode(), item.getStatus()))
                    .filter(item -> fsClient.isWebRtcUserRegistered(address, item.getAgentNumber()))
                    .toList();
            freeAgentList.addAll(sipAgentStatusVoList);
        }


        //无空闲坐席
        if (CollectionUtil.isEmpty(freeAgentList)) {
            handleNoFreeAgent(address, callInfo, uniqueId, callSkill, null);
            return;
        }

        Map<Long, Integer> skillLevelMap = agentList.stream().collect(Collectors.toMap(CallSkillAgentRelVo::getAgentId, CallSkillAgentRelVo::getLevel, (key1, key2) -> key1));
        freeAgentList.forEach(item -> item.setLevel(skillLevelMap.get(item.getId())));
        //获取坐席策略
        SipAgentVo agentInfo = getAgentStrategy(callInfo, callSkill, freeAgentList);
        if (Objects.isNull(agentInfo)) {
            log.info("转技能组 获取空闲坐席失败 callee:{}, skillId:{}", callInfo.getCallee(), skillId);
            callInfo.setSkillHangUpReason(HangupCauseEnum.FULLBUSY.getDesc());
            handleNoFreeAgent(address, callInfo, uniqueId, callSkill, null);
            return;
        }
        transferAgentHandler(address, agentInfo, callInfo, uniqueId, callSkill, null,
                callInfo.getQueueEndTime());

    }

    private void callQueueStrategy(String address, CallInfo callInfo, String uniqueId, CallSkillVo skill) {
        if (!queuedCallIds.add(callInfo.getCallId())) {
            log.info("call already queued, callId:{}, skillId:{}", callInfo.getCallId(), callInfo.getSkillId());
            return;
        }
        PriorityBlockingQueue<CallQueue> callQueues = callQueueMap.computeIfAbsent(
                callInfo.getSkillId(), key -> new PriorityBlockingQueue<>());
        int queueLength = skill.getQueueLength() == null ? 10 : skill.getQueueLength();
        boolean queueFull;
        synchronized (callQueues) {
            queueFull = callQueues.size() >= queueLength;
            if (!queueFull) {
                callInfo.setQueueStartTime(DateUtil.current());
                if (callInfo.getFirstQueueTime() == null) {
                    callInfo.setFirstQueueTime(callInfo.getQueueStartTime());
                }
                callQueues.add(CallQueue.builder().callId(callInfo.getCallId())
                        .skillId(callInfo.getSkillId())
                        .address(address)
                        .type(1)
                        .startTime(callInfo.getQueueStartTime())
                        .uniqueId(uniqueId).build());
            }
        }
        if (queueFull) {
            queuedCallIds.remove(callInfo.getCallId());
            overFlowStrategy(address, callInfo, uniqueId, skill);
            return;
        }
        saveCallInfo(callInfo);
    }

    private void handleNoFreeAgent(String address, CallInfo callInfo, String uniqueId,
                                   CallSkillVo skill, CallQueue queuedCall) {
        int fullBusyType = skill.getFullBusyType() == null ? 2 : skill.getFullBusyType();
        switch (fullBusyType) {
            case 0:
                if (queuedCall == null) {
                    callQueueStrategy(address, callInfo, uniqueId, skill);
                } else {
                    if (!requeueAfterOriginateFailure(callInfo, skill, queuedCall)) {
                        overFlowStrategy(address, callInfo, uniqueId, skill);
                    }
                }
                break;
            case 1:
                if (queuedCall != null) {
                    callInfo.setQueueEndTime(DateUtil.current());
                }
                overFlowStrategy(address, callInfo, uniqueId, skill);
                break;
            case 2:
                if (queuedCall != null) {
                    callInfo.setQueueEndTime(DateUtil.current());
                }
                agentUnavailablePromptService.playAndHangup(
                        address, callInfo, uniqueId, HangupCauseEnum.OVERFLOW);
                break;
            default:
                log.warn("unsupported fullBusyType:{}, skillId:{}", fullBusyType, skill.getId());
                agentUnavailablePromptService.playAndHangup(
                        address, callInfo, uniqueId, HangupCauseEnum.OVERFLOW);
                break;
        }
    }

    private boolean requeueAfterOriginateFailure(CallInfo callInfo, CallSkillVo skill, CallQueue callQueue) {
        PriorityBlockingQueue<CallQueue> queues = callQueueMap.computeIfAbsent(
                skill.getId(), key -> new PriorityBlockingQueue<>());
        int queueLength = skill.getQueueLength() == null ? 10 : skill.getQueueLength();
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
        saveCallInfo(callInfo);
        fsClient.playBreak(callQueue.getAddress(), callQueue.getUniqueId());
        fsClient.playFile(callQueue.getAddress(), callQueue.getUniqueId(), "queue.wav");
        callQueue.setPlayFlag(true);
        log.info("call requeued after agent originate failure, callId:{}, skillId:{}",
                callInfo.getCallId(), skill.getId());
        return true;
    }

    private void overFlowStrategy(String address, CallInfo callInfo, String uniqueId, CallSkillVo skill) {
        callInfo.setOverflowCount(callInfo.getOverflowCount() == null ? 0 : callInfo.getOverflowCount() + 1);
        if (Objects.equals(skill.getOverflowType(), 0)) {
            agentUnavailablePromptService.playAndHangup(
                    address, callInfo, uniqueId, HangupCauseEnum.FULLBUSY);
        } else if (Objects.equals(skill.getOverflowType(), 1)) {
            //转IVR
            FsIvrRouteHandler fsIvrRouteHandler = SpringUtils.getBean(FsIvrRouteHandler.class);
            fsIvrRouteHandler.handler(address, callInfo, uniqueId, skill.getOverflowValue());
        } else {
            log.warn("unsupported overflowType:{}, skillId:{}", skill.getOverflowType(), skill.getId());
            agentUnavailablePromptService.playAndHangup(
                    address, callInfo, uniqueId, HangupCauseEnum.FULLBUSY);
        }
        saveCallInfo(callInfo);
    }


    private SipAgentVo getAgentStrategy(CallInfo callInfo, CallSkillVo skill, List<SipAgentStatusVo> freeAgentList) {
        switch (skill.getStrategyType()) {
            //0-随机
            case 0:
                return RandomUtil.randomEle(freeAgentList);
            //1-轮询
            case 1:
                String pollingKey = StringUtils.format(CacheConstants.CALL_SKILL_POLLING_KEY, skill.getId());
                int pollingNum = redisService.keyIsExists(pollingKey) ? redisService.getCacheObject(pollingKey) : 0;
                Integer nextPollingNum = findNextPollingNum(freeAgentList, pollingNum);
                redisService.setCacheObject(StringUtils.format(CacheConstants.CALL_SKILL_POLLING_KEY, skill.getId()), nextPollingNum);
                return freeAgentList.get(nextPollingNum);
            //2-最长空闲时间
            case 2:
                return freeAgentList.stream().max(Comparator.comparing(item -> DateUtil.current() - item.getStatusTime())).orElse(null);
            //3-当天最少应答次数
            case 3:
                return freeAgentList.stream().min(Comparator.comparing(SipAgentStatusVo::getReceptionNum)).orElse(null);
            //4-最长话后时长
            case 4:
                return freeAgentList.stream().min(Comparator.comparing(item -> DateUtil.current() - item.getCallEndTime())).orElse(null);
            default:
                return null;
        }
    }




    //acd排队策略
    private void fsAcd() {
        try {
            Set<Long> skillIds = callQueueMap.keySet();
            List<CallQueue> queueList = callQueueMap.values().stream().flatMap(Collection::stream).toList();
            if(CollectionUtil.isEmpty(skillIds) || CollectionUtil.isEmpty(queueList)){
                return;
            }
            CallSkillQuery skillQuery = new CallSkillQuery();
            skillQuery.setIds(new ArrayList<>(skillIds));
            List<CallSkillVo> callSkillList = iCallSkillService.getListByIds(skillQuery);
            if(CollectionUtil.isEmpty(callSkillList)){
                return;
            }
            long current = DateUtil.current();
            callSkillList.sort(Comparator.comparing(CallSkillVo::getPriority, Comparator.reverseOrder()));
            for (CallSkillVo skill : callSkillList) {
                PriorityBlockingQueue<CallQueue> callQueues = callQueueMap.get(skill.getId());
                if (callQueues == null) {
                    continue;
                }
                Iterator<CallQueue> iterator = callQueues.iterator();
                while (iterator.hasNext()){
                    CallQueue callQueue = iterator.next();
                    //正常排队超时
                    if (current/1000 - callQueue.getStartTime()/1000 >= skill.getTimeOut()) {
                        callAgentExecutor.execute(() -> {
                            queueTimeout(callQueue);
                        });
                        iterator.remove();
                        queuedCallIds.remove(callQueue.getCallId());
                        continue;
                    }
                    CallInfo callInfo = fsCallCacheService.getCallInfo(callQueue.getCallId());
                    if (callInfo == null) {
                        iterator.remove();
                        queuedCallIds.remove(callQueue.getCallId());
                        continue;
                    }

                    List<CallSkillAgentRelVo> agentList = skill.getAgentList();
                    List<String> agentIds = agentList.stream().map(CallSkillAgentRelVo::getAgentId).map(String::valueOf).toList();

                    List<SipAgentStatusVo> callSipAgentStatusList = redisService.getMultiCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentIds);
                    List<SipAgentStatusVo> freeAgentList = new LinkedList<>();
                    //获取空闲状态坐席列表
                    if (CollectionUtil.isNotEmpty(callSipAgentStatusList)) {
                        List<SipAgentStatusVo> sipAgentStatusVoList = callSipAgentStatusList.stream()
                                .filter(item -> Objects.equals(SipAgentStatusEnum.READY.getCode(), item.getStatus()))
                                .filter(item -> fsClient.isWebRtcUserRegistered(
                                        callQueue.getAddress(), item.getAgentNumber()))
                                .toList();
                        freeAgentList.addAll(sipAgentStatusVoList);
                    }

                    if(CollectionUtil.isEmpty(freeAgentList)){
                        if (callQueue.getPlayFlag() == null || !callQueue.getPlayFlag()) {
                            fsClient.playFile(callQueue.getAddress(), callQueue.getUniqueId(), "queue.wav");
                            callQueue.setPlayFlag(true);
                        }
                        continue;
                    }
                    Map<Long, Integer> skillLevelMap = agentList.stream().collect(Collectors.toMap(CallSkillAgentRelVo::getAgentId, CallSkillAgentRelVo::getLevel, (key1, key2) -> key1));
                    freeAgentList.forEach(item -> item.setLevel(skillLevelMap.get(item.getId())));

                    SipAgentVo agentInfo = getAgentStrategy(callInfo, skill, freeAgentList);
                    if (Objects.isNull(agentInfo)) {
                        log.info("转技能组 队列获取空闲坐席失败 callee:{}, skillId:{}", callInfo.getCallee(), skill.getId());
                        if (!callQueue.getPlayFlag()) {
                            fsClient.playFile(callQueue.getAddress(), callQueue.getUniqueId(), "queue.wav");
                            callQueue.setPlayFlag(true);
                        }
                        continue;
                    }
                    iterator.remove();
                    queuedCallIds.remove(callQueue.getCallId());
                    Long previousQueueEndTime = callInfo.getQueueEndTime();
                    //停止放音
                    callAgentExecutor.execute(() -> {
                        if (Boolean.TRUE.equals(callQueue.getPlayFlag())) {
                            fsClient.playBreak(callQueue.getAddress(), callQueue.getUniqueId());
                            callQueue.setPlayFlag(false);
                        }
                        callInfo.setQueueEndTime(current);
                        //转坐席音
                        fsClient.playFile(callQueue.getAddress(),callQueue.getUniqueId(),"agentbusy.wav");
                        transferAgentHandler(callQueue.getAddress(), agentInfo, callInfo,
                                callQueue.getUniqueId(), skill, callQueue, previousQueueEndTime);
                    });
                }
                //callQueueMap.remove(skill.getId());
            }
        } catch (Exception e) {
            log.error("排队异常 error:{}",e.getMessage(),e);
        }
    }

    private void transferAgentHandler(String address, SipAgentVo agentInfo, CallInfo callInfo,
                                      String uniqueId, CallSkillVo skill, CallQueue queuedCall,
                                      Long previousQueueEndTime) {
        String agentNumber = agentInfo.getAgentNumber();
        String otherUniqueId = RandomUtil.randomNumbers(32);

        if (StringUtils.isEmpty(agentNumber)) {
            log.error("transferAgentHandler agent:{} 坐席未绑定SIP号码, callId:{}", agentInfo.getId(), callInfo.getCallId());
            callInfo.setSkillHangUpReason(HangupCauseEnum.AGENT_NO_BAND_SIP.getDesc());
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }
        if (!fsClient.isWebRtcUserRegistered(address, agentNumber)) {
            log.warn("Browser softphone is not registered, callId:{}, agentId:{}, agentNumber:{}",
                    callInfo.getCallId(), agentInfo.getId(), agentNumber);
            callInfo.setQueueEndTime(previousQueueEndTime);
            handleNoFreeAgent(address, callInfo, uniqueId, skill, queuedCall);
            return;
        }
        CallRouteVo callRoute = fsCallCacheService.getCallRoute(agentNumber, 1);
        if(Objects.isNull(callRoute)){
            log.info("transferAgentHandler 未配置号码路由 callee:{}",agentNumber);
            callInfo.setSkillHangUpReason(HangupCauseEnum.NOT_ROUTE.getDesc());
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }
        FsSipGatewayQuery query = new FsSipGatewayQuery();
        query.setGatewayType(0);
        List<FsSipGateway> gatewayList = iFsSipGatewayService.getList(query);
        if(CollectionUtil.isEmpty(gatewayList)){
            log.info("transferAgentHandler 号码路由未关联网关信息 callee:{}",agentNumber);
            callInfo.setSkillHangUpReason(HangupCauseEnum.ROUTE_NOT_GATEWAY.getDesc());
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }
        CallRoutingSnapshot routingSnapshot = new CallRoutingSnapshot(
                callInfo.getAgentId(), callInfo.getAgentNumber(), callInfo.getAgentName(),
                callInfo.getCallee(), callInfo.getProcess(), previousQueueEndTime);
        callInfo.setAgentId(agentInfo.getId());
        callInfo.setAgentNumber(agentInfo.getAgentNumber());
        callInfo.setAgentName(agentInfo.getName());
        callInfo.setCallee(agentInfo.getAgentNumber());
        //构建主叫通道
        ChannelInfo otherChannelInfo = ChannelInfo.builder().callId(callInfo.getCallId()).uniqueId(otherUniqueId).cdrType(2).type(2).directionType(2)
                .agentId(agentInfo.getId()).agentNumber(agentInfo.getAgentNumber()).agentName(agentInfo.getName())
                .callTime(DateUtil.current()).otherUniqueId(uniqueId)
                .called(agentNumber).caller(callInfo.getCaller()).display(callInfo.getCallerDisplay()).build();
        callInfo.setChannelInfoMap(otherUniqueId,otherChannelInfo);
        callInfo.addUniqueIdList(otherUniqueId);
        callInfo.setProcess(ProcessEnum.CALL_BRIDGE);
        fsCallCacheService.saveCallInfo(callInfo);
        fsCallCacheService.saveCallRel(otherUniqueId,callInfo.getCallId());

        boolean receptionIncremented = incrementAgentReception(agentInfo.getId());
        fsClient.makeWebRtcCall(address, callInfo.getCallId(), callInfo.getCallee(),
                callInfo.getCallerDisplay(), otherUniqueId, callInfo.getCalleeTimeOut(),
                failure -> {
                    handleAgentOriginateFailure(address, callInfo.getCallId(), uniqueId, skill,
                            queuedCall, agentInfo, otherUniqueId, routingSnapshot,
                            receptionIncremented, failure);
                });
    }

    private void handleAgentOriginateFailure(String address, Long callId, String callerUniqueId,
                                              CallSkillVo skill, CallQueue queuedCall,
                                              SipAgentVo attemptedAgent, String attemptUniqueId,
                                              CallRoutingSnapshot routingSnapshot,
                                              boolean receptionIncremented, String failure) {
        CallInfo currentCall = fsCallCacheService.getCallInfo(callId);
        if (currentCall == null || !rollbackCurrentAgentAttempt(
                currentCall, attemptedAgent, attemptUniqueId, callerUniqueId, routingSnapshot)) {
            log.info("stale agent originate failure ignored, callId:{}, attemptUniqueId:{}, response:{}",
                    callId, attemptUniqueId, failure);
            return;
        }
        rollbackAgentReception(attemptedAgent.getId(), receptionIncremented);
        log.warn("agent originate failed; applying skill full-busy strategy, callId:{}, agent:{}, response:{}",
                callId, attemptedAgent.getAgentNumber(), failure);
        handleNoFreeAgent(address, currentCall, callerUniqueId, skill, queuedCall);
    }

    private boolean incrementAgentReception(Long agentId) {
        if (agentId == null || !redisService.getCacheMapHasKey(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId))) {
            return false;
        }
        SipAgentStatusVo agentStatus = redisService.getCacheMapValue(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId));
        if (agentStatus == null) {
            return false;
        }
        agentStatus.setReceptionNum(agentStatus.getReceptionNum() == null
                ? 1 : agentStatus.getReceptionNum() + 1);
        redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY,
                String.valueOf(agentId), agentStatus);
        return true;
    }

    private void rollbackAgentReception(Long agentId, boolean receptionIncremented) {
        if (!receptionIncremented || agentId == null
                || !redisService.getCacheMapHasKey(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId))) {
            return;
        }
        SipAgentStatusVo agentStatus = redisService.getCacheMapValue(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agentId));
        if (agentStatus == null || agentStatus.getReceptionNum() == null
                || agentStatus.getReceptionNum() <= 0) {
            return;
        }
        agentStatus.setReceptionNum(agentStatus.getReceptionNum() - 1);
        redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY,
                String.valueOf(agentId), agentStatus);
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
        saveCallInfo(callInfo);
        return true;
    }

    private record CallRoutingSnapshot(Long agentId, String agentNumber, String agentName,
                                       String callee, ProcessEnum process, Long queueEndTime) {
    }

    /**
     * 超时挂机
     * @param callQueue
     */
    private void queueTimeout(CallQueue callQueue) {
        queuedCallIds.remove(callQueue.getCallId());
        fsClient.playBreak(callQueue.getAddress(), callQueue.getUniqueId());
        Long current = DateUtil.current();
        CallInfo callInfo = fsCallCacheService.getCallInfo(callQueue.getCallId());
        if (callInfo == null) {
            return;
        }
        callInfo.setQueueEndTime(current);
        log.info("排队超时 callId:{} queueTimeout:{}", callQueue.getCallId(), callQueue.getStartTime());
        //排队超时挂机
        callInfo.setHangupDir(3);
        callInfo.setHangupCause(HangupCauseEnum.QUEUE_TIME_OUT.getCode());
        callInfo.setQueueEndTime(current);
        if (!CollectionUtils.isEmpty(callInfo.getDetailList())) {
            CallInfoDetail callDetail = callInfo.getDetailList().get(callInfo.getDetailList().size() - 1);
            if (callDetail != null) {
                callDetail.setEndTime(current);
            }
        }
        callInfo.setSkillHangUpReason(HangupCauseEnum.QUEUE_TIME_OUT.getDesc());

        fsClient.hangupCall(callQueue.getAddress(), callQueue.getCallId(), callQueue.getUniqueId());
        saveCallInfo(callInfo);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("开启呼入队列扫描");
        fsAcdThread.scheduleAtFixedRate(this::fsAcd, 5, 2, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        log.info("关闭呼入队列扫描");
        fsAcdThread.shutdown();
    }
    /**
     * 二分查询轮训级别
     *
     * @param freeAgentList
     * @param pollingNum
     * @return
     */
   private Integer findNextPollingNum(List<SipAgentStatusVo> freeAgentList, int pollingNum) {
        freeAgentList.sort(Comparator.comparing(SipAgentStatusVo::getLevel));
        int left = 0;
        int right = freeAgentList.size() - 1;
        if(pollingNum >= freeAgentList.get(right).getLevel()){
            return freeAgentList.get(left).getLevel();
        }
        while (left < right) {
            //防止整数溢出
            int mid = (right - left) / 2 + left;
            //如果当前元素比目标元素小或者相同 则去他右边找
            if (freeAgentList.get(mid).getLevel() <= pollingNum) {
                left = mid + 1;
            } else {
                //如果当前元素比目标元素大 则去他左边找 此时注意 应该包括其当前边界
                right = mid;
            }
        }
        return freeAgentList.get(left).getLevel();
    }



}
