package com.example.autoscript.script;

import com.example.autoscript.service.ActionExecutor;

public class ClickActionStep implements ActionStep {

    private final ActionExecutor actionExecutor;

    public ClickActionStep(ActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    @Override
    public String name() {
        return "click-action";
    }

    @Override
    public void execute(MonitorContext context) throws Exception {
        int clickX = context.getConfig().getClickX();
        int clickY = context.getConfig().getClickY();
        boolean backgroundMode = context.getConfig() != null && context.getConfig().isBackgroundClickMode();
        boolean ok = actionExecutor.clickClient(context.getWindow(), clickX, clickY, context.getConfig());
        context.log(ok
                ? String.format(backgroundMode ? "已发送后台点击消息，client=(%d,%d)" : "已执行点击，client=(%d,%d)", clickX, clickY)
                : "点击执行失败");
    }
}
