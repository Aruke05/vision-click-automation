package com.example.autoscript.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class VcRedistributableInstaller {

    public static final URI OFFICIAL_DOWNLOAD_URI = URI.create("https://aka.ms/vc14/vc_redist.x64.exe");
    private static final String INSTALLER_FILENAME = "VC_redist.x64.exe";

    private final Path workingDirectory;
    private final boolean runningAsAdmin;
    private final Consumer<String> logger;

    public VcRedistributableInstaller(Path workingDirectory,
                                      boolean runningAsAdmin,
                                      Consumer<String> logger) {
        this.workingDirectory = workingDirectory == null ? Path.of(".").toAbsolutePath().normalize() : workingDirectory;
        this.runningAsAdmin = runningAsAdmin;
        this.logger = logger;
    }

    public InstallResult install() {
        try {
            Path installerPath = resolveInstallerPath();
            log("准备安装 Microsoft Visual C++ x64 运行库: " + installerPath);
            InstallerRunResult runResult = runInstaller(installerPath);
            int exitCode = runResult.exitCode();
            if (exitCode == 0) {
                return new InstallResult(InstallStatus.INSTALLED, installerPath, true,
                        "运行库安装完成，请重新启动程序以启用 OpenCV。", exitCode, runResult.logPath());
            }
            if (exitCode == 3010) {
                return new InstallResult(InstallStatus.RESTART_REQUIRED, installerPath, true,
                        "运行库安装完成，系统要求重启。请先重启系统，再重新打开程序。", exitCode, runResult.logPath());
            }
            if (exitCode == 1223) {
                return new InstallResult(InstallStatus.CANCELED, installerPath, true,
                        "已取消安装，当前继续使用纯 Java 匹配。", exitCode, runResult.logPath());
            }
            return new InstallResult(InstallStatus.FAILED, installerPath, true,
                    buildFailureMessage(exitCode, runResult.logPath()), exitCode, runResult.logPath());
        } catch (Exception e) {
            return new InstallResult(InstallStatus.FAILED, null, false,
                    "安装运行库失败: " + e.getMessage(), -1, null);
        }
    }

    private Path resolveInstallerPath() throws Exception {
        Path bundledInstaller = findBundledInstaller();
        if (bundledInstaller != null) {
            log("已找到本地运行库安装包: " + bundledInstaller);
            return bundledInstaller;
        }
        return downloadInstaller();
    }

    private Path findBundledInstaller() {
        Set<Path> candidates = new LinkedHashSet<>();
        collectInstallerCandidates(candidates, workingDirectory);
        collectInstallerCandidates(candidates, workingDirectory.getParent());
        collectInstallerCandidates(candidates, resolveRuntimeDirectory());
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private void collectInstallerCandidates(Set<Path> candidates, Path dir) {
        if (dir == null) {
            return;
        }
        candidates.add(dir.resolve(INSTALLER_FILENAME));
        candidates.add(dir.resolve(INSTALLER_FILENAME.toLowerCase()));
        candidates.add(dir.resolve("resource").resolve(INSTALLER_FILENAME));
        candidates.add(dir.resolve("resource").resolve(INSTALLER_FILENAME.toLowerCase()));
        candidates.add(dir.resolve("redist").resolve(INSTALLER_FILENAME));
        candidates.add(dir.resolve("redist").resolve(INSTALLER_FILENAME.toLowerCase()));
    }

    private Path resolveRuntimeDirectory() {
        try {
            CodeSource codeSource = VcRedistributableInstaller.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path downloadInstaller() throws Exception {
        Path downloadDir = Path.of(System.getProperty("java.io.tmpdir"), "desktop-auto-script", "redist");
        Files.createDirectories(downloadDir);
        Path output = downloadDir.resolve(INSTALLER_FILENAME);

        log("未找到本地运行库安装包，开始从微软官方下载: " + OFFICIAL_DOWNLOAD_URI);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(OFFICIAL_DOWNLOAD_URI)
                .GET()
                .header("User-Agent", "desktop-auto-script/1.0")
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("下载运行库安装包失败，HTTP " + statusCode + "，下载地址: " + OFFICIAL_DOWNLOAD_URI);
        }
        try (InputStream inputStream = Objects.requireNonNull(response.body(), "response.body")) {
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
        }
        long size = Files.size(output);
        if (size <= 0L) {
            throw new IOException("下载完成但文件为空: " + output);
        }
        log("运行库安装包下载完成: " + output + " (" + size + " bytes)");
        return output;
    }

    private InstallerRunResult runInstaller(Path installerPath) throws Exception {
        if (installerPath == null || !Files.isRegularFile(installerPath)) {
            throw new IllegalArgumentException("安装包不存在: " + installerPath);
        }
        Path logPath = createInstallerLogPath();
        if (runningAsAdmin) {
            Process process = new ProcessBuilder(
                    installerPath.toString(),
                    "/install",
                    "/passive",
                    "/norestart",
                    "/log",
                    logPath.toString()
            ).redirectErrorStream(true).start();
            return new InstallerRunResult(process.waitFor(), logPath);
        }

        String escapedPath = installerPath.toString().replace("'", "''");
        String escapedLogPath = logPath.toString().replace("'", "''");
        String script = "$ErrorActionPreference='Stop'; "
                + "try { "
                + "$p = Start-Process -FilePath '" + escapedPath + "' "
                + "-ArgumentList '/install','/passive','/norestart','/log','" + escapedLogPath + "' -Verb RunAs -Wait -PassThru; "
                + "exit $p.ExitCode "
                + "} catch { "
                + "if ($_.Exception -and $_.Exception.NativeErrorCode -eq 1223) { exit 1223 } "
                + "if ($_.FullyQualifiedErrorId -like '*1223*') { exit 1223 } "
                + "Write-Error $_; exit 1 "
                + "}";
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start();
        return new InstallerRunResult(process.waitFor(), logPath);
    }

    private Path createInstallerLogPath() throws IOException {
        Path logDir = Path.of(System.getProperty("java.io.tmpdir"), "desktop-auto-script", "redist", "logs");
        Files.createDirectories(logDir);
        String filename = "vc_redist_install_" + System.currentTimeMillis() + ".log";
        return logDir.resolve(filename).toAbsolutePath().normalize();
    }

    private String buildFailureMessage(int exitCode, Path logPath) {
        StringBuilder message = new StringBuilder();
        if (exitCode == 1602) {
            message.append("安装程序提前结束（退出码 1602）。");
            message.append(" 如果你没有主动取消，通常需要查看安装日志确认具体原因。");
        } else {
            message.append("安装程序返回非成功退出码: ").append(exitCode);
        }
        String logSummary = readInstallerLogSummary(logPath);
        if (!logSummary.isBlank()) {
            message.append("\n安装日志摘要:\n").append(logSummary);
        }
        if (logPath != null) {
            message.append("\n安装日志: ").append(logPath);
        }
        return message.toString();
    }

    private String readInstallerLogSummary(Path logPath) {
        if (logPath == null || Files.notExists(logPath) || !Files.isRegularFile(logPath)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(logPath);
            List<String> interesting = new ArrayList<>();
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (lower.contains("error")
                        || lower.contains("fail")
                        || lower.contains("return code")
                        || lower.contains("0x")
                        || lower.contains("final result")
                        || lower.contains("cancel")
                        || lower.contains("operation")
                        || lower.contains("apply")
                        || lower.contains("package")) {
                    interesting.add(trimmed);
                }
            }
            List<String> picked = interesting.isEmpty() ? tail(lines, 12) : tail(interesting, 12);
            String joined = String.join(System.lineSeparator(), picked).trim();
            if (joined.length() > 1200) {
                return joined.substring(0, 1200) + "...";
            }
            return joined;
        } catch (Exception e) {
            log("读取 VC++ 安装日志失败: " + e.getMessage());
            return "";
        }
    }

    private List<String> tail(List<String> lines, int maxCount) {
        List<String> filtered = new ArrayList<>();
        if (lines == null || lines.isEmpty() || maxCount <= 0) {
            return filtered;
        }
        for (String line : lines) {
            if (line != null) {
                String trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    filtered.add(trimmed);
                }
            }
        }
        int fromIndex = Math.max(0, filtered.size() - maxCount);
        return filtered.subList(fromIndex, filtered.size());
    }

    private void log(String message) {
        if (logger != null && message != null && !message.isBlank()) {
            logger.accept(message);
        }
    }

    public enum InstallStatus {
        INSTALLED,
        RESTART_REQUIRED,
        CANCELED,
        FAILED
    }

    public record InstallResult(InstallStatus status,
                                Path installerPath,
                                boolean installAttempted,
                                String message,
                                int exitCode,
                                Path logPath) {
    }

    private record InstallerRunResult(int exitCode, Path logPath) {
    }
}
