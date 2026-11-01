package com.autoscientist;

import com.autoscientist.util.FileStorageService;
import com.autoscientist.util.LocalStorageBackend;
import com.autoscientist.util.StorageBackend;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the storage abstraction layer and FileStorageService integration.
 * Uses a temp directory — no production data is touched.
 */
class StorageBackendTest {

    @TempDir
    Path tempDir;

    private StorageBackend backend;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        backend = new LocalStorageBackend(tempDir);
        fileStorageService = new FileStorageService(backend);
        fileStorageService.init();
    }

    // ---- StorageBackend direct tests ----

    @Test
    @DisplayName("Store and retrieve a file round-trip")
    void storeAndRetrieve() throws IOException {
        String key = "test-file.csv";
        byte[] content = "id,name\n1,Alice\n2,Bob\n".getBytes(StandardCharsets.UTF_8);
        backend.store(key, new ByteArrayInputStream(content), content.length);

        assertTrue(backend.exists(key));
        try (InputStream in = backend.retrieve(key)) {
            String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("id,name\n1,Alice\n2,Bob\n", result);
        }
    }

    @Test
    @DisplayName("Delete removes the file")
    void deleteRemovesFile() throws IOException {
        String key = "to-delete.csv";
        byte[] content = "x".getBytes();
        backend.store(key, new ByteArrayInputStream(content), content.length);
        assertTrue(backend.exists(key));

        backend.delete(key);
        assertFalse(backend.exists(key));
    }

    @Test
    @DisplayName("Retrieve non-existent key throws IOException")
    void retrieveNonExistent() {
        assertThrows(IOException.class, () -> backend.retrieve("no-such-key.csv"));
    }

    @Test
    @DisplayName("Delete non-existent key does not throw")
    void deleteNonExistentSilent() {
        assertDoesNotThrow(() -> backend.delete("no-such-key.csv"));
    }

    @Test
    @DisplayName("Path traversal is blocked")
    void pathTraversalBlocked() {
        assertThrows(IllegalArgumentException.class, () ->
                backend.store("../escape.csv", new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test
    @DisplayName("Backend describe() returns a human-readable string")
    void describeReturnsString() {
        String desc = backend.describe();
        assertNotNull(desc);
        assertTrue(desc.startsWith("local:"));
    }

    @Test
    @DisplayName("checkAccessible() returns true for a valid local backend")
    void checkAccessibleLocal() {
        assertTrue(backend.checkAccessible(), "LocalStorageBackend should report accessible for a writable temp dir");
    }

    // ---- FileStorageService integration tests ----

    @Test
    @DisplayName("storeBytes creates a retrievable key")
    void storeBytesAndRetrieve() throws IOException {
        byte[] csvData = "col1,col2\na,b\n".getBytes(StandardCharsets.UTF_8);
        String key = fileStorageService.storeBytes(csvData, "test.csv");
        assertNotNull(key);
        assertTrue(key.endsWith(".csv"));

        try (InputStream in = fileStorageService.getInputStream(key)) {
            String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("col1,col2\na,b\n", result);
        }
    }

    @Test
    @DisplayName("getInputStream throws BadRequestException for missing file")
    void getInputStreamMissingFile() {
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> fileStorageService.getInputStream("non-existent-key.csv"));
    }

    @Test
    @DisplayName("deleteFile returns true for existing file, false for missing")
    void deleteFileBehavior() {
        byte[] data = "x".getBytes();
        String key = fileStorageService.storeBytes(data, "del-test.csv");
        assertTrue(fileStorageService.deleteFile(key));
        assertFalse(fileStorageService.deleteFile(key));
    }

    @Test
    @DisplayName("isHealthy uses non-destructive check and returns true for accessible backend")
    void healthCheckNonDestructive() {
        // Count objects in tempDir before
        java.io.File[] before = tempDir.toFile().listFiles();
        int countBefore = before == null ? 0 : before.length;

        assertTrue(fileStorageService.isHealthy());

        // isHealthy must NOT create any new files (non-destructive)
        java.io.File[] after = tempDir.toFile().listFiles();
        int countAfter = after == null ? 0 : after.length;
        assertEquals(countBefore, countAfter,
                "isHealthy must not create or leave any probe files");
    }

    @Test
    @DisplayName("storeBytes generates unique keys for same filename")
    void uniqueKeysGenerated() {
        byte[] data = "a".getBytes();
        String key1 = fileStorageService.storeBytes(data, "same.csv");
        String key2 = fileStorageService.storeBytes(data, "same.csv");
        assertNotEquals(key1, key2, "Keys must be unique even for same filename");
    }

    @Test
    @DisplayName("Blank storage key is rejected")
    void blankKeyRejected() {
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> fileStorageService.getInputStream(""));
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> fileStorageService.getInputStream("  "));
    }

    @Test
    @DisplayName("Key with path separator is rejected")
    void pathSeparatorRejected() {
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> fileStorageService.getInputStream("../etc/passwd"));
        assertThrows(com.autoscientist.exception.BadRequestException.class,
                () -> fileStorageService.getInputStream("subdir/file.csv"));
    }

    @Test
    @DisplayName("Excel extensions are preserved in generated keys")
    void extensionsPreserved() {
        byte[] data = "x".getBytes();
        assertTrue(fileStorageService.storeBytes(data, "wb.xlsx").endsWith(".xlsx"));
        assertTrue(fileStorageService.storeBytes(data, "wb.xls").endsWith(".xls"));
        assertTrue(fileStorageService.storeBytes(data, "data.csv").endsWith(".csv"));
    }

    @Test
    @DisplayName("getBackendDescription returns the backend describe() string")
    void backendDescriptionDelegates() {
        String desc = fileStorageService.getBackendDescription();
        assertNotNull(desc);
        assertEquals(backend.describe(), desc);
    }
}
