package com.example.autoscript.service;

import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;

import java.awt.image.BufferedImage;

public interface CaptureService {

    BufferedImage capture(WindowInfo window, MonitorRegion region);
}
