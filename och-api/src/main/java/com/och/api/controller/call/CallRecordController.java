package com.och.api.controller.call;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.query.call.CallRecordQuery;
import com.och.system.domain.vo.call.CallRecordVo;
import com.och.system.service.ICallRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 话单管理：通话记录查询(主叫/被叫/方向/网关/坐席/挂断原因/号码变换结果等)。
 * 话单通过 callId 与录音、后续转写关联。
 *
 * @author och
 */
@Tag(name = "话单管理")
@RestController
@RequestMapping("call/v1/callRecord")
public class CallRecordController extends BaseController {

    @Autowired
    private ICallRecordService iCallRecordService;

    @Log(title = "话单列表(分页)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:record:page:list')")
    @Operation(summary = "话单列表(分页)", method = "POST")
    @PostMapping("/page/list")
    public ResResult<PageInfo<CallRecordVo>> pageList(@RequestBody CallRecordQuery query) {
        List<CallRecordVo> list = iCallRecordService.getCallPageList(query);
        PageInfo<CallRecordVo> pageInfo = PageInfo.of(list);
        return success(pageInfo);
    }

    @Log(title = "话单详情(按主键)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:record:get')")
    @Operation(summary = "话单详情(按主键ID)", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<CallRecordVo> get(@PathVariable("id") Long id) {
        return success(iCallRecordService.getDetail(id));
    }

    @Log(title = "话单详情(按callId)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:record:get')")
    @Operation(summary = "话单详情(按呼叫唯一ID，关联录音与转写)", method = "POST")
    @PostMapping("/getByCallId/{callId}")
    public ResResult<CallRecordVo> getByCallId(@PathVariable("callId") String callId) {
        com.och.system.domain.entity.CallRecord record = iCallRecordService.getByCallId(callId);
        return success(record == null ? null : iCallRecordService.getDetail(record.getId()));
    }
}
