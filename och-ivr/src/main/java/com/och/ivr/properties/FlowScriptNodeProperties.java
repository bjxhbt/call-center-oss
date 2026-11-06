package com.och.ivr.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 脚本节点属性。对应原系统 script 节点(初始化变量等简单逻辑)。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FlowScriptNodeProperties extends FlowNodeProperties {

    /**
     * 脚本内容。支持简单语句列表，以分号或换行分隔，例如:
     *   var num=0;
     *   lastlevel="";
     */
    private String content;

    /**
     * 写入的目标变量名(可选，用于简单赋值场景)
     */
    private String pathVar;

    /**
     * 下一步事件，默认 next
     */
    private String event;
}
