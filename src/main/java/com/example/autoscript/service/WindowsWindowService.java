package com.example.autoscript.service;

import com.example.autoscript.model.WindowInfo;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.awt.Point;
import java.awt.Rectangle;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class WindowsWindowService implements WindowService {

    private static final int SW_RESTORE = 9;

    @Override
    public List<WindowInfo> listWindows() {
        List<WindowInfo> windows = new ArrayList<>();

        User32Compat.INSTANCE.EnumWindows(new WinUser.WNDENUMPROC() {
            @Override
            public boolean callback(HWND hWnd, com.sun.jna.Pointer data) {
                if (!User32Compat.INSTANCE.IsWindowVisible(hWnd)) {
                    return true;
                }

                char[] titleBuffer = new char[1024];
                User32Compat.INSTANCE.GetWindowText(hWnd, titleBuffer, titleBuffer.length);
                String title = Native.toString(titleBuffer).trim();
                if (title.isEmpty()) {
                    return true;
                }

                char[] classBuffer = new char[256];
                User32Compat.INSTANCE.GetClassName(hWnd, classBuffer, classBuffer.length);
                String className = Native.toString(classBuffer).trim();

                RECT rect = new RECT();
                if (!User32Compat.INSTANCE.GetWindowRect(hWnd, rect)) {
                    return true;
                }

                IntByReference pidRef = new IntByReference();
                User32Compat.INSTANCE.GetWindowThreadProcessId(hWnd, pidRef);
                int pid = pidRef.getValue();

                try {
                    Rectangle windowRect = rectToRectangle(rect);
                    Rectangle clientRectOnScreen = queryClientRectOnScreen(hWnd);
                    String processName = resolveProcessName(pid);

                    windows.add(new WindowInfo(
                            hWnd,
                            pid,
                            title,
                            className,
                            processName,
                            windowRect,
                            clientRectOnScreen
                    ));
                } catch (Exception ignored) {
                    // 某些窗口可能拒绝访问 client rect，直接跳过即可。
                }
                return true;
            }
        }, null);

        windows.sort(Comparator.comparing(WindowInfo::getTitle, String.CASE_INSENSITIVE_ORDER));
        return windows;
    }

    @Override
    public Rectangle getClientRectOnScreen(WindowInfo window) {
        return queryClientRectOnScreen(window.getHandle());
    }

    @Override
    public Point clientToScreen(WindowInfo window, int clientX, int clientY) {
        POINT point = new POINT();
        point.x = clientX;
        point.y = clientY;
        if (!User32Compat.INSTANCE.ClientToScreen(window.getHandle(), point)) {
            throw new IllegalStateException("ClientToScreen 失败: " + window.toDisplayText());
        }
        return new Point(point.x, point.y);
    }

    @Override
    public boolean isAlive(WindowInfo window) {
        return User32Compat.INSTANCE.IsWindow(window.getHandle());
    }

    @Override
    public boolean isMinimized(WindowInfo window) {
        return User32Compat.INSTANCE.IsIconic(window.getHandle());
    }

    @Override
    public void restoreIfMinimized(WindowInfo window) {
        if (isMinimized(window)) {
            User32Compat.INSTANCE.ShowWindow(window.getHandle(), SW_RESTORE);
        }
    }

    @Override
    public void bringToFront(WindowInfo window) {
        User32Compat.INSTANCE.SetForegroundWindow(window.getHandle());
    }

    @Override
    public Optional<WindowInfo> tryRestoreBinding(List<WindowInfo> windows, long pid, String title, String className) {
        if (windows == null || windows.isEmpty()) {
            return Optional.empty();
        }

        if (pid > 0) {
            Optional<WindowInfo> byPid = windows.stream()
                    .filter(w -> w.getProcessId() == pid)
                    .findFirst();
            if (byPid.isPresent()) {
                return byPid;
            }
        }

        String normalizedTitle = safeLower(title);
        String normalizedClass = safeLower(className);
        return windows.stream()
                .filter(w -> !normalizedTitle.isBlank() && safeLower(w.getTitle()).equals(normalizedTitle)
                        || (!normalizedClass.isBlank() && safeLower(w.getClassName()).equals(normalizedClass)))
                .findFirst();
    }

    private Rectangle queryClientRectOnScreen(HWND hWnd) {
        RECT clientRect = new RECT();
        if (!User32Compat.INSTANCE.GetClientRect(hWnd, clientRect)) {
            throw new IllegalStateException("GetClientRect 失败");
        }
        POINT clientTopLeft = new POINT();
        clientTopLeft.x = 0;
        clientTopLeft.y = 0;
        if (!User32Compat.INSTANCE.ClientToScreen(hWnd, clientTopLeft)) {
            throw new IllegalStateException("ClientToScreen 失败");
        }
        return new Rectangle(clientTopLeft.x, clientTopLeft.y,
                clientRect.right - clientRect.left,
                clientRect.bottom - clientRect.top);
    }

    private Rectangle rectToRectangle(RECT rect) {
        return new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
    }

    private String resolveProcessName(int pid) {
        return ProcessHandle.of(pid)
                .flatMap(ph -> ph.info().command())
                .map(command -> {
                    try {
                        return Paths.get(command).getFileName().toString();
                    } catch (Exception e) {
                        return command;
                    }
                })
                .orElse("pid=" + pid);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
