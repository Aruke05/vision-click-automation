package com.example.autoscript.script;

public interface Condition {
    String name();

    ConditionResult evaluate(MonitorContext context) throws Exception;
}
