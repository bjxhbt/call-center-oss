package com.och.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Data
@Component
@ConfigurationProperties(prefix = "s3-storage")
public class S3StorageConfig {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "call-recordings";
    private String region = "us-east-1";
    /** Same-origin download proxy; it must preserve the upstream Host and signed path. */
    private String downloadPrefix;

    public String browserDownloadUrl(String presignedUrl) {
        if (downloadPrefix == null || downloadPrefix.isBlank()) {
            return presignedUrl;
        }
        if (!downloadPrefix.startsWith("/") || downloadPrefix.startsWith("//")
                || downloadPrefix.contains("?") || downloadPrefix.contains("#")) {
            throw new IllegalStateException("S3 download prefix must be an absolute local path");
        }
        URI signed = URI.create(presignedUrl);
        URI upstream = URI.create(endpoint);
        if (!upstream.getScheme().equals(signed.getScheme())
                || !upstream.getRawAuthority().equals(signed.getRawAuthority())) {
            throw new IllegalArgumentException("Unexpected S3 signing endpoint");
        }
        String prefix = downloadPrefix.replaceFirst("/+$", "");
        return prefix + signed.getRawPath()
                + (signed.getRawQuery() == null ? "" : "?" + signed.getRawQuery());
    }
}
