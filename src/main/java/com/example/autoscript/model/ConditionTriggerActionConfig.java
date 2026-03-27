package com.example.autoscript.model;

import java.io.Serializable;

public class ConditionTriggerActionConfig implements Serializable {

    private ConditionTriggerActionType type = ConditionTriggerActionType.CLICK_REGION;

    public ConditionTriggerActionConfig() {
    }

    public ConditionTriggerActionConfig(ConditionTriggerActionType type) {
        setType(type);
    }

    public ConditionTriggerActionConfig(ConditionTriggerActionConfig other) {
        this(other == null ? ConditionTriggerActionType.CLICK_REGION : other.getType());
    }

    public ConditionTriggerActionType getType() {
        return type == null ? ConditionTriggerActionType.CLICK_REGION : type;
    }

    public void setType(ConditionTriggerActionType type) {
        this.type = type == null ? ConditionTriggerActionType.CLICK_REGION : type;
    }

    public String toDisplayText() {
        return getType().getDisplayName();
    }

    public static ConditionTriggerActionConfig clickRegion() {
        return new ConditionTriggerActionConfig(ConditionTriggerActionType.CLICK_REGION);
    }

    public static ConditionTriggerActionConfig stopMonitoring() {
        return new ConditionTriggerActionConfig(ConditionTriggerActionType.STOP_MONITORING);
    }
}
