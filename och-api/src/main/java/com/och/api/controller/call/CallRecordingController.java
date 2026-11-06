package com.och.api.controller.call;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.constant.SysSettingConfig;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.query.call.CallRecordQuery;
import com.och.system.domain.vo.call.CallRecordVo;
import com.och.system.service.ICallRecordService;
import com.och.system.util.RecordingFileResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 录音管理：录音列表、详情、在线播放地址与上传结果回写。
 * 录音通过 callId 与话单、后续转写关联。
 *
 * @author och
 */
@Tag(name = "录音管理")
@RestController
@RequestMapping("call/v1/recording")
public class CallRecordingController extends BaseController {

    @Autowired
    private ICallRecordService iCallRecordService;

    @Autowired
    private SysSettingConfig sysSettingConfig;

    /** 文件服务对外访问前缀(用于在线播放；按实际部署的 och-file / MinIO 地址配置，不硬编码) */
    @Value("${och.recording.play-base-url:}")
    private String playBaseUrl;

    @Log(title = "录音列表(分页)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:recording:page:list')")
    @Operation(summary = "录音列表(分页，按录音状态/号码/时间过滤)", method = "POST")
    @PostMapping("/page/list")
    public ResResult<PageInfo<CallRecordVo>> pageList(@RequestBody CallRecordQuery query) {
        List<CallRecordVo> list = iCallRecordService.getCallPageList(query);
        PageInfo<CallRecordVo> pageInfo = PageInfo.of(list);
        return success(pageInfo);
    }

    @Log(title = "录音详情", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:recording:get')")
    @Operation(summary = "录音详情(按主键ID)", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<CallRecordVo> get(@PathVariable("id") Long id) {
        return success(iCallRecordService.getDetail(id));
    }

    @Log(title = "录音在线播放地址", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:recording:play')")
    @Operation(summary = "获取录音在线播放地址(统一后端代理，兼容本地录音回退)", method = "POST")
    @PostMapping("/play/{id}")
    public ResResult<RecordingPlayVo> play(@PathVariable("id") Long id) {
        CallRecordVo record = iCallRecordService.getDetail(id);
        RecordingPlayVo vo = new RecordingPlayVo();
        if (record == null) {
            vo.setStatus(3);
            vo.setFailReason("录音记录不存在");
            return success(vo);
        }
        vo.setCallId(record.getCallId());
        vo.setFilePath(record.getFilePath());
        vo.setRecordUrl(record.getRecordUrl());
        vo.setDuration(record.getRecordDuration());
        vo.setRecordStatus(record.getRecordStatus());
        vo.setPlayUrl(resolvePlayUrl(record));
        if (StringUtils.isBlank(vo.getPlayUrl())) {
            vo.setStatus(3);
            vo.setFailReason("录音文件暂不可访问(未上传或对象存储不可用)");
        } else {
            vo.setStatus(2);
        }
        return success(vo);
    }

    @Log(title = "录音上传结果回写", businessType = BusinessTypeEnum.UPDATE)
    @PreAuthorize("@authz.hasPerm('call:recording:update')")
    @Operation(summary = "录音上传完成/失败结果回写(幂等，复用话单守卫更新)", method = "POST")
    @PostMapping("/update")
    public ResResult<Boolean> update(@RequestBody RecordingUpdateQuery query) {
        if (query.getCallId() == null || query.getCallId().isEmpty()) {
            return error("callId 不能为空");
        }
        CallRecord record = new CallRecord();
        record.setCallId(query.getCallId());
        record.setRecordStatus(query.getStatus());
        record.setRecordUrl(query.getRecordUrl());
        record.setRecordDuration(query.getDuration());
        boolean changed = iCallRecordService.saveOrUpdateByCallId(record);
        return success(changed);
    }

    private String resolvePlayUrl(CallRecordVo record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.isNotBlank(record.getCallId())
                && (StringUtils.isNotBlank(record.getRecordUrl())
                || RecordingFileResolver.resolveExisting(record.getFilePath(), sysSettingConfig.getFsProfile()) != null)) {
            return "/v1/call/record/stream/" + record.getCallId();
        }
        if (StringUtils.isBlank(record.getFilePath())) {
            return null;
        }
        if (StringUtils.isBlank(playBaseUrl)) {
            return null;
        }
        String base = playBaseUrl.endsWith("/") ? playBaseUrl.substring(0, playBaseUrl.length() - 1) : playBaseUrl;
        String path = record.getFilePath().startsWith("/") ? record.getFilePath() : "/" + record.getFilePath();
        return base + path;
    }

    @Data
    public static class RecordingPlayVo {
        @Schema(description = "呼叫唯一ID")
        private String callId;
        @Schema(description = "录音文件原始路径")
        private String filePath;
        @Schema(description = "MinIO/OSS 录音URL")
        private String recordUrl;
        @Schema(description = "在线播放URL")
        private String playUrl;
        @Schema(description = "录音时长(秒)")
        private Integer duration;
        @Schema(description = "录音状态 0-未上传 1-上传中 2-已上传 3-上传失败")
        private Integer recordStatus;
        @Schema(description = "状态码(3=不可播放)")
        private Integer status;
        @Schema(description = "失败原因")
        private String failReason;
    }

    @Data
    public static class RecordingUpdateQuery {
        @Schema(description = "呼叫唯一ID")
        private String callId;
        @Schema(description = "录音状态 0-未上传 1-上传中 2-已上传 3-上传失败")
        private Integer status;
        @Schema(description = "录音文件可访问URL")
        private String recordUrl;
        @Schema(description = "录音时长(秒)")
        private Integer duration;
    }
}
