package com.och.esl.handler.call;

import cn.hutool.core.date.DateUtil;
import com.och.common.annotation.EslProcessName;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.DirectionEnum;
import com.och.common.enums.ProcessEnum;
import com.och.esl.handler.route.FsAbstractRouteHandler;
import com.och.esl.utils.EslEventUtil;
import com.och.system.domain.vo.route.CallRouteVo;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 呼入路由处理：呼入始终先进入配置路由，日程仅在 IVR 转人工节点检查。
 *
 * @author danmo
 * @date 2023-10-23 17:05
 **/
@EslProcessName(ProcessEnum.CALL_ROUTE)
@Component
@Slf4j
public class FsCallIRouteProcess extends FsAbstractCallProcess {

    @Override
    public void handler(String address, EslEvent event, CallInfo callInfo, ChannelInfo lfsChannelInfo) {
        log.info("进入callRoute电话: callId:{} caller:{} called:{} uniqueId:{}, otherUniqueId:{}", callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(), lfsChannelInfo.getOtherUniqueId(), lfsChannelInfo.getUniqueId());
        // 从event中补充callee（Redis反序列化可能丢失该字段）
        if (callInfo.getCallee() == null) {
            String destNumber = EslEventUtil.getCallerDestinationNumber(event);
            if (destNumber != null) {
                log.info("callee为空，从event补充callee: {}", destNumber);
                callInfo.setCallee(destNumber);
            }
        }
        // 从event中补充channelInfo.uniqueId（Redis反序列化可能丢失该字段）
        if (lfsChannelInfo.getUniqueId() == null) {
            String uniqueId = EslEventUtil.getUniqueId(event);
            if (uniqueId != null) {
                log.info("uniqueId为空，从event补充uniqueId: {}", uniqueId);
                lfsChannelInfo.setUniqueId(uniqueId);
            }
        }
        lfsCallCacheService.saveCallInfo(callInfo);
        lfsCallCacheService.saveCallRel(lfsChannelInfo.getUniqueId(), callInfo.getCallId());
        // 暂存呼入链路可追溯信息(DID=被叫接入号、原始主叫)，挂断落库时合并进话单
        com.och.system.domain.vo.call.CallTrace inboundTrace = callTraceStore.getOrCreate(callInfo.getCallId());
        inboundTrace.setDid(callInfo.getCallee());
        inboundTrace.setOriginalCaller(callInfo.getCaller());
        callTraceStore.save(callInfo.getCallId(), inboundTrace);

        CallRouteVo callRoute = lfsCallCacheService.getCallRoute(callInfo.getCallee(), callInfo.getDirection());
        if (Objects.isNull(callRoute) && Objects.equals(callInfo.getDirection(), DirectionEnum.OUTBOUND.getType())) {
            CallRouteVo inboundRoute = lfsCallCacheService.getCallRoute(callInfo.getCallee(), DirectionEnum.INBOUND.getType());
            if (Objects.nonNull(inboundRoute)) {
                log.warn("呼叫方向与呼入路由冲突，已纠正为呼入 callId:{} callee:{} oldDirection:{}",
                        callInfo.getCallId(), callInfo.getCallee(), callInfo.getDirection());
                callInfo.setDirection(DirectionEnum.INBOUND.getType());
                lfsCallCacheService.saveCallInfo(callInfo);
                callRoute = inboundRoute;
            }
        }
        if (Objects.isNull(callRoute)) {
            log.info("进入callRoute电话但呼入号码查询为空 callId：{}，caller：{}，callee：{}", callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee());
            recordInboundFailure(callInfo, "呼入未匹配到路由(DID=" + callInfo.getCallee() + ")");
            fsClient.hangupCall(address, callInfo.getCallId(), lfsChannelInfo.getUniqueId());
            return;
        }
        // 记录命中的路由信息
        inboundTrace.setRouteId(callRoute.getId());
        inboundTrace.setRouteName(callRoute.getName());
        inboundTrace.setRouteType(callRoute.getRouteType());
        callTraceStore.save(callInfo.getCallId(), inboundTrace);

        // 假期日历覆盖: 先查今天是否特殊日(假期/调休)
        if (Objects.equals(callInfo.getDirection(), DirectionEnum.OUTBOUND.getType())) {
            String outboundRecordPath = sysSettingConfig.getFsProfile() + "/" + DateUtil.today() + "/" + callInfo.getCallId() + "_" + DateUtil.current() + sysSettingConfig.getFsFileSuffix();
            fsClient.record(address, callInfo.getCallId(), lfsChannelInfo.getUniqueId(), outboundRecordPath);
            callInfo.setRecord(outboundRecordPath);
            // 录音开始时间取实际发起录音时刻(此时 answerTime 尚未产生)，确保话单 record_start_time 非空
            callInfo.setRecordStartTime(System.currentTimeMillis());
            lfsCallCacheService.saveCallInfo(callInfo);
            log.info("Outbound call bypasses inbound schedule, callId:{}, routeType:{}, routeValue:{}",
                    callInfo.getCallId(), callRoute.getRouteType(), callRoute.getRouteValue());
            FsAbstractRouteHandler routeHandler = routeFactory.factory(callRoute.getRouteType());
            if (Objects.nonNull(routeHandler)) {
                routeHandler.handler(address, callInfo, lfsChannelInfo.getUniqueId(), callRoute.getRouteValue());
            } else {
                log.error("Outbound route handler not found, callId:{}, routeType:{}",
                        callInfo.getCallId(), callRoute.getRouteType());
                fsClient.hangupCall(address, callInfo.getCallId(), lfsChannelInfo.getUniqueId());
            }
            return;
        }

        log.info("呼入先进入配置路由，日程仅在转人工时检查 callId:{} routeType:{} routeValue:{}",
                callInfo.getCallId(), callRoute.getRouteType(), callRoute.getRouteValue());
        lfsCallCacheService.saveCallInfo(callInfo);
        FsAbstractRouteHandler routeHandler = routeFactory.factory(callRoute.getRouteType());
        if (Objects.nonNull(routeHandler)) {
            routeHandler.handler(address, callInfo, lfsChannelInfo.getUniqueId(), callRoute.getRouteValue());
        } else {
            log.error("呼入路由处理器不存在 callId:{} routeType:{}", callInfo.getCallId(), callRoute.getRouteType());
            fsClient.hangupCall(address, callInfo.getCallId(), lfsChannelInfo.getUniqueId());
        }
    }

    /**
     * 记录呼入失败原因(暂存到 CallTrace，挂断落库时合并进话单 failure_reason)。
     */
    private void recordInboundFailure(CallInfo callInfo, String reason) {
        try {
            com.och.system.domain.vo.call.CallTrace trace = callTraceStore.getOrCreate(callInfo.getCallId());
            trace.setDid(callInfo.getCallee());
            trace.setOriginalCaller(callInfo.getCaller());
            trace.setFailureReason(reason);
            callTraceStore.save(callInfo.getCallId(), trace);
        } catch (Exception e) {
            log.warn("记录呼入失败原因异常 callId:{}", callInfo.getCallId(), e);
        }
    }

}
