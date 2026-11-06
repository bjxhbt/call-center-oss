package com.och.api.controller.ai;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.och.ai.tts.entity.TtsFile;
import com.och.api.controller.ai.dto.TtsFileListVo;
import com.och.api.controller.ai.dto.TtsFilePageQuery;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TTS 文件管理接口（方案 8.7）：查询、分页、详情、删除（含业务引用检查）。
 */
@Slf4j
@Tag(name = "TTS 文件管理")
@RestController
@RequestMapping("/tts/v1/file-mgr")
@RequiredArgsConstructor
public class TtsFileController extends BaseController {

    private final com.och.ai.tts.service.TtsBusinessService ttsBusinessService;

    @Operation(summary = "TTS 文件分页列表", method = "POST")
    @PreAuthorize("@authz.hasPerm('ai:tts:file:list')")
    @PostMapping("/page/list")
    public ResResult<PageInfo<TtsFileListVo>> pageList(@RequestBody TtsFilePageQuery query) {
        int pageNum = query.getPageIndex() == null || query.getPageIndex() < 1 ? 1 : query.getPageIndex();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        PageHelper.startPage(pageNum, pageSize);
        List<TtsFile> list = ttsBusinessService.listFiles(query.getProvider(), query.getKeyword());
        List<TtsFileListVo> vos = list.stream().map(TtsFileListVo::of).collect(Collectors.toList());
        return success(new PageInfo<>(vos));
    }

    @Operation(summary = "TTS 文件列表（不分页）", method = "POST")
    @PreAuthorize("@authz.hasPerm('ai:tts:file:list')")
    @PostMapping("/list")
    public ResResult<List<TtsFileListVo>> list(@RequestBody TtsFilePageQuery query) {
        List<TtsFileListVo> vos = ttsBusinessService.listFiles(query.getProvider(), query.getKeyword()).stream()
                .map(TtsFileListVo::of).collect(Collectors.toList());
        return success(vos);
    }

    @Operation(summary = "TTS 文件详情", method = "GET")
    @PreAuthorize("@authz.hasPerm('ai:tts:file:list')")
    @GetMapping("/get/{id}")
    public ResResult<TtsFileListVo> get(@PathVariable("id") Long id) {
        return success(TtsFileListVo.of(ttsBusinessService.getFileById(id)));
    }

    @Operation(summary = "删除 TTS 文件（被引用时拒绝）", method = "POST")
    @PreAuthorize("@authz.hasPerm('ai:tts:file:delete')")
    @PostMapping("/delete/{id}")
    public ResResult<Void> delete(@PathVariable("id") Long id) {
        if (id == null) {
            return ResResult.error("id 不能为空");
        }
        try {
            boolean removed = ttsBusinessService.deleteFile(id);
            log.info("删除 TTS 文件 id={} diskRemoved={}", id, removed);
            return ResResult.success();
        } catch (Exception e) {
            // 业务引用占用等错误，明确返回
            return ResResult.error(StringUtils.isBlank(e.getMessage()) ? "删除失败" : e.getMessage());
        }
    }
}
