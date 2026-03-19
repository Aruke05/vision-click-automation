package com.example.autoscript.service;

import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.CaptureMode;
import com.example.autoscript.model.WindowInfo;

import java.awt.image.BufferedImage;

public interface CaptureService {

    default BufferedImage capture(WindowInfo window, MonitorRegion region) {
        return capture(window, region, CaptureMode.SCREEN);
    }

    BufferedImage capture(WindowInfo window, MonitorRegion region, CaptureMode captureMode);
}
