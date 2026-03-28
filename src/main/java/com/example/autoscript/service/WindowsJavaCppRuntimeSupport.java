package com.example.autoscript.service;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.bytedeco.javacpp.Loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WindowsJavaCppRuntimeSupport {

    private static final String[] RUNTIME_DLLS = {
            "jnijavacpp.dll",
            "libomp140.x86_64.dll",
            "vcomp140.dll",
            "concrt140.dll",
            "vcruntime140.dll",
            "vcruntime140_1.dll",
            "vcruntime140_threads.dll",
            "msvcp140.dll",
            "msvcp140_1.dll",
            "msvcp140_2.dll",
            "ucrtbase.dll"
    };

    private static final AtomicBoolean PREPARED = new AtomicBoolean(false);

    private WindowsJavaCppRuntimeSupport() {
    }

    public static void prepare() {
        if (!Loader.getPlatform().startsWith("windows")) {
            return;
        }
        if (PREPARED.get()) {
            return;
        }
        synchronized (PREPARED) {
            if (PREPARED.get()) {
                return;
            }
            try {
                Path runtimeDir = resolveRuntimeDir();
                Files.createDirectories(runtimeDir);
                List<String> extracted = extractBundledRuntimeDlls(runtimeDir);
                registerDllDirectory(runtimeDir);
                if (runtimeDir.resolve("libomp140.x86_64.dll").toFile().isFile()) {
                    System.load(runtimeDir.resolve("libomp140.x86_64.dll").toString());
                }
                PREPARED.set(true);
            } catch (Exception e) {
                throw new IllegalStateException("准备 JavaCPP Windows 运行时失败: " + e.getMessage(), e);
            }
        }
    }

    private static Path resolveRuntimeDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "desktop-auto-script", "javacpp-runtime", Loader.getPlatform())
                .toAbsolutePath()
                .normalize();
    }

    private static List<String> extractBundledRuntimeDlls(Path runtimeDir) throws IOException {
        List<String> extracted = new ArrayList<>();
        String platform = Loader.getPlatform();
        for (String fileName : RUNTIME_DLLS) {
            String resourcePath = "/org/bytedeco/javacpp/" + platform + "/" + fileName;
            try (InputStream inputStream = WindowsJavaCppRuntimeSupport.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    continue;
                }
                Path output = runtimeDir.resolve(fileName);
                Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
                extracted.add(output.toString());
            }
        }
        if (extracted.isEmpty()) {
            throw new IOException("未在 classpath 中找到 org/bytedeco/javacpp/" + platform + " 下的运行时 DLL");
        }
        return extracted;
    }

    private static void registerDllDirectory(Path runtimeDir) {
        boolean success = Kernel32Ext.INSTANCE.SetDllDirectoryW(new WString(runtimeDir.toString()));
        if (!success) {
            throw new IllegalStateException("SetDllDirectoryW 失败: " + runtimeDir);
        }
    }

    private interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetDllDirectoryW(WString path);
    }
}
