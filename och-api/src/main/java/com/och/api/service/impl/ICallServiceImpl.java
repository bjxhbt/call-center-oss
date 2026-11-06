package com.och.api.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.och.api.service.ICallService;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.DirectionEnum;
import com.och.common.enums.ProcessEnum;
import com.och.common.exception.CommonException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFsCallCacheService;
import com.och.security.utils.SecurityUtils;
import com.och.system.domain.entity.CallDisplay;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.entity.CorpInfo;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.agent.SipAgentQuery;
import com.och.system.domain.query.call.CallQuery;
import com.och.system.domain.query.call.CallRecordQuery;
import com.och.system.domain.query.display.CallDisplayQuery;
import com.och.system.domain.query.fssip.FsSipGatewayQuery;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.domain.vo.call.CallRecordVo;
import com.och.system.domain.vo.display.CallDisplayVo;
import com.och.system.domain.vo.route.CallRouteVo;
import com.och.system.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ICallServiceImpl implements ICallService {

    @Autowired
    private FsClient fsClient;

    @Resource
    private ISipAgentService iSipAgentService;

    @Resource
    private ICallDisplayService iCallDisplayService;
    @Resource
    private ICorpInfoService corpInfoService;

    @Autowired
    private IFsCallCacheService iFsCallCacheService;

    @Autowired
    private ICallRecordService iCallRecordService;

    @Autowired
    private IFsSipGatewayService iFsSipGatewayService;

    @Override
    public Long makeCall(CallQuery query) {
        if (Objects.isNull(query.getAgentId())) {
            throw new CommonException("agentId cannot be null");
        }
        SipAgentQuery sipAgentQuery = new SipAgentQuery();
        sipAgentQuery.setId(query.getAgentId());
        List<SipAgentVo> agentList = iSipAgentService.getInfoByQuery(sipAgentQuery);
        if (CollectionUtil.isEmpty(agentList)) {
            throw new CommonException("agent not found");
        }

        SipAgentVo sipAgent = agentList.get(0);
        if (StringUtils.isBlank(sipAgent.getAgentNumber())) {
            throw new CommonException("坐席未配置SIP号码");
        }
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            throw new CommonException("FreeSWITCH服务不可用，请稍后重试");
        }
        if (!fsClient.isWebRtcUserRegistered(address, sipAgent.getAgentNumber())) {
            throw new CommonException("浏览器软电话未注册，请先注册后再拨号");
        }

        Long callId = IdUtil.getSnowflakeNextId();
        String uniqueId = RandomUtil.randomNumbers(32);
        CallInfo callInfo = CallInfo.builder().callId(callId)
                .agentId(sipAgent.getId()).agentNumber(sipAgent.getAgentNumber()).agentName(sipAgent.getName())
                .caller(sipAgent.getAgentNumber()).callee(query.getCallee()).direction(DirectionEnum.OUTBOUND.getType())
                .callTime(DateUtil.current()).hiddenCustomer(query.getHiddenCustomer())
                .calleeTimeOut(query.getCalleeTimeOut()).build();
        callInfo.addUniqueIdList(uniqueId);
        callInfo.setProcess(ProcessEnum.CALL_ROUTE);

        CallDisplayQuery poolQuery = new CallDisplayQuery();
        List<CallDisplayVo> displayList = iCallDisplayService.getList(poolQuery);
        if (CollectionUtil.isEmpty(displayList)) {
            throw new CommonException("call display not configured");
        }
        CallDisplayVo callDisplay = RandomUtil.randomEle(displayList);
        callInfo.setCallerDisplay(query.getCallee());
        callInfo.setCalleeDisplay(callDisplay.getPhone());

        ChannelInfo channelInfo = ChannelInfo.builder().callId(callId).uniqueId(uniqueId).cdrType(2).type(2)
                .agentId(sipAgent.getId()).agentNumber(sipAgent.getAgentNumber()).agentName(sipAgent.getName())
                .callTime(DateUtil.current())
                .caller(callInfo.getCallee()).called(callInfo.getCaller()).display(callInfo.getCalleeDisplay()).build();
        callInfo.setChannelInfoMap(uniqueId, channelInfo);

        iFsCallCacheService.saveCallInfo(callInfo);
        iFsCallCacheService.saveCallRel(uniqueId, callId);

        CallRouteVo callRoute = iFsCallCacheService.getCallRoute(callInfo.getCallee(), 2);
        if (Objects.isNull(callRoute)) {
            throw new CommonException("call route not configured");
        }
        FsSipGateway sipGateway = iFsSipGatewayService.getDetail(Long.valueOf(callRoute.getRouteValue()));
        fsClient.makeCall(address, callId, callInfo.getCallee(), callInfo.getCalleeDisplay(), uniqueId,
                query.getCallerTimeOut(), sipGateway);
        return callId;
    }

    @Override
    public void hangup(Long callId) {
        if (Objects.isNull(callId)) {
            throw new CommonException("callId cannot be null");
        }
        CallInfo callInfo = iFsCallCacheService.getCallInfo(callId);
        fsClient.killCallsByCallId(callId);
        if (Objects.isNull(callInfo) || Objects.isNull(callInfo.getChannelMap())) {
            log.info("call cache is empty after kill by callId:{}", callId);
            return;
        }
        callInfo.getChannelMap().values().forEach(channelInfo -> {
            boolean keepCallerForSatisfaction = callInfo.getFlowDataContext() != null
                    && channelInfo != null
                    && Objects.equals(channelInfo.getDirectionType(), 1);
            if (keepCallerForSatisfaction) {
                log.info("keep caller channel for satisfaction, callId:{}, uniqueId:{}",
                        callId, channelInfo.getUniqueId());
            } else if (channelInfo != null && StringUtils.isNotBlank(channelInfo.getUniqueId())) {
                fsClient.killCall(callId, channelInfo.getUniqueId());
            }
        });
    }

    @Override
    public void hangupAgent(Long agentId) {
        if (Objects.isNull(agentId)) {
            throw new CommonException("invalid request");
        }
        SipAgentQuery sipAgentQuery = new SipAgentQuery();
        sipAgentQuery.setId(agentId);
        List<SipAgentVo> agentList = iSipAgentService.getInfoByQuery(sipAgentQuery);
        if (CollectionUtil.isEmpty(agentList) || StringUtils.isBlank(agentList.get(0).getAgentNumber())) {
            throw new CommonException("invalid request");
        }
        fsClient.killCallsByAgentNumber(agentList.get(0).getAgentNumber());
    }

    @Override
    public void hangupAgentNumber(String agentNumber) {
        if (StringUtils.isBlank(agentNumber)) {
            throw new CommonException("invalid request");
        }
        fsClient.killCallsByAgentNumber(agentNumber);
    }

    @Override
    public CallRecordVo getCallInfo(Long callId) {
        CallRecordVo callRecordVo = new CallRecordVo();
        CallRecord callRecord = iCallRecordService.getById(callId);
        if(Objects.isNull(callRecord)){
            throw new CommonException("configuration missing");
        }
        BeanUtils.copyProperties(callRecord, callRecordVo);
        return callRecordVo;
    }

    @Override
    public List<CallRecordVo> getCallPageList(CallRecordQuery query) {
        return iCallRecordService.getCallPageList(query);
    }



}

