package com.example.autoscript.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Properties;

public class OpenCvRecoveryStateStore {

    private final Path statePath;

    public OpenCvRecoveryStateStore() {
        this(resolveDefaultStatePath());
    }

    public OpenCvRecoveryStateStore(Path statePath) {
        this.statePath = statePath == null ? resolveDefaultStatePath() : statePath.toAbsolutePath().normalize();
    }

    public Optional<RecoveryState> load() throws IOException {
        if (Files.notExists(statePath) || !Files.isRegularFile(statePath)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(statePath, StandardOpenOption.READ)) {
            properties.load(inputStream);
        }
        return Optional.of(new RecoveryState(
                parseLong(properties.getProperty("createdAtEpochMillis"), System.currentTimeMillis()),
                properties.getProperty("installStatus", "").trim(),
                parseInt(properties.getProperty("installExitCode"), -1),
                properties.getProperty("installMessage", "").trim(),
                properties.getProperty("installerPath", "").trim(),
                properties.getProperty("installerLogPath", "").trim(),
                properties.getProperty("startupWarning", "").trim(),
                properties.getProperty("startupDiagnostic", "").trim()
        ));
    }

    public void save(RecoveryState state) throws IOException {
        if (state == null) {
            throw new IllegalArgumentException("state == null");
        }
        Path parent = statePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.setProperty("createdAtEpochMillis", String.valueOf(state.createdAtEpochMillis()));
        properties.setProperty("installStatus", safe(state.installStatus()));
        properties.setProperty("installExitCode", String.valueOf(state.installExitCode()));
        properties.setProperty("installMessage", safe(state.installMessage()));
        properties.setProperty("installerPath", safe(state.installerPath()));
        properties.setProperty("installerLogPath", safe(state.installerLogPath()));
        properties.setProperty("startupWarning", safe(state.startupWarning()));
        properties.setProperty("startupDiagnostic", safe(state.startupDiagnostic()));
        try (OutputStream outputStream = Files.newOutputStream(statePath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(outputStream, "OpenCV recovery state");
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(statePath);
    }

    public Path getStatePath() {
        return statePath;
    }

    private static Path resolveDefaultStatePath() {
        return Path.of(System.getProperty("java.io.tmpdir"), "desktop-auto-script", "opencv-recovery.properties")
                .toAbsolutePath()
                .normalize();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw == null ? "" : raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record RecoveryState(long createdAtEpochMillis,
                                String installStatus,
                                int installExitCode,
                                String installMessage,
                                String installerPath,
                                String installerLogPath,
                                String startupWarning,
                                String startupDiagnostic) {
    }
}
