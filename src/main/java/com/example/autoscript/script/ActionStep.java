package com.example.autoscript.script;

public interface ActionStep {
    String name();

    void execute(MonitorContext context) throws Exception;
}
