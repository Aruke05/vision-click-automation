package com.example.autoscript.service;

import com.example.autoscript.model.MatchResult;

import java.awt.image.BufferedImage;

public interface ImageMatcher {

    MatchResult match(BufferedImage searchImage, BufferedImage templateImage);
}
