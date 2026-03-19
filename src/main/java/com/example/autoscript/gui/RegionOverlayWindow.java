package com.example.autoscript.gui;

import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

public class RegionOverlayWindow {

    private static final int BORDER_WIDTH = 3;
    private static final Color BORDER_COLOR = new Color(255, 78, 66);

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

        top.setBounds(x, y, width, line);
        bottom.setBounds(x, y + height - line, width, line);
        left.setBounds(x, y, line, height);
        right.setBounds(x + width - line, y, line, height);

        for (JWindow edge : edgeWindows) {
            if (!edge.isVisible()) {
                edge.setVisible(true);
            }
            edge.toFront();
            edge.repaint();
        }
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
}
