package com.och.system.service;

import com.och.common.base.IBaseService;
import com.och.system.domain.entity.NumberTransformRule;
import com.och.system.domain.query.number.NumberTransformRuleAddQuery;
import com.och.system.domain.query.number.NumberTransformRuleQuery;
import com.och.system.domain.vo.number.NumberTransformRuleVo;

import java.util.List;

/**
 * 号码转换规则 CRUD 服务
 *
 * @author och
 */
public interface INumberTransformRuleService extends IBaseService<NumberTransformRule> {

    void add(NumberTransformRuleAddQuery query);

    void edit(NumberTransformRuleAddQuery query);

    NumberTransformRuleVo getDetail(Long id);

    void delete(NumberTransformRuleQuery query);

    List<NumberTransformRuleVo> getList(NumberTransformRuleQuery query);

    List<NumberTransformRuleVo> getPageList(NumberTransformRuleQuery query);

    /**
     * 查询某方向、某网关下所有启用的规则(优先级升序)，供号码转换服务调用。
     * gatewayId 为 null 时仅返回未绑定具体网关的通用规则；非 null 时同时包含通用规则与该网关规则。
     */
    java.util.List<NumberTransformRule> listEnabledRules(Integer direction, Long gatewayId);
}
