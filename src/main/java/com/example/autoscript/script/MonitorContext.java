package com.example.autoscript.script;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.model.MatchResult;
import com.example.autoscript.model.WindowInfo;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MonitorContext {

    private final WindowInfo window;
    private final AppConfig config;
    private final String triggerConditionName;
    private final BufferedImage capturedRegion;
    private final BufferedImage templateImage;
    private final Consumer<String> logger;

    private MatchResult matchResult;
    private final Map<String, ConditionResult> conditionResults = new LinkedHashMap<>();
    private boolean stopMonitoringRequested;
    private String stopMonitoringMessage;

    public MonitorContext(WindowInfo window,
                          AppConfig config,
                          String triggerConditionName,
                          BufferedImage capturedRegion,
                          BufferedImage templateImage,
                          Consumer<String> logger) {
        this.window = window;
        this.config = config;
        this.triggerConditionName = triggerConditionName == null ? "" : triggerConditionName.trim();
        this.capturedRegion = capturedRegion;
        this.templateImage = templateImage;
        this.logger = logger;
    }

    public WindowInfo getWindow() {
        return window;
    }

    public AppConfig getConfig() {
        return config;
    }

    public String getTriggerConditionName() {
        return triggerConditionName;
    }

    public BufferedImage getCapturedRegion() {
        return capturedRegion;
    }

    public BufferedImage getTemplateImage() {
        return templateImage;
    }

    public MatchResult getMatchResult() {
        return matchResult;
    }

    public void setMatchResult(MatchResult matchResult) {
        this.matchResult = matchResult;
    }

    public void putConditionResult(String conditionName, ConditionResult result) {
        if (conditionName == null || conditionName.isBlank() || result == null) {
            return;
        }
        conditionResults.put(conditionName, result);
    }

    public Map<String, ConditionResult> getConditionResults() {
        return conditionResults;
    }

    public void requestStopMonitoring(String message) {
        this.stopMonitoringRequested = true;
        if (message == null || message.isBlank()) {
            if (triggerConditionName.isBlank()) {
                this.stopMonitoringMessage = "监控已经停止。";
            } else {
                this.stopMonitoringMessage = "条件[" + triggerConditionName + "]已触发，监控已经停止。";
            }
            return;
        }
        this.stopMonitoringMessage = message.trim();
    }

    public boolean isStopMonitoringRequested() {
        return stopMonitoringRequested;
    }

    public String getStopMonitoringMessage() {
        return stopMonitoringMessage;
    }

    public void log(String msg) {
        if (logger != null) {
            logger.accept(msg);
        }
    }
}
