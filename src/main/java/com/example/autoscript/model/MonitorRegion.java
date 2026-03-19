package com.example.autoscript.model;

import java.awt.Rectangle;
import java.io.Serializable;

public class MonitorRegion implements Serializable {

    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    // 兼容旧配置字段，当前逻辑统一按 client 内部坐标（x,y）解析区域。
    private Anchor anchor = Anchor.TOP_LEFT;
    private int offsetX = 0;
    private int offsetY = 0;
    private int width = 320;
    private int height = 180;
    // 记录配置时窗口 client 尺寸，用于运行时按比例缩放坐标。
    private int referenceWidth = 0;
    private int referenceHeight = 0;

    public MonitorRegion() {
    }

    public MonitorRegion(Anchor anchor, int offsetX, int offsetY, int width, int height) {
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
    }

    public Rectangle resolveWithin(Rectangle baseRect) {
        if (baseRect == null) {
            throw new IllegalArgumentException("baseRect 不能为空");
        }
        int resolvedX = scaleValue(offsetX, baseRect.width, referenceWidth);
        int resolvedY = scaleValue(offsetY, baseRect.height, referenceHeight);
        int resolvedWidth = Math.max(1, scaleValue(width, baseRect.width, referenceWidth));
        int resolvedHeight = Math.max(1, scaleValue(height, baseRect.height, referenceHeight));
        return new Rectangle(baseRect.x + resolvedX, baseRect.y + resolvedY, resolvedWidth, resolvedHeight);
    }

    public int getX() {
        return offsetX;
    }

    public void setX(int x) {
        this.offsetX = x;
    }

    public int getY() {
        return offsetY;
    }

    public void setY(int y) {
        this.offsetY = y;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(int offsetX) {
        this.offsetX = offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(int offsetY) {
        this.offsetY = offsetY;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getReferenceWidth() {
        return referenceWidth;
    }

    public void setReferenceWidth(int referenceWidth) {
        this.referenceWidth = Math.max(0, referenceWidth);
    }

    public int getReferenceHeight() {
        return referenceHeight;
    }

    public void setReferenceHeight(int referenceHeight) {
        this.referenceHeight = Math.max(0, referenceHeight);
    }

    public boolean hasReferenceSize() {
        return referenceWidth > 0 && referenceHeight > 0;
    }

    private int scaleValue(int value, int currentSize, int referenceSize) {
        if (referenceSize <= 0 || currentSize <= 0) {
            return value;
        }
        return (int) Math.round((double) value * (double) currentSize / (double) referenceSize);
    }

    @Override
    public String toString() {
        return "MonitorRegion{" +
                "x=" + offsetX +
                ", y=" + offsetY +
                ", width=" + width +
                ", height=" + height +
                ", referenceWidth=" + referenceWidth +
                ", referenceHeight=" + referenceHeight +
                '}';
    }
}
