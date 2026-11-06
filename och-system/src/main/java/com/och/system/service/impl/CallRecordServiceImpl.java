package com.och.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.och.common.base.BaseServiceImpl;
import com.och.common.domain.CallInfo;
import com.och.system.domain.enums.RecordStatusEnum;
import com.och.system.domain.query.call.CallRecordQuery;
import com.och.system.domain.vo.call.CallRecordVo;
import com.och.system.domain.vo.call.CallTrace;
import com.och.system.mapper.CallRecordMapper;
import com.och.system.domain.entity.CallRecord;
import com.och.system.service.ICallRecordService;
import com.och.system.util.CallRecordMergeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 呼叫记录表(CallRecord)表服务实现类
 *
 * @author danmo
 * @since 2024-11-21 11:23:02
 */
@Slf4j
@Service
public class CallRecordServiceImpl extends BaseServiceImpl<CallRecordMapper, CallRecord> implements ICallRecordService {

    @Override
    public List<CallRecordVo> getCallPageList(CallRecordQuery query) {
        startPage(query.getPageIndex(), query.getPageSize(), query.getSortField(), query.getSort());
        return baseMapper.getCallPageList(query);
    }

    @Override
    public CallRecord getByCallId(String callId) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallRecord::getCallId, callId);
        return getOne(wrapper);
    }

    @Override
    public boolean saveOrUpdateByCallId(CallRecord record) {
        if (record == null || record.getCallId() == null || record.getCallId().isEmpty()) {
            log.warn("saveOrUpdateByCallId 参数非法，callId 为空");
            return false;
        }
        CallRecord existing = getByCallId(record.getCallId());
        if (existing == null) {
            // 并发场景下另一线程可能已插入，依赖 call_id 唯一约束兜底；失败则回退为守卫式更新
            try {
                return save(record);
            } catch (Exception e) {
                log.warn("并发插入话单被唯一约束拒绝 callId:{}，回退为更新", record.getCallId());
                existing = getByCallId(record.getCallId());
                if (existing == null) {
                    return false;
                }
            }
        }
        return applyGuardedUpdate(existing, record);
    }

    @Override
    public CallRecord ensureRecordingRecord(CallInfo callInfo) {
        if (callInfo == null || callInfo.getCallId() == null) {
            return null;
        }
        CallRecord snapshot = new CallRecord();
        snapshot.transferForRecording(callInfo);
        if (!saveOrUpdateByCallId(snapshot)) {
            CallRecord existing = getByCallId(snapshot.getCallId());
            if (existing == null) {
                log.warn("录音话单预创建失败 callId:{}", snapshot.getCallId());
                return null;
            }
            return existing;
        }
        return getByCallId(snapshot.getCallId());
    }

    @Override
    public boolean updateRecordingFields(String callId, String filePath, String recordUrl,
                                         Integer recordDuration, Integer recordStatus) {
        if (callId == null || callId.isEmpty()) {
            return false;
        }
        LambdaUpdateWrapper<CallRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CallRecord::getCallId, callId);
        if (filePath != null) {
            wrapper.set(CallRecord::getFilePath, filePath);
        }
        if (recordUrl != null) {
            wrapper.set(CallRecord::getRecordUrl, recordUrl);
        }
        if (recordDuration != null) {
            wrapper.set(CallRecord::getRecordDuration, recordDuration);
        }
        if (recordStatus != null) {
            wrapper.set(CallRecord::getRecordStatus, recordStatus);
            // 上传中允许从未上传/上次失败重试；已上传不能回退。
            if (recordStatus == 1) {
                wrapper.and(condition -> condition.isNull(CallRecord::getRecordStatus)
                        .or().in(CallRecord::getRecordStatus, 0, 3));
            } else if (recordStatus == 3) {
                wrapper.and(condition -> condition.isNull(CallRecord::getRecordStatus)
                        .or().in(CallRecord::getRecordStatus, 0, 1, 3));
            } else if (recordStatus != 2) {
                wrapper.and(condition -> condition.isNull(CallRecord::getRecordStatus)
                        .or().eq(CallRecord::getRecordStatus, 0));
            }
        }
        if (filePath == null && recordUrl == null && recordDuration == null && recordStatus == null) {
            return false;
        }
        return baseMapper.update(null, wrapper) > 0;
    }

    /**
     * 守卫式更新：将 incoming 中尚未落库的字段合并进 existing（委托 {@link CallRecordMergeUtil}），
     * 仅当产生变更时才调用 updateById 持久化，避免并发事件互相覆盖接听时间/结束时间/挂断原因等。
     */
    private boolean applyGuardedUpdate(CallRecord existing, CallRecord incoming) {
        boolean changed = CallRecordMergeUtil.merge(existing, incoming);
        return changed && updateById(existing);
    }

    /**
     * 将链路可追溯信息(trace)合并进话单实体(仅填充实体中尚为空的字段)。
     * 用于挂断落库前把路由阶段暂存的原始/转换号码、网关、DID、失败原因等写入话单。
     */
    @Override
    public void mergeTrace(CallRecord callRecord, CallTrace trace) {
        if (callRecord == null || trace == null) {
            return;
        }
        if (callRecord.getGatewayId() == null) {
            callRecord.setGatewayId(trace.getGatewayId());
        }
        if (callRecord.getGatewayName() == null && trace.getGatewayName() != null) {
            callRecord.setGatewayName(trace.getGatewayName());
        }
        if (callRecord.getFromGatewayId() == null) {
            callRecord.setFromGatewayId(trace.getFromGatewayId());
        }
        if (callRecord.getFromGatewayName() == null && trace.getFromGatewayName() != null) {
            callRecord.setFromGatewayName(trace.getFromGatewayName());
        }
        if (callRecord.getDid() == null && trace.getDid() != null) {
            callRecord.setDid(trace.getDid());
        }
        if (callRecord.getOriginalCallee() == null && trace.getOriginalCallee() != null) {
            callRecord.setOriginalCallee(trace.getOriginalCallee());
        }
        if (callRecord.getConvertedCallee() == null && trace.getConvertedCallee() != null) {
            callRecord.setConvertedCallee(trace.getConvertedCallee());
        }
        if (callRecord.getRouteId() == null) {
            callRecord.setRouteId(trace.getRouteId());
        }
        if (callRecord.getRouteName() == null && trace.getRouteName() != null) {
            callRecord.setRouteName(trace.getRouteName());
        }
        if (callRecord.getSipStatus() == null && trace.getSipStatus() != null) {
            callRecord.setSipStatus(trace.getSipStatus());
        }
        if (callRecord.getFailureReason() == null && trace.getFailureReason() != null) {
            callRecord.setFailureReason(trace.getFailureReason());
        }
    }

    @Override
    public CallRecordVo getDetail(Long id) {
        if (id == null) {
            return null;
        }
        CallRecord record = getById(id);
        if (record == null) {
            return null;
        }
        CallRecordVo vo = new CallRecordVo();
        vo.setId(record.getId());
        vo.setCallId(record.getCallId());
        vo.setCallerNumber(record.getCallerNumber());
        vo.setCallerDisplayNumber(record.getCallerDisplayNumber());
        vo.setCalleeNumber(record.getCalleeNumber());
        vo.setCalleeDisplayNumber(record.getCalleeDisplayNumber());
        vo.setNumberLocation(record.getNumberLocation());
        vo.setAgentId(record.getAgentId());
        vo.setAgentNumber(record.getAgentNumber());
        vo.setAgentName(record.getAgentName());
        vo.setCallState(record.getCallState());
        vo.setDirection(record.getDirection());
        vo.setCallStartTime(record.getCallStartTime());
        vo.setCallEndTime(record.getCallEndTime());
        vo.setAnswerFlag(record.getAnswerFlag());
        vo.setAnswerTime(record.getAnswerTime());
        vo.setRingingTime(record.getRingingTime());
        vo.setHangupDir(record.getHangupDir());
        vo.setHangupCauseCode(record.getHangupCauseCode());
        vo.setFilePath(record.getFilePath());
        vo.setRingingPath(record.getRingingPath());
        vo.setRecordUrl(record.getRecordUrl());
        vo.setRecordDuration(record.getRecordDuration());
        vo.setRecordStatus(record.getRecordStatus());
        vo.setSatisfaction(record.getSatisfaction());
        vo.setSatisfactionTime(record.getSatisfactionTime());
        vo.setGatewayId(record.getGatewayId());
        vo.setGatewayName(record.getGatewayName());
        vo.setFromGatewayId(record.getFromGatewayId());
        vo.setFromGatewayName(record.getFromGatewayName());
        vo.setDid(record.getDid());
        vo.setOriginalCallee(record.getOriginalCallee());
        vo.setConvertedCallee(record.getConvertedCallee());
        vo.setRouteId(record.getRouteId());
        vo.setRouteName(record.getRouteName());
        vo.setRecordStartTime(record.getRecordStartTime());
        vo.setRecordEndTime(record.getRecordEndTime());
        vo.setSipStatus(record.getSipStatus());
        vo.setFailureReason(record.getFailureReason());
        vo.setCreateBy(record.getCreateBy());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }
}
