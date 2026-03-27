package com.example.autoscript.model;

public enum ConditionTriggerActionType {
    CLICK_REGION("点击区域"),
    STOP_MONITORING("停止监控");

    private final String displayName;

    ConditionTriggerActionType(String displayName) {
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
