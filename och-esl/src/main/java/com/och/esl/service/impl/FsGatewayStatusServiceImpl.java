package com.och.esl.service.impl;

import com.och.esl.client.FsClient;
import com.och.esl.service.IFsGatewayStatusService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.fssip.FsSipGatewayQuery;
import com.och.system.domain.vo.fssip.GatewayStatusVo;
import com.och.system.service.IFsSipGatewayService;
import com.och.system.util.SofiaStatusParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 网关状态检测与动态重载实现。
 *
 * @author och
 */
@Slf4j
@Service
public class FsGatewayStatusServiceImpl implements IFsGatewayStatusService {

    private static final String PROFILE_EXTERNAL = "external";

    @Lazy
    @Autowired
    private FsClient fsClient;

    @Autowired
    private IFsSipGatewayService iFsSipGatewayService;

    @Override
    public GatewayStatusVo getStatus(Long gatewayId) {
        GatewayStatusVo vo = new GatewayStatusVo();
        vo.setId(gatewayId);
        vo.setStateCode(SofiaStatusParser.STATE_NOT_FOUND);
        vo.setState("UNKNOWN");
        vo.setFailReason("网关不存在");
        if (gatewayId == null) {
            return vo;
        }
        FsSipGateway gateway = iFsSipGatewayService.getDetail(gatewayId);
        if (gateway == null || StringUtils.isBlank(gateway.getName())) {
            return vo;
        }
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            vo.setName(gateway.getName());
            vo.setFailReason("无可用 FreeSWITCH ESL 连接");
            return vo;
        }
        String output = fsClient.sofiaStatusGateway(address, gateway.getName());
        GatewayStatusVo parsed = SofiaStatusParser.parseGateway(output, gateway.getName());
        parsed.setId(gatewayId);
        parsed.setStateDesc(SofiaStatusParser.stateDesc(parsed.getStateCode()));
        // 若为 IP 认证型(不注册)且状态未知，回显为"IP认证型(无需注册)"
        if (gateway.getRegister() != null && gateway.getRegister() == 0
                && (parsed.getStateCode() == null || parsed.getStateCode() == SofiaStatusParser.STATE_UNKNOWN)) {
            parsed.setStateCode(SofiaStatusParser.STATE_REGISTERED);
            parsed.setState("IP_AUTH");
            parsed.setStateDesc("IP认证型(无需注册)");
            parsed.setFailReason(null);
        }
        return parsed;
    }

    @Override
    public List<GatewayStatusVo> listStatus() {
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            log.warn("listStatus: 无可用 FreeSWITCH ESL 连接");
            return Collections.emptyList();
        }
        String output = fsClient.sofiaStatusGateway(address, null);
        Map<String, String> rows = SofiaStatusParser.parseGatewayList(output);
        // 以数据库网关为基准，逐个补充状态
        List<FsSipGateway> gateways = iFsSipGatewayService.getList(new FsSipGatewayQuery());
        List<GatewayStatusVo> result = new ArrayList<>();
        if (gateways != null) {
            for (FsSipGateway gateway : gateways) {
                if (gateway == null || gateway.getType() == null || gateway.getType() != 2) {
                    continue;
                }
                GatewayStatusVo one = new GatewayStatusVo();
                one.setId(gateway.getId());
                one.setName(gateway.getName());
                one.setProxy(gateway.getProxy());
                one.setRealm(gateway.getRealm());
                one.setUsername(gateway.getUserName());
                one.setRegister(gateway.getRegister());
                String row = rows.get(gateway.getName());
                if (StringUtils.isNotBlank(row)) {
                    // 表格最后一列通常为 State
                    String[] parts = row.split("\\s+");
                    String state = parts.length > 0 ? parts[parts.length - 1] : "";
                    one.setState(state);
                    one.setStateCode(SofiaStatusParser.mapState(state));
                } else {
                    one.setState("UNLOADED");
                    one.setStateCode(SofiaStatusParser.STATE_FAILED);
                    one.setFailReason("网关未加载到 FreeSWITCH(可能需要 rescan)");
                }
                one.setStateDesc(SofiaStatusParser.stateDesc(one.getStateCode()));
                result.add(one);
            }
        }
        return result;
    }

    @Override
    public String reloadExternal() {
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            log.warn("reloadExternal: 无可用 FreeSWITCH ESL 连接");
            return "no FreeSWITCH connection";
        }
        return fsClient.sofiaProfileRescan(address, PROFILE_EXTERNAL);
    }

    @Override
    public String restartExternal() {
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            return "no FreeSWITCH connection";
        }
        return fsClient.sofiaProfileRestart(address, PROFILE_EXTERNAL);
    }

    @Override
    public String killGateway(String gatewayName) {
        if (StringUtils.isBlank(gatewayName)) {
            return "gatewayName is blank";
        }
        String address = fsClient.anyAddress();
        if (StringUtils.isBlank(address)) {
            return "no FreeSWITCH connection";
        }
        return fsClient.sofiaProfileKillgw(address, PROFILE_EXTERNAL, gatewayName);
    }
}
