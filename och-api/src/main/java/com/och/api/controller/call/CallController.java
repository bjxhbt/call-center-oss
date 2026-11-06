package com.och.api.controller.call;

import com.github.pagehelper.PageInfo;
import com.och.api.service.ICallService;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.constant.SysSettingConfig;
import com.och.common.utils.StringUtils;
import com.och.file.config.S3StorageConfig;
import com.och.file.handler.S3FileUploadHandler;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.query.call.CallQuery;
import com.och.system.domain.query.call.CallRecordQuery;
import com.och.system.domain.vo.call.CallRecordVo;
import com.och.system.service.ICallRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Tag(name = "Call API")
@RequestMapping("/v1/call")
@RestController
public class CallController extends BaseController {

    @Autowired
    private ICallService iCallService;

    @Autowired
    private ICallRecordService callRecordService;

    @Autowired
    private SysSettingConfig sysSettingConfig;

    @Autowired
    private S3StorageConfig storageConfig;

    @Operation(description = "Create call", method = "POST")
    @PostMapping("/create")
    public ResResult makeCall(@RequestBody CallQuery query) {
        Long callId = iCallService.makeCall(query);
        return success(callId);
    }

    @Operation(description = "Hangup call", method = "POST")
    @PostMapping("/hangup/{callId}")
    public ResResult hangup(@PathVariable("callId") Long callId) {
        iCallService.hangup(callId);
        return success();
    }

    @Operation(description = "Hangup agent call", method = "POST")
    @PostMapping("/hangup/agent/{agentId}")
    public ResResult hangupAgent(@PathVariable("agentId") Long agentId) {
        iCallService.hangupAgent(agentId);
        return success();
    }

    @Operation(description = "Hangup agent call by SIP number", method = "POST")
    @PostMapping("/hangup/agent-number/{agentNumber}")
    public ResResult hangupAgentNumber(@PathVariable("agentNumber") String agentNumber) {
        iCallService.hangupAgentNumber(agentNumber);
        return success();
    }

    @Operation(description = "Get call info", method = "POST")
    @PostMapping("/get/{callId}")
    public ResResult<CallRecordVo> getCallInfo(@PathVariable("callId") Long callId) {
        CallRecordVo callRecord = iCallService.getCallInfo(callId);
        return success(callRecord);
    }

    @Operation(description = "Get call page list", method = "POST")
    @PostMapping("/page/list")
    public ResResult<PageInfo<CallRecordVo>> getCallPageList(@RequestBody CallRecordQuery query) {
        List<CallRecordVo> recordList = iCallService.getCallPageList(query);
        PageInfo<CallRecordVo> pageInfo = new PageInfo<>(recordList);
        return success(pageInfo);
    }

    @Operation(description = "Get recording play URL", method = "GET")
    @GetMapping("/record/play/{callId}")
    public ResResult<String> playRecording(@PathVariable("callId") String callId) {
        CallRecord record = callRecordService.getByCallId(callId);
        if (!hasPlayableRecording(record)) {
            return ResResult.error("Call record not found");
        }
        // 返回后端同源流式地址(经 nginx 反代),而非 rustfs 预签名内网 URL。
        // 原预签名 URL 暴露 192.168.x 内网 IP + RustFS 对 SigV4 预签名兼容不稳,浏览器无法播放。
        return success("/v1/call/record/stream/" + callId);
    }

