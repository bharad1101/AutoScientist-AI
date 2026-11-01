package com.autoscientist.util;

import com.autoscientist.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * High-level file storage service used by all dataset-consuming code.
 *
 * <p>Delegates physical I/O to a {@link StorageBackend} (local filesystem).
 * The public API (store / retrieve / delete by opaque key) is stable so no
 * downstream service needs modification.</p>
 *
 * <p><b>Render limitation:</b> Render's free tier uses an ephemeral filesystem.
 * Uploaded files do not survive redeployments. When a file is missing, a
 * user-friendly error message asks the user to re-upload the dataset.</p>
 */
@Slf4j
@Service
public class FileStorageService {

    private final StorageBackend storageBackend;

    public FileStorageService(StorageBackend storageBackend) {
        this.storageBackend = storageBackend;
    }

    @PostConstruct
    public void init() {
        log.info("FileStorageService ready — backend: {}", storageBackend.describe());
    }

    // ---- Store methods ----

    /**
     * Stores a {@link MultipartFile} and returns a unique storage key.
     * The key is a UUID with the original file extension preserved.
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (!ExcelImportService.isSupportedFilename(originalFilename)) {
            throw new BadRequestException("Only .csv, .xls and .xlsx files are supported.");
        }

        String key = generateKey(originalFilename);
        try (InputStream in = file.getInputStream()) {
            storageBackend.store(key, in, file.getSize());
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    /**
     * Stores raw bytes (e.g. a converted CSV rendition of a workbook) under a
     * derived name.
     */
    public String storeBytes(byte[] content, String desiredFilename) {
        String key = generateKey(desiredFilename);
        try (InputStream in = new ByteArrayInputStream(content)) {
            storageBackend.store(key, in, content.length);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    // ---- Retrieve methods ----

    /**
     * Opens a readable stream for the stored file identified by its key.
     *
     * @throws IOException if the file does not exist in the storage backend.
     */
    public InputStream getInputStream(String storedFilename) throws IOException {
        validateKey(storedFilename);
        try {
            return storageBackend.retrieve(storedFilename);
        } catch (IOException e) {
            throw new BadRequestException(
                    "Dataset file is no longer available. It may have been lost during a server "
                    + "restart. Please re-upload the dataset. (key: " + storedFilename + ")");
        }
    }

    /**
     * Returns the storage key after validating that the referenced file still
     * exists.
     *
     * @deprecated Prefer {@link #getInputStream(String)} — a storage key is not
     *             a filesystem path when S3 is active.
     */
    @Deprecated
    public java.nio.file.Path loadFilePath(String storedFilename) {
        validateKey(storedFilename);
        if (!storageBackend.exists(storedFilename)) {
            throw new BadRequestException(
                    "Dataset file is no longer available. It may have been lost during a server "
                    + "restart. Please re-upload the dataset.");
        }
        // Return a synthetic Path for backward compatibility; callers should
        // migrate to getInputStream().
        return java.nio.file.Path.of(storedFilename);
    }

    // ---- Delete ----

    /**
     * Deletes a stored file. Returns {@code true} if the file was deleted,
     * {@code false} if it did not exist.
     */
    public boolean deleteFile(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) return false;
        boolean existed = storageBackend.exists(storedFilename);
        storageBackend.delete(storedFilename);
        return existed;
    }

    // ---- Health ----

    /**
     * Non-destructive connectivity check: returns {@code true} if the storage
     * backend is reachable and its bucket/directory is accessible.
     *
     * <p>Uses a HeadBucket call (S3) or a directory stat (local filesystem) —
     * no objects are created or deleted during a readiness probe.</p>
     */
    public boolean isHealthy() {
        try {
            return storageBackend.checkAccessible();
        } catch (Exception e) {
            log.warn("Storage health check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Returns the backend description string. */
    public String getBackendDescription() {
        return storageBackend.describe();
    }

    // ---- Private helpers ----

    private String generateKey(String filename) {
        String ext = ".csv";
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".xls")) ext = ".xls";
            else if (lower.endsWith(".xlsx")) ext = ".xlsx";
        }
        return UUID.randomUUID().toString() + ext;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Storage key must not be blank.");
        }
        if (key.contains("..") || key.contains("/") || key.contains("\\")) {
            throw new BadRequestException("Invalid storage key.");
        }
    }
}
