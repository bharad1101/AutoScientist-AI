package com.autoscientist.config;

import com.autoscientist.util.LocalStorageBackend;
import com.autoscientist.util.StorageBackend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Configures the local filesystem storage backend.
 *
 * <p>Files are stored at the directory specified by {@code app.upload.dir}
 * (environment variable {@code FILE_UPLOAD_DIR}, default {@code ./uploads/datasets}).
 * The directory is created automatically if it does not exist.</p>
 *
 * <p><b>Render limitation:</b> Render's free tier uses an ephemeral filesystem.
 * Uploaded files do not survive redeployments or instance replacements.
 * Users must re-upload datasets after each deployment.</p>
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Value("${app.upload.dir:./uploads/datasets}")
    private String localUploadDir;

    @Bean
    public StorageBackend storageBackend() {
        log.info("Initialising local filesystem storage backend: dir={}", localUploadDir);
        return new LocalStorageBackend(Paths.get(localUploadDir));
    }
}
