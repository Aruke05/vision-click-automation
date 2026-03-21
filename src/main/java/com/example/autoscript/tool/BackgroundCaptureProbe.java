package com.example.autoscript.tool;

import com.example.autoscript.model.CaptureMode;
import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;
import com.example.autoscript.service.CaptureService;
import com.example.autoscript.service.RobotCaptureService;
import com.example.autoscript.service.WindowService;
import com.example.autoscript.service.WindowsWindowService;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BackgroundCaptureProbe {

    private static final long DEFAULT_PID = 8528L;
    private static final String DEFAULT_TITLE_KEYWORD = "FallenDoll";
    private static final String DEFAULT_CLASS_NAME = "UnrealWindow";

    private BackgroundCaptureProbe() {
    }

    public static void main(String[] args) throws Exception {
        long pid = parsePid(args);
        String titleKeyword = args.length >= 2 ? args[1] : DEFAULT_TITLE_KEYWORD;
        String className = args.length >= 3 ? args[2] : DEFAULT_CLASS_NAME;

        WindowService windowService = new WindowsWindowService();
        List<WindowInfo> windows = windowService.listWindows();
        Optional<WindowInfo> targetOpt = windows.stream()
                .filter(w -> (pid > 0 && w.getProcessId() == pid)
                        || containsIgnoreCase(w.getTitle(), titleKeyword)
                        || equalsIgnoreCase(w.getClassName(), className))
                .findFirst();

        if (targetOpt.isEmpty()) {
            System.out.println("未找到目标窗口。可见窗口如下：");
            for (WindowInfo window : windows) {
                System.out.println("  " + window.toDisplayText() + ", process=" + window.getProcessName());
            }
            return;
        }

        WindowInfo target = targetOpt.get();
        Rectangle clientRect = windowService.getClientRectOnScreen(target);
        MonitorRegion region = new MonitorRegion();
        region.setX(0);
        region.setY(0);
        region.setWidth(clientRect.width);
        region.setHeight(clientRect.height);
        region.setReferenceWidth(clientRect.width);
        region.setReferenceHeight(clientRect.height);

        System.out.println("目标窗口: " + target.toDisplayText() + ", process=" + target.getProcessName());
        System.out.println("ClientRect: " + clientRect);

        CaptureService captureService = new RobotCaptureService(windowService, message -> System.out.println("[probe] " + message));
        Path captureDir = Paths.get("captures");
        Files.createDirectories(captureDir);

        runProbe(captureService, target, region, CaptureMode.WINDOW_HANDLE, captureDir);
        runProbe(captureService, target, region, CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN, captureDir);
    }

    private static void runProbe(CaptureService captureService,
                                 WindowInfo window,
                                 MonitorRegion region,
                                 CaptureMode mode,
                                 Path captureDir) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        String fileName = "probe-" + mode.name().toLowerCase(Locale.ROOT) + "-" + timestamp + ".png";
        Path output = captureDir.resolve(fileName);
        long start = System.currentTimeMillis();

        try {
            BufferedImage image = captureService.capture(window, region, mode);
            ImageIO.write(image, "png", output.toFile());
            double blackRatio = calculateBlackRatio(image);
            long cost = System.currentTimeMillis() - start;
            System.out.printf("mode=%s success size=%dx%d blackRatio=%.4f costMs=%d file=%s%n",
                    mode,
                    image.getWidth(),
                    image.getHeight(),
                    blackRatio,
                    cost,
                    output.toAbsolutePath());
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            System.out.printf("mode=%s failed costMs=%d msg=%s%n", mode, cost, e.getMessage());
        }
    }

    private static double calculateBlackRatio(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return 1.0D;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, Math.min(width, height) / 120);
        long total = 0L;
        long black = 0L;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r <= 10 && g <= 10 && b <= 10) {
                    black++;
                }
                total++;
            }
        }
        if (total <= 0L) {
            return 1.0D;
        }
        return (double) black / (double) total;
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        if (value == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        if (value == null || expected == null || expected.isBlank()) {
            return false;
        }
        return value.trim().equalsIgnoreCase(expected.trim());
    }

    private static long parsePid(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_PID;
        }
        try {
            return Long.parseLong(args[0].trim());
        } catch (Exception ignored) {
            return DEFAULT_PID;
        }
    }
}
