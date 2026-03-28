package com.example.autoscript.service;

import com.example.autoscript.DesktopAutomationApp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

public class ApplicationRelauncher {

    private final Path workingDirectory;

    public ApplicationRelauncher(Path workingDirectory) {
        this.workingDirectory = workingDirectory == null
                ? Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
                : workingDirectory.toAbsolutePath().normalize();
    }

    public RelaunchPlan relaunch() throws Exception {
        RelaunchPlan plan = buildPlan();
        ProcessBuilder builder = new ProcessBuilder(plan.command());
        if (plan.workingDirectory() != null && Files.isDirectory(plan.workingDirectory())) {
            builder.directory(plan.workingDirectory().toFile());
        }
        builder.start();
        return plan;
    }

    public RelaunchPlan buildPlan() throws Exception {
        Path javaCommand = resolveJavaCommand();
        Path codeSourcePath = resolveCodeSourcePath();
        List<String> command = new ArrayList<>();
        command.add(javaCommand.toString());

        if (isJarPath(codeSourcePath)) {
            command.add("-jar");
            command.add(codeSourcePath.toString());
            return new RelaunchPlan(List.copyOf(command), workingDirectory,
                    "java -jar " + codeSourcePath.getFileName());
        }

        String classpath = System.getProperty("java.class.path", "").trim();
        if (classpath.isBlank()) {
            throw new IllegalStateException("无法确定当前程序的 classpath，无法自动重启。");
        }
        command.add("-cp");
        command.add(classpath);
        command.add(DesktopAutomationApp.class.getName());
        return new RelaunchPlan(List.copyOf(command), workingDirectory,
                "java -cp <classpath> " + DesktopAutomationApp.class.getSimpleName());
    }

    private Path resolveJavaCommand() {
        Path binDir = Paths.get(System.getProperty("java.home", ""), "bin");
        if (Files.isDirectory(binDir)) {
            Path javaw = binDir.resolve("javaw.exe");
            if (Files.isRegularFile(javaw)) {
                return javaw.toAbsolutePath().normalize();
            }
            Path java = binDir.resolve("java.exe");
            if (Files.isRegularFile(java)) {
                return java.toAbsolutePath().normalize();
            }
            Path generic = binDir.resolve("java");
            if (Files.isRegularFile(generic)) {
                return generic.toAbsolutePath().normalize();
            }
        }
        return Paths.get("java");
    }

    private Path resolveCodeSourcePath() throws Exception {
        CodeSource codeSource = DesktopAutomationApp.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("无法确定当前程序启动位置，无法自动重启。");
        }
        return Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
    }

    private boolean isJarPath(Path path) {
        if (path == null) {
            return false;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        return Files.isRegularFile(path) && name.endsWith(".jar");
    }

    public record RelaunchPlan(List<String> command, Path workingDirectory, String description) {
    }
}
