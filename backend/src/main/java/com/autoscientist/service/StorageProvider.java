package com.autoscientist.service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Service handling transient file storage for jobs. Implementations must ensure that
 * files are stored in a safe, unique location and can be retrieved or deleted by
 * the owning job. The storage is intended for temporary use only – files are
 * cleaned up after the job finishes or via the hourly TTL cleanup.
 */
public interface StorageProvider {
    /**
     * Store a temporary file and return a unique identifier.
     */
    UUID saveTempFile(InputStream inputStream) throws Exception;

    /** Retrieve the absolute path of a stored file given its identifier. */
    Path getFile(UUID id) throws Exception;

    /** Delete a stored file. */
    void deleteFile(UUID id) throws Exception;
}
