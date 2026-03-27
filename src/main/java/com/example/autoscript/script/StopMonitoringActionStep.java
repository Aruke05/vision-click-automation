package com.example.autoscript.script;

public class StopMonitoringActionStep implements ActionStep {

    @Override
    public String name() {
        return "stop-monitoring-action";
    }

    @Override
    public void execute(MonitorContext context) {
        context.requestStopMonitoring(null);
        context.log("已请求停止监控");
    }
}
