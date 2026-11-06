package com.och.api.controller.sys;

import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.domain.file.FileUploadVo;
import com.och.system.service.ISysFileService;
import com.och.system.domain.entity.SysFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/system/v1/file")
public class SysFileController extends BaseController {

    @Autowired
    private ISysFileService sysFileService;

    /**
     * 文件上传
     *
     * @param file 文件
     * @param type 上传方式 local ali  tx
     * @return 文件上传结果
     */
    @Operation(description = "文件上传", method = "POST")
    @PostMapping("/upload")
    public ResResult<FileUploadVo> uploadFile(@RequestParam("file") MultipartFile file) {
        FileUploadVo uploadedFile = sysFileService.uploadFile(file,null);
        return success(uploadedFile);
    }

    @Operation(description = "文件下载", method = "GET")
    @org.springframework.web.bind.annotation.GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@org.springframework.web.bind.annotation.PathVariable Long id) {
        SysFile sysFile = sysFileService.getById(id);
        if (sysFile == null || sysFile.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = resolveLocalPath(sysFile.getFilePath());
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(sysFile.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(path.toFile().length())
                .body(resource);
    }

    private Path resolveLocalPath(String configuredPath) {
        String normalized = configuredPath.replace('\\', '/');
        normalized = normalized.replaceFirst("^https?://(?:127\\.0\\.0\\.1|localhost)(?::\\d+)?", "");
        normalized = normalized.replaceFirst("^(?:127\\.0\\.0\\.1|localhost)(?::\\d+)?", "");
        return Paths.get(normalized).normalize();
    }


}
