package com.example.autoscript.script;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.service.ActionExecutor;
import com.example.autoscript.service.WindowService;

import java.awt.Point;
import java.awt.Rectangle;

public class ClickActionStep implements ActionStep {

    private final ActionExecutor actionExecutor;
    private final WindowService windowService;

    public ClickActionStep(ActionExecutor actionExecutor, WindowService windowService) {
        this.actionExecutor = actionExecutor;
        this.windowService = windowService;
    }

    @Override
    public String name() {
        return "click-action";
    }

    @Override
    public void execute(MonitorContext context) throws Exception {
        AppConfig config = context.getConfig();
        Rectangle clientRect = windowService.getClientRectOnScreen(context.getWindow());
        Point clickPoint = config.resolveClickPoint(clientRect);
        int clickX = clickPoint.x;
        int clickY = clickPoint.y;
        boolean backgroundMode = config.isBackgroundClickMode();
        boolean ok = actionExecutor.clickClient(context.getWindow(), clickX, clickY, config);
        context.log(ok
                ? String.format(backgroundMode ? "已发送后台点击消息，client=(%d,%d)" : "已执行点击，client=(%d,%d)", clickX, clickY)
                : "点击执行失败");
    }
}
