package com.example.autoscript.service;

import com.example.autoscript.model.MonitorRegion;
import com.example.autoscript.model.WindowInfo;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class RobotCaptureService implements CaptureService {

    private final Robot robot;
    private final WindowService windowService;

    public RobotCaptureService(WindowService windowService) throws Exception {
        this.robot = new Robot();
        this.windowService = windowService;
        this.robot.setAutoDelay(10);
    }

    @Override
    public BufferedImage capture(WindowInfo window, MonitorRegion region) {
        Rectangle clientRect = windowService.getClientRectOnScreen(window);
        Rectangle target = region.resolveWithin(clientRect);
        Rectangle captureRect = target.intersection(clientRect);
        if (captureRect.width <= 0 || captureRect.height <= 0) {
            int maxX = clientRect.width - region.getWidth();
            int maxY = clientRect.height - region.getHeight();
            throw new IllegalArgumentException("监控区域越界: " + region
                    + ", client=" + clientRect
                    + ", 建议 client 坐标范围 x=[0," + maxX + "], y=[0," + maxY + "]");
        }
        Rectangle awtCaptureRect = toAwtCoordinates(captureRect);
        return robot.createScreenCapture(awtCaptureRect);
    }

    private Rectangle toAwtCoordinates(Rectangle nativeRect) {
        GraphicsConfiguration gc = pickGraphicsConfig(nativeRect);
        if (gc == null) {
            return new Rectangle(nativeRect);
        }
        Rectangle logicalBounds = gc.getBounds();
        AffineTransform tx = gc.getDefaultTransform();
        double sx = tx.getScaleX() <= 0.0D ? 1.0D : tx.getScaleX();
        double sy = tx.getScaleY() <= 0.0D ? 1.0D : tx.getScaleY();

        int nativeOriginX = (int) Math.round(logicalBounds.x * sx);
        int nativeOriginY = (int) Math.round(logicalBounds.y * sy);

        int x = logicalBounds.x + (int) Math.round((nativeRect.x - nativeOriginX) / sx);
        int y = logicalBounds.y + (int) Math.round((nativeRect.y - nativeOriginY) / sy);
        int w = Math.max(1, (int) Math.round(nativeRect.width / sx));
        int h = Math.max(1, (int) Math.round(nativeRect.height / sy));
        Rectangle converted = new Rectangle(x, y, w, h);
        if (!intersectsAnyScreen(converted)) {
            return new Rectangle(nativeRect);
        }
        return converted;
    }

    private GraphicsConfiguration pickGraphicsConfig(Rectangle nativeRect) {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (devices == null || devices.length == 0) {
            return null;
        }
        Point center = new Point(
                nativeRect.x + Math.max(0, nativeRect.width / 2),
                nativeRect.y + Math.max(0, nativeRect.height / 2)
        );
        GraphicsConfiguration best = null;
        long bestDistance = Long.MAX_VALUE;
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle nativeBounds = toNativeBounds(gc);
            if (nativeBounds.contains(center)) {
                return gc;
            }
            long distance = distanceToRect(center, nativeBounds);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = gc;
            }
        }
        return best;
    }

    private Rectangle toNativeBounds(GraphicsConfiguration gc) {
        Rectangle logical = gc.getBounds();
        AffineTransform tx = gc.getDefaultTransform();
        double sx = tx.getScaleX() <= 0.0D ? 1.0D : tx.getScaleX();
        double sy = tx.getScaleY() <= 0.0D ? 1.0D : tx.getScaleY();
        return new Rectangle(
                (int) Math.round(logical.x * sx),
                (int) Math.round(logical.y * sy),
                Math.max(1, (int) Math.round(logical.width * sx)),
                Math.max(1, (int) Math.round(logical.height * sy))
        );
    }

    private long distanceToRect(Point point, Rectangle rect) {
        int dx = 0;
        if (point.x < rect.x) {
            dx = rect.x - point.x;
        } else if (point.x > rect.x + rect.width) {
            dx = point.x - (rect.x + rect.width);
        }

        int dy = 0;
        if (point.y < rect.y) {
            dy = rect.y - point.y;
        } else if (point.y > rect.y + rect.height) {
            dy = point.y - (rect.y + rect.height);
        }
        return (long) dx * dx + (long) dy * dy;
    }

    private boolean intersectsAnyScreen(Rectangle rect) {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (devices == null || devices.length == 0) {
            return true;
        }
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            if (gc != null && rect.intersects(gc.getBounds())) {
                return true;
            }
        }
        return false;
    }
}
