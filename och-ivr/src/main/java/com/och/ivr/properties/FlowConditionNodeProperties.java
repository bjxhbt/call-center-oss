package com.och.ivr.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 条件分支节点属性
 * 对应原系统 condition 节点：按表达式结果走不同分支(path_0/path_1/...)
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FlowConditionNodeProperties extends FlowNodeProperties {

    /**
     * 分支列表(按优先级从上到下)
     */
    private List<Branch> branches;

    /**
     * 默认分支事件(所有表达式都不匹配时)，对应原系统无表达式时的默认路径
     */
    private String defaultEvent;

    @Data
    public static class Branch {
        /**
         * 表达式(支持 ==,!=,>,>=,<,<=,&&,||,contains)；为空表示恒真(默认分支)
         */
        private String expression;
        /**
         * 命中后发送的事件名，对应 edge.event
         */
        private String event;
    }
}
