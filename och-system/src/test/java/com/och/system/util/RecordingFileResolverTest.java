package com.och.system.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingFileResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesLegacyRootByDateAndFileName() throws Exception {
        Path root = tempDir.resolve("recordings");
        Path dateDirectory = root.resolve("2026-08-20");
        Files.createDirectories(dateDirectory);
        Path recording = dateDirectory.resolve("12345_67890.wav");
        Files.write(recording, new byte[]{1, 2, 3});

        Path resolved = RecordingFileResolver.resolveExisting(
                "/record/2026-08-20/12345_67890.wav", root.toString());

        assertThat(resolved).isEqualTo(recording.toAbsolutePath().normalize());
    }

    @Test
    void rejectsPathTraversalAndUnsupportedFileName() throws Exception {
        Path root = tempDir.resolve("recordings");
        Files.createDirectories(root.resolve("2026-08-20"));
        Files.write(root.resolve("2026-08-20").resolve("secret.txt"), new byte[]{1});

        assertThat(RecordingFileResolver.resolveExisting(
                "/record/2026-08-20/../secret.txt", root.toString())).isNull();
        assertThat(RecordingFileResolver.resolveExisting(
                "/record/2026-08-20/secret.txt", root.toString())).isNull();
    }
}
