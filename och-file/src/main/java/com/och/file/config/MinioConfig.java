package com.och.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link S3StorageConfig} instead. Kept for config backward compatibility.
 */
@Data
@Component
@ConfigurationProperties(prefix = "s3-storage")
@Deprecated
public class MinioConfig {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "call-recordings";
}
