package com.example.autoscript.model;

import java.awt.Dimension;
import java.awt.Point;

public record MatchResult(double score, Point location, Dimension templateSize) {

    public boolean matched(double threshold) {
        return score >= threshold;
    }
}
