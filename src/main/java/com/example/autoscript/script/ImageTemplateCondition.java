package com.example.autoscript.script;

import com.example.autoscript.model.MatchResult;
import com.example.autoscript.service.ImageMatcher;

import java.awt.image.BufferedImage;
import java.util.Objects;

public class ImageTemplateCondition implements Condition {

    private final String conditionName;
    private final ImageMatcher imageMatcher;
    private final BufferedImage templateImage;

    public ImageTemplateCondition(ImageMatcher imageMatcher) {
        this("C1", imageMatcher, null);
    }

    public ImageTemplateCondition(String conditionName,
                                  ImageMatcher imageMatcher,
                                  BufferedImage templateImage) {
        this.conditionName = Objects.requireNonNull(conditionName, "conditionName");
        this.imageMatcher = imageMatcher;
        this.templateImage = templateImage;
    }

    @Override
    public String name() {
        return conditionName;
    }

    @Override
    public ConditionResult evaluate(MonitorContext context) {
        BufferedImage currentTemplate = templateImage != null ? templateImage : context.getTemplateImage();
        if (currentTemplate == null) {
            throw new IllegalStateException("模板图为空，无法执行条件: " + conditionName);
        }
        BufferedImage captured = context.getCapturedRegion();
        if (captured == null) {
            return new ConditionResult(
                    false,
                    0.0D,
                    "截图为空，无法执行匹配",
                    new java.awt.Point(0, 0)
            );
        }
        MatchResult result = imageMatcher.match(captured, currentTemplate);
        context.setMatchResult(result);
        boolean matched = result.matched(context.getConfig().getThreshold());
        String message = matched
                ? String.format("条件 %s 命中(score=%.4f)", conditionName, result.score())
                : String.format("条件 %s 未达阈值(score=%.4f,capture=%dx%d,template=%dx%d)",
                conditionName,
                result.score(),
                captured.getWidth(),
                captured.getHeight(),
                currentTemplate.getWidth(),
                currentTemplate.getHeight());
        return new ConditionResult(
                matched,
                result.score(),
                message,
                result.location()
        );
    }
}
