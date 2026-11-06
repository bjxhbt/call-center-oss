package com.och.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.och.common.base.BaseServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.exception.CommonException;
import com.och.system.domain.entity.NumberTransformRule;
import com.och.system.domain.query.number.NumberTransformRuleAddQuery;
import com.och.system.domain.query.number.NumberTransformRuleQuery;
import com.och.system.domain.vo.number.NumberTransformRuleVo;
import com.och.system.mapper.NumberTransformRuleMapper;
import com.och.system.service.INumberTransformRuleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 号码转换规则 CRUD 服务实现
 *
 * @author och
 */
@Service
public class NumberTransformRuleServiceImpl
        extends BaseServiceImpl<NumberTransformRuleMapper, NumberTransformRule>
        implements INumberTransformRuleService {

    @Override
    public void add(NumberTransformRuleAddQuery query) {
        if (checkName(query.getName(), null)) {
            throw new CommonException("规则名称已存在");
        }
        NumberTransformRule rule = new NumberTransformRule();
        BeanUtil.copyProperties(query, rule);
        if (Objects.isNull(rule.getStatus())) {
            rule.setStatus(0);
        }
        if (Objects.isNull(rule.getPriority())) {
            rule.setPriority(100);
        }
        save(rule);
    }

    @Override
    public void edit(NumberTransformRuleAddQuery query) {
        NumberTransformRule rule = getById(query.getId());
        if (Objects.isNull(rule)) {
            throw new CommonException("无效ID");
        }
        if (checkName(query.getName(), query.getId())) {
            throw new CommonException("规则名称已存在");
        }
        NumberTransformRule update = new NumberTransformRule();
        BeanUtil.copyProperties(query, update);
        updateById(update);
    }

    @Override
    public NumberTransformRuleVo getDetail(Long id) {
        return baseMapper.getDetail(id);
    }

    @Override
    public void delete(NumberTransformRuleQuery query) {
        List<Long> ids = collectIds(query);
        if (CollectionUtil.isEmpty(ids)) {
            return;
        }
        List<NumberTransformRule> list = ids.stream().map(id -> {
            NumberTransformRule rule = new NumberTransformRule();
            rule.setId(id);
            rule.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
            return rule;
        }).collect(Collectors.toList());
        updateBatchById(list);
    }

    @Override
    public List<NumberTransformRuleVo> getList(NumberTransformRuleQuery query) {
        return baseMapper.getList(query);
    }

    @Override
    public List<NumberTransformRuleVo> getPageList(NumberTransformRuleQuery query) {
        startPage(query.getPageIndex(), query.getPageSize(), query.getSortField(), query.getSort());
        return getList(query);
    }

    /**
     * 查询某方向、某网关下所有启用的规则(优先级升序)，供号码转换服务调用。
     * gatewayId 为 null 时仅返回未绑定具体网关的通用规则。
     */
    public List<NumberTransformRule> listEnabledRules(Integer direction, Long gatewayId) {
        LambdaQueryWrapper<NumberTransformRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NumberTransformRule::getDirection, direction)
                .eq(NumberTransformRule::getStatus, 1);
        if (gatewayId == null) {
            wrapper.isNull(NumberTransformRule::getGatewayId);
        } else {
            wrapper.and(w -> w.isNull(NumberTransformRule::getGatewayId)
                    .or().eq(NumberTransformRule::getGatewayId, gatewayId));
        }
        wrapper.orderByAsc(NumberTransformRule::getPriority).orderByAsc(NumberTransformRule::getId);
        return list(wrapper);
    }

    private boolean checkName(String name, Long excludeId) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        LambdaQueryWrapper<NumberTransformRule> wrapper = new LambdaQueryWrapper<NumberTransformRule>()
                .eq(NumberTransformRule::getName, name);
        if (excludeId != null) {
            wrapper.ne(NumberTransformRule::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    private List<Long> collectIds(NumberTransformRuleQuery query) {
        java.util.LinkedList<Long> ids = new java.util.LinkedList<>();
        if (query.getId() != null) {
            ids.add(query.getId());
        }
        if (CollectionUtil.isNotEmpty(query.getIds())) {
            ids.addAll(query.getIds());
        }
        return ids;
    }
}
