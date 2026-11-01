package com.autoscientist.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Filesystem-backed storage for local development. Files are written to a
 * configurable directory (defaults to {@code ./uploads/datasets}).
 *
 * <p>Path-traversal protection is applied to every key before it is resolved
 * against the base directory.</p>
 */
@Slf4j
public class LocalStorageBackend implements StorageBackend {

    private final Path baseDir;

    public LocalStorageBackend(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
            // Write-test to fail fast on read-only mounts.
            Path probe = Files.createTempFile(this.baseDir, ".write-test-", ".tmp");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialise local storage at " + this.baseDir, e);
        }
    }

    @Override
    public void store(String key, InputStream inputStream, long contentLength) throws IOException {
        Path target = safePath(key);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("LocalStorage: stored {} ({} bytes)", key, contentLength);
    }

    @Override
    public InputStream retrieve(String key) throws IOException {
        Path target = safePath(key);
        if (!Files.exists(target)) {
            throw new IOException("File not found in local storage: " + key);
        }
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String key) {
        try {
            Path target = safePath(key);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("LocalStorage: failed to delete {}: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Files.exists(safePath(key));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String describe() {
        return "local:" + baseDir;
    }

    private Path safePath(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank.");
        }
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path traversal denied for key: " + key);
        }
        return resolved;
    }
}
