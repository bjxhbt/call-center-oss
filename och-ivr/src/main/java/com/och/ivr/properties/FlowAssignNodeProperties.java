package com.och.ivr.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 赋值节点属性
 * 对应原系统 assign 节点：向流程变量写入值
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FlowAssignNodeProperties extends FlowNodeProperties {

    /**
     * 变量名
     */
    private String variable;

    /**
     * 表达式/字面量。支持:
     *  - date_to_string(sysdate(),"fmt") 日期函数
     *  - 其它以 ${var} 引用流程变量
     *  - 纯文本作为字面量
     */
    private String expression;

    /**
     * 下一步事件，默认 next
     */
    private String event;
}