    @Operation(description = "Stream recording (同源在线播放,支持 Range)", method = "GET")
    @GetMapping("/record/stream/{callId}")
    public void streamRecording(@PathVariable("callId") String callId,
                                jakarta.servlet.http.HttpServletRequest request,
                                HttpServletResponse response) {
        try {
            CallRecord record = callRecordService.getByCallId(callId);
            Path localFile = resolveLocalRecording(record);
            if (record == null || (!isRemoteRecordingReady(record) && localFile == null)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (isRemoteRecordingReady(record)) {
                try {
                    streamRemoteRecording(record, request, response);
                    return;
                } catch (Exception remoteException) {
                    log.warn("Remote recording stream failed, trying local file: callId={}, error={}",
                            callId, remoteException.getMessage());
                }
            }
            if (localFile != null) {
                streamLocalRecording(localFile, request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            log.error("Stream recording failed: callId={}, error={}", callId, e.getMessage(), e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ignored) {
            }
        }
    }

    @Operation(description = "Download recording", method = "GET")
    @GetMapping("/record/download/{callId}")
    public void downloadRecording(@PathVariable("callId") String callId, HttpServletResponse response) {
        try {
            CallRecord record = callRecordService.getByCallId(callId);
            Path localFile = resolveLocalRecording(record);
            if (record == null || (!isRemoteRecordingReady(record) && localFile == null)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("Call record not found");
                return;
            }
            String fileName = URLEncoder.encode(callId + ".wav", StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setContentType("audio/wav");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
            if (isRemoteRecordingReady(record)) {
                try {
                    String objectKey = extractObjectKey(record.getRecordUrl());
                    S3FileUploadHandler handler = new S3FileUploadHandler(sysSettingConfig, storageConfig);
                    handler.download(objectKey, response.getOutputStream());
                    return;
                } catch (Exception remoteException) {
                    log.warn("Remote recording download failed, trying local file: callId={}, error={}",
                            callId, remoteException.getMessage());
                }
            }
            if (localFile != null) {
                Files.copy(localFile, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (Exception e) {
            log.error("Download recording failed: callId={}, error={}", callId, e.getMessage(), e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Download recording failed: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    private void streamRemoteRecording(CallRecord record,
                                       jakarta.servlet.http.HttpServletRequest request,
                                       HttpServletResponse response) throws Exception {
        String objectKey = extractObjectKey(record.getRecordUrl());
        S3FileUploadHandler handler = new S3FileUploadHandler(sysSettingConfig, storageConfig);
        long total = handler.contentLength(objectKey);
        String rangeHeader = request.getHeader("Range");
        long start = 0;
        long end = total - 1;
        boolean partial = false;
        if (StringUtils.isNotBlank(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash == 0) {
                    long suffix = Long.parseLong(spec.substring(1));
                    start = Math.max(0, total - suffix);
                } else {
                    start = Long.parseLong(spec.substring(0, dash));
                    if (dash < spec.length() - 1) {
                        end = Long.parseLong(spec.substring(dash + 1));
                    }
                }
                partial = true;
            } catch (NumberFormatException ignore) {
                partial = false;
                start = 0;
                end = total - 1;
            }
        }
        if (start >= total) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + total);
            return;
        }
        end = Math.min(end, total - 1);

        response.setContentType("audio/wav");
        response.setHeader("Accept-Ranges", "bytes");
        if (partial) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + total);
            response.setHeader("Content-Length", String.valueOf(end - start + 1));
            handler.downloadRange(objectKey, response.getOutputStream(), start, end);
        } else {
            response.setHeader("Content-Length", String.valueOf(total));
            handler.download(objectKey, response.getOutputStream());
        }
        response.flushBuffer();
    }

    private void streamLocalRecording(Path localFile,
                                      jakarta.servlet.http.HttpServletRequest request,
                                      HttpServletResponse response) throws Exception {
        long total = Files.size(localFile);
        long start = 0;
        long end = total - 1;
        boolean partial = false;
        String rangeHeader = request.getHeader("Range");
        if (StringUtils.isNotBlank(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash == 0) {
                    start = Math.max(0, total - Long.parseLong(spec.substring(1)));
                } else {
                    start = Long.parseLong(spec.substring(0, dash));
                    if (dash < spec.length() - 1) {
                        end = Long.parseLong(spec.substring(dash + 1));
                    }
                }
                partial = true;
            } catch (NumberFormatException ignore) {
                start = 0;
                end = total - 1;
            }
        }
        if (start >= total) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + total);
            return;
        }
        end = Math.min(end, total - 1);
        response.setContentType("audio/wav");
        response.setHeader("Accept-Ranges", "bytes");
        response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        if (partial) {
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + total);
        }
        response.setHeader("Content-Length", String.valueOf(end - start + 1));
        try (RandomAccessFile input = new RandomAccessFile(localFile.toFile(), "r")) {
            input.seek(start);
            byte[] buffer = new byte[8192];
            long remaining = end - start + 1;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                response.getOutputStream().write(buffer, 0, read);
                remaining -= read;
            }
        }
        response.flushBuffer();
    }

    private boolean hasPlayableRecording(CallRecord record) {
        return record != null && (isRemoteRecordingReady(record) || resolveLocalRecording(record) != null);
    }

    private boolean isRemoteRecordingReady(CallRecord record) {
        return record != null && StringUtils.isNotBlank(record.getRecordUrl());
    }

    private Path resolveLocalRecording(CallRecord record) {
        if (record == null) {
            return null;
        }
        return com.och.system.util.RecordingFileResolver.resolveExisting(
                record.getFilePath(), sysSettingConfig.getFsProfile());
    }

    private String extractObjectKey(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        String withoutProtocol = url.replaceFirst("https?://", "");
        int slashCount = 0;
        int startIdx = 0;
        for (int i = 0; i < withoutProtocol.length(); i++) {
            if (withoutProtocol.charAt(i) == '/') {
                slashCount++;
                if (slashCount == 2) {
                    startIdx = i + 1;
                    break;
                }
            }
        }
        return withoutProtocol.substring(startIdx).split("\\?", 2)[0];
    }
}
