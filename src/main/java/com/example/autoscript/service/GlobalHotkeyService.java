package com.example.autoscript.service;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GlobalHotkeyService implements AutoCloseable {

    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;
    private static final int HOTKEY_ID_START = 1;
    private static final int HOTKEY_ID_STOP = 2;

    private static final int MOD_ALT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_SHIFT = 0x0004;
    private static final int MOD_WIN = 0x0008;
    private static final int MOD_NOREPEAT = 0x4000;

    private volatile Thread workerThread;
    private volatile int workerThreadId;
    private volatile boolean running;

    public synchronized void registerHotkeys(String startHotkey,
                                             String stopHotkey,
                                             Runnable onStart,
                                             Runnable onStop) {
        HotkeySpec startSpec = parseHotkey(startHotkey);
        HotkeySpec stopSpec = parseHotkey(stopHotkey);
        if (startSpec.modifiers == stopSpec.modifiers && startSpec.vkCode == stopSpec.vkCode) {
            throw new IllegalArgumentException("启动热键和停止热键不能相同: " + startSpec.displayText);
        }

        close();
        running = true;
        CountDownLatch startupLatch = new CountDownLatch(1);
        AtomicReference<RuntimeException> startupError = new AtomicReference<>();
        Thread thread = new Thread(() -> hotkeyLoop(startSpec, stopSpec, onStart, onStop, startupLatch, startupError),
                "global-hotkey-worker");
        thread.setDaemon(true);
        workerThread = thread;
        thread.start();

        try {
            boolean started = startupLatch.await(2, TimeUnit.SECONDS);
            if (!started) {
                close();
                throw new IllegalStateException("注册全局热键超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
            throw new IllegalStateException("注册全局热键被中断", e);
        }
        RuntimeException error = startupError.get();
        if (error != null) {
            close();
            throw error;
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        int threadId = workerThreadId;
        if (threadId != 0) {
            User32Compat.INSTANCE.PostThreadMessage(threadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
        }

        Thread thread = workerThread;
        workerThread = null;
        if (thread != null && thread.isAlive()) {
            try {
                thread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void hotkeyLoop(HotkeySpec startSpec,
                            HotkeySpec stopSpec,
                            Runnable onStart,
                            Runnable onStop,
                            CountDownLatch startupLatch,
                            AtomicReference<RuntimeException> startupError) {
        boolean startRegistered = false;
        boolean stopRegistered = false;
        try {
            workerThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
            HWND targetWindow = null;

            startRegistered = User32Compat.INSTANCE.RegisterHotKey(
                    targetWindow, HOTKEY_ID_START, startSpec.modifiers, startSpec.vkCode);
            if (!startRegistered) {
                throw new IllegalStateException("注册启动热键失败: " + startSpec.displayText
                        + " (GetLastError=" + Kernel32.INSTANCE.GetLastError() + ")");
            }

            stopRegistered = User32Compat.INSTANCE.RegisterHotKey(
                    targetWindow, HOTKEY_ID_STOP, stopSpec.modifiers, stopSpec.vkCode);
            if (!stopRegistered) {
                throw new IllegalStateException("注册停止热键失败: " + stopSpec.displayText
                        + " (GetLastError=" + Kernel32.INSTANCE.GetLastError() + ")");
            }

            startupLatch.countDown();

            WinUser.MSG msg = new WinUser.MSG();
            while (running) {
                int result = User32Compat.INSTANCE.GetMessage(msg, targetWindow, 0, 0);
                if (result == -1) {
                    throw new IllegalStateException("热键消息循环异常 (GetLastError="
                            + Kernel32.INSTANCE.GetLastError() + ")");
                }
                if (result == 0) {
                    break;
                }
                if (msg.message == WM_HOTKEY) {
                    int hotkeyId = msg.wParam.intValue();
                    if (hotkeyId == HOTKEY_ID_START && onStart != null) {
                        onStart.run();
                    } else if (hotkeyId == HOTKEY_ID_STOP && onStop != null) {
                        onStop.run();
                    }
                } else {
                    User32Compat.INSTANCE.TranslateMessage(msg);
                    User32Compat.INSTANCE.DispatchMessage(msg);
                }
            }
        } catch (RuntimeException e) {
            startupError.set(e);
            if (startupLatch.getCount() > 0) {
                startupLatch.countDown();
            }
        } finally {
            HWND targetWindow = null;
            if (startRegistered) {
                User32Compat.INSTANCE.UnregisterHotKey(targetWindow, HOTKEY_ID_START);
            }
            if (stopRegistered) {
                User32Compat.INSTANCE.UnregisterHotKey(targetWindow, HOTKEY_ID_STOP);
            }
            running = false;
            workerThreadId = 0;
        }
    }

    public static String normalizeHotkeyText(String rawText, String fallback) {
        String candidate = rawText == null ? "" : rawText.trim();
        if (candidate.isBlank()) {
            candidate = fallback;
        }
        return parseHotkey(candidate).displayText;
    }

    private static HotkeySpec parseHotkey(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("热键不能为空");
        }
        String[] parts = text.toUpperCase(Locale.ROOT).replace(" ", "").split("\\+");
        int modifiers = MOD_NOREPEAT;
        Integer vkCode = null;
        String keyText = null;
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            switch (part) {
                case "CTRL":
                case "CONTROL":
                case "CTL":
                    modifiers |= MOD_CONTROL;
                    break;
                case "ALT":
                    modifiers |= MOD_ALT;
                    break;
                case "SHIFT":
                    modifiers |= MOD_SHIFT;
                    break;
                case "WIN":
                case "WINDOWS":
                case "META":
                    modifiers |= MOD_WIN;
                    break;
                default:
                    if (vkCode != null) {
                        throw new IllegalArgumentException("热键格式错误，只能有一个主键: " + rawText);
                    }
                    vkCode = resolveVkCode(part);
                    keyText = part;
                    break;
            }
        }
        if (vkCode == null) {
            throw new IllegalArgumentException("热键缺少主键: " + rawText);
        }
        String display = buildDisplayText(modifiers, keyText);
        return new HotkeySpec(modifiers, vkCode, display);
    }

    private static int resolveVkCode(String token) {
        if (token.length() == 1) {
            char c = token.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return c;
            }
            if (c >= '0' && c <= '9') {
                return c;
            }
        }
        if (token.matches("F([1-9]|1[0-9]|2[0-4])")) {
            int fn = Integer.parseInt(token.substring(1));
            return KeyEvent.VK_F1 + (fn - 1);
        }
        switch (token) {
            case "ESC":
            case "ESCAPE":
                return KeyEvent.VK_ESCAPE;
            case "ENTER":
            case "RETURN":
                return KeyEvent.VK_ENTER;
            case "SPACE":
                return KeyEvent.VK_SPACE;
            case "TAB":
                return KeyEvent.VK_TAB;
            case "UP":
                return KeyEvent.VK_UP;
            case "DOWN":
                return KeyEvent.VK_DOWN;
            case "LEFT":
                return KeyEvent.VK_LEFT;
            case "RIGHT":
                return KeyEvent.VK_RIGHT;
            case "HOME":
                return KeyEvent.VK_HOME;
            case "END":
                return KeyEvent.VK_END;
            case "PAGEUP":
            case "PGUP":
                return KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN":
            case "PGDN":
                return KeyEvent.VK_PAGE_DOWN;
            case "INSERT":
            case "INS":
                return KeyEvent.VK_INSERT;
            case "DELETE":
            case "DEL":
                return KeyEvent.VK_DELETE;
            default:
                break;
        }
        throw new IllegalArgumentException("不支持的热键主键: " + token + "（示例: F9 / F10 / CTRL+F9）");
    }

    private static String buildDisplayText(int modifiers, String keyText) {
        List<String> parts = new ArrayList<>();
        if ((modifiers & MOD_CONTROL) != 0) {
            parts.add("CTRL");
        }
        if ((modifiers & MOD_ALT) != 0) {
            parts.add("ALT");
        }
        if ((modifiers & MOD_SHIFT) != 0) {
            parts.add("SHIFT");
        }
        if ((modifiers & MOD_WIN) != 0) {
            parts.add("WIN");
        }
        parts.add(keyText);
        return String.join("+", parts);
    }

    private static final class HotkeySpec {
        private final int modifiers;
        private final int vkCode;
        private final String displayText;

        private HotkeySpec(int modifiers, int vkCode, String displayText) {
            this.modifiers = modifiers;
            this.vkCode = vkCode;
            this.displayText = displayText;
        }
    }
}
