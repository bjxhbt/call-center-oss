package com.och.ai.asr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.och.ai.asr.domain.query.TranscriptQuery;
import com.och.ai.asr.domain.vo.TranscriptSummaryVo;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.entity.CallTranscriptSegment;
import com.och.ai.asr.mapper.CallTranscriptMapper;
import com.och.ai.asr.mapper.CallTranscriptSegmentMapper;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.common.enums.DeleteStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 转写记录管理实现（方案 7.14）。
 *
 * <p>摘要按 callId 幂等；段落按 callId+segmentId 幂等去重。所有写操作吞掉异常仅记日志，
 * 避免转写落库失败影响通话（方案 7.15：ASR 异常不得挂断电话）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsrTranscriptServiceImpl implements IAsrTranscriptService {

    private final CallTranscriptMapper transcriptMapper;
    private final CallTranscriptSegmentMapper segmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CallTranscript getOrCreateSummary(AsrSessionContext ctx) {
        String callId = String.valueOf(ctx.getCallId());
        CallTranscript existing = getByCallId(callId);
        if (existing != null) {
            return existing;
        }
        CallTranscript t = new CallTranscript();
        t.setCallId(callId);
        t.setUniqueId(ctx.getUniqueId());
        t.setCallerNumber(ctx.getCaller());
        t.setCalleeNumber(ctx.getCallee());
        t.setAgentId(ctx.getAgentId());
        t.setAgentNumber(ctx.getAgentNumber());
        t.setAgentName(ctx.getAgentName());
        t.setDirection(ctx.getDirection());
        t.setProvider(ctx.getProvider() == null ? null : ctx.getProvider().name());
        t.setSampleRate(ctx.getSampleRate());
        t.setFullText("");
        t.setSegmentCount(0);
        t.setCharCount(0);
        t.setStartTime(ctx.getBridgeTime() == null ? new Date() : new Date(ctx.getBridgeTime()));
        t.setStatus(STATUS_RUNNING);
        t.setReconnectCount(0);
        t.setDelFlag(DeleteStatusEnum.DELETE_NO.getIndex());
        t.setCreateTime(new Date());
        t.setUpdateTime(new Date());
        transcriptMapper.insert(t);
        return t;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean appendFinalSegment(String callId, AsrSegment segment, AsrProviderType provider, Long transcriptId) {
        if (segment == null || callId == null) {
            return false;
        }
        // 幂等：同 callId + segmentId 已存在则跳过（重连重复结果不二次落库）
        Long exists = segmentMapper.selectCount(new LambdaQueryWrapper<CallTranscriptSegment>()
                .eq(CallTranscriptSegment::getCallId, callId)
                .eq(CallTranscriptSegment::getSegmentId, segment.getSegmentId()));
        if (exists != null && exists > 0) {
            log.debug("段落已存在，跳过 callId:{} segmentId:{}", callId, segment.getSegmentId());
            return false;
        }
        CallTranscriptSegment row = new CallTranscriptSegment();
        row.setTranscriptId(transcriptId);
        row.setCallId(callId);
        row.setSegmentId(segment.getSegmentId());
        row.setSeq(segment.getSequence());
        row.setSpeaker(segment.getSpeaker() == null ? null : segment.getSpeaker().code());
        row.setText(segment.getText());
        row.setIsFinal(1);
        row.setBeginMs(segment.getBeginMs());
        row.setEndMs(segment.getEndMs());
        row.setConfidence(segment.getConfidence());
        row.setProvider(provider == null ? null : provider.name());
        segmentMapper.insert(row);

        // 更新摘要：追加全文、计数
        CallTranscript summary = getByCallId(callId);
        if (summary != null) {
            String prev = summary.getFullText() == null ? "" : summary.getFullText();
            summary.setFullText((prev + segment.getText()).trim());
            summary.setSegmentCount((summary.getSegmentCount() == null ? 0 : summary.getSegmentCount()) + 1);
            summary.setCharCount(summary.getFullText().length());
            summary.setUpdateTime(new Date());
            transcriptMapper.updateById(summary);
        }
        return true;
    }

    @Override
    public void updateLatencies(String callId, Long firstTextLatencyMs, Long finalLatencyMs) {
        CallTranscript t = getByCallId(callId);
        if (t == null) {
            return;
        }
        boolean dirty = false;
        if (firstTextLatencyMs != null && (t.getFirstTextLatencyMs() == null || firstTextLatencyMs < t.getFirstTextLatencyMs())) {
            t.setFirstTextLatencyMs(firstTextLatencyMs);
            dirty = true;
        }
        if (finalLatencyMs != null && (t.getFinalLatencyMs() == null || finalLatencyMs > t.getFinalLatencyMs())) {
            t.setFinalLatencyMs(finalLatencyMs);
            dirty = true;
        }
        if (dirty) {
            t.setUpdateTime(new Date());
            transcriptMapper.updateById(t);
        }
    }

    @Override
    public void updateReconnectCount(String callId, int reconnectCount) {
        CallTranscript t = getByCallId(callId);
        if (t == null) {
            return;
        }
        if (t.getReconnectCount() == null || reconnectCount > t.getReconnectCount()) {
            t.setReconnectCount(reconnectCount);
            t.setUpdateTime(new Date());
            transcriptMapper.updateById(t);
        }
    }

    @Override
    public void finalizeSummary(String callId, FinalizeRequest req) {
        CallTranscript t = getByCallId(callId);
        if (t == null) {
            log.warn("finalizeSummary 摘要不存在 callId:{}", callId);
            return;
        }
        if (req.status != null) {
            t.setStatus(req.status);
        }
        if (req.endMs != null) {
            t.setEndTime(new Date(req.endMs));
            if (t.getStartTime() != null) {
                t.setDurationMs(req.endMs - t.getStartTime().getTime());
            }
        } else {
            t.setEndTime(new Date());
        }
        if (req.reconnectCount != null) {
            t.setReconnectCount(req.reconnectCount);
        }
        if (req.errorCode != null) {
            t.setErrorCode(req.errorCode);
        }
        if (req.errorMsg != null) {
            t.setErrorMsg(req.errorMsg);
        }
        if (req.recordUrl != null) {
            t.setRecordUrl(req.recordUrl);
        }
        // 无段落文本时降级为 FAILED（便于排查空转写）
        if (STATUS_SUCCESS.equals(t.getStatus()) && (t.getSegmentCount() == null || t.getSegmentCount() == 0)) {
            t.setStatus(STATUS_PARTIAL);
        }
        t.setUpdateTime(new Date());
        transcriptMapper.updateById(t);
    }

    @Override
    public void markError(String callId, String code, String message) {
        CallTranscript t = getByCallId(callId);
        if (t == null) {
            return;
        }
        t.setStatus(STATUS_FAILED);
        t.setErrorCode(code);
        t.setErrorMsg(message);
        t.setEndTime(new Date());
        t.setUpdateTime(new Date());
        transcriptMapper.updateById(t);
    }

    @Override
    public CallTranscript getByCallId(String callId) {
        if (callId == null || callId.isBlank()) {
            return null;
        }
        return transcriptMapper.selectOne(new LambdaQueryWrapper<CallTranscript>()
                .eq(CallTranscript::getCallId, callId)
                .last("LIMIT 1"));
    }

    @Override
    public List<CallTranscriptSegment> listSegments(String callId) {
        return segmentMapper.selectList(new LambdaQueryWrapper<CallTranscriptSegment>()
                .eq(CallTranscriptSegment::getCallId, callId)
                .orderByAsc(CallTranscriptSegment::getSeq));
    }

    @Override
    public IPage<TranscriptSummaryVo> pageList(TranscriptQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int size = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        Page<CallTranscript> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<CallTranscript> wrapper = new LambdaQueryWrapper<CallTranscript>()
                .eq(CallTranscript::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .like(query.getCallId() != null && !query.getCallId().isBlank(), CallTranscript::getCallId, query.getCallId())
                .like(query.getCallerNumber() != null && !query.getCallerNumber().isBlank(), CallTranscript::getCallerNumber, query.getCallerNumber())
                .like(query.getCalleeNumber() != null && !query.getCalleeNumber().isBlank(), CallTranscript::getCalleeNumber, query.getCalleeNumber())
                .eq(query.getAgentId() != null, CallTranscript::getAgentId, query.getAgentId())
                .like(query.getAgentNumber() != null && !query.getAgentNumber().isBlank(), CallTranscript::getAgentNumber, query.getAgentNumber())
                .eq(query.getDirection() != null, CallTranscript::getDirection, query.getDirection())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(), CallTranscript::getStatus, query.getStatus())
                .eq(query.getProvider() != null && !query.getProvider().isBlank(), CallTranscript::getProvider, query.getProvider())
                .orderByDesc(CallTranscript::getCreateTime);
        // 只查未删除
        wrapper.eq(CallTranscript::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex());
        IPage<CallTranscript> pageResult = transcriptMapper.selectPage(page, wrapper);
        return pageResult.convert(TranscriptSummaryVo::of);
    }

    @Override
    public TranscriptSummaryVo getDetail(Long id) {
        CallTranscript t = transcriptMapper.selectById(id);
        TranscriptSummaryVo vo = TranscriptSummaryVo.of(t);
        if (vo != null) {
            vo.setSegments(listSegments(t.getCallId()));
        }
        return vo;
    }
}
