package com.autoscientist.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction over physical file storage. Implementations may write to a local
 * filesystem (development) or to S3-compatible object storage (production).
 *
 * <p>Every stored file is identified by a stable, opaque <em>key</em> (returned
 * by the store methods). The key is persisted in the database as the canonical
 * reference for the dataset's physical file. It must never contain directory
 * separators or path-traversal sequences.</p>
 */
public interface StorageBackend {

    /**
     * Stores the content of an {@link InputStream} under the given key.
     *
     * @param key         Unique object key (e.g. UUID + extension).
     * @param inputStream Content to store. The caller is responsible for closing.
     * @param contentLength Byte length, or -1 if unknown.
     * @throws IOException on storage failure.
     */
    void store(String key, InputStream inputStream, long contentLength) throws IOException;

    /**
     * Opens a readable stream for the stored object identified by {@code key}.
     *
     * @throws IOException if the object does not exist or cannot be read.
     */
    InputStream retrieve(String key) throws IOException;

    /**
     * Deletes the object identified by {@code key}. Silently succeeds if the
     * object does not exist.
     */
    void delete(String key);

    /**
     * Returns {@code true} if an object with the given key exists in the store.
     */
    boolean exists(String key);

    /**
     * Non-destructive connectivity check. Returns {@code true} if the backend
     * is reachable and the configured bucket/directory is accessible.
     *
     * <p>Implementations must NOT create or delete any user-visible objects.
     * Use a bucket HEAD request (S3) or a directory stat (local filesystem).</p>
     */
    boolean checkAccessible();

    /** A short human-readable description of this backend (e.g. "local:/app/uploads" or "s3://bucket"). */
    String describe();
}
