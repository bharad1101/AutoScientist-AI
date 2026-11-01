package com.autoscientist.config;

import com.autoscientist.util.LocalStorageBackend;
import com.autoscientist.util.S3StorageBackend;
import com.autoscientist.util.StorageBackend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Selects the storage backend based on environment variables.
 *
 * <ul>
 *   <li>If {@code STORAGE_BACKEND=s3} and S3 credentials are present, uses
 *       {@link S3StorageBackend}.</li>
 *   <li>Otherwise, falls back to {@link LocalStorageBackend} rooted at
 *       {@code app.upload.dir}.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Value("${app.storage.backend:local}")
    private String backendType;

    @Value("${app.upload.dir:./uploads/datasets}")
    private String localUploadDir;

    @Value("${app.storage.s3.bucket:#{null}}")
    private String s3Bucket;

    @Value("${app.storage.s3.endpoint:#{null}}")
    private String s3Endpoint;

    @Value("${app.storage.s3.region:auto}")
    private String s3Region;

    @Value("${app.storage.s3.access-key:#{null}}")
    private String s3AccessKey;

    @Value("${app.storage.s3.secret-key:#{null}}")
    private String s3SecretKey;

    @Bean
    public StorageBackend storageBackend() {
        if ("s3".equalsIgnoreCase(backendType)
                && s3Bucket != null && !s3Bucket.isBlank()
                && s3AccessKey != null && !s3AccessKey.isBlank()
                && s3SecretKey != null && !s3SecretKey.isBlank()) {
            log.info("Initialising S3-compatible storage backend: bucket={}", s3Bucket);
            return new S3StorageBackend(s3Bucket, s3Endpoint, s3Region, s3AccessKey, s3SecretKey);
        }

        log.info("Initialising local filesystem storage backend: dir={}", localUploadDir);
        return new LocalStorageBackend(Paths.get(localUploadDir));
    }
}
