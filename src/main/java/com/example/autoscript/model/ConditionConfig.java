package com.example.autoscript.model;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConditionConfig implements Serializable {

    private String name = "";
    private MonitorRegion monitorRegion = new MonitorRegion();
    private double threshold = 0.90D;
    private int clickX = 100;
    private int clickY = 100;
    private List<String> templatePaths = new ArrayList<>();
    private String conditionExpression = "";
    private List<ConditionTriggerActionConfig> triggerActions = new ArrayList<>();
    private Boolean legacyStopMonitoringAfterTrigger;

    public ConditionConfig() {
    }

    public ConditionConfig(ConditionConfig other) {
        if (other == null) {
            return;
        }
        this.name = other.getName();
        MonitorRegion otherRegion = other.getMonitorRegion();
        MonitorRegion clonedRegion = new MonitorRegion();
        if (otherRegion != null) {
            clonedRegion.setAnchor(otherRegion.getAnchor());
            clonedRegion.setX(otherRegion.getX());
            clonedRegion.setY(otherRegion.getY());
            clonedRegion.setWidth(otherRegion.getWidth());
            clonedRegion.setHeight(otherRegion.getHeight());
            clonedRegion.setReferenceWidth(otherRegion.getReferenceWidth());
            clonedRegion.setReferenceHeight(otherRegion.getReferenceHeight());
        }
        this.monitorRegion = clonedRegion;
        this.threshold = other.getThreshold();
        this.clickX = other.getClickX();
        this.clickY = other.getClickY();
        this.templatePaths = other.getTemplatePaths();
        this.conditionExpression = other.getConditionExpression();
        this.triggerActions = other.getTriggerActions();
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public MonitorRegion getMonitorRegion() {
        return monitorRegion;
    }

    public void setMonitorRegion(MonitorRegion monitorRegion) {
        this.monitorRegion = monitorRegion == null ? new MonitorRegion() : monitorRegion;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
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

    public List<String> getTemplatePaths() {
        return templatePaths == null ? new ArrayList<>() : new ArrayList<>(templatePaths);
    }

    public void setTemplatePaths(List<String> templatePaths) {
        this.templatePaths = templatePaths == null ? new ArrayList<>() : new ArrayList<>(templatePaths);
    }

    public String getConditionExpression() {
        return conditionExpression == null ? "" : conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression == null ? "" : conditionExpression.trim();
    }

    public List<ConditionTriggerActionConfig> getTriggerActions() {
        return copyTriggerActions(resolveTriggerActions());
    }

    public void setTriggerActions(List<ConditionTriggerActionConfig> triggerActions) {
        this.triggerActions = copyTriggerActions(triggerActions);
        this.legacyStopMonitoringAfterTrigger = null;
    }

    public ConditionTriggerActionType getPrimaryTriggerActionType() {
        List<ConditionTriggerActionConfig> actions = resolveTriggerActions();
        if (actions.isEmpty()) {
            return ConditionTriggerActionType.CLICK_REGION;
        }
        return actions.get(0).getType();
    }

    public void setPrimaryTriggerActionType(ConditionTriggerActionType actionType) {
        List<ConditionTriggerActionConfig> actions = new ArrayList<>();
        actions.add(new ConditionTriggerActionConfig(actionType));
        this.triggerActions = actions;
        this.legacyStopMonitoringAfterTrigger = null;
    }

    public String getTriggerActionsSummary() {
        return resolveTriggerActions().stream()
                .map(ConditionTriggerActionConfig::toDisplayText)
                .collect(Collectors.joining(" + "));
    }

    @JsonSetter("stopMonitoringAfterTrigger")
    public void setLegacyStopMonitoringAfterTrigger(Boolean stopMonitoringAfterTrigger) {
        this.legacyStopMonitoringAfterTrigger = stopMonitoringAfterTrigger;
        if (triggerActions == null || triggerActions.isEmpty()) {
            setPrimaryTriggerActionType(Boolean.TRUE.equals(stopMonitoringAfterTrigger)
                    ? ConditionTriggerActionType.STOP_MONITORING
                    : ConditionTriggerActionType.CLICK_REGION);
        }
    }

    private List<ConditionTriggerActionConfig> resolveTriggerActions() {
        if (triggerActions != null && !triggerActions.isEmpty()) {
            return copyTriggerActions(triggerActions);
        }
        List<ConditionTriggerActionConfig> fallback = new ArrayList<>();
        fallback.add(Boolean.TRUE.equals(legacyStopMonitoringAfterTrigger)
                ? ConditionTriggerActionConfig.stopMonitoring()
                : ConditionTriggerActionConfig.clickRegion());
        return fallback;
    }

    private List<ConditionTriggerActionConfig> copyTriggerActions(List<ConditionTriggerActionConfig> source) {
        List<ConditionTriggerActionConfig> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (ConditionTriggerActionConfig action : source) {
            if (action != null) {
                copied.add(new ConditionTriggerActionConfig(action));
            }
        }
        return copied;
    }
}
