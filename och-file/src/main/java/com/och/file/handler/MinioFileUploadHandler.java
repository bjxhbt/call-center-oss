package com.och.file.handler;

import com.och.common.constant.SysSettingConfig;
import com.och.file.config.S3StorageConfig;

/**
 * @deprecated Use {@link S3FileUploadHandler} instead.
 */
@Deprecated
public class MinioFileUploadHandler extends S3FileUploadHandler {

    public MinioFileUploadHandler(SysSettingConfig sysSettingConfig, S3StorageConfig config) {
        super(sysSettingConfig, config);
    }
}
