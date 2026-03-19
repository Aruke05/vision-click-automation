package com.example.autoscript.service;

import com.example.autoscript.model.AppConfig;
import com.example.autoscript.model.WindowInfo;
import com.example.autoscript.script.ConditionResult;
import com.example.autoscript.script.MonitorContext;
import com.example.autoscript.script.MonitorRule;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MonitoringService {

    private final WindowService windowService;
    private final CaptureService captureService;
    private final Consumer<String> logger;

    private ScheduledExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong cycleCounter = new AtomicLong(0L);
    private volatile String lastMatchedConditionKey;

    public MonitoringService(WindowService windowService,
                             CaptureService captureService,
                             Consumer<String> logger) {
        this.windowService = windowService;
        this.captureService = captureService;
        this.logger = logger;
    }

    public synchronized void start(WindowInfo window,
                                   AppConfig config,
                                   List<PollingCondition> pollingConditions) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(pollingConditions, "pollingConditions");
        if (pollingConditions.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个轮询条件");
        }

        if (running.get()) {
            log("监控已经在运行中");
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "monitor-worker");
                t.setDaemon(true);
                return t;
            }
        });
        running.set(true);
        cycleCounter.set(0L);
        lastMatchedConditionKey = null;
        log("监控已启动，轮询条件数量=" + pollingConditions.size() + ", intervalMs=" + config.getIntervalMs());

        executorService.scheduleWithFixedDelay(() -> runOneCycle(window, config, pollingConditions),
                0,
                config.getIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running.set(false);
        lastMatchedConditionKey = null;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        cycleCounter.set(0L);
        log("监控已停止");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void runOneCycle(WindowInfo window,
                             AppConfig config,
                             List<PollingCondition> pollingConditions) {
        if (!running.get()) {
            return;
        }

        try {
            long cycleNo = cycleCounter.incrementAndGet();
            if (!windowService.isAlive(window)) {
                log("目标窗口已失效，自动停止监控");
                stop();
                return;
            }

            if (windowService.isMinimized(window)) {
                log("目标窗口当前最小化，跳过本轮检测");
                return;
            }

            List<String> missDetails = new ArrayList<>();

            for (int index = 0; index < pollingConditions.size(); index++) {
                PollingCondition pollingCondition = pollingConditions.get(index);
                if (pollingCondition == null) {
                    continue;
                }
                AppConfig conditionConfig = pollingCondition.getConfig();
                BufferedImage captured = captureService.capture(
                        window,
                        conditionConfig.getMonitorRegion(),
                        conditionConfig.getCaptureMode()
                );
                MonitorContext context = new MonitorContext(
                        window,
                        conditionConfig,
                        captured,
                        pollingCondition.getTemplateImage(),
                        logger
                );
                boolean conditionMatched = pollingCondition.getRule().matches(context);
                String detail = buildConditionDetail(context);
                String prefix = String.format("[条件%d-%s]", index + 1, pollingCondition.getName());
                if (conditionMatched) {
                    String matchedConditionKey = pollingCondition.getKey();
                    boolean allowRepeatTrigger = config.isRepeatTrigger();
                    if (allowRepeatTrigger || !Objects.equals(lastMatchedConditionKey, matchedConditionKey)) {
                        pollingCondition.getRule().runActions(context);
                        if (allowRepeatTrigger && Objects.equals(lastMatchedConditionKey, matchedConditionKey)) {
                            log("轮询#" + cycleNo + " 持续命中，已重复触发动作" + prefix + "；" + detail);
                        } else {
                            log("轮询#" + cycleNo + " 匹配成功，已触发动作" + prefix + "；" + detail);
                        }
                    } else {
                        log("轮询#" + cycleNo + " 持续命中，已抑制重复触发" + prefix + "；" + detail);
                    }
                    lastMatchedConditionKey = matchedConditionKey;
                    log("轮询#" + cycleNo + " 检测结束，命中" + prefix + "，本轮已结束");
                    return;
                }
                missDetails.add(prefix + "未命中(" + String.format("%.2f", conditionConfig.getThreshold()) + ")；" + detail);
            }

            lastMatchedConditionKey = null;
            if (missDetails.isEmpty()) {
                log("轮询#" + cycleNo + " 未命中；无可用条件");
            } else {
                log("轮询#" + cycleNo + " 未命中；" + String.join(" || ", missDetails));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("目标窗口未获得前台焦点")) {
                log("点击前台焦点失败，但已按配置继续运行并强制尝试真实点击。详情: " + e.getMessage());
                return;
            }
            if (e.getMessage() != null && e.getMessage().contains("鼠标移动失败")) {
                log("鼠标被移动或坐标存在偏差，本次点击已跳过，监控继续。详情: " + e.getMessage());
                return;
            }
            log("监控执行异常: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("监控区域越界")) {
                log("监控区域越界，已自动停止。请调整区域 X/Y 与宽高后重试。详情: " + e.getMessage());
                stop();
                return;
            }
            if (e.getMessage() != null && e.getMessage().contains("点击坐标越界")) {
                log("点击坐标越界，已自动停止。请调整点击 X/Y 后重试。详情: " + e.getMessage());
                stop();
                return;
            }
            log("监控执行异常: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            log("监控执行异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        if (logger != null) {
            logger.accept(msg);
        }
    }

    private String buildConditionDetail(MonitorContext context) {
        if (context.getConditionResults().isEmpty()) {
            return "无条件结果";
        }
        return context.getConditionResults().entrySet().stream()
                .map(entry -> formatCondition(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" | "));
    }

    private String formatCondition(String name, ConditionResult result) {
        if (result == null) {
            return name + "=null";
        }
        String state = result.matched() ? "T" : "F";
        String score = String.format("%.4f", result.score());
        return name + "=" + state + "(score=" + score + ",loc=" + result.location() + ",msg=" + result.message() + ")";
    }

    public static final class PollingCondition {
        private final String key;
        private final String name;
        private final AppConfig config;
        private final BufferedImage templateImage;
        private final MonitorRule rule;

        public PollingCondition(String key,
                                String name,
                                AppConfig config,
                                BufferedImage templateImage,
                                MonitorRule rule) {
            this.key = key == null ? "" : key;
            this.name = name == null ? "" : name;
            this.config = Objects.requireNonNull(config, "config");
            this.templateImage = Objects.requireNonNull(templateImage, "templateImage");
            this.rule = Objects.requireNonNull(rule, "rule");
        }

        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }

        public AppConfig getConfig() {
            return config;
        }

        public BufferedImage getTemplateImage() {
            return templateImage;
        }

        public MonitorRule getRule() {
            return rule;
        }
    }
}
