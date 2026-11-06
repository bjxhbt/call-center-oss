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
import com.och.ivr.properties.FlowDatabaseNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;

/**
 * 数据库节点处理。对应原系统 database 节点。
 * 执行SQL，结果集第一行的各列(按别名)写入流程变量。
 */
@Slf4j
@Component("FlowDatabaseHandler")
public class FlowDatabaseHandler extends AbstractIFlowNodeHandler {

    public FlowDatabaseHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
    }

    @Override
    public void execute(FlowDataContext flowData) throws FlowNodeException {
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        if (Objects.isNull(flowNode)) {
            throw new FlowNodeException("数据库节点配置错误");
        }
        FlowDatabaseNodeProperties props = JSONObject.parseObject(flowNode.getProperties(), FlowDatabaseNodeProperties.class);
        if (props == null || StringUtils.isBlank(props.getQuery())) {
            iFlowNoticeService.notice(2, "next", flowData);
            return;
        }
        Map<String, String> vars = FlowVariables.all(redisService, flowData.getInstanceId());
        String sql = renderSql(props.getQuery(), vars);
        String url = "jdbc:mysql://" + props.getIp() + ":" + props.getPort() + "/" + props.getSchema() + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
        log.info("数据库节点执行SQL: {} url:{}", sql, url);
        String driver = StringUtils.isNotBlank(props.getDriver()) ? props.getDriver() : "com.mysql.cj.jdbc.Driver";
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            // 兼容旧驱动名 com.mysql.jdbc.Driver
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e2) {
                throw new FlowNodeException("数据库驱动不存在(" + driver + "): " + e2.getMessage() + "。请在 och-ivr 中引入 mysql-connector-j 依赖。");
            }
        }
        try (Connection conn = DriverManager.getConnection(url, props.getUsername(), props.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            if (rs.next()) {
                int n = meta.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    String col = meta.getColumnLabel(i);
                    String val = rs.getString(i);
                    FlowVariables.put(redisService, flowData.getInstanceId(), col, val == null ? "" : val);
                }
            }
        } catch (Exception e) {
            log.error("数据库节点执行失败 callId:{} sql:{} err:{}", flowData.getCallId(), sql, e.getMessage(), e);
            throw new FlowNodeException("数据库节点执行失败: " + e.getMessage());
        }
        String event = StringUtils.isNotBlank(props.getEvent()) ? props.getEvent() : "next";
        iFlowNoticeService.notice(2, event, flowData);
    }

    /** SQL 中支持 {var} 与 ${var} 两种占位，引用流程变量 */
    private String renderSql(String sql, Map<String, String> vars) {
        String s = sql;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("${" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        // 原系统使用 {\"%\"+cid+\"%\"} 这种花括号块，统一替换 {name} 为变量值
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{[^{}]*?\"?%?\"?\\s*\\+?\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\+?\\s*\"?%?\"?[^{}]*?\\}");
        java.util.regex.Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String val = vars.get(name);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
