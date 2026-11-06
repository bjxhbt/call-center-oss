package com.och.ivr.handler.node;

import com.alibaba.fastjson.JSONObject;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.FlowDataContext;
import com.och.common.exception.FlowNodeException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.esl.service.AgentTransferScheduleService;
import com.och.common.domain.CallInfo;
import com.och.system.service.IWorkOrderService;
import com.och.ivr.domain.vo.FlowNodeVo;
import com.och.ivr.handler.route.FlowAgentRouteHandler;
import com.och.ivr.handler.route.FlowCallOutRouteHandler;
import com.och.ivr.handler.route.FlowSipRouteHandler;
import com.och.ivr.handler.route.FlowSkillGroupRouteHandler;
import com.och.ivr.properties.FlowNodeProperties;
import com.och.ivr.properties.FlowTransferNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 转接节点处理
 * @author danmo
 * @date 2024-12-26
 */
@Slf4j
@Component("FlowTransferHandler")
public class FlowTransferHandler extends AbstractIFlowNodeHandler{

    private final FlowAgentRouteHandler flowAgentRouteHandler;
    private final FlowCallOutRouteHandler flowCallOutRouteHandler;
    private final FlowSipRouteHandler flowSipRouteHandler;
    private final FlowSkillGroupRouteHandler flowSkillGroupRouteHandler;
    private final AgentTransferScheduleService agentTransferScheduleService;
    private final IWorkOrderService workOrderService;

    public FlowTransferHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService, FlowAgentRouteHandler flowAgentRouteHandler, FlowCallOutRouteHandler flowCallOutRouteHandler, FlowSipRouteHandler flowSipRouteHandler, FlowSkillGroupRouteHandler flowSkillGroupRouteHandler, AgentTransferScheduleService agentTransferScheduleService, IWorkOrderService workOrderService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
        this.flowAgentRouteHandler = flowAgentRouteHandler;
        this.flowCallOutRouteHandler = flowCallOutRouteHandler;
        this.flowSipRouteHandler = flowSipRouteHandler;
        this.flowSkillGroupRouteHandler = flowSkillGroupRouteHandler;
        this.agentTransferScheduleService = agentTransferScheduleService;
        this.workOrderService = workOrderService;
    }


    @Override
    public void execute(FlowDataContext flowData) {
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        FlowTransferNodeProperties transferNodeProperties = JSONObject.parseObject(flowNode.getProperties(), FlowTransferNodeProperties.class);
        if(Objects.isNull(transferNodeProperties)){
            throw new FlowNodeException("转接节点配置错误");
        }
        if(StringUtils.isBlank(transferNodeProperties.getRouteValue())){
            throw new FlowNodeException("转接节点配置错误");
        }

        if (Objects.equals(transferNodeProperties.getRouteType(), 1)
                || Objects.equals(transferNodeProperties.getRouteType(), 4)) {
            // 转人工入口即静默落工单(幂等,已有则跳过):
            // 非工作时间拦截、坐席繁忙排队/溢出、无空闲坐席等场景坐席腿不会建立,
            // 挂在坐席腿 park 上的 createWorkOrderOnRinging 不会执行——在此兜底记录,
            // 且不推送任何弹屏(INCOMING_CALL 仅在坐席腿真实振铃时发送)。
            ensureWorkOrderSilently(flowData);
            CallInfo callInfo = fsCallCacheService.getCallInfo(flowData.getCallId());
            if (callInfo == null || !agentTransferScheduleService.allowTransfer(
                    flowData.getAddress(), callInfo, flowData.getUniqueId())) {
                return;
            }
        }

        switch (transferNodeProperties.getRouteType()){
            case 1 ->  flowAgentRouteHandler.handler(flowData,transferNodeProperties);
            case 2 -> flowCallOutRouteHandler.handler(flowData,transferNodeProperties);
            case 3 -> flowSipRouteHandler.handler(flowData,transferNodeProperties);
            case 4 -> flowSkillGroupRouteHandler.handler(flowData,transferNodeProperties);
            default -> throw new FlowNodeException("转接节点配置错误");
        }
    }

    /**
     * 静默落工单:按 callId 幂等(createFromRinging 已有则仅补空字段)。
     * 任何异常只记日志,绝不阻断转接主流程。
     */
    private void ensureWorkOrderSilently(FlowDataContext flowData) {
        try {
            CallInfo callInfo = fsCallCacheService.getCallInfo(flowData.getCallId());
            if (callInfo == null) {
                return;
            }
            workOrderService.createFromRinging(
                    callInfo.getCallId(),
                    callInfo.getCaller(),
                    callInfo.getCallee(),
                    callInfo.getAgentId(),
                    callInfo.getAgentName());
        } catch (Exception e) {
            log.warn("silent work-order create failed (ignored) callId:{} err:{}", flowData.getCallId(), e.getMessage());
        }
    }
}
