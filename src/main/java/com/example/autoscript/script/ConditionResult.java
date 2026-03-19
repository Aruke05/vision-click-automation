package com.example.autoscript.script;

import java.awt.Point;

public record ConditionResult(boolean matched, double score, String message, Point location) {
}
