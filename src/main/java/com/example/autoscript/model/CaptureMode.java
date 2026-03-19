package com.example.autoscript.model;

public enum CaptureMode {
    SCREEN("屏幕截图（原方式）"),
    WINDOW_HANDLE("窗口句柄截图（后台防遮挡）");

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
