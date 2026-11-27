package com.autoscientist.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalStorageProvider implements StorageProvider {

    private final Path storageDir;

    public LocalStorageProvider(@Value("${app.upload.dir:./uploads/datasets}") String uploadDir) throws IOException {
        this.storageDir = Paths.get(uploadDir).resolve("temp");
        Files.createDirectories(storageDir);
    }

    @Override
    public UUID saveTempFile(InputStream inputStream) throws IOException {
        UUID id = UUID.randomUUID();
        Path target = storageDir.resolve(id.toString());
        Path temporary = storageDir.resolve(id + ".part");
        try {
            Files.copy(inputStream, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target);
            }
        } catch (IOException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        return id;
    }

    @Override
    public Path getFile(UUID id) {
        return storageDir.resolve(id.toString());
    }

    @Override
    public void deleteFile(UUID id) throws IOException {
        Path file = getFile(id);
        Files.deleteIfExists(file);
    }
}
