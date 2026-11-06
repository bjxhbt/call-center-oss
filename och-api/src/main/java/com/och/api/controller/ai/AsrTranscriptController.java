package com.och.api.controller.ai;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.och.ai.asr.domain.query.TranscriptQuery;
import com.och.ai.asr.domain.vo.TranscriptSummaryVo;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 转写记录管理接口（方案 7.14）：按 callId/号码/坐席查询转写摘要与明细，关联话单与录音。
 *
 * @author och
 */
@Tag(name = "实时转写记录")
@RestController
@RequestMapping("asr/v1/transcript")
public class AsrTranscriptController extends BaseController {

    @Autowired
    private IAsrTranscriptService transcriptService;

    @Log(title = "转写记录列表(分页)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('ai:transcript:list')")
    @Operation(summary = "转写记录列表(分页，按号码/坐席/状态/服务商过滤)", method = "POST")
    @PostMapping("/page/list")
    public ResResult<IPage<TranscriptSummaryVo>> pageList(@RequestBody TranscriptQuery query) {
        return success(transcriptService.pageList(query));
    }

    @Log(title = "转写详情(按主键)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('ai:transcript:get')")
    @Operation(summary = "转写详情(摘要 + 段落明细)", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<TranscriptSummaryVo> get(@PathVariable("id") Long id) {
        return success(transcriptService.getDetail(id));
    }

    @Log(title = "转写详情(按callId)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('ai:transcript:get')")
    @Operation(summary = "转写详情(按 callId，关联话单/录音)", method = "POST")
    @PostMapping("/getByCallId/{callId}")
    public ResResult<TranscriptSummaryVo> getByCallId(@PathVariable("callId") String callId) {
        var summary = transcriptService.getByCallId(callId);
        if (summary == null) {
            return success(null);
        }
        return success(transcriptService.getDetail(summary.getId()));
    }
}
