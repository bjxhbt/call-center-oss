package com.och.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.system.domain.entity.NumberTransformRule;
import com.och.system.domain.query.number.NumberTransformRuleQuery;
import com.och.system.domain.vo.number.NumberTransformRuleVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 号码转换规则 Mapper
 *
 * @author och
 */
@Mapper
public interface NumberTransformRuleMapper extends BaseMapper<NumberTransformRule> {

    /**
     * 条件查询(分页/不分页通用)
     */
    List<NumberTransformRuleVo> getList(NumberTransformRuleQuery query);

    /**
     * 详情
     */
    NumberTransformRuleVo getDetail(Long id);
}
