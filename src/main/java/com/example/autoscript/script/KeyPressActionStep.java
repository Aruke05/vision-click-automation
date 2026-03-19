package com.example.autoscript.script;

import com.example.autoscript.service.ActionExecutor;

import java.awt.event.KeyEvent;

public class KeyPressActionStep implements ActionStep {

    private final ActionExecutor actionExecutor;
    private final int keyCode;

    public KeyPressActionStep(ActionExecutor actionExecutor, int keyCode) {
        this.actionExecutor = actionExecutor;
        this.keyCode = keyCode;
    }

    public static int parseKeyCode(String keyCodeName) {
        if (keyCodeName == null || keyCodeName.isBlank()) {
            throw new IllegalArgumentException("keyCodeName 不能为空");
        }
        try {
            return KeyEvent.class.getField(keyCodeName).getInt(null);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 keyCode: " + keyCodeName, e);
        }
    }

    @Override
    public String name() {
        return "key-press-action";
    }

    @Override
    public void execute(MonitorContext context) throws Exception {
        boolean ok = actionExecutor.keyPress(context.getWindow(), keyCode);
        context.log(ok ? "已执行按键: keyCode=" + keyCode : "按键执行失败: keyCode=" + keyCode);
    }
}
