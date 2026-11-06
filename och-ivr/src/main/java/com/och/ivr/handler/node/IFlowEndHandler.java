package com.och.ivr.handler.node;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.exception.FlowNodeException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.domain.vo.FlowNodeVo;
import com.och.ivr.domain.entity.FlowInstances;
import com.och.ivr.properties.FlowEndNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Date;

/**
 * 结束节点处理
 *
 * @author danmo
 * @date 2024-12-26
 */
@Slf4j
@Component("FlowEndHandler")
public class IFlowEndHandler extends AbstractIFlowNodeHandler {


    public IFlowEndHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
    }

    @Override
    public void execute(FlowDataContext flowData) {
        log.info("结束节点处理 flowData：{}", flowData);
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        if(Objects.isNull(flowNode)){
            throw new FlowNodeException("结束节点配置错误");
        }
        CallInfo callInfo = fsCallCacheService.getCallInfo(flowData.getCallId());
        if (Objects.isNull(callInfo)){
            throw new FlowNodeException("callInfo is null");
        }
        if (callInfo.getDetailList() != null) {
            callInfo.getDetailList().forEach(detail -> {
                if (Objects.equals(detail.getTransferType(), 2)){
                    detail.setEndTime(DateUtil.current());
                }
            });
        }
        callInfo.setDetailList(callInfo.getDetailList());
        callInfo.setFlowDataContext(flowData);
        fsCallCacheService.saveCallInfo(callInfo);
        String properties = flowNode.getProperties();
        if(StringUtils.isNotBlank(properties)){
            FlowEndNodeProperties endNodeProperties = JSONObject.parseObject(properties, FlowEndNodeProperties.class);
            //判断是否挂机
            if (Boolean.TRUE.equals(endNodeProperties.getHangUp())){
                log.info("结束节点执行挂机 callId:{}, uniqueId:{}", flowData.getCallId(), flowData.getUniqueId());
                fsClient.hangupCall(flowData.getAddress(), flowData.getCallId(), flowData.getUniqueId());
            }
        }
        FlowInstances instances = FlowInstances.builder()
                .id(flowData.getInstanceId())
                .status(2)
                .endTime(new Date())
                .currentNodeId(flowData.getCurrentNodeId())
                .variables(JSON.toJSONString(FlowVariables.all(redisService, flowData.getInstanceId())))
                .build();
        iFlowInstancesService.updateById(instances);
        iFlowNoticeService.notice(3, "end", flowData);

    }

}
