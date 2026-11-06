package com.och.api.controller.fs;

import com.och.api.service.IFsPlaybackService;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.vo.playback.PlaybackStatusInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FreeSWITCH 通道播放管理(任务 8.8)。
 * <p>
 * 供 TTS/验收方案调用：按通道 UUID 播放、停止音频文件，并查询播放状态。
 * 播放失败不会挂断当前通话。
 *
 * @author och
 */
@Tag(name = "FreeSWITCH通道播放")
@RestController
@RequestMapping("fs/v1/playback")
public class FsPlaybackController extends BaseController {

    @Autowired
    private IFsPlaybackService iFsPlaybackService;

    @Log(title = "通道播放", businessType = BusinessTypeEnum.OTHER)
    @PreAuthorize("@authz.hasPerm('fs:playback:play')")
    @Operation(summary = "在指定通道上播放音频文件", method = "POST")
    @PostMapping("/play")
    public ResResult<PlaybackStatusInfo> play(@RequestBody PlaybackPlayQuery query) {
        return success(iFsPlaybackService.play(query.getUuid(), query.getFile(), query.getLoop()));
    }

    @Log(title = "停止通道播放", businessType = BusinessTypeEnum.OTHER)
    @PreAuthorize("@authz.hasPerm('fs:playback:stop')")
    @Operation(summary = "停止指定通道的播放", method = "POST")
    @PostMapping("/stop/{uuid}")
    public ResResult<PlaybackStatusInfo> stop(@PathVariable("uuid") String uuid) {
        return success(iFsPlaybackService.stop(uuid));
    }

    @Log(title = "通道播放状态", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('fs:playback:status')")
    @Operation(summary = "查询指定通道的播放状态", method = "POST")
    @PostMapping("/status/{uuid}")
    public ResResult<PlaybackStatusInfo> status(@PathVariable("uuid") String uuid) {
        return success(iFsPlaybackService.status(uuid));
    }

    @Data
    public static class PlaybackPlayQuery {
        @Schema(description = "通道UUID(必填)")
        private String uuid;
        @Schema(description = "音频文件(FS可访问路径，或 say:/tone_stream: 语法)")
        private String file;
        @Schema(description = "循环次数(null或<=0为单次)")
        private Integer loop;
    }
}
