package com.och.ai.asr.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.entity.CallTranscriptSegment;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 转写摘要 VO（方案 7.14）：摘要字段 + 段落明细（详情查询时携带）。
 *
 * <p>字段命名与前端 {@code TranscriptHistoryItem}/列表展示对齐，便于直接序列化。</p>
 */
@Data
public class TranscriptSummaryVo {

    private Long id;
    private String callId;
    private String uniqueId;
    private String callerNumber;
    private String calleeNumber;
    private Long agentId;
    private String agentNumber;
    private String agentName;
    private Integer direction;
    private String provider;
    private Integer sampleRate;
    private String fullText;
    private Integer segmentCount;
    private Integer charCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    private Long durationMs;
    private String status;
    private String errorCode;
    private String errorMsg;
    private String recordUrl;
    private Long firstTextLatencyMs;
    private Long finalLatencyMs;
    private Integer reconnectCount;

    /** 段落明细（列表查询不返回，详情查询返回）。 */
    private List<CallTranscriptSegment> segments;

    public static TranscriptSummaryVo of(CallTranscript t) {
        if (t == null) {
            return null;
        }
        TranscriptSummaryVo vo = new TranscriptSummaryVo();
        vo.setId(t.getId());
        vo.setCallId(t.getCallId());
        vo.setUniqueId(t.getUniqueId());
        vo.setCallerNumber(t.getCallerNumber());
        vo.setCalleeNumber(t.getCalleeNumber());
        vo.setAgentId(t.getAgentId());
        vo.setAgentNumber(t.getAgentNumber());
        vo.setAgentName(t.getAgentName());
        vo.setDirection(t.getDirection());
        vo.setProvider(t.getProvider());
        vo.setSampleRate(t.getSampleRate());
        vo.setFullText(t.getFullText());
        vo.setSegmentCount(t.getSegmentCount());
        vo.setCharCount(t.getCharCount());
        vo.setStartTime(t.getStartTime());
        vo.setEndTime(t.getEndTime());
        vo.setDurationMs(t.getDurationMs());
        vo.setStatus(t.getStatus());
        vo.setErrorCode(t.getErrorCode());
        vo.setErrorMsg(t.getErrorMsg());
        vo.setRecordUrl(t.getRecordUrl());
        vo.setFirstTextLatencyMs(t.getFirstTextLatencyMs());
        vo.setFinalLatencyMs(t.getFinalLatencyMs());
        vo.setReconnectCount(t.getReconnectCount());
        return vo;
    }
}
