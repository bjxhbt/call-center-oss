package com.och.system.service;

import com.och.system.domain.vo.number.NumberTransformResult;

/**
 * 号码转换服务：根据数据库配置的规则对被叫/主叫号码进行变换。
 * <p>
 * 具体号码规则(如 918xxxxxxxxx -&gt; 018xxxxxxxxx)全部由 number_transform_rule 表配置驱动。
 *
 * @author och
 */
public interface INumberTransformService {

    /**
     * 按方向与网关对号码执行转换。
     *
     * @param direction      方向 1-呼入 2-呼出(与DirectionEnum一致)
     * @param originalNumber 原始被叫号码
     * @param originalCaller 原始主叫号码(可为空)
     * @param gatewayId      目标网关ID(可为空，表示使用通用规则)
     * @return 转换结果，包含原始号码、转换后号码、主叫转换结果、命中规则与网关信息
     */
    NumberTransformResult transform(Integer direction, String originalNumber, String originalCaller, Long gatewayId);
}
