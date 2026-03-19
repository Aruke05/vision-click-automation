package com.example.autoscript.gui;

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

        hLine.setBounds(point.x - half, point.y - halfThickness, CROSS_SIZE, THICKNESS);
        vLine.setBounds(point.x - halfThickness, point.y - half, THICKNESS, CROSS_SIZE);
        center.setBounds(point.x - centerHalf, point.y - centerHalf, CENTER_SIZE, CENTER_SIZE);

        for (JWindow window : markerWindows) {
            if (!window.isVisible()) {
                window.setVisible(true);
            }
            window.toFront();
            window.repaint();
        }
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
}
