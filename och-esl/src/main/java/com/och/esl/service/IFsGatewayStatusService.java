package com.och.esl.service;

import com.och.system.domain.vo.fssip.GatewayStatusVo;

import java.util.List;

/**
 * SIP 网关状态检测与动态重载服务。
 *
 * @author och
 */
public interface IFsGatewayStatusService {

    /**
     * 查询单个网关实时状态(sofia status gateway name)。
     */
    GatewayStatusVo getStatus(Long gatewayId);

    /**
     * 查询全部 external 网关状态(sofia status)。
     */
    List<GatewayStatusVo> listStatus();

    /**
     * 重新扫描 external profile，加载新增/修改的动态网关。
     * 返回 FreeSWITCH 原始回显。
     */
    String reloadExternal();

    /**
     * 重启 external profile(重新加载全部网关，会短暂中断注册)。
     */
    String restartExternal();

    /**
     * 杀掉指定网关(sofia profile external killgw name)，用于删除/替换网关时避免重复。
     */
    String killGateway(String gatewayName);
}
