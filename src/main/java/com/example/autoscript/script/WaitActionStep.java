package com.example.autoscript.script;

public class WaitActionStep implements ActionStep {

    private final long millis;

    public WaitActionStep(long millis) {
        this.millis = Math.max(0L, millis);
    }

    @Override
    public String name() {
        return "wait-action";
    }

    @Override
    public void execute(MonitorContext context) throws Exception {
        if (millis > 0) {
            Thread.sleep(millis);
            context.log("等待完成: " + millis + "ms");
        }
    }
}
