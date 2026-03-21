package com.example.autoscript.model;

public enum CaptureMode {
    SCREEN("屏幕截图（原方式）"),
    WINDOW_HANDLE("窗口句柄截图（严格后台）"),
    WINDOW_HANDLE_FALLBACK_SCREEN("窗口句柄截图（失败时回退屏幕）");

    private final String displayName;

    CaptureMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
