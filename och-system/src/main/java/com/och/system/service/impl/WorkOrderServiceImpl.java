package com.och.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.och.common.exception.CommonException;
import com.och.common.base.BaseServiceImpl;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.entity.WorkOrder;
import com.och.system.mapper.CallRecordMapper;
import com.och.system.mapper.WorkOrderMapper;
import com.och.system.service.IWorkOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 工单服务实现类
 */
@Slf4j
@Service
public class WorkOrderServiceImpl extends BaseServiceImpl<WorkOrderMapper, WorkOrder> implements IWorkOrderService {

    @Autowired
    private CallRecordMapper callRecordMapper;

    @Override
    public Long saveDetails(WorkOrder incoming) {
        WorkOrder target = null;
        if (incoming.getId() != null) {
            target = baseMapper.selectById(incoming.getId());
        }
        if (target == null && incoming.getCallId() != null && !incoming.getCallId().isBlank()) {
            target = baseMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getCallId, incoming.getCallId())
                    .orderByDesc(WorkOrder::getCreateTime)
                    .last("limit 1"));
        }

        boolean creating = target == null;
        if (creating) {
            target = new WorkOrder();
            target.setStatus(0);
            target.setCreateTime(new Date());
        }
        copyDetails(incoming, target);
        if (incoming.getStatus() != null && incoming.getStatus() >= 0 && incoming.getStatus() <= 3) {
            target.setStatus(incoming.getStatus());
            if (incoming.getStatus() == 2 || incoming.getStatus() == 3) {
                if (target.getFinishTime() == null) {
                    target.setFinishTime(new Date());
                }
            } else {
                target.setFinishTime(null);
            }
        }
        enrichCallFields(target);
        if (target.getPriority() == null) {
            target.setPriority(2);
        }
        if (target.getStatus() == null) {
            target.setStatus(0);
        }
        if (target.getTitle() == null || target.getTitle().isBlank()) {
            target.setTitle(buildTitle(target));
        }
        if ((target.getDescription() == null || target.getDescription().isBlank())
                && target.getAcceptanceContent() != null) {
            target.setDescription(target.getAcceptanceContent());
        }
        target.setUpdateTime(new Date());
        if (creating) {
            baseMapper.insert(target);
        } else {
            baseMapper.updateById(target);
        }
        log.info("Work order details saved: orderId={}, callId={}", target.getId(), target.getCallId());
        return target.getId();
    }

    @Override
    public List<WorkOrder> listHistory(String phone, Long excludeId) {
        if (phone == null || phone.isBlank()) {
            return List.of();
        }
        String normalized = phone.trim();
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .and(item -> item.eq(WorkOrder::getCallerNumber, normalized)
                        .or().eq(WorkOrder::getMobile1, normalized)
                        .or().eq(WorkOrder::getMobile2, normalized)
                        .or().eq(WorkOrder::getFixedPhone, normalized))
                .orderByDesc(WorkOrder::getCreateTime)
                .last("limit 50");
        if (excludeId != null) {
            wrapper.ne(WorkOrder::getId, excludeId);
        }
        return baseMapper.selectList(wrapper);
    }

    private void enrichCallFields(WorkOrder order) {
        CallRecord callRecord = null;
        if (order.getCallRecordId() != null) {
            callRecord = callRecordMapper.selectById(order.getCallRecordId());
        } else if (order.getCallId() != null && !order.getCallId().isBlank()) {
            callRecord = callRecordMapper.selectOne(new LambdaQueryWrapper<CallRecord>()
                    .eq(CallRecord::getCallId, order.getCallId())
                    .orderByDesc(CallRecord::getCreateTime)
                    .last("limit 1"));
        }
        if (callRecord == null) {
            return;
        }
        order.setCallRecordId(callRecord.getId());
        order.setCallId(callRecord.getCallId());
        if (order.getCallerNumber() == null || order.getCallerNumber().isBlank()) {
            order.setCallerNumber(callRecord.getCallerNumber());
        }
        if (order.getCalleeNumber() == null || order.getCalleeNumber().isBlank()) {
            order.setCalleeNumber(callRecord.getCalleeNumber());
        }
        order.setAgentId(callRecord.getAgentId());
        order.setAgentName(callRecord.getAgentName());
        order.setSatisfaction(callRecord.getSatisfaction());
        order.setSatisfactionTime(callRecord.getSatisfactionTime());
    }

    private String buildTitle(WorkOrder order) {
        String type = order.getWorkOrderType() == null || order.getWorkOrderType().isBlank()
                ? "咨询" : order.getWorkOrderType();
        String subject = order.getCustomerName();
        if (subject == null || subject.isBlank()) {
            subject = order.getCallerNumber();
        }
        return subject == null || subject.isBlank() ? type + "工单" : type + "-" + subject;
    }

    private void copyDetails(WorkOrder source, WorkOrder target) {
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setCustomerId(source.getCustomerId());
        target.setCustomerName(source.getCustomerName());
        target.setMobile1(source.getMobile1());
        target.setMobile2(source.getMobile2());
        target.setFixedPhone(source.getFixedPhone());
        target.setCustomerAddress(source.getCustomerAddress());
        target.setCustomerType(source.getCustomerType());
        target.setCompanyName(source.getCompanyName());
        target.setWorkOrderType(source.getWorkOrderType());
        target.setCallRecordId(source.getCallRecordId());
        target.setCallId(source.getCallId());
        target.setCallerNumber(source.getCallerNumber());
        target.setCalleeNumber(source.getCalleeNumber());
        target.setNumberLocation(source.getNumberLocation());
        target.setBusinessType(source.getBusinessType());
        target.setIssueType(source.getIssueType());
        target.setAcceptanceContent(source.getAcceptanceContent());
        target.setAttachmentsJson(source.getAttachmentsJson());
        target.setPriority(source.getPriority());
    }

    @Override
    public Long createFromCall(Long callRecordId, String title, String description, Integer priority) {
        CallRecord callRecord = callRecordMapper.selectById(callRecordId);
        if (callRecord == null) {
            throw new CommonException("呼叫记录不存在");
        }

        WorkOrder order = new WorkOrder();
        order.setTitle(title);
        order.setDescription(description);
        order.setCallRecordId(callRecordId);
        order.setCallId(callRecord.getCallId());
        order.setCallerNumber(callRecord.getCallerNumber());
        order.setCalleeNumber(callRecord.getCalleeNumber());
        order.setAgentId(callRecord.getAgentId());
        order.setAgentName(callRecord.getAgentName());
        order.setSatisfaction(callRecord.getSatisfaction());
        order.setSatisfactionTime(callRecord.getSatisfactionTime());
        order.setPriority(priority != null ? priority : 2);
        order.setStatus(0);
        order.setCreateTime(new Date());

        baseMapper.insert(order);
        log.info("Work order created from call: orderId={}, callRecordId={}", order.getId(), callRecordId);
        return order.getId();
    }

    @Override
    public Long createFromCallId(String callId, String title, String description, Integer priority) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallRecord::getCallId, callId);
        CallRecord callRecord = callRecordMapper.selectOne(wrapper);

        LambdaQueryWrapper<WorkOrder> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(WorkOrder::getCallId, callId).last("limit 1");
        WorkOrder existing = baseMapper.selectOne(orderWrapper);
        if (existing != null) {
            boolean changed = false;
            if (title != null && !title.isEmpty() && !title.equals(existing.getTitle())) {
                existing.setTitle(title);
                changed = true;
            }
            if (description != null && !description.equals(existing.getDescription())) {
                existing.setDescription(description);
                changed = true;
            }
            if (priority != null && (existing.getPriority() == null || !existing.getPriority().equals(priority))) {
                existing.setPriority(priority);
                changed = true;
            }
            if (callRecord != null) {
                if (existing.getCallRecordId() == null) {
                    existing.setCallRecordId(callRecord.getId());
                    changed = true;
                }
                if (existing.getCallerNumber() == null && callRecord.getCallerNumber() != null) {
                    existing.setCallerNumber(callRecord.getCallerNumber());
                    changed = true;
                }
                if (existing.getCalleeNumber() == null && callRecord.getCalleeNumber() != null) {
                    existing.setCalleeNumber(callRecord.getCalleeNumber());
                    changed = true;
                }
                if (existing.getAgentId() == null && callRecord.getAgentId() != null) {
                    existing.setAgentId(callRecord.getAgentId());
                    changed = true;
                }
                if (existing.getAgentName() == null && callRecord.getAgentName() != null) {
                    existing.setAgentName(callRecord.getAgentName());
                    changed = true;
                }
                if (callRecord.getSatisfaction() != null
                        && !callRecord.getSatisfaction().equals(existing.getSatisfaction())) {
                    existing.setSatisfaction(callRecord.getSatisfaction());
                    existing.setSatisfactionTime(callRecord.getSatisfactionTime());
                    changed = true;
                }
            }
            if (changed) {
                existing.setUpdateTime(new Date());
                baseMapper.updateById(existing);
            }
            return existing.getId();
        }

        WorkOrder order = new WorkOrder();
        order.setTitle(title);
        order.setDescription(description);
        order.setCallId(callId);
        if (callRecord != null) {
            order.setCallRecordId(callRecord.getId());
            order.setCallerNumber(callRecord.getCallerNumber());
            order.setCalleeNumber(callRecord.getCalleeNumber());
            order.setAgentId(callRecord.getAgentId());
            order.setAgentName(callRecord.getAgentName());
            order.setSatisfaction(callRecord.getSatisfaction());
            order.setSatisfactionTime(callRecord.getSatisfactionTime());
        }
        order.setPriority(priority != null ? priority : 2);
        order.setStatus(0);
        order.setCreateTime(new Date());

        baseMapper.insert(order);
        log.info("Work order created from callId: orderId={}, callId={}", order.getId(), callId);
        return order.getId();
    }

    @Override
    public void updateSatisfactionByCallId(String callId, String satisfaction, Date satisfactionTime) {
        if (callId == null || callId.isBlank() || satisfaction == null || satisfaction.isBlank()) {
            return;
        }
        LambdaUpdateWrapper<WorkOrder> wrapper = new LambdaUpdateWrapper<WorkOrder>()
                .eq(WorkOrder::getCallId, callId)
                .set(WorkOrder::getSatisfaction, satisfaction)
                .set(WorkOrder::getSatisfactionTime, satisfactionTime)
                .set(WorkOrder::getUpdateTime, new Date());
        int updated = baseMapper.update(null, wrapper);
        log.info("Work order satisfaction updated: callId={}, satisfaction={}, count={}",
                callId, satisfaction, updated);
    }

    @Override
    public Long createFromRinging(Long callId, String callerNumber, String calleeNumber, Long agentId, String agentName) {
        if (callId == null) {
            throw new CommonException("呼叫ID不能为空");
        }
        String callIdText = String.valueOf(callId);
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkOrder::getCallId, callIdText).last("limit 1");
        WorkOrder existing = baseMapper.selectOne(wrapper);
        if (existing != null) {
            boolean changed = false;
            if (existing.getCallerNumber() == null && callerNumber != null) {
                existing.setCallerNumber(callerNumber);
                changed = true;
            }
            if (existing.getCalleeNumber() == null && calleeNumber != null) {
                existing.setCalleeNumber(calleeNumber);
                changed = true;
            }
            if (existing.getAgentId() == null && agentId != null) {
                existing.setAgentId(agentId);
                changed = true;
            }
            if (existing.getAgentName() == null && agentName != null) {
                existing.setAgentName(agentName);
                changed = true;
            }
            if (changed) {
                existing.setUpdateTime(new Date());
                baseMapper.updateById(existing);
            }
            return existing.getId();
        }

        WorkOrder order = new WorkOrder();
        order.setTitle("电话工单-" + (callerNumber != null ? callerNumber : callIdText));
        order.setDescription("电话振铃阶段自动创建");
        order.setCallId(callIdText);
        order.setCallerNumber(callerNumber);
        order.setCalleeNumber(calleeNumber);
        order.setAgentId(agentId);
        order.setAgentName(agentName);
        order.setPriority(2);
        order.setStatus(0);
        order.setCreateTime(new Date());
        baseMapper.insert(order);
        log.info("Work order created on ringing: orderId={}, callId={}, caller={}, callee={}, agentId={}",
                order.getId(), callIdText, callerNumber, calleeNumber, agentId);
        return order.getId();
    }

    @Override
    public void updateStatus(Long id, Integer status, String remark, Long handlerId, String handlerName) {
        WorkOrder order = baseMapper.selectById(id);
        if (order == null) {
            throw new CommonException("工单不存在");
        }

        order.setStatus(status);
        order.setHandleRemark(remark);
        if (handlerId != null) {
            order.setHandlerId(handlerId);
            order.setHandlerName(handlerName);
        }
        if (status == 2 || status == 3) {
            order.setFinishTime(new Date());
        }
        order.setUpdateTime(new Date());

        baseMapper.updateById(order);
        log.info("Work order status updated: orderId={}, status={}", id, status);
    }
}
