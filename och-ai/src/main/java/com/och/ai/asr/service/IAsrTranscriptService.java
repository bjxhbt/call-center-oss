package com.och.ai.asr.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.och.ai.asr.domain.query.TranscriptQuery;
import com.och.ai.asr.domain.vo.TranscriptSummaryVo;
import com.och.ai.asr.entity.CallTranscript;
import com.och.ai.asr.entity.CallTranscriptSegment;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.model.AsrSegment;
import com.och.ai.asr.model.AsrSessionContext;

import java.util.List;

/**
 * 转写记录管理服务（方案 7.14）。
 *
 * <p>负责摘要(call_transcript)与段落(call_transcript_segment)的读写：
 * 会话开始时创建摘要、最终段落到达时追加并更新全文、结束时落库状态。
 * 临时结果不落库。提供按 callId/号码/坐席的查询能力，并关联话单与录音。</p>
 */
public interface IAsrTranscriptService {

    /** 状态常量。 */
    String STATUS_RUNNING = "RUNNING";
    String STATUS_SUCCESS = "SUCCESS";
    String STATUS_PARTIAL = "PARTIAL";
    String STATUS_FAILED = "FAILED";
    String STATUS_OFFLINE = "OFFLINE";

    /**
     * 按 callId 获取或创建摘要（RUNNING）。幂等：已存在则返回现有。
     */
    CallTranscript getOrCreateSummary(AsrSessionContext ctx);

    /**
     * 追加一个最终段落（按 callId+segmentId 幂等去重），并更新摘要全文/计数。
     *
     * @return true=新增落库；false=因重复跳过
     */
    boolean appendFinalSegment(String callId, AsrSegment segment, AsrProviderType provider, Long transcriptId);

    /** 更新摘要延迟指标（首文本延迟只取更小值）。 */
    void updateLatencies(String callId, Long firstTextLatencyMs, Long finalLatencyMs);

    /** 更新重连次数（取更大值）。 */
    void updateReconnectCount(String callId, int reconnectCount);

    /**
     * 结束摘要：落库结束时间/时长/状态/录音地址/错误。
     */
    void finalizeSummary(String callId, FinalizeRequest req);

    /** 标记错误状态。 */
    void markError(String callId, String code, String message);

    /** 按 callId 取摘要（无则 null）。 */
    CallTranscript getByCallId(String callId);

    /** 按 callId 取段落明细（按 seq 升序）。 */
    List<CallTranscriptSegment> listSegments(String callId);

    /** 分页查询摘要。 */
    IPage<TranscriptSummaryVo> pageList(TranscriptQuery query);

    /** 详情：摘要 + 段落明细。 */
    TranscriptSummaryVo getDetail(Long id);

    /** 结束请求参数。 */
    class FinalizeRequest {
        public String status;
        public Long endMs;
        public Integer reconnectCount;
        public String errorCode;
        public String errorMsg;
        public String recordUrl;

        public static FinalizeRequest of(String status) {
            FinalizeRequest r = new FinalizeRequest();
            r.status = status;
            return r;
        }
    }
}
