package com.example.autoscript.service;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.model.WindowInfo;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class WindowClickExecutor implements ActionExecutor {

    private static final int WM_MOUSEMOVE = 0x0200;
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int MK_LBUTTON = 0x0001;
    private static final int VK_LBUTTON = 0x01;
    private static final int KEY_PRESSED_MASK = 0x8000;
    private static final int MOUSE_VERIFY_TOLERANCE_PX = 12;
    private static final int MOUSE_VERIFY_RETRY = 2;
    private static final int FOREGROUND_RESTORE_RETRY = 3;
    private static final int CLICK_SETTLE_DELAY_MS = 35;
    private static final int INPUT_UNLOCK_SETTLE_DELAY_MS = 45;
    private static final int LEFT_BUTTON_RELEASE_WAIT_MS = 180;
    private static final int LEFT_BUTTON_RELEASE_POLL_MS = 8;

    private final WindowService windowService;
    private final Robot robot;

    public WindowClickExecutor(WindowService windowService) throws Exception {
        this.windowService = windowService;
        this.robot = new Robot();
        this.robot.setAutoDelay(10);
    }

    @Override
    public boolean clickClient(WindowInfo window, int clientX, int clientY, AppConfig config) {
        Rectangle clientRect = windowService.getClientRectOnScreen(window);
        if (clientX < 0 || clientY < 0 || clientX >= clientRect.width || clientY >= clientRect.height) {
            throw new IllegalArgumentException("点击坐标越界: client=(" + clientX + "," + clientY
                    + "), clientSize=" + clientRect.width + "x" + clientRect.height
                    + ", 建议范围 x=[0," + Math.max(0, clientRect.width - 1)
                    + "], y=[0," + Math.max(0, clientRect.height - 1) + "]");
        }

        boolean backgroundMode = config != null && config.isBackgroundClickMode();
        boolean forceRobot = requiresPhysicalClick(window);
        if (backgroundMode || !forceRobot) {
            boolean postOk = clickByPostMessage(window, clientX, clientY);
            if (backgroundMode || postOk) {
                return postOk;
            }
        }

        Point screenPoint = windowService.clientToScreen(window, clientX, clientY);
        HWND previousForeground = User32Compat.INSTANCE.GetForegroundWindow();
        Point previousCursor = queryCursorPosition();
        InputGuard inputGuard = InputGuard.acquire(screenPoint);
        try {
            windowService.restoreIfMinimized(window);
            windowService.bringToFront(window);
            robot.delay(80);

            boolean foregroundReady = waitForeground(window, forceRobot ? 4 : 2, 35);
            if (!foregroundReady) {
                forceClickByRobot(window, screenPoint);
                return true;
            }
            clickByRobot(screenPoint);
            return true;
        } finally {
            inputGuard.prepareForRestore();
            restoreForegroundWindow(previousForeground);
            inputGuard.restoreCursorClip();
            restoreCursorPosition(previousCursor);
            waitForLeftButtonRelease();
            robot.delay(INPUT_UNLOCK_SETTLE_DELAY_MS);
            inputGuard.releaseInputBlock();
        }
    }

    @Override
    public boolean keyPress(WindowInfo window, int keyCode) {
        windowService.restoreIfMinimized(window);
        windowService.bringToFront(window);
        robot.keyPress(keyCode);
        robot.delay(20);
        robot.keyRelease(keyCode);
        return true;
    }

    private boolean requiresPhysicalClick(WindowInfo window) {
        String className = window == null || window.getClassName() == null
                ? ""
                : window.getClassName().trim().toLowerCase(Locale.ROOT);
        return className.contains("unreal")
                || className.contains("unity")
                || className.contains("chrome_widgetwin");
    }

    private boolean isForegroundWindow(WindowInfo window) {
        HWND foreground = User32Compat.INSTANCE.GetForegroundWindow();
        return isSameWindow(window == null ? null : window.getHandle(), foreground);
    }

    private boolean waitForeground(WindowInfo window, int attempts, int delayMs) {
        int total = Math.max(1, attempts);
        int delay = Math.max(1, delayMs);
        for (int i = 0; i < total; i++) {
            if (isForegroundWindow(window)) {
                return true;
            }
            windowService.bringToFront(window);
            robot.delay(delay);
        }
        return isForegroundWindow(window);
    }

    private boolean isSameWindow(HWND a, HWND b) {
        if (a == null || b == null || a.getPointer() == null || b.getPointer() == null) {
            return false;
        }
        long pa = Pointer.nativeValue(a.getPointer());
        long pb = Pointer.nativeValue(b.getPointer());
        return pa != 0L && pa == pb;
    }

    private boolean clickByPostMessage(WindowInfo window, int clientX, int clientY) {
        long lParamValue = ((long) clientY << 16) | (clientX & 0xFFFFL);
        LPARAM lParam = new LPARAM(lParamValue);
        boolean moveOk = User32Compat.INSTANCE.PostMessage(window.getHandle(), WM_MOUSEMOVE, new WPARAM(0), lParam);
        boolean downOk = User32Compat.INSTANCE.PostMessage(window.getHandle(), WM_LBUTTONDOWN, new WPARAM(MK_LBUTTON), lParam);
        boolean upOk = User32Compat.INSTANCE.PostMessage(window.getHandle(), WM_LBUTTONUP, new WPARAM(0), lParam);
        return moveOk && downOk && upOk;
    }

    private void forceClickByRobot(WindowInfo window, Point screenPoint) {
        for (int i = 0; i < 3; i++) {
            windowService.bringToFront(window);
            robot.delay(35);
            clickByRobot(screenPoint);
            robot.delay(30);
            if (isForegroundWindow(window)) {
                return;
            }
        }
    }

    private void clickByRobot(Point nativeScreenPoint) {
        if (nativeScreenPoint == null) {
            throw new IllegalArgumentException("点击坐标不能为空");
        }
        Point current = null;
        for (int i = 0; i <= MOUSE_VERIFY_RETRY; i++) {
            try {
                User32Compat.INSTANCE.SetCursorPos(nativeScreenPoint.x, nativeScreenPoint.y);
            } catch (Exception ignored) {
            }
            current = queryCursorPosition();
            if (current == null) {
                if (i < MOUSE_VERIFY_RETRY) {
                    robot.delay(12);
                }
                continue;
            }
            int dx = Math.abs(current.x - nativeScreenPoint.x);
            int dy = Math.abs(current.y - nativeScreenPoint.y);
            if (dx <= MOUSE_VERIFY_TOLERANCE_PX && dy <= MOUSE_VERIFY_TOLERANCE_PX) {
                break;
            }
            if (i < MOUSE_VERIFY_RETRY) {
                robot.delay(12);
            }
        }
        if (current != null) {
            int dx = Math.abs(current.x - nativeScreenPoint.x);
            int dy = Math.abs(current.y - nativeScreenPoint.y);
            if (dx > MOUSE_VERIFY_TOLERANCE_PX || dy > MOUSE_VERIFY_TOLERANCE_PX) {
                throw new IllegalStateException("鼠标移动失败: nativeTarget=(" + nativeScreenPoint.x + "," + nativeScreenPoint.y
                        + "), actual=(" + current.x + "," + current.y + ")");
            }
        }
        robot.delay(25);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(CLICK_SETTLE_DELAY_MS);
    }

    private Point queryCursorPosition() {
        try {
            POINT point = new POINT();
            if (User32Compat.INSTANCE.GetCursorPos(point)) {
                return new Point(point.x, point.y);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void restoreCursorPosition(Point position) {
        if (position == null) {
            return;
        }
        for (int i = 0; i <= MOUSE_VERIFY_RETRY; i++) {
            try {
                User32Compat.INSTANCE.SetCursorPos(position.x, position.y);
            } catch (Exception ignored) {
            }
            Point current = queryCursorPosition();
            if (current != null) {
                int dx = Math.abs(current.x - position.x);
                int dy = Math.abs(current.y - position.y);
                if (dx <= MOUSE_VERIFY_TOLERANCE_PX && dy <= MOUSE_VERIFY_TOLERANCE_PX) {
                    return;
                }
            }
            if (i < MOUSE_VERIFY_RETRY) {
                robot.delay(12);
            }
        }
    }

    private void waitForLeftButtonRelease() {
        int waited = 0;
        while (waited < LEFT_BUTTON_RELEASE_WAIT_MS && isLeftButtonPressed()) {
            robot.delay(LEFT_BUTTON_RELEASE_POLL_MS);
            waited += LEFT_BUTTON_RELEASE_POLL_MS;
        }
    }

    private boolean isLeftButtonPressed() {
        try {
            return (User32Compat.INSTANCE.GetAsyncKeyState(VK_LBUTTON) & KEY_PRESSED_MASK) != 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void restoreForegroundWindow(HWND hWnd) {
        if (hWnd == null || hWnd.getPointer() == null) {
            return;
        }
        try {
            for (int i = 0; i < FOREGROUND_RESTORE_RETRY; i++) {
                User32Compat.INSTANCE.SetForegroundWindow(hWnd);
                robot.delay(22);
                if (isSameWindow(hWnd, User32Compat.INSTANCE.GetForegroundWindow())) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final class InputGuard {
        private static final int WATCHDOG_INTERVAL_MS = 6;
        private static final int WATCHDOG_JOIN_TIMEOUT_MS = 100;

        private final Point lockedPoint;
        private final AtomicBoolean active = new AtomicBoolean(false);
        private RECT previousClipRect;
        private boolean hasPreviousClipRect = false;
        private boolean inputBlocked = false;
        private boolean cursorClipped = false;
        private Thread watchdogThread;

        private InputGuard(Point lockedPoint) {
            this.lockedPoint = lockedPoint == null ? null : new Point(lockedPoint);
        }

        static InputGuard acquire(Point lockedPoint) {
            InputGuard guard = new InputGuard(lockedPoint);
            guard.lock();
            return guard;
        }

        void prepareForRestore() {
            stopWatchdog();
        }

        void releaseInputBlock() {
            if (inputBlocked) {
                tryBlockInput(false);
                inputBlocked = false;
            }
        }

        void restoreCursorClip() {
            restoreClipCursor();
        }

        void release() {
            releaseInputBlock();
            restoreCursorClip();
        }

        private void lock() {
            captureCurrentClipCursor();
            inputBlocked = tryBlockInput(true);
            if (lockedPoint != null) {
                cursorClipped = tryClipCursor(lockedPoint.x, lockedPoint.y);
                startWatchdog();
            }
        }

        private void captureCurrentClipCursor() {
            try {
                RECT current = new RECT();
                if (User32Compat.INSTANCE.GetClipCursor(current)) {
                    hasPreviousClipRect = true;
                    previousClipRect = new RECT();
                    previousClipRect.left = current.left;
                    previousClipRect.top = current.top;
                    previousClipRect.right = current.right;
                    previousClipRect.bottom = current.bottom;
                }
            } catch (Exception ignored) {
                hasPreviousClipRect = false;
                previousClipRect = null;
            }
        }

        private boolean tryBlockInput(boolean block) {
            try {
                return User32Compat.INSTANCE.BlockInput(block);
            } catch (Exception ignored) {
                return false;
            }
        }

        private boolean tryClipCursor(int x, int y) {
            try {
                RECT lockRect = new RECT();
                lockRect.left = x;
                lockRect.top = y;
                lockRect.right = x + 1;
                lockRect.bottom = y + 1;
                return User32Compat.INSTANCE.ClipCursor(lockRect);
            } catch (Exception ignored) {
                return false;
            }
        }

        private void restoreClipCursor() {
            if (!cursorClipped) {
                return;
            }
            try {
                if (hasPreviousClipRect && previousClipRect != null) {
                    User32Compat.INSTANCE.ClipCursor(previousClipRect);
                } else {
                    User32Compat.INSTANCE.ClipCursor(null);
                }
            } catch (Exception ignored) {
            }
        }

        private void startWatchdog() {
            if (lockedPoint == null) {
                return;
            }
            active.set(true);
            watchdogThread = new Thread(() -> {
                while (active.get()) {
                    try {
                        User32Compat.INSTANCE.SetCursorPos(lockedPoint.x, lockedPoint.y);
                    } catch (Exception ignored) {
                    }
                    try {
                        Thread.sleep(WATCHDOG_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "mouse-input-guard");
            watchdogThread.setDaemon(true);
            watchdogThread.start();
        }

        private void stopWatchdog() {
            active.set(false);
            if (watchdogThread == null) {
                return;
            }
            watchdogThread.interrupt();
            try {
                watchdogThread.join(WATCHDOG_JOIN_TIMEOUT_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            watchdogThread = null;
        }
    }
}
