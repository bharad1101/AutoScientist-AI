package com.autoscientist.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * S3-compatible object storage backend using the standard
 * {@link software.amazon.awssdk.services.s3.S3Client}. Works with AWS S3,
 * Cloudflare R2, MinIO, DigitalOcean Spaces, Backblaze B2, and any
 * S3-compatible service.
 *
 * <p>Configuration is injected at construction time; no credentials or
 * endpoints are ever hardcoded or logged.</p>
 */
@Slf4j
public class S3StorageBackend implements StorageBackend {

    private final software.amazon.awssdk.services.s3.S3Client s3;
    private final String bucket;

    /**
     * @param bucket    Bucket name.
     * @param endpoint  S3-compatible endpoint URI, or {@code null} for AWS default.
     * @param region    AWS region (use "auto" for Cloudflare R2, "us-east-1" for AWS).
     * @param accessKey Access key ID.
     * @param secretKey Secret access key.
     */
    public S3StorageBackend(String bucket, String endpoint, String region,
                            String accessKey, String secretKey) {
        this.bucket = bucket;

        var builder = software.amazon.awssdk.services.s3.S3Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .credentialsProvider(
                        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                        accessKey, secretKey)))
                // Cloudflare R2 requires path-style access; harmless for AWS.
                .forcePathStyle(true);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        this.s3 = builder.build();

        // Log bucket name and region only — never log credentials or full endpoint.
        log.info("S3StorageBackend initialised: bucket={}, region={}", bucket, region);
    }

    @Override
    public void store(String key, InputStream inputStream, long contentLength) throws IOException {
        try {
            byte[] bytes = inputStream.readAllBytes();
            var putRequest = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentLength((long) bytes.length)
                    .build();
            s3.putObject(putRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
            log.debug("S3: stored object key={}, size={} bytes", key, bytes.length);
        } catch (Exception e) {
            throw new IOException("Failed to upload to S3 storage: " + sanitise(e.getMessage()), e);
        }
    }

    @Override
    public InputStream retrieve(String key) throws IOException {
        try {
            var getRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            byte[] bytes = s3.getObjectAsBytes(getRequest).asByteArray();
            log.debug("S3: retrieved object key={}, size={} bytes", key, bytes.length);
            return new ByteArrayInputStream(bytes);
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            throw new IOException("Object not found in S3 storage: " + key);
        } catch (Exception e) {
            throw new IOException("Failed to retrieve from S3 storage: " + sanitise(e.getMessage()), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            var deleteRequest = software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3.deleteObject(deleteRequest);
            log.debug("S3: deleted object key={}", key);
        } catch (Exception e) {
            log.warn("S3: failed to delete key={}: {}", key, sanitise(e.getMessage()));
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            var headRequest = software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3.headObject(headRequest);
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("S3: exists check failed for key={}: {}", key, sanitise(e.getMessage()));
            return false;
        }
    }

    /**
     * Non-destructive connectivity check using HeadBucket.
     * Does NOT create or delete any objects. Returns {@code true} if the bucket
     * is reachable and the credentials have at least list/head access.
     */
    @Override
    public boolean checkAccessible() {
        try {
            var headBucketRequest = software.amazon.awssdk.services.s3.model.HeadBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build();
            s3.headBucket(headBucketRequest);
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchBucketException e) {
            log.warn("S3 health: bucket '{}' does not exist", bucket);
            return false;
        } catch (Exception e) {
            log.warn("S3 health: bucket check failed: {}", sanitise(e.getMessage()));
            return false;
        }
    }

    @Override
    public String describe() {
        return "s3://" + bucket;
    }

    /**
     * Strips potential credential fragments from exception messages before logging.
     * Removes anything that looks like an AWS credential (26-char uppercase strings)
     * and query-string auth parameters.
     */
    private static String sanitise(String msg) {
        if (msg == null) return "unknown error";
        return msg.replaceAll("[A-Z0-9]{20,}", "***")
                  .replaceAll("(?i)(x-amz-credential|authorization)[^,&\\s]*", "***");
    }
}
