package com.example.autoscript.model;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppConfig implements Serializable {

    private int intervalMs = 500;
    private double threshold = 0.90D;
    private MonitorRegion monitorRegion = new MonitorRegion();
    private int clickX = 100;
    private int clickY = 100;
    private boolean repeatTrigger = true;
    private boolean backgroundClickMode = false;
    private boolean moveWindowToBackAfterTrigger = true;
    private CaptureMode captureMode = CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN;
    private String startHotkey = "F9";
    private String stopHotkey = "F10";
    private String templatePath = "";
    private List<String> templatePaths = new ArrayList<>();
    private String conditionExpression = "";
    private List<ConditionConfig> conditions = new ArrayList<>();
    private long lastWindowPid = -1L;
    private String lastWindowTitle = "";
    private String lastWindowClassName = "";

    public int getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public MonitorRegion getMonitorRegion() {
        return monitorRegion;
    }

    public void setMonitorRegion(MonitorRegion monitorRegion) {
        this.monitorRegion = monitorRegion == null ? new MonitorRegion() : monitorRegion;
    }

    public int getClickX() {
        return clickX;
    }

    public void setClickX(int clickX) {
        this.clickX = clickX;
    }

    public int getClickY() {
        return clickY;
    }

    public void setClickY(int clickY) {
        this.clickY = clickY;
    }

    public int resolveClickX(Rectangle clientRect) {
        if (clientRect == null) {
            return clickX;
        }
        return scaleValue(clickX, clientRect.width, getReferenceWidth());
    }

    public int resolveClickY(Rectangle clientRect) {
        if (clientRect == null) {
            return clickY;
        }
        return scaleValue(clickY, clientRect.height, getReferenceHeight());
    }

    public Point resolveClickPoint(Rectangle clientRect) {
        return new Point(resolveClickX(clientRect), resolveClickY(clientRect));
    }

    public boolean isRepeatTrigger() {
        return repeatTrigger;
    }

    public void setRepeatTrigger(boolean repeatTrigger) {
        this.repeatTrigger = repeatTrigger;
    }

    public boolean isBackgroundClickMode() {
        return backgroundClickMode;
    }

    public void setBackgroundClickMode(boolean backgroundClickMode) {
        this.backgroundClickMode = backgroundClickMode;
    }

    public boolean isMoveWindowToBackAfterTrigger() {
        return moveWindowToBackAfterTrigger;
    }

    public void setMoveWindowToBackAfterTrigger(boolean moveWindowToBackAfterTrigger) {
        this.moveWindowToBackAfterTrigger = moveWindowToBackAfterTrigger;
    }

    public CaptureMode getCaptureMode() {
        return captureMode == null ? CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN : captureMode;
    }

    public void setCaptureMode(CaptureMode captureMode) {
        this.captureMode = captureMode == null ? CaptureMode.WINDOW_HANDLE_FALLBACK_SCREEN : captureMode;
    }

    public String getStartHotkey() {
        return startHotkey;
    }

    public void setStartHotkey(String startHotkey) {
        this.startHotkey = startHotkey == null ? "" : startHotkey.trim();
    }

    public String getStopHotkey() {
        return stopHotkey;
    }

    public void setStopHotkey(String stopHotkey) {
        this.stopHotkey = stopHotkey == null ? "" : stopHotkey.trim();
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath == null ? "" : templatePath;
        if (this.templatePaths == null || this.templatePaths.isEmpty()) {
            if (this.templatePath.isBlank()) {
                this.templatePaths = new ArrayList<>();
            } else {
                this.templatePaths = new ArrayList<>(Collections.singletonList(this.templatePath));
            }
        }
    }

    public List<String> getTemplatePaths() {
        if ((templatePaths == null || templatePaths.isEmpty()) && templatePath != null && !templatePath.isBlank()) {
            return new ArrayList<>(Collections.singletonList(templatePath));
        }
        return templatePaths == null ? new ArrayList<>() : new ArrayList<>(templatePaths);
    }

    public void setTemplatePaths(List<String> templatePaths) {
        this.templatePaths = templatePaths == null ? new ArrayList<>() : new ArrayList<>(templatePaths);
        this.templatePath = this.templatePaths.isEmpty() ? "" : this.templatePaths.get(0);
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression == null ? "" : conditionExpression;
    }

    public List<ConditionConfig> getConditions() {
        if ((conditions == null || conditions.isEmpty()) && hasLegacyConditionData()) {
            List<ConditionConfig> legacy = new ArrayList<>();
            legacy.add(buildLegacyCondition());
            return legacy;
        }
        return copyConditions(conditions);
    }

    public void setConditions(List<ConditionConfig> conditions) {
        this.conditions = copyConditions(conditions);
        syncLegacyFieldsFromFirstCondition();
    }

    public long getLastWindowPid() {
        return lastWindowPid;
    }

    public void setLastWindowPid(long lastWindowPid) {
        this.lastWindowPid = lastWindowPid;
    }

    public String getLastWindowTitle() {
        return lastWindowTitle;
    }

    public void setLastWindowTitle(String lastWindowTitle) {
        this.lastWindowTitle = lastWindowTitle;
    }

    public String getLastWindowClassName() {
        return lastWindowClassName;
    }

    public void setLastWindowClassName(String lastWindowClassName) {
        this.lastWindowClassName = lastWindowClassName;
    }

    private boolean hasLegacyConditionData() {
        return !getTemplatePaths().isEmpty() || !getConditionExpression().isBlank();
    }

    private ConditionConfig buildLegacyCondition() {
        ConditionConfig condition = new ConditionConfig();
        condition.setName("条件1");
        MonitorRegion region = new MonitorRegion();
        MonitorRegion currentRegion = getMonitorRegion();
        if (currentRegion != null) {
            region.setAnchor(currentRegion.getAnchor());
            region.setX(currentRegion.getX());
            region.setY(currentRegion.getY());
            region.setWidth(currentRegion.getWidth());
            region.setHeight(currentRegion.getHeight());
            region.setReferenceWidth(currentRegion.getReferenceWidth());
            region.setReferenceHeight(currentRegion.getReferenceHeight());
        }
        condition.setMonitorRegion(region);
        condition.setThreshold(getThreshold());
        condition.setClickX(getClickX());
        condition.setClickY(getClickY());
        condition.setTemplatePaths(getTemplatePaths());
        condition.setConditionExpression(getConditionExpression());
        return condition;
    }

    private List<ConditionConfig> copyConditions(List<ConditionConfig> source) {
        List<ConditionConfig> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (ConditionConfig condition : source) {
            if (condition == null) {
                continue;
            }
            copied.add(new ConditionConfig(condition));
        }
        return copied;
    }

    private void syncLegacyFieldsFromFirstCondition() {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        ConditionConfig first = conditions.get(0);
        if (first == null) {
            return;
        }
        setThreshold(first.getThreshold());
        setClickX(first.getClickX());
        setClickY(first.getClickY());
        setTemplatePaths(first.getTemplatePaths());
        setConditionExpression(first.getConditionExpression());

        MonitorRegion firstRegion = first.getMonitorRegion();
        MonitorRegion region = new MonitorRegion();
        if (firstRegion != null) {
            region.setAnchor(firstRegion.getAnchor());
            region.setX(firstRegion.getX());
            region.setY(firstRegion.getY());
            region.setWidth(firstRegion.getWidth());
            region.setHeight(firstRegion.getHeight());
            region.setReferenceWidth(firstRegion.getReferenceWidth());
            region.setReferenceHeight(firstRegion.getReferenceHeight());
        }
        setMonitorRegion(region);
    }

    private int getReferenceWidth() {
        MonitorRegion region = getMonitorRegion();
        return region == null ? 0 : region.getReferenceWidth();
    }

    private int getReferenceHeight() {
        MonitorRegion region = getMonitorRegion();
        return region == null ? 0 : region.getReferenceHeight();
    }

    private int scaleValue(int value, int currentSize, int referenceSize) {
        if (referenceSize <= 0 || currentSize <= 0) {
            return value;
        }
        return (int) Math.round((double) value * (double) currentSize / (double) referenceSize);
    }
}
