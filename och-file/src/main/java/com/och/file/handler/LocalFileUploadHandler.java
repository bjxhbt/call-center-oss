package com.och.file.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.och.common.annotation.FileUploadType;
import com.och.common.constant.SysSettingConfig;
import com.och.common.domain.file.FileUploadVo;
import com.och.common.exception.FileException;
import com.och.common.utils.MimeTypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * 本地文件上传处理器。
 *
 * @author danmo
 * @date 2023-11-01 15:16
 **/
@FileUploadType(value = "local")
@Slf4j
@Service
public class LocalFileUploadHandler extends AbstractFileUploadHandler {

    public LocalFileUploadHandler(SysSettingConfig lfsSettingConfig) {
        super(lfsSettingConfig);
    }

    @Override
    public FileUploadVo upload(MultipartFile multipartFile) {
        String oldName = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(oldName);
        if (!checkFileFormat(suffix)) {
            throw new FileException(String.format("%s文件格式不允许上传", suffix));
        }
        Integer fileType = MimeTypeUtils.getFileType(suffix).getCode();
        String uuid = IdUtil.fastSimpleUUID();
        File saveFile = createSaveFile(fileType, uuid + "." + suffix);
        try {
            multipartFile.transferTo(saveFile);
        } catch (IOException e) {
            throw new FileException(e.getMessage());
        }
        return buildFileUploadVo(oldName, suffix, uuid, saveFile, multipartFile.getSize());
    }

    @Override
    public FileUploadVo upload(File uploadFile) {
        String oldName = uploadFile.getName();
        String suffix = FileUtil.getSuffix(oldName);
        if (!checkFileFormat(suffix)) {
            throw new FileException(String.format("%s文件格式不允许上传", suffix));
        }
        Integer fileType = MimeTypeUtils.getFileType(suffix).getCode();
        String uuid = IdUtil.fastSimpleUUID();
        File saveFile = createSaveFile(fileType, uuid + "." + suffix);
        FileUtil.copy(uploadFile, saveFile, true);
        return buildFileUploadVo(oldName, suffix, uuid, saveFile, FileUtil.size(uploadFile));
    }

    private File createSaveFile(Integer fileType, String fileName) {
        File saveFile = new File(getFileTempPath(fileType), fileName).getAbsoluteFile();
        File parent = saveFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new FileException("无法创建文件上传目录");
        }
        return saveFile;
    }

    private FileUploadVo buildFileUploadVo(String oldName, String suffix, String uuid,
                                           File saveFile, long fileSize) {
        FileUploadVo fileUploadVo = new FileUploadVo();
        fileUploadVo.setCosId(uuid);
        fileUploadVo.setFileName(oldName);
        fileUploadVo.setFilePath(saveFile.getPath());
        fileUploadVo.setFileSize(String.valueOf(fileSize));
        fileUploadVo.setFileSuffix(suffix);
        fileUploadVo.setFileType(MimeTypeUtils.getFileType(suffix).getCode());
        return fileUploadVo;
    }
}
