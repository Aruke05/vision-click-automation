package com.example.autoscript.service;

import com.example.autoscript.model.CaptureMode;
import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
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

public class RobotCaptureService implements CaptureService {

    private static final int PW_CLIENTONLY = 0x00000001;
    private static final int PW_RENDERFULLCONTENT = 0x00000002;
    private static final int SRCCOPY = 0x00CC0020;
    private static final DirectColorModel SCREENSHOT_COLOR_MODEL = new DirectColorModel(24, 0x00FF0000, 0x0000FF00, 0x000000FF);
    private static final int[] SCREENSHOT_BAND_MASKS = {
            SCREENSHOT_COLOR_MODEL.getRedMask(),
            SCREENSHOT_COLOR_MODEL.getGreenMask(),
            SCREENSHOT_COLOR_MODEL.getBlueMask()
    };

    private final WindowService windowService;

    public RobotCaptureService(WindowService windowService) throws Exception {
        this.windowService = windowService;
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
            return captureByWindowHandle(window, captureRect);
        }
        return captureByScreen(captureRect);
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
                    SRCCOPY
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

    private BufferedImage captureByWindowHandle(WindowInfo window, Rectangle captureRect) {
        Rectangle windowRect = queryWindowRectOnScreen(window);
        HWND hWnd = window.getHandle();

        List<String> attempts = new ArrayList<>();
        BufferedImage windowImage = tryPrintWindowCapture(
                hWnd,
                windowRect.width,
                windowRect.height,
                new int[]{PW_RENDERFULLCONTENT, 0},
                attempts
        );
        if (windowImage != null) {
            int localX = captureRect.x - windowRect.x;
            int localY = captureRect.y - windowRect.y;
            return safeSubImage(windowImage, localX, localY, captureRect.width, captureRect.height, "窗口位图");
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
            int localX = captureRect.x - clientRect.x;
            int localY = captureRect.y - clientRect.y;
            return safeSubImage(clientImage, localX, localY, captureRect.width, captureRect.height, "Client位图");
        }

        throw new IllegalStateException("窗口句柄抓图失败（PrintWindow 不可用）：" + String.join(" | ", attempts)
                + "。可切换截图模式为“屏幕截图（原方式）”继续使用。");
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

}
