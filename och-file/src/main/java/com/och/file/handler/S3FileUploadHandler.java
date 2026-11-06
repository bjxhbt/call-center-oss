package com.och.file.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.och.common.annotation.FileUploadType;
import com.och.common.constant.SysSettingConfig;
import com.och.common.domain.file.FileUploadVo;
import com.och.common.exception.FileException;
import com.och.common.utils.MimeTypeUtils;
import com.och.file.config.S3StorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * S3-compatible file upload handler (works with RustFS, MinIO, AWS S3, etc.)
 */
@FileUploadType(value = "minio")
@Slf4j
public class S3FileUploadHandler extends AbstractFileUploadHandler {

    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final S3StorageConfig config;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3FileUploadHandler(SysSettingConfig sysSettingConfig, S3StorageConfig config) {
        super(sysSettingConfig);
        this.config = config;

        AwsBasicCredentials creds = AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey());
        StaticCredentialsProvider credsProvider = StaticCredentialsProvider.create(creds);
        Region region = Region.of(config.getRegion());
        URI endpoint = URI.create(config.getEndpoint());

        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credsProvider)
                .region(region)
                .forcePathStyle(true)
                .build();

        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credsProvider)
                .region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        log.info("S3FileUploadHandler initialized, endpoint={}, bucket={}", config.getEndpoint(), config.getBucket());
    }

    @Override
    public FileUploadVo upload(MultipartFile multipartFile) {
        String originalName = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalName);
        if (!checkFileFormat(suffix)) {
            throw new FileException(String.format("%s文件格式不被允许上传", suffix));
        }

        try {
            ensureBucketExists();

            Integer fileType = MimeTypeUtils.getFileType(suffix).getCode();
            String uuid = IdUtil.fastSimpleUUID();
            String fileName = uuid + "." + suffix;
            String objectKey = buildObjectKey(fileName, fileType);

            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(objectKey)
                            .contentType(multipartFile.getContentType())
                            .build(),
                    RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize()));

            log.info("MultipartFile uploaded to S3: bucket={}, key={}, size={}", config.getBucket(), objectKey, multipartFile.getSize());
            return buildFileUploadVo(objectKey, originalName, suffix, fileType, multipartFile.getSize());
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("S3 MultipartFile upload failed: {}", e.getMessage(), e);
            throw new FileException("S3上传失败: " + e.getMessage());
        }
    }

    @Override
    public FileUploadVo upload(File uploadFile) {
        String originalName = uploadFile.getName();
        String suffix = FileUtil.getSuffix(originalName);
        if (!checkFileFormat(suffix)) {
            throw new FileException(String.format("%s文件格式不被允许上传", suffix));
        }

        try {
            ensureBucketExists();

            Integer fileType = MimeTypeUtils.getFileType(suffix).getCode();
            String uuid = IdUtil.fastSimpleUUID();
            String fileName = uuid + "." + suffix;
            String objectKey = buildObjectKey(fileName, fileType);

            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(objectKey)
                            .contentType("audio/wav")
                            .build(),
                    RequestBody.fromFile(uploadFile));

            log.info("File uploaded to S3: bucket={}, key={}, size={}", config.getBucket(), objectKey, uploadFile.length());
            return buildFileUploadVo(objectKey, originalName, suffix, fileType, uploadFile.length());
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("S3 File upload failed: {}", e.getMessage(), e);
            throw new FileException("S3上传失败: " + e.getMessage());
        }
    }

    /**
     * Get presigned GET URL for playback/download
     */
    public String getPresignedUrl(String objectKey, int durationHours) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(durationHours))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(objectKey)
                            .build())
                    .build();
            return config.browserDownloadUrl(s3Presigner.presignGetObject(presignRequest).url().toString());
        } catch (Exception e) {
            log.error("S3 presigned URL generation failed: key={}, error={}", objectKey, e.getMessage(), e);
            throw new FileException("获取播放地址失败: " + e.getMessage());
        }
    }

    public void download(String objectKey, OutputStream outputStream) {
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(objectKey)
                .build())) {
            inputStream.transferTo(outputStream);
            outputStream.flush();
        } catch (Exception e) {
            log.error("S3 object download failed: key={}, error={}", objectKey, e.getMessage(), e);
            throw new FileException("录音下载失败: " + e.getMessage());
        }
    }

    /**
     * 按字节区间下载(供在线播放 Range 请求,audio 标签拖动进度)。
     */
    public void downloadRange(String objectKey, OutputStream outputStream, long start, Long end) {
        String range = "bytes=" + start + "-" + (end == null ? "" : end);
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(objectKey)
                .range(range)
                .build())) {
            inputStream.transferTo(outputStream);
            outputStream.flush();
        } catch (Exception e) {
            log.error("S3 object range download failed: key={}, range={}, error={}", objectKey, range, e.getMessage(), e);
            throw new FileException("录音区间下载失败: " + e.getMessage());
        }
    }

    /**
     * 对象大小(字节),用于 Range/Content-Length。
     */
    public long contentLength(String objectKey) {
        try {
            HeadObjectResponse resp = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(objectKey)
                    .build());
            return resp.contentLength();
        } catch (Exception e) {
            log.error("S3 head object failed: key={}, error={}", objectKey, e.getMessage(), e);
            throw new FileException("获取录音大小失败: " + e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(config.getBucket()).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404 && !"NoSuchBucket".equals(e.awsErrorDetails() == null
                    ? null : e.awsErrorDetails().errorCode())) {
                throw e;
            }
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(config.getBucket()).build());
                log.info("Created S3 bucket: {}", config.getBucket());
            } catch (S3Exception createException) {
                if (createException.statusCode() != 409) {
                    throw createException;
                }
                log.info("S3 bucket was created concurrently: {}", config.getBucket());
            }
        }
    }

    private String buildObjectKey(String fileName, Integer fileType) {
        String datePath = LocalDate.now().format(DATE_PATH_FORMAT);
        return datePath + "/" + fileName;
    }

    private FileUploadVo buildFileUploadVo(String objectKey, String originalName,
                                            String suffix, Integer fileType, long fileSize) {
        String filePath = config.getEndpoint() + "/" + config.getBucket() + "/" + objectKey;

        FileUploadVo vo = new FileUploadVo();
        vo.setCosId(objectKey);
        vo.setFileName(originalName);
        vo.setFilePath(filePath);
        vo.setFileSize(String.valueOf(fileSize));
        vo.setFileSuffix(suffix);
        vo.setFileType(fileType);
        return vo;
    }
}
