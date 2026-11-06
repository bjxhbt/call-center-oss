package com.och.file.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3StorageConfigTest {
    private final S3StorageConfig config = new S3StorageConfig();

    @Test
    void preservesEncodedObjectKeyAndSignatureDuringProxyRewrite() {
        config.setEndpoint("http://rustfs:9000");
        config.setDownloadPrefix("/object-storage/");
        String signed = "http://rustfs:9000/demo/a%20b/%E5%BD%95%E9%9F%B3%2B.wav?X-Amz-Signature=a%2Bb&X-Amz-Credential=c%2Fd";
        assertEquals("/object-storage/demo/a%20b/%E5%BD%95%E9%9F%B3%2B.wav?X-Amz-Signature=a%2Bb&X-Amz-Credential=c%2Fd",
                config.browserDownloadUrl(signed));
    }

    @Test
    void leavesExistingDeploymentsWithoutProxyUnchanged() {
        String signed = "https://storage.example.test/demo/recording.wav?signature=test";
        assertEquals(signed, config.browserDownloadUrl(signed));
    }

    @Test
    void rejectsUnexpectedUpstreamAndExternalDownloadPrefix() {
        config.setEndpoint("http://rustfs:9000");
        config.setDownloadPrefix("/object-storage");
        assertThrows(IllegalArgumentException.class,
                () -> config.browserDownloadUrl("http://other:9000/demo/file?signature=test"));
        config.setDownloadPrefix("//external.example.test");
        assertThrows(IllegalStateException.class,
                () -> config.browserDownloadUrl("http://rustfs:9000/demo/file?signature=test"));
    }
}
