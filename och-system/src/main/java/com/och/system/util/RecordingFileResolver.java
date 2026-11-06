package com.och.system.util;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Resolves a recording path stored in a call record to a file visible to the
 * current application process. This also supports records created before the
 * production recording root was corrected.
 */
public final class RecordingFileResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String DEFAULT_LINUX_RECORDING_ROOT = "/usr/local/freeswitch/recordings";
    private static final String DEFAULT_WINDOWS_RECORDING_ROOT = "C:/record";

    private RecordingFileResolver() {
    }

    public static Path resolveExisting(String requestedPath, String configuredRoot) {
        if (StringUtils.isBlank(requestedPath)) {
            return null;
        }

        if (StringUtils.isBlank(configuredRoot)) {
            return null;
        }

        Path root = toPath(resolveConfiguredRoot(configuredRoot));
        if (root == null) {
            return null;
        }

        Path requested = toPath(requestedPath);
        if (requested != null && requested.startsWith(root) && isReadableFile(requested)) {
            return requested;
        }

        String fileName = requested == null || requested.getFileName() == null
                ? null : requested.getFileName().toString();
        if (!isSafeRecordingFileName(fileName)) {
            return null;
        }

        String datePart = findDatePart(requestedPath);
        if (datePart != null) {
            Path datedCandidate = root.resolve(datePart).resolve(fileName).normalize();
            if (datedCandidate.startsWith(root.normalize()) && isReadableFile(datedCandidate)) {
                return datedCandidate;
            }
        }

        Path rootCandidate = root.resolve(fileName).normalize();
        if (rootCandidate.startsWith(root.normalize()) && isReadableFile(rootCandidate)) {
            return rootCandidate;
        }
        return null;
    }

    public static String resolveConfiguredRoot(String configuredRoot) {
        String resolved = StringUtils.trimToNull(configuredRoot);
        if (StringUtils.isBlank(resolved)) {
            return isWindows() ? DEFAULT_WINDOWS_RECORDING_ROOT : DEFAULT_LINUX_RECORDING_ROOT;
        }
        if (!isWindows() && looksLikeWindowsPath(resolved)) {
            String environmentPath = StringUtils.trimToNull(System.getenv("OCH_RECORDING_FS_PROFILE"));
            if (StringUtils.isNotBlank(environmentPath) && !looksLikeWindowsPath(environmentPath)) {
                return environmentPath;
            }
            return DEFAULT_LINUX_RECORDING_ROOT;
        }
        return resolved;
    }

    public static Path toPath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        try {
            return Paths.get(path.trim().replace('\\', '/')).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isReadableFile(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            return false;
        }
        try {
            return Files.size(path) > 0;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String findDatePart(String path) {
        String[] parts = path.trim().replace('\\', '/').split("/");
        for (String part : parts) {
            if (part.length() != 10) {
                continue;
            }
            try {
                LocalDate.parse(part, DATE_FORMAT);
                return part;
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static boolean isSafeRecordingFileName(String fileName) {
        return StringUtils.isNotBlank(fileName)
                && fileName.matches("[A-Za-z0-9_-]+\\.(wav|mp3|amr|gsm|ogg)");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean looksLikeWindowsPath(String path) {
        return path != null && path.matches("^[A-Za-z]:[\\\\/].*");
    }
}
