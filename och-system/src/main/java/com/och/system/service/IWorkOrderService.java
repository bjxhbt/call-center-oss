package com.och.system.service;

import com.och.common.base.IBaseService;
import com.och.system.domain.entity.WorkOrder;

import java.util.List;

/**
 * 工单服务接口
 */
public interface IWorkOrderService extends IBaseService<WorkOrder> {

    Long saveDetails(WorkOrder workOrder);

    List<WorkOrder> listHistory(String phone, Long excludeId);

    /**
     * 根据来电记录创建工单
     *
     * @param callRecordId 呼叫记录ID
     * @param title        工单标题
     * @param description  工单描述
     * @param priority     优先级
     * @return 工单ID
     */
    Long createFromCall(Long callRecordId, String title, String description, Integer priority);

    /**
     * 根据callId字符串创建工单
     *
     * @param callId      呼叫唯一ID
     * @param title       工单标题
     * @param description 工单描述
     * @param priority    优先级
     * @return 工单ID
     */
    Long createFromCallId(String callId, String title, String description, Integer priority);

    /**
     * 鎸搩闃舵鑷姩鍒涘缓宸ュ崟
     *
     * @param callId       鍛煎彨ID
     * @param callerNumber 涓诲彨鍙风爜
     * @param calleeNumber 琚彨鍙风爜
     * @param agentId      鍧愬腑ID
     * @param agentName    鍧愬腑鍚嶇О
     * @return 宸ュ崟ID
     */
    Long createFromRinging(Long callId, String callerNumber, String calleeNumber, Long agentId, String agentName);

    void updateSatisfactionByCallId(String callId, String satisfaction, java.util.Date satisfactionTime);

    /**
     * 更新工单状态
     *
     * @param id       工单ID
     * @param status   目标状态
     * @param remark   处理备注
     * @param handlerId   处理人ID
     * @param handlerName 处理人名称
     */
    void updateStatus(Long id, Integer status, String remark, Long handlerId, String handlerName);
}
