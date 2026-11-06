import com.och.common.constant.SysSettingConfig;
import com.och.common.domain.file.FileUploadVo;
import com.och.file.config.S3StorageConfig;
import com.och.file.handler.S3FileUploadHandler;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;

/** Initialize only the dedicated demo bucket and verify the actual application upload handler. */
public class DemoObjectStorage {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("--init") || args[0].equals("--check"))) {
            throw new IllegalArgumentException("Use --init or --check");
        }
        S3StorageConfig config = new S3StorageConfig();
        config.setEndpoint(required("S3_ENDPOINT"));
        config.setAccessKey(required("S3_ACCESS_KEY"));
        config.setSecretKey(required("S3_SECRET_KEY"));
        config.setBucket(required("S3_STORAGE_BUCKET"));
        config.setRegion(System.getenv().getOrDefault("S3_STORAGE_REGION", "us-east-1"));
        if (!"http://rustfs:9000".equals(config.getEndpoint())
                || !"xhbt-demo-recordings".equals(config.getBucket())) {
            throw new IllegalStateException("Refusing to initialize a non-demo storage endpoint or bucket");
        }
        try (S3Client client = S3Client.builder().endpointOverride(URI.create(config.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .region(Region.of(config.getRegion())).forcePathStyle(true).build()) {
            try {
                client.headBucket(b -> b.bucket(config.getBucket()));
            } catch (S3Exception error) {
                if (!args[0].equals("--init") || error.statusCode() != 404) throw error;
                client.createBucket(b -> b.bucket(config.getBucket()));
            }
            // S3 URLs must remain private; no anonymous download policy is installed.
            S3FileUploadHandler uploader = new S3FileUploadHandler(new SysSettingConfig(), config);
            byte[] payload = new byte[1644];
            for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 127);
            var fixture = Files.createTempFile("xhbt-storage-check-", ".wav");
            String key = null;
            try {
                Files.write(fixture, payload);
                FileUploadVo uploaded = uploader.upload(fixture.toFile());
                key = uploaded.getCosId();
                if (uploader.contentLength(key) != payload.length) throw new IllegalStateException("Size mismatch");
                var full = new ByteArrayOutputStream();
                uploader.download(key, full);
                if (!Arrays.equals(payload, full.toByteArray())) throw new IllegalStateException("Download mismatch");
                var partial = new ByteArrayOutputStream();
                uploader.downloadRange(key, partial, 44, 143L);
                if (!Arrays.equals(Arrays.copyOfRange(payload, 44, 144), partial.toByteArray())) {
                    throw new IllegalStateException("Range download mismatch");
                }
                var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                var anonymous = http.send(HttpRequest.newBuilder(URI.create(uploaded.getFilePath()))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (anonymous.statusCode() != 403) throw new IllegalStateException("Unexpected anonymous object access");
            } finally {
                Files.deleteIfExists(fixture);
                if (key != null) client.deleteObject(DeleteObjectRequest.builder().bucket(config.getBucket()).key(key).build());
            }
        }
        System.out.println("RustFS bucket ready; application upload, full/range download and anonymous-access rejection passed");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment variable: " + name);
        return value;
    }
}
