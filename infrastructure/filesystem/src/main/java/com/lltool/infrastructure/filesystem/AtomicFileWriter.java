package com.lltool.infrastructure.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe Atomic File Writer (Phase 3.4).
 * Writes data to a temporary file first, then atomically renames/moves it to target destination.
 */
public final class AtomicFileWriter {

    private static final Logger log = LoggerFactory.getLogger(AtomicFileWriter.class);

    private AtomicFileWriter() {}

    public static void writeString(Path targetFile, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writeBytes(targetFile, bytes);
    }

    public static void writeBytes(Path targetFile, byte[] data) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), "atomic-", ".tmp");
        try {
            Files.write(tempFile, data);
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback for filesystems that do not support ATOMIC_MOVE
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Atomically written file: {}", targetFile);
        } finally {
            if (Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }
}

