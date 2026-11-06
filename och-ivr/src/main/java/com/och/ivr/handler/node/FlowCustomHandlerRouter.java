package com.och.ivr.handler.node;

import com.och.common.enums.FlowNodeTypeEnum;
import com.och.common.utils.SpringUtils;
import com.och.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点处理器路由。
 * 先查 FlowNodeTypeEnum(框架内置 0-8)；为空时回退到扩展节点的 businessType 映射。
 * 扩展节点：9-条件 10-赋值 11-数据库 12-脚本
 */
public final class FlowCustomHandlerRouter {

    public static final int CONDITION = 9;
    public static final int ASSIGN = 10;
    public static final int DATABASE = 11;
    public static final int SCRIPT = 12;

    private static final Map<Integer, String> EXT = new HashMap<>();

    static {
        EXT.put(CONDITION, "FlowConditionHandler");
        EXT.put(ASSIGN, "FlowAssignHandler");
        EXT.put(DATABASE, "FlowDatabaseHandler");
        EXT.put(SCRIPT, "FlowScriptHandler");
    }

    private FlowCustomHandlerRouter() {
    }

    public static AbstractIFlowNodeHandler get(Integer businessType) {
        if (businessType == null) {
            return null;
        }
        String name = FlowNodeTypeEnum.getHandler(businessType);
        if (StringUtils.isBlank(name)) {
            name = EXT.get(businessType);
        }
        if (StringUtils.isBlank(name)) {
            return null;
        }
        try {
            return SpringUtils.getBean(name, AbstractIFlowNodeHandler.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isExtension(Integer businessType) {
        return businessType != null && EXT.containsKey(businessType);
    }
}
