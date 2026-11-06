package com.och.esl.handler.route;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.och.common.annotation.EslRouteName;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.ProcessEnum;
import com.och.common.enums.RouteTypeEnum;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.vo.number.NumberTransformResult;
import com.och.system.domain.vo.call.CallTrace;
import com.och.system.service.ICallTraceStore;
import com.och.system.service.INumberTransformService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 呼出路由处理器：被叫号码经数据库驱动的号码转换规则规范化后，通过指定 SIP 网关呼出。
 * <p>
 * 号码变换(去前缀/加前缀/替换/主叫替换，如 918xxxxxxxxx -&gt; 018xxxxxxxxx)全部由
 * number_transform_rule 表配置驱动；未命中任何规则时回退到旧版接入码属性配置。
 *
 * @author danmo
 * @date 2023-11-10 17:20
 **/
@EslRouteName(RouteTypeEnum.CALLOUT)
@Component
@Slf4j
public class FsCallOutRouteHandler extends FsAbstractRouteHandler {

    @Value("${freeswitch.outbound.access-prefix:}")
    private String accessPrefix;

    @Value("${freeswitch.outbound.destination-prefix:}")
    private String destinationPrefix;

    @Autowired
    private INumberTransformService numberTransformService;

    @Autowired
    private ICallTraceStore callTraceStore;

    @Override
    public void handler(String address, CallInfo callInfo, String uniqueId, String sipGatewayId) {
        log.info("转外呼 callId:{} transfer to {}", callInfo.getCallId(), sipGatewayId);
        String otherUniqueId = RandomUtil.randomNumbers(32);

        //构建被叫通道
        ChannelInfo otherChannelInfo = ChannelInfo.builder().callId(callInfo.getCallId()).uniqueId(otherUniqueId).cdrType(2).type(2).directionType(2)
                .callTime(DateUtil.current()).otherUniqueId(uniqueId)
                .called(callInfo.getCallee()).caller(callInfo.getCaller()).display(callInfo.getCallerDisplay()).build();
        callInfo.addUniqueIdList(otherUniqueId);
        callInfo.setChannelInfoMap(otherUniqueId,otherChannelInfo);
        callInfo.setProcess(ProcessEnum.CALL_BRIDGE);

        CallInfoDetail detail = new CallInfoDetail();
        detail.setCallId(callInfo.getCallId());
        detail.setStartTime(DateUtil.current());
        detail.setOrderNum(callInfo.getDetailList() == null ? 0 : callInfo.getDetailList().size() + 1);
        detail.setTransferType(5);

        FsSipGateway sipGateway = iFsSipGatewayService.getDetail(Long.valueOf(sipGatewayId));
        if (sipGateway == null || StringUtils.isBlank(sipGateway.getName())) {
            log.error("Outbound gateway not found, callId:{}, gatewayId:{}", callInfo.getCallId(), sipGatewayId);
            recordFailure(callInfo, sipGatewayId, "呼出网关不存在: gatewayId=" + sipGatewayId);
            fsClient.hangupCall(address, callInfo.getCallId(), uniqueId);
            return;
        }

        // 号码转换：优先使用数据库配置的 number_transform_rule，未命中则回退属性配置
        Long gatewayId = sipGateway.getId();
        NumberTransformResult transform = numberTransformService.transform(
                com.och.common.enums.DirectionEnum.OUTBOUND.getType(),
                callInfo.getCallee(), callInfo.getCallerDisplay(), gatewayId);
        String destination = transform.isMatched() ? transform.getTransformedNumber() : formatDestination(callInfo.getCallee());
        String calleeDisplay = transform.isMatched() && StringUtils.isNotBlank(transform.getTransformedCaller())
                ? transform.getTransformedCaller() : callInfo.getCalleeDisplay();

        // 暂存原始号码/转换后号码/路由/网关，供话单与录音关联
        CallTrace trace = callTraceStore.getOrCreate(callInfo.getCallId());
        trace.setOriginalCallee(callInfo.getCallee());
        trace.setConvertedCallee(destination);
        trace.setOriginalCaller(callInfo.getCallerDisplay());
        trace.setConvertedCaller(calleeDisplay);
        trace.setRouteType(RouteTypeEnum.CALLOUT.getType());
        trace.setGatewayId(gatewayId);
        trace.setGatewayName(sipGateway.getName());
        if (transform.isMatched()) {
            trace.setRouteId(transform.getRuleId());
            trace.setRouteName(transform.getRuleName());
        }
        callTraceStore.save(callInfo.getCallId(), trace);

        log.info("Outbound route callId:{}, gateway:{}, dialed:{}, destination:{}, transformMatched:{}",
                callInfo.getCallId(), sipGateway.getName(), callInfo.getCallee(), destination, transform.isMatched());
        fsClient.makeCall(address,callInfo.getCallId(), destination,calleeDisplay,otherUniqueId,callInfo.getCalleeTimeOut(), sipGateway);
        detail.setEndTime(DateUtil.current());
        callInfo.addDetailList(detail);
        fsCallCacheService.saveCallInfo(callInfo);
        fsCallCacheService.saveCallRel(otherUniqueId,callInfo.getCallId());
    }

    private void recordFailure(CallInfo callInfo, String gatewayId, String reason) {
        try {
            CallTrace trace = callTraceStore.getOrCreate(callInfo.getCallId());
            trace.setGatewayId(StringUtils.isNotBlank(gatewayId) ? Long.valueOf(gatewayId) : null);
            trace.setOriginalCallee(callInfo.getCallee());
            trace.setFailureReason(reason);
            callTraceStore.save(callInfo.getCallId(), trace);
        } catch (Exception e) {
            log.warn("记录呼出失败原因异常 callId:{}", callInfo.getCallId(), e);
        }
    }

    private String formatDestination(String dialedNumber) {
        String destination = StringUtils.trimToEmpty(dialedNumber);
        if (StringUtils.isNotEmpty(accessPrefix) && destination.startsWith(accessPrefix)) {
            destination = destination.substring(accessPrefix.length());
        }
        if (StringUtils.isNotEmpty(destinationPrefix) && !destination.startsWith(destinationPrefix)) {
            destination = destinationPrefix + destination;
        }
        return destination;
    }
}
