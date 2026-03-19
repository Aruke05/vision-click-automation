package com.example.autoscript.model;

import com.sun.jna.platform.win32.WinDef.HWND;

import java.awt.Rectangle;

public class WindowInfo {

    private final HWND handle;
    private final long processId;
    private final String title;
    private final String className;
    private final String processName;
    private final Rectangle windowRect;
    private final Rectangle clientRectOnScreen;

    public WindowInfo(HWND handle,
                      long processId,
                      String title,
                      String className,
                      String processName,
                      Rectangle windowRect,
                      Rectangle clientRectOnScreen) {
        this.handle = handle;
        this.processId = processId;
        this.title = title;
        this.className = className;
        this.processName = processName;
        this.windowRect = windowRect;
        this.clientRectOnScreen = clientRectOnScreen;
    }

    public HWND getHandle() {
        return handle;
    }

    public long getProcessId() {
        return processId;
    }

    public String getTitle() {
        return title;
    }

    public String getClassName() {
        return className;
    }

    public String getProcessName() {
        return processName;
    }

    public Rectangle getWindowRect() {
        return windowRect;
    }

    public Rectangle getClientRectOnScreen() {
        return clientRectOnScreen;
    }

    public String toDisplayText() {
        return String.format("pid=%d, title=%s, class=%s", processId, title, className);
    }
}
