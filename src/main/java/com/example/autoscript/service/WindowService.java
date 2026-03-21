package com.example.autoscript.service;

import com.example.autoscript.model.WindowInfo;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

public interface WindowService {

    List<WindowInfo> listWindows();

    Rectangle getClientRectOnScreen(WindowInfo window);

    Point clientToScreen(WindowInfo window, int clientX, int clientY);

    boolean isAlive(WindowInfo window);

    boolean isMinimized(WindowInfo window);

    void restoreIfMinimized(WindowInfo window);

    void bringToFront(WindowInfo window);

    void moveToBack(WindowInfo window);

    Optional<WindowInfo> tryRestoreBinding(List<WindowInfo> windows, long pid, String title, String className);
}
