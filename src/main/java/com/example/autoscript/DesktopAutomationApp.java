package com.example.autoscript;

import com.example.autoscript.gui.MainFrame;

import javax.swing.SwingUtilities;

public class DesktopAutomationApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame frame = new MainFrame();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("应用启动失败", e);
            }
        });
    }
}
