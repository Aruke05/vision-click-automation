package com.example.autoscript.service;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.model.WindowInfo;

public interface ActionExecutor {

    boolean clickClient(WindowInfo window, int clientX, int clientY, AppConfig config) throws Exception;

    default boolean clickClient(WindowInfo window, int clientX, int clientY) throws Exception {
        return clickClient(window, clientX, clientY, null);
    }

    boolean keyPress(WindowInfo window, int keyCode) throws Exception;
}
