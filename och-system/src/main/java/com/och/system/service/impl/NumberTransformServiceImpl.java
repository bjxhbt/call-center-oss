package com.och.system.service.impl;

import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.entity.NumberTransformRule;
import com.och.system.domain.vo.number.NumberTransformMatcher;
import com.och.system.domain.vo.number.NumberTransformResult;
import com.och.system.service.INumberTransformService;
import com.och.system.service.INumberTransformRuleService;
import com.och.system.service.IFsSipGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 号码转换服务实现：按优先级遍历启用规则，首个命中即变换并返回。
 *
 * @author och
 */
@Slf4j
@Service
public class NumberTransformServiceImpl implements INumberTransformService {

    @Autowired
    private INumberTransformRuleService numberTransformRuleService;

    @Autowired
    private IFsSipGatewayService iFsSipGatewayService;

    @Override
    public NumberTransformResult transform(Integer direction, String originalNumber, String originalCaller, Long gatewayId) {
        String number = StringUtils.defaultString(originalNumber);
        String caller = StringUtils.defaultString(originalCaller);
        NumberTransformResult miss = NumberTransformResult.notMatched(number, caller);
        if (StringUtils.isBlank(number)) {
            return miss;
        }
        miss.setGatewayId(gatewayId);
        fillGatewayName(miss, gatewayId);

        List<NumberTransformRule> rules;
        try {
            rules = numberTransformRuleService.listEnabledRules(direction, gatewayId);
        } catch (Exception e) {
            log.error("查询号码转换规则失败 direction:{}, gatewayId:{}", direction, gatewayId, e);
            return miss;
        }
        if (rules == null || rules.isEmpty()) {
            return miss;
        }

        for (NumberTransformRule rule : rules) {
            if (Objects.isNull(rule) || !NumberTransformMatcher.matches(rule, number)) {
                continue;
            }
            String transformed = NumberTransformMatcher.apply(rule, number);
            String transformedCaller = StringUtils.isNotBlank(rule.getCallerReplace())
                    ? rule.getCallerReplace() : caller;

            NumberTransformResult hit = new NumberTransformResult();
            hit.setOriginalNumber(number);
            hit.setTransformedNumber(transformed);
            hit.setOriginalCaller(caller);
            hit.setTransformedCaller(transformedCaller);
            hit.setMatched(true);
            hit.setRuleId(rule.getId());
            hit.setRuleName(rule.getName());
            hit.setGatewayId(rule.getGatewayId() != null ? rule.getGatewayId() : gatewayId);
            fillGatewayName(hit, hit.getGatewayId());
            log.info("号码转换命中规则 callNumber:{} -> {}, caller:{} -> {}, ruleId:{}, ruleName:{}, gatewayId:{}",
                    number, transformed, caller, transformedCaller, rule.getId(), rule.getName(), hit.getGatewayId());
            return hit;
        }
        return miss;
    }

    private void fillGatewayName(NumberTransformResult result, Long gatewayId) {
        if (gatewayId == null) {
            return;
        }
        try {
            FsSipGateway gateway = iFsSipGatewayService.getDetail(gatewayId);
            if (gateway != null) {
                result.setGatewayName(gateway.getName());
            }
        } catch (Exception e) {
            log.warn("查询网关名称失败 gatewayId:{}", gatewayId, e);
        }
    }
}
