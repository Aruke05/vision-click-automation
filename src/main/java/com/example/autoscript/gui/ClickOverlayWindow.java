package com.example.autoscript.gui;

import com.example.autoscript.service.User32Compat;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;

import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

public class ClickOverlayWindow {

    private static final int CROSS_SIZE = 16;
    private static final int THICKNESS = 2;
    private static final int CENTER_SIZE = 6;
    private static final Color COLOR = new Color(66, 196, 255);
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;

    private final List<JWindow> markerWindows = new ArrayList<>(3);

    public ClickOverlayWindow(Window owner) {
        for (int i = 0; i < 3; i++) {
            markerWindows.add(createMarkerWindow());
        }
    }

    public void showAt(Point point) {
        if (point == null) {
            hide();
            return;
        }

        JWindow hLine = markerWindows.get(0);
        JWindow vLine = markerWindows.get(1);
        JWindow center = markerWindows.get(2);

        int half = CROSS_SIZE / 2;
        int centerHalf = CENTER_SIZE / 2;
        int halfThickness = THICKNESS / 2;

        showWindowAt(hLine, point.x - half, point.y - halfThickness, CROSS_SIZE, THICKNESS);
        showWindowAt(vLine, point.x - halfThickness, point.y - half, THICKNESS, CROSS_SIZE);
        showWindowAt(center, point.x - centerHalf, point.y - centerHalf, CENTER_SIZE, CENTER_SIZE);
    }

    public void hide() {
        for (JWindow window : markerWindows) {
            if (window.isVisible()) {
                window.setVisible(false);
            }
        }
    }

    public void dispose() {
        for (JWindow window : markerWindows) {
            window.dispose();
        }
        markerWindows.clear();
    }

    private JWindow createMarkerWindow() {
        JWindow window = new JWindow((Window) null);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setType(Window.Type.UTILITY);
        window.setBackground(COLOR);
        window.getContentPane().setBackground(COLOR);
        return window;
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
