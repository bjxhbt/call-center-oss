package com.och.ivr.handler.node;

import com.alibaba.fastjson.JSONObject;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.FlowDataContext;
import com.och.common.exception.FlowNodeException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.domain.vo.FlowNodeVo;
import com.och.ivr.properties.FlowConditionNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 条件分支节点处理。对应原系统 condition 节点。
 * 依次求值 branches 中的表达式，命中则发送对应 event；
 * 都不命中则发送 defaultEvent(或第一个空表达式分支)。
 */
@Slf4j
@Component("FlowConditionHandler")
public class FlowConditionHandler extends AbstractIFlowNodeHandler {

    public FlowConditionHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
    }

    @Override
    public void execute(FlowDataContext flowData) throws FlowNodeException {
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        if (Objects.isNull(flowNode)) {
            throw new FlowNodeException("条件节点配置错误");
        }
        FlowConditionNodeProperties props = JSONObject.parseObject(flowNode.getProperties(), FlowConditionNodeProperties.class);
        Map<String, String> vars = FlowVariables.all(redisService, flowData.getInstanceId());
        List<FlowConditionNodeProperties.Branch> branches = props == null ? null : props.getBranches();
        if (branches != null) {
            for (FlowConditionNodeProperties.Branch b : branches) {
                boolean hit = StringUtils.isBlank(b.getExpression()) || FlowExpression.evalBool(b.getExpression(), vars);
                if (hit) {
                    log.info("条件节点命中分支 expr={} event={} callId:{}", b.getExpression(), b.getEvent(), flowData.getCallId());
                    iFlowNoticeService.notice(2, StringUtils.isNotBlank(b.getEvent()) ? b.getEvent() : "next", flowData);
                    return;
                }
            }
        }
        String defaultEvent = props != null && StringUtils.isNotBlank(props.getDefaultEvent()) ? props.getDefaultEvent() : "next";
        log.info("条件节点走默认分支 event={} callId:{}", defaultEvent, flowData.getCallId());
        iFlowNoticeService.notice(2, defaultEvent, flowData);
    }
}
