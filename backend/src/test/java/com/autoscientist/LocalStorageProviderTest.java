package com.autoscientist;

import com.autoscientist.service.LocalStorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageProviderTest {

    @TempDir
    Path uploadDir;

    @Test
    void storesAndDeletesTemporaryFilesUnderTheConfiguredUploadDirectory() throws Exception {
        LocalStorageProvider provider = new LocalStorageProvider(uploadDir.toString());

        UUID id = provider.saveTempFile(new ByteArrayInputStream("dataset".getBytes(StandardCharsets.UTF_8)));
        Path file = provider.getFile(id);

        assertThat(file).exists();
        assertThat(file.getParent().getFileName().toString()).isEqualTo("temp");
        assertThat(Files.readString(file)).isEqualTo("dataset");

        provider.deleteFile(id);
        assertThat(file).doesNotExist();
    }
}
