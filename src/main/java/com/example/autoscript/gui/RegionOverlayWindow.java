package com.example.autoscript.gui;

import com.example.autoscript.service.User32Compat;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;

import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

public class RegionOverlayWindow {

    private static final int BORDER_WIDTH = 3;
    private static final Color BORDER_COLOR = new Color(255, 78, 66);
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;

    private final List<JWindow> edgeWindows = new ArrayList<>(4);

    public RegionOverlayWindow(Window owner) {
        // 使用无 owner 窗体，避免只能浮在主配置窗口之上的限制。
        for (int i = 0; i < 4; i++) {
            edgeWindows.add(createEdgeWindow());
        }
    }

    public void showAt(Rectangle rect) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            hideOverlay();
            return;
        }

        int x = rect.x;
        int y = rect.y;
        int width = Math.max(1, rect.width);
        int height = Math.max(1, rect.height);
        int line = Math.max(1, Math.min(BORDER_WIDTH, Math.min(width, height)));

        JWindow top = edgeWindows.get(0);
        JWindow right = edgeWindows.get(1);
        JWindow bottom = edgeWindows.get(2);
        JWindow left = edgeWindows.get(3);

        showWindowAt(top, x, y, width, line);
        showWindowAt(bottom, x, y + height - line, width, line);
        showWindowAt(left, x, y, line, height);
        showWindowAt(right, x + width - line, y, line, height);
    }

    public void hideOverlay() {
        for (JWindow edge : edgeWindows) {
            if (edge.isVisible()) {
                edge.setVisible(false);
            }
        }
    }

    public void dispose() {
        for (JWindow edge : edgeWindows) {
            edge.dispose();
        }
        edgeWindows.clear();
    }

    private JWindow createEdgeWindow() {
        JWindow edge = new JWindow((Window) null);
        edge.setAlwaysOnTop(true);
        edge.setFocusableWindowState(false);
        edge.setAutoRequestFocus(false);
        edge.setType(Window.Type.UTILITY);
        edge.setBackground(BORDER_COLOR);
        edge.getContentPane().setBackground(BORDER_COLOR);
        return edge;
    }

    private void showWindowAt(JWindow window, int x, int y, int width, int height) {
        if (!window.isVisible()) {
            window.setVisible(true);
        }
        if (!setNativeBounds(window, x, y, width, height)) {
            window.setBounds(x, y, width, height);
        }
        window.toFront();
        window.repaint();
    }

    private boolean setNativeBounds(JWindow window, int x, int y, int width, int height) {
        try {
            Pointer pointer = Native.getComponentPointer(window);
            if (pointer == null || Pointer.nativeValue(pointer) == 0L) {
                return false;
            }
            HWND hWnd = new HWND(pointer);
            return User32Compat.INSTANCE.SetWindowPos(
                    hWnd,
                    null,
                    x,
                    y,
                    width,
                    height,
                    SWP_NOZORDER | SWP_NOACTIVATE
            );
        } catch (Throwable ignored) {
            return false;
        }
    }
}
