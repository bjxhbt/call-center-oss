package com.och.system.service;

import com.och.common.base.IBaseService;
import com.och.common.domain.CallInfo;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.query.call.CallRecordQuery;
import com.och.system.domain.vo.call.CallRecordVo;
import com.och.system.domain.vo.call.CallTrace;

import java.util.List;

/**
 * 呼叫记录表(CallRecord)表服务接口
 *
 * @author danmo
 * @since 2024-11-21 11:23:01
 */
public interface ICallRecordService extends IBaseService<CallRecord> {

    List<CallRecordVo> getCallPageList(CallRecordQuery query);

    /**
     * 根据呼叫唯一ID查询通话记录
     *
     * @param callId 呼叫唯一ID
     * @return CallRecord实体，如不存在返回null
     */
    CallRecord getByCallId(String callId);

    /**
     * 按 callId 幂等保存或更新话单（任务 9.8 数据一致性）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>不存在：新增（依赖 call_record.call_id 唯一约束兜底并发重复插入）；</li>
     *   <li>已存在：执行守卫式更新——只写入尚未落库的字段，已设置的接听时间(answerTime)、
     *       结束时间(callEndTime)、振铃时间(ringingTime)、挂断方向(hangupDir)、挂断原因
     *       (hangupCauseCode)、呼叫结果(callState)不会被后续事件覆盖；</li>
     *   <li>录音状态：仅在非终态时允许更新，终态(已上传/上传失败)不可回退。</li>
     * </ul>
     *
     * <p>本方法不依赖内存变量，并发安全由数据库唯一约束 + 字段级守卫共同保证。
     * 调用方仍应先用 {@code CallEventIdempotentService} 过滤重复 ESL 事件。</p>
     *
     * @param record 携带最新字段的话单，callId 不能为空
     * @return true=发生了新增或字段更新；false=因幂等/守卫未产生变更
     */
    boolean saveOrUpdateByCallId(CallRecord record);

    /**
     * 在录音开始/停止事件到达时确保话单已经存在，避免上传事件早于挂机事件时丢失录音。
     *
     * @param callInfo 当前呼叫缓存
     * @return 已存在或新建的话单，失败返回 null
     */
    CallRecord ensureRecordingRecord(CallInfo callInfo);

    /**
     * 只更新录音相关字段，避免异步上传用旧话单覆盖挂机结果、接听时间等字段。
     */
    boolean updateRecordingFields(String callId, String filePath, String recordUrl,
                                   Integer recordDuration, Integer recordStatus);

    /**
     * 将链路可追溯信息(trace)合并进话单实体(仅填充非空字段)。
     */
    void mergeTrace(CallRecord callRecord, CallTrace trace);

    /**
     * 按主键查询话单详情(VO)。
     */
    CallRecordVo getDetail(Long id);
}
