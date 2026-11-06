package com.och.api.controller.fs;

import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.constant.SysSettingConfig;
import com.och.file.config.S3StorageConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S3-compatible storage management API (RustFS/MinIO)
 */
@Slf4j
@Tag(name = "存储管理")
@RequestMapping("/v1/storage")
@RestController
public class StorageController extends BaseController {

    @Autowired
    private S3StorageConfig storageConfig;

    @Autowired
    private SysSettingConfig sysSettingConfig;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    private S3Client getS3Client() {
        if (s3Client == null) {
            AwsBasicCredentials creds = AwsBasicCredentials.create(storageConfig.getAccessKey(), storageConfig.getSecretKey());
            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(storageConfig.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .region(Region.of(storageConfig.getRegion()))
                    .forcePathStyle(true)
                    .build();
        }
        return s3Client;
    }

    private S3Presigner getS3Presigner() {
        if (s3Presigner == null) {
            AwsBasicCredentials creds = AwsBasicCredentials.create(storageConfig.getAccessKey(), storageConfig.getSecretKey());
            s3Presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(storageConfig.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .region(Region.of(storageConfig.getRegion()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        }
        return s3Presigner;
    }

    @Operation(description = "获取存储桶列表")
    @GetMapping("/buckets")
    public ResResult<List<Map<String, Object>>> listBuckets() {
        try {
            ListBucketsResponse response = getS3Client().listBuckets();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Bucket bucket : response.buckets()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", bucket.name());
                map.put("creationDate", bucket.creationDate().toString());
                result.add(map);
            }
            return success(result);
        } catch (Exception e) {
            log.error("获取存储桶列表失败: {}", e.getMessage(), e);
            return ResResult.error("获取存储桶列表失败: " + e.getMessage());
        }
    }

    @Operation(description = "获取对象列表")
    @GetMapping("/objects")
    public ResResult<Map<String, Object>> listObjects(
            @RequestParam(defaultValue = "") String bucket,
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            String bucketName = bucket.isEmpty() ? storageConfig.getBucket() : bucket;

            List<Map<String, Object>> allObjects = new ArrayList<>();
            long totalSize = 0;

            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder().bucket(bucketName);
            if (!prefix.isEmpty()) {
                reqBuilder.prefix(prefix);
            }

            ListObjectsV2Response response = getS3Client().listObjectsV2(reqBuilder.build());
            for (S3Object obj : response.contents()) {
                Map<String, Object> o = new HashMap<>();
                o.put("objectKey", obj.key());
                o.put("size", obj.size());
                o.put("lastModified", obj.lastModified() != null ? obj.lastModified().toString() : "");
                o.put("etag", obj.eTag());
                allObjects.add(o);
                totalSize += obj.size();
            }

            int total = allObjects.size();
            int fromIndex = Math.min((page - 1) * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<Map<String, Object>> pageList = allObjects.subList(fromIndex, toIndex);

            Map<String, Object> data = new HashMap<>();
            data.put("list", pageList);
            data.put("total", total);
            data.put("totalSize", totalSize);
            data.put("page", page);
            data.put("size", size);
            data.put("bucket", bucketName);
            return success(data);
        } catch (Exception e) {
            log.error("获取对象列表失败: {}", e.getMessage(), e);
            return ResResult.error("获取对象列表失败: " + e.getMessage());
        }
    }

    @Operation(description = "获取存储统计")
    @GetMapping("/stats")
    public ResResult<Map<String, Object>> getStats() {
        try {
            String bucketName = storageConfig.getBucket();
            boolean exists = true;
            try {
                getS3Client().headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            } catch (Exception e) {
                exists = false;
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("endpoint", storageConfig.getEndpoint());
            stats.put("bucket", bucketName);
            stats.put("bucketExists", exists);

            if (!exists) {
                stats.put("objectCount", 0);
                stats.put("totalSize", 0);
                return success(stats);
            }

            ListObjectsV2Response response = getS3Client().listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucketName).build());

            long objectCount = response.keyCount() != null ? response.keyCount() : response.contents().size();
            long totalSize = response.contents().stream().mapToLong(S3Object::size).sum();

            stats.put("objectCount", objectCount);
            stats.put("totalSize", totalSize);
            return success(stats);
        } catch (Exception e) {
            log.error("获取存储统计失败: {}", e.getMessage(), e);
            return ResResult.error("获取存储统计失败: " + e.getMessage());
        }
    }

    @Operation(description = "删除对象")
    @DeleteMapping("/object")
    public ResResult<Void> deleteObject(
            @RequestParam(defaultValue = "") String bucket,
            @RequestParam String objectKey) {
        try {
            String bucketName = bucket.isEmpty() ? storageConfig.getBucket() : bucket;
            getS3Client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
            log.info("Deleted object: bucket={}, key={}", bucketName, objectKey);
            return success();
        } catch (Exception e) {
            log.error("删除对象失败: {}", e.getMessage(), e);
            return ResResult.error("删除对象失败: " + e.getMessage());
        }
    }

    @Operation(description = "获取对象下载URL")
    @GetMapping("/download-url")
    public ResResult<String> getDownloadUrl(
            @RequestParam(defaultValue = "") String bucket,
            @RequestParam String objectKey) {
        try {
            String bucketName = bucket.isEmpty() ? storageConfig.getBucket() : bucket;
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build())
                    .build();
            String url = getS3Presigner().presignGetObject(presignRequest).url().toString();
            return success(storageConfig.browserDownloadUrl(url));
        } catch (Exception e) {
            log.error("获取下载URL失败: {}", e.getMessage(), e);
            return ResResult.error("获取下载URL失败: " + e.getMessage());
        }
    }
}
