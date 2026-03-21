package com.example.autoscript.service;

import com.example.autoscript.model.CaptureMode;
import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RobotCaptureService implements CaptureService {

    private static final int PW_CLIENTONLY = 0x00000001;
    private static final int PW_RENDERFULLCONTENT = 0x00000002;
    private static final int SRCCOPY = 0x00CC0020;
    private static final int CAPTUREBLT = 0x40000000;
    private static final int RDW_INVALIDATE = 0x0001;
    private static final int RDW_ALLCHILDREN = 0x0080;
    private static final int RDW_UPDATENOW = 0x0100;
    private static final int RDW_FRAME = 0x0400;
    private static final int WM_PRINT = 0x0317;
    private static final int WM_PRINTCLIENT = 0x0318;
    private static final int PRF_CHECKVISIBLE = 0x00000001;
    private static final int PRF_NONCLIENT = 0x00000002;
    private static final int PRF_CLIENT = 0x00000004;
    private static final int PRF_ERASEBKGND = 0x00000008;
    private static final int PRF_CHILDREN = 0x00000010;
    private static final int PRF_OWNED = 0x00000020;
    private static final int BLACK_LUMA_THRESHOLD = 10;
    private static final double BLACK_PIXEL_RATIO_THRESHOLD = 0.985D;
    private static final int BLACK_DYNAMIC_RANGE_THRESHOLD = 18;
    private static final DirectColorModel SCREENSHOT_COLOR_MODEL = new DirectColorModel(24, 0x00FF0000, 0x0000FF00, 0x000000FF);
    private static final int[] SCREENSHOT_BAND_MASKS = {
            SCREENSHOT_COLOR_MODEL.getRedMask(),
            SCREENSHOT_COLOR_MODEL.getGreenMask(),
            SCREENSHOT_COLOR_MODEL.getBlueMask()
    };

    private final WindowService windowService;
    private final Consumer<String> logger;
    private final ConcurrentHashMap<Long, ContentMapping> contentMappingCache = new ConcurrentHashMap<>();

    private record ContentMapping(int sourceWidth, int sourceHeight, Rectangle contentRect, boolean enabled) {
    }

    public RobotCaptureService(WindowService windowService) throws Exception {
        this(windowService, null);
    }

    public RobotCaptureService(WindowService windowService, Consumer<String> logger) throws Exception {
        this.windowService = windowService;
        this.logger = logger;
    }

    @Override
    public BufferedImage capture(WindowInfo window, MonitorRegion region, CaptureMode captureMode) {
        Rectangle clientRect = windowService.getClientRectOnScreen(window);
        Rectangle target = region.resolveWithin(clientRect);
        Rectangle captureRect = target.intersection(clientRect);
        if (captureRect.width <= 0 || captureRect.height <= 0) {
            int maxX = clientRect.width - target.width;
            int maxY = clientRect.height - target.height;
            throw new IllegalArgumentException("监控区域越界: " + region
                    + ", client=" + clientRect
                    + ", 建议 client 坐标范围 x=[0," + maxX + "], y=[0," + maxY + "]");
        }
        CaptureMode effectiveMode = captureMode == null ? CaptureMode.SCREEN : captureMode;
        if (effectiveMode == CaptureMode.WINDOW_HANDLE) {
            return captureByWindowHandle(window, captureRect, false);
        }
        if (effectiveMode == CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN) {
            try {
                return captureByWindowHandle(window, captureRect, true);
            } catch (IllegalStateException e) {
                if (!shouldFallbackToScreen(e)) {
                    throw e;
                }
                return captureByScreenWithFallback(captureRect, e);
            }
        }
        return captureByScreen(captureRect);
    }

    private BufferedImage captureByScreenWithFallback(Rectangle captureRect, IllegalStateException handleFailure) {
        String reason = handleFailure.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = handleFailure.getClass().getSimpleName();
        }
        log("窗口句柄截图失败，自动回退到屏幕截图。详情: " + reason);
        try {
            return captureByScreen(captureRect);
        } catch (Exception screenException) {
            throw new IllegalStateException(reason + "；且自动回退屏幕截图失败: " + screenException.getMessage(), screenException);
        }
    }

    private boolean shouldFallbackToScreen(IllegalStateException e) {
        String message = e == null ? null : e.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("窗口句柄抓图失败")
                || message.contains("PrintWindow")
                || message.contains("位图近乎全黑");
    }

    private BufferedImage captureByScreen(Rectangle captureRect) {
        return captureDesktopRect(captureRect);
    }

    private BufferedImage captureDesktopRect(Rectangle captureRect) {
        if (captureRect == null || captureRect.width <= 0 || captureRect.height <= 0) {
            throw new IllegalArgumentException("截图区域无效: " + captureRect);
        }

        HDC desktopDc = null;
        HDC memoryDc = null;
        HBITMAP bitmap = null;
        HANDLE oldBitmap = null;
        try {
            desktopDc = User32Compat.INSTANCE.GetDC((HWND) null);
            if (desktopDc == null) {
                throw new IllegalStateException("GetDC(Desktop) 失败, error=" + Native.getLastError());
            }
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(desktopDc);
            if (memoryDc == null) {
                throw new IllegalStateException("CreateCompatibleDC 失败, error=" + Native.getLastError());
            }
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(desktopDc, captureRect.width, captureRect.height);
            if (bitmap == null) {
                throw new IllegalStateException("CreateCompatibleBitmap 失败, error=" + Native.getLastError());
            }
            oldBitmap = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (oldBitmap == null) {
                throw new IllegalStateException("SelectObject 失败, error=" + Native.getLastError());
            }
            boolean copied = GDI32.INSTANCE.BitBlt(
                    memoryDc,
                    0,
                    0,
                    captureRect.width,
                    captureRect.height,
                    desktopDc,
                    captureRect.x,
                    captureRect.y,
                    SRCCOPY | CAPTUREBLT
            );
            if (!copied) {
                throw new IllegalStateException("BitBlt 失败, error=" + Native.getLastError());
            }
            return readBitmapToImage(desktopDc, bitmap, captureRect.width, captureRect.height);
        } finally {
            if (oldBitmap != null && memoryDc != null) {
                GDI32.INSTANCE.SelectObject(memoryDc, oldBitmap);
            }
            if (bitmap != null) {
                GDI32.INSTANCE.DeleteObject(bitmap);
            }
            if (memoryDc != null) {
                GDI32.INSTANCE.DeleteDC(memoryDc);
            }
            if (desktopDc != null) {
                User32Compat.INSTANCE.ReleaseDC((HWND) null, desktopDc);
            }
        }
    }

    private BufferedImage captureByWindowHandle(WindowInfo window, Rectangle captureRect, boolean validateFrame) {
        Rectangle windowRect = queryWindowRectOnScreen(window);
        HWND hWnd = window.getHandle();
        requestWindowRedraw(hWnd);

        List<String> attempts = new ArrayList<>();
        BufferedImage windowImage = tryPrintWindowCapture(
                hWnd,
                windowRect.width,
                windowRect.height,
                new int[]{PW_RENDERFULLCONTENT, 0},
                attempts
        );
        if (windowImage != null) {
            if (validateFrame && isMostlyBlack(windowImage)) {
                attempts.add("窗口位图近乎全黑");
            } else {
                int localX = captureRect.x - windowRect.x;
                int localY = captureRect.y - windowRect.y;
                return extractHandleSubImage(hWnd, windowImage, localX, localY, captureRect.width, captureRect.height, "窗口位图");
            }
        }

        Rectangle clientRect = windowService.getClientRectOnScreen(window);
        BufferedImage clientImage = tryPrintWindowCapture(
                hWnd,
                clientRect.width,
                clientRect.height,
                new int[]{PW_CLIENTONLY | PW_RENDERFULLCONTENT, PW_CLIENTONLY},
                attempts
        );
        if (clientImage != null) {
            if (validateFrame && isMostlyBlack(clientImage)) {
                attempts.add("Client位图近乎全黑");
            } else {
                int localX = captureRect.x - clientRect.x;
                int localY = captureRect.y - clientRect.y;
                return extractHandleSubImage(hWnd, clientImage, localX, localY, captureRect.width, captureRect.height, "Client位图");
            }
        }

        BufferedImage wmPrintWindowImage = tryWmPrintCapture(
                hWnd,
                windowRect.width,
                windowRect.height,
                WM_PRINT,
                PRF_CHECKVISIBLE | PRF_NONCLIENT | PRF_CLIENT | PRF_ERASEBKGND | PRF_CHILDREN | PRF_OWNED,
                "WM_PRINT窗口",
                attempts
        );
        if (wmPrintWindowImage != null) {
            if (validateFrame && isMostlyBlack(wmPrintWindowImage)) {
                attempts.add("WM_PRINT窗口位图近乎全黑");
            } else {
                int localX = captureRect.x - windowRect.x;
                int localY = captureRect.y - windowRect.y;
                return extractHandleSubImage(hWnd, wmPrintWindowImage, localX, localY, captureRect.width, captureRect.height, "WM_PRINT窗口位图");
            }
        }

        BufferedImage wmPrintClientImage = tryWmPrintCapture(
                hWnd,
                clientRect.width,
                clientRect.height,
                WM_PRINTCLIENT,
                PRF_CLIENT | PRF_ERASEBKGND | PRF_CHILDREN,
                "WM_PRINTCLIENT",
                attempts
        );
        if (wmPrintClientImage != null) {
            if (validateFrame && isMostlyBlack(wmPrintClientImage)) {
                attempts.add("WM_PRINTCLIENT位图近乎全黑");
            } else {
                int localX = captureRect.x - clientRect.x;
                int localY = captureRect.y - clientRect.y;
                return extractHandleSubImage(hWnd, wmPrintClientImage, localX, localY, captureRect.width, captureRect.height, "WM_PRINTCLIENT位图");
            }
        }

        BufferedImage bitBltClientImage = tryWindowDcBitBltCapture(
                hWnd,
                clientRect.width,
                clientRect.height,
                attempts
        );
        if (bitBltClientImage != null) {
            if (validateFrame && isMostlyBlack(bitBltClientImage)) {
                attempts.add("BitBlt客户端位图近乎全黑");
            } else {
                int localX = captureRect.x - clientRect.x;
                int localY = captureRect.y - clientRect.y;
                return extractHandleSubImage(hWnd, bitBltClientImage, localX, localY, captureRect.width, captureRect.height, "BitBlt客户端位图");
            }
        }

        throw new IllegalStateException("窗口句柄抓图失败（PrintWindow 不可用或返回黑帧）：" + String.join(" | ", attempts)
                + "。建议：以管理员启动；目标窗口改为窗口化/无边框并尝试关闭硬件加速。");
    }

    private Rectangle queryWindowRectOnScreen(WindowInfo window) {
        RECT rect = new RECT();
        if (!User32Compat.INSTANCE.GetWindowRect(window.getHandle(), rect)) {
            throw new IllegalStateException("GetWindowRect 失败，error=" + Native.getLastError());
        }
        int width = Math.max(0, rect.right - rect.left);
        int height = Math.max(0, rect.bottom - rect.top);
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("目标窗口尺寸无效: " + width + "x" + height);
        }
        return new Rectangle(rect.left, rect.top, width, height);
    }

    private BufferedImage tryPrintWindowCapture(HWND hWnd,
                                                int width,
                                                int height,
                                                int[] flags,
                                                List<String> attempts) {
        if (width <= 0 || height <= 0) {
            attempts.add("尺寸无效(" + width + "x" + height + ")");
            return null;
        }
        for (int flag : flags) {
            try {
                BufferedImage image = printWindowToImage(hWnd, width, height, flag);
                if (image != null) {
                    attempts.add("flag=" + flag + " 成功");
                    return image;
                }
                attempts.add("flag=" + flag + " 返回false(error=" + Native.getLastError() + ")");
            } catch (Exception e) {
                attempts.add("flag=" + flag + " 异常(" + e.getMessage() + ")");
            }
        }
        return null;
    }

    private BufferedImage printWindowToImage(HWND hWnd, int width, int height, int flags) {
        HDC windowDc = null;
        HDC memoryDc = null;
        HBITMAP bitmap = null;
        HANDLE oldBitmap = null;
        try {
            windowDc = User32Compat.INSTANCE.GetDC(hWnd);
            if (windowDc == null) {
                throw new IllegalStateException("GetDC 失败, error=" + Native.getLastError());
            }
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
            if (memoryDc == null) {
                throw new IllegalStateException("CreateCompatibleDC 失败, error=" + Native.getLastError());
            }
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, width, height);
            if (bitmap == null) {
                throw new IllegalStateException("CreateCompatibleBitmap 失败, error=" + Native.getLastError());
            }
            oldBitmap = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (oldBitmap == null) {
                throw new IllegalStateException("SelectObject 失败, error=" + Native.getLastError());
            }

            boolean printed = User32Compat.INSTANCE.PrintWindow(hWnd, memoryDc, flags);
            if (!printed) {
                return null;
            }
            return readBitmapToImage(windowDc, bitmap, width, height);
        } finally {
            if (oldBitmap != null && memoryDc != null) {
                GDI32.INSTANCE.SelectObject(memoryDc, oldBitmap);
            }
            if (bitmap != null) {
                GDI32.INSTANCE.DeleteObject(bitmap);
            }
            if (memoryDc != null) {
                GDI32.INSTANCE.DeleteDC(memoryDc);
            }
            if (windowDc != null) {
                User32Compat.INSTANCE.ReleaseDC(hWnd, windowDc);
            }
        }
    }

    private BufferedImage readBitmapToImage(HDC sourceDc, HBITMAP bitmap, int width, int height) {
        WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
        bitmapInfo.bmiHeader.biWidth = width;
        bitmapInfo.bmiHeader.biHeight = -height;
        bitmapInfo.bmiHeader.biPlanes = 1;
        bitmapInfo.bmiHeader.biBitCount = 32;
        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;

        int pixelCount = width * height;
        Memory memory = new Memory((long) pixelCount * 4L);
        int lines = GDI32.INSTANCE.GetDIBits(sourceDc, bitmap, 0, height, memory, bitmapInfo, WinGDI.DIB_RGB_COLORS);
        if (lines <= 0) {
            throw new IllegalStateException("GetDIBits 失败, error=" + Native.getLastError());
        }

        DataBufferInt dataBuffer = new DataBufferInt(memory.getIntArray(0L, pixelCount), pixelCount);
        return new BufferedImage(
                SCREENSHOT_COLOR_MODEL,
                Raster.createPackedRaster(dataBuffer, width, height, width, SCREENSHOT_BAND_MASKS, null),
                false,
                null
        );
    }

    private BufferedImage extractHandleSubImage(HWND hWnd,
                                                BufferedImage source,
                                                int localX,
                                                int localY,
                                                int width,
                                                int height,
                                                String sourceName) {
        if (source == null) {
            throw new IllegalStateException(sourceName + "为空，无法裁剪");
        }
        ContentMapping mapping = resolveContentMapping(hWnd, source);
        if (!mapping.enabled()) {
            return safeSubImage(source, localX, localY, width, height, sourceName);
        }

        Rectangle contentRect = mapping.contentRect();
        int mappedX = contentRect.x + scaleValue(localX, source.getWidth(), contentRect.width);
        int mappedY = contentRect.y + scaleValue(localY, source.getHeight(), contentRect.height);
        int mappedWidth = Math.max(1, scaleValue(width, source.getWidth(), contentRect.width));
        int mappedHeight = Math.max(1, scaleValue(height, source.getHeight(), contentRect.height));

        int maxX = contentRect.x + contentRect.width;
        int maxY = contentRect.y + contentRect.height;
        mappedX = clamp(mappedX, contentRect.x, Math.max(contentRect.x, maxX - 1));
        mappedY = clamp(mappedY, contentRect.y, Math.max(contentRect.y, maxY - 1));
        mappedWidth = Math.max(1, Math.min(mappedWidth, maxX - mappedX));
        mappedHeight = Math.max(1, Math.min(mappedHeight, maxY - mappedY));

        return safeSubImage(source, mappedX, mappedY, mappedWidth, mappedHeight, sourceName + "(内容映射)");
    }

    private ContentMapping resolveContentMapping(HWND hWnd, BufferedImage image) {
        if (hWnd == null || image == null) {
            Rectangle rect = image == null ? new Rectangle(0, 0, 0, 0) : new Rectangle(0, 0, image.getWidth(), image.getHeight());
            return new ContentMapping(rect.width, rect.height, rect, false);
        }

        long key = Pointer.nativeValue(hWnd.getPointer());
        ContentMapping cached = contentMappingCache.get(key);
        if (cached != null && cached.sourceWidth() == image.getWidth() && cached.sourceHeight() == image.getHeight()) {
            return cached;
        }

        Rectangle fullRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        Rectangle contentRect = detectContentRect(image);
        boolean enabled = shouldEnableContentMapping(fullRect, contentRect);
        ContentMapping mapping = new ContentMapping(image.getWidth(), image.getHeight(), contentRect, enabled);
        contentMappingCache.put(key, mapping);
        return mapping;
    }

    private Rectangle detectContentRect(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return new Rectangle(0, 0, 0, 0);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(2, Math.min(width, height) / 180);
        int threshold = BLACK_LUMA_THRESHOLD + 2;

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if (sampleLuma(image, x, y) > threshold) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return new Rectangle(0, 0, width, height);
        }

        int padding = Math.max(2, step * 2);
        int left = Math.max(0, minX - padding);
        int top = Math.max(0, minY - padding);
        int right = Math.min(width, maxX + padding + 1);
        int bottom = Math.min(height, maxY + padding + 1);
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private boolean shouldEnableContentMapping(Rectangle fullRect, Rectangle contentRect) {
        if (fullRect == null || contentRect == null || fullRect.width <= 0 || fullRect.height <= 0) {
            return false;
        }
        if (contentRect.width <= 0 || contentRect.height <= 0) {
            return false;
        }
        double widthRatio = (double) contentRect.width / (double) fullRect.width;
        double heightRatio = (double) contentRect.height / (double) fullRect.height;
        if (widthRatio > 0.92D && heightRatio > 0.92D) {
            return false;
        }
        int tolerance = Math.max(3, Math.min(fullRect.width, fullRect.height) / 120);
        return contentRect.x <= tolerance && contentRect.y <= tolerance;
    }

    private int sampleLuma(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private int scaleValue(int value, int sourceSize, int targetSize) {
        if (sourceSize <= 0 || targetSize <= 0) {
            return value;
        }
        return (int) Math.round((double) value * (double) targetSize / (double) sourceSize);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private BufferedImage safeSubImage(BufferedImage source, int x, int y, int width, int height, String sourceName) {
        if (source == null) {
            throw new IllegalStateException(sourceName + "为空，无法裁剪");
        }
        if (x < 0 || y < 0 || x + width > source.getWidth() || y + height > source.getHeight()) {
            throw new IllegalStateException(sourceName + "裁剪越界: x=" + x + ", y=" + y
                    + ", w=" + width + ", h=" + height
                    + ", source=" + source.getWidth() + "x" + source.getHeight());
        }
        BufferedImage extracted = source.getSubimage(x, y, width, height);
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = copy.createGraphics();
        try {
            g2d.drawImage(extracted, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return copy;
    }

    private boolean isMostlyBlack(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, Math.min(width, height) / 80);

        long total = 0L;
        long black = 0L;
        int minLuma = 255;
        int maxLuma = 0;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                if (luma <= BLACK_LUMA_THRESHOLD) {
                    black++;
                }
                minLuma = Math.min(minLuma, luma);
                maxLuma = Math.max(maxLuma, luma);
                total++;
            }
        }
        if (total <= 0) {
            return false;
        }
        double blackRatio = (double) black / (double) total;
        return blackRatio >= BLACK_PIXEL_RATIO_THRESHOLD && (maxLuma - minLuma) <= BLACK_DYNAMIC_RANGE_THRESHOLD;
    }

    private void log(String message) {
        if (logger != null && message != null && !message.isBlank()) {
            logger.accept(message);
        }
    }

    private void requestWindowRedraw(HWND hWnd) {
        if (hWnd == null) {
            return;
        }
        try {
            User32Compat.INSTANCE.RedrawWindow(
                    hWnd,
                    null,
                    null,
                    RDW_INVALIDATE | RDW_ALLCHILDREN | RDW_UPDATENOW | RDW_FRAME
            );
        } catch (Exception ignored) {
        }
    }

    private BufferedImage tryWindowDcBitBltCapture(HWND hWnd,
                                                   int width,
                                                   int height,
                                                   List<String> attempts) {
        if (width <= 0 || height <= 0) {
            attempts.add("BitBlt尺寸无效(" + width + "x" + height + ")");
            return null;
        }
        try {
            BufferedImage image = bitBltWindowDcToImage(hWnd, width, height);
            if (image != null) {
                attempts.add("BitBlt(GetDC) 成功");
                return image;
            }
            attempts.add("BitBlt(GetDC) 返回false(error=" + Native.getLastError() + ")");
        } catch (Exception e) {
            attempts.add("BitBlt(GetDC) 异常(" + e.getMessage() + ")");
        }
        return null;
    }

    private BufferedImage tryWmPrintCapture(HWND hWnd,
                                            int width,
                                            int height,
                                            int message,
                                            int printFlags,
                                            String label,
                                            List<String> attempts) {
        if (width <= 0 || height <= 0) {
            attempts.add(label + "尺寸无效(" + width + "x" + height + ")");
            return null;
        }
        try {
            BufferedImage image = wmPrintToImage(hWnd, width, height, message, printFlags, label, attempts);
            if (image != null) {
                return image;
            }
            attempts.add(label + " 返回null");
        } catch (Exception e) {
            attempts.add(label + " 异常(" + e.getMessage() + ")");
        }
        return null;
    }

    private BufferedImage wmPrintToImage(HWND hWnd,
                                         int width,
                                         int height,
                                         int message,
                                         int printFlags,
                                         String label,
                                         List<String> attempts) {
        HDC windowDc = null;
        HDC memoryDc = null;
        HBITMAP bitmap = null;
        HANDLE oldBitmap = null;
        try {
            windowDc = User32Compat.INSTANCE.GetDC(hWnd);
            if (windowDc == null) {
                throw new IllegalStateException("GetDC 失败, error=" + Native.getLastError());
            }
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
            if (memoryDc == null) {
                throw new IllegalStateException("CreateCompatibleDC 失败, error=" + Native.getLastError());
            }
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, width, height);
            if (bitmap == null) {
                throw new IllegalStateException("CreateCompatibleBitmap 失败, error=" + Native.getLastError());
            }
            oldBitmap = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (oldBitmap == null) {
                throw new IllegalStateException("SelectObject 失败, error=" + Native.getLastError());
            }

            long hdcValue = Pointer.nativeValue(memoryDc.getPointer());
            LRESULT result = User32Compat.INSTANCE.SendMessage(
                    hWnd,
                    message,
                    new WPARAM(hdcValue),
                    new com.sun.jna.platform.win32.WinDef.LPARAM(printFlags)
            );
            attempts.add(label + " 结果=" + result.longValue());
            return readBitmapToImage(windowDc, bitmap, width, height);
        } finally {
            if (oldBitmap != null && memoryDc != null) {
                GDI32.INSTANCE.SelectObject(memoryDc, oldBitmap);
            }
            if (bitmap != null) {
                GDI32.INSTANCE.DeleteObject(bitmap);
            }
            if (memoryDc != null) {
                GDI32.INSTANCE.DeleteDC(memoryDc);
            }
            if (windowDc != null) {
                User32Compat.INSTANCE.ReleaseDC(hWnd, windowDc);
            }
        }
    }

    private BufferedImage bitBltWindowDcToImage(HWND hWnd, int width, int height) {
        HDC windowDc = null;
        HDC memoryDc = null;
        HBITMAP bitmap = null;
        HANDLE oldBitmap = null;
        try {
            windowDc = User32Compat.INSTANCE.GetDC(hWnd);
            if (windowDc == null) {
                throw new IllegalStateException("GetDC 失败, error=" + Native.getLastError());
            }
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
            if (memoryDc == null) {
                throw new IllegalStateException("CreateCompatibleDC 失败, error=" + Native.getLastError());
            }
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, width, height);
            if (bitmap == null) {
                throw new IllegalStateException("CreateCompatibleBitmap 失败, error=" + Native.getLastError());
            }
            oldBitmap = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (oldBitmap == null) {
                throw new IllegalStateException("SelectObject 失败, error=" + Native.getLastError());
            }

            boolean copied = GDI32.INSTANCE.BitBlt(
                    memoryDc,
                    0,
                    0,
                    width,
                    height,
                    windowDc,
                    0,
                    0,
                    SRCCOPY | CAPTUREBLT
            );
            if (!copied) {
                return null;
            }
            return readBitmapToImage(windowDc, bitmap, width, height);
        } finally {
            if (oldBitmap != null && memoryDc != null) {
                GDI32.INSTANCE.SelectObject(memoryDc, oldBitmap);
            }
            if (bitmap != null) {
                GDI32.INSTANCE.DeleteObject(bitmap);
            }
            if (memoryDc != null) {
                GDI32.INSTANCE.DeleteDC(memoryDc);
            }
            if (windowDc != null) {
                User32Compat.INSTANCE.ReleaseDC(hWnd, windowDc);
            }
        }
    }

}
