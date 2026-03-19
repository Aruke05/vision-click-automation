package com.example.autoscript.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ConditionConfig implements Serializable {

    private String name = "";
    private MonitorRegion monitorRegion = new MonitorRegion();
    private double threshold = 0.90D;
    private int clickX = 100;
    private int clickY = 100;
    private List<String> templatePaths = new ArrayList<>();
    private String conditionExpression = "";

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
        }
        this.monitorRegion = clonedRegion;
        this.threshold = other.getThreshold();
        this.clickX = other.getClickX();
        this.clickY = other.getClickY();
        this.templatePaths = other.getTemplatePaths();
        this.conditionExpression = other.getConditionExpression();
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
}
