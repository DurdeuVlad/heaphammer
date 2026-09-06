package com.dwurdy.heaphammer.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

/**
 * File utility providing atomic file writes to protect against report/plan corruption.
 */
public final class FileStorage {
    private FileStorage() {}

    public static void writeStringAtomic(Path targetFile, String content) throws IOException {
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        Objects.requireNonNull(content, "content must not be null");

        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = (parent != null ? parent : Paths.get("."))
                .resolve(targetFile.getFileName().toString() + ".tmp." + System.nanoTime());

        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }

    public static String readString(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
