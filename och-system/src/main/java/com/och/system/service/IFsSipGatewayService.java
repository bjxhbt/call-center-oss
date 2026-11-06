package com.och.system.service;

import com.och.common.base.IBaseService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.fssip.FsSipGatewayAddQuery;
import com.och.system.domain.query.fssip.FsSipGatewayQuery;

import java.util.List;

/**
 * SIP网关表(FsSipGateway)
 *
 * @author danmo
 * @date 2023-09-13 13:53:45
 */
public interface IFsSipGatewayService extends IBaseService<FsSipGateway> {

    void add(FsSipGatewayAddQuery query);

    void edit(FsSipGatewayAddQuery query);

    FsSipGateway getDetail(Long id);

    void delete(FsSipGatewayQuery query);

    List<FsSipGateway> getList(FsSipGatewayQuery query);

    List<FsSipGateway> getPageList(FsSipGatewayQuery query);

    /**
     * 按网关名称查询(动态加载网关不允许重名，用于重名检测)。
     */
    FsSipGateway getByName(String name);

    /**
     * 名称是否已存在(排除指定ID)，用于新增/修改时的重名校验。
     */
    boolean existsByName(String name, Long excludeId);

}
