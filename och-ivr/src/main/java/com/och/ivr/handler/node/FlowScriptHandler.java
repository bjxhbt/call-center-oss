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
import com.och.ivr.properties.FlowScriptNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本节点处理。对应原系统 script 节点。
 * 解析简单赋值语句(var name=值 / name=值)写入流程变量，然后下一步。
 */
@Slf4j
@Component("FlowScriptHandler")
public class FlowScriptHandler extends AbstractIFlowNodeHandler {

    private static final Pattern ASSIGN = Pattern.compile("(?:var\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^;\\n]+)");

    public FlowScriptHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
    }

    @Override
    public void execute(FlowDataContext flowData) throws FlowNodeException {
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        if (Objects.isNull(flowNode)) {
            throw new FlowNodeException("脚本节点配置错误");
        }
        FlowScriptNodeProperties props = JSONObject.parseObject(flowNode.getProperties(), FlowScriptNodeProperties.class);
        if (props != null && StringUtils.isNotBlank(props.getContent())) {
            Matcher m = ASSIGN.matcher(props.getContent());
            while (m.find()) {
                String name = m.group(1);
                String val = m.group(2);
                if (val != null && val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
                    val = val.substring(1, val.length() - 1);
                }
                if (val != null) val = val.trim();
                FlowVariables.put(redisService, flowData.getInstanceId(), name, val);
            }
        }
        String event = props != null && StringUtils.isNotBlank(props.getEvent()) ? props.getEvent() : "next";
        iFlowNoticeService.notice(2, event, flowData);
    }
}
