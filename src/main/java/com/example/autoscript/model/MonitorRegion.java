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
        int x = baseRect.x + offsetX;
        int y = baseRect.y + offsetY;
        return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
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

    @Override
    public String toString() {
        return "MonitorRegion{" +
                "x=" + offsetX +
                ", y=" + offsetY +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}
