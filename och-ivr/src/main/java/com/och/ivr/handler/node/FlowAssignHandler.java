package com.och.ivr.handler.node;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.FlowDataContext;
import com.och.common.exception.FlowNodeException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.domain.vo.FlowNodeVo;
import com.och.ivr.properties.FlowAssignNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 赋值节点处理。对应原系统 assign 节点。
 * 将表达式计算结果写入流程变量，然后走 event(默认 next)。
 */
@Slf4j
@Component("FlowAssignHandler")
public class FlowAssignHandler extends AbstractIFlowNodeHandler {

    public FlowAssignHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
    }

    @Override
    public void execute(FlowDataContext flowData) throws FlowNodeException {
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        if (Objects.isNull(flowNode)) {
            throw new FlowNodeException("赋值节点配置错误");
        }
        FlowAssignNodeProperties props = JSONObject.parseObject(flowNode.getProperties(), FlowAssignNodeProperties.class);
        if (props == null || StringUtils.isBlank(props.getVariable())) {
            // 无变量定义，直接下一步
            iFlowNoticeService.notice(2, props != null && StringUtils.isNotBlank(props.getEvent()) ? props.getEvent() : "next", flowData);
            return;
        }
        Map<String, String> vars = FlowVariables.all(redisService, flowData.getInstanceId());
        String value = evalExpression(props.getExpression(), vars);
        FlowVariables.put(redisService, flowData.getInstanceId(), props.getVariable(), value);
        if ("myd".equalsIgnoreCase(props.getVariable()) && flowData.getCallId() != null) {
            redisService.setCacheObject("och:call:satisfaction:" + flowData.getCallId(), value,
                    7, TimeUnit.DAYS);
        }
        log.info("赋值节点 {}={} flowData:{}", props.getVariable(), value, flowData.getCallId());
        String event = StringUtils.isNotBlank(props.getEvent()) ? props.getEvent() : "next";
        iFlowNoticeService.notice(2, event, flowData);
    }

    /** 计算表达式。支持 date_to_string(sysdate(),"fmt")，以及 ${var} 引用，其余按字面量。 */
    private String evalExpression(String expr, Map<String, String> vars) {
        if (StringUtils.isBlank(expr)) {
            return "";
        }
        // 数值自增：形如 errorinput+1 / num+1 之类，这里支持简单 "<var>+1" / "<var>-1"
        if (expr.matches("^[A-Za-z_][A-Za-z0-9_]*\\s*\\+\\s*\\d+$")) {
            String[] parts = expr.split("\\+");
            String name = parts[0].trim();
            int delta = Integer.parseInt(parts[1].trim());
            String cur = vars.get(name);
            int val = 0;
            if (StringUtils.isNotBlank(cur)) {
                try { val = Integer.parseInt(cur.trim()); } catch (NumberFormatException ignore) { val = 0; }
            }
            return String.valueOf(val + delta);
        }
        // date_to_string(sysdate(),"fmt")
        String resolved = FlowFunctions.dateToString(expr, new Date());
        if (resolved != null) {
            return resolved;
        }
        // date_to_string(now,"fmt") 用当前时间
        // ${var} 替换
        return substituteVars(expr, vars);
    }

    private String substituteVars(String expr, Map<String, String> vars) {
        String s = expr;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("${" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        // 去掉首尾引号(若整体是字面量)
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }
}
