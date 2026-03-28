package com.example.autoscript.gui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class SelectionMaskOverlayWindow {

    public interface SelectionCallback {
        void onSelected(Rectangle selectionRect);

        void onCanceled();
    }

    private static final int PANEL_MARGIN = 14;
    private static final int INFO_AREA_HEIGHT = 54;
    private static final double MAX_PREVIEW_WIDTH_RATIO = 0.86D;
    private static final double MAX_PREVIEW_HEIGHT_RATIO = 0.76D;
    private static final Color PANEL_BG = new Color(24, 24, 24);
    private static final Color MASK_COLOR = new Color(0, 0, 0, 120);
    private static final Color BORDER_COLOR = new Color(255, 196, 66, 220);
    private static final Color INFO_BG_COLOR = new Color(0, 0, 0, 170);
    private static final Color INFO_TEXT_COLOR = new Color(245, 245, 245);

    private final Window owner;
    private final JWindow window;
    private final OverlayPanel overlayPanel = new OverlayPanel();

    private Rectangle imagePreviewRect = new Rectangle();
    private Point dragStart;
    private Rectangle selectionPreviewRect;
    private BufferedImage backgroundImage;
    private String hintText = "";
    private SelectionCallback callback;
    private boolean finished;

    public SelectionMaskOverlayWindow(Window owner) {
        this.owner = owner;
        this.window = new JWindow((Window) null);
        this.window.setAlwaysOnTop(true);
        this.window.setFocusableWindowState(true);
        this.window.setAutoRequestFocus(true);
        this.window.setType(Window.Type.UTILITY);
        // 排除应用级模态阻塞，否则从条件编辑弹窗里打开蒙版时，
        // Windows 下会出现“看得到蒙版但点击只有报错音”的现象。
        this.window.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        this.window.setContentPane(overlayPanel);
        this.overlayPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        installMouseHandlers();
        installKeyBindings();
    }

    public void start(Rectangle screenBounds,
                      BufferedImage background,
                      String hint,
                      SelectionCallback callback) {
        BufferedImage safeBackground = resolveBackground(background, screenBounds);
        this.backgroundImage = safeBackground;
        this.hintText = hint == null ? "" : hint.trim();
        this.callback = Objects.requireNonNull(callback, "callback");
        this.dragStart = null;
        this.selectionPreviewRect = null;
        this.finished = false;

        Rectangle virtualBounds = resolveVirtualScreenBounds();
        Rectangle previewRect = buildPreviewRect(
                safeBackground.getWidth(),
                safeBackground.getHeight(),
                virtualBounds
        );
        this.imagePreviewRect = previewRect;

        int panelWidth = previewRect.width + PANEL_MARGIN * 2;
        int panelHeight = previewRect.height + PANEL_MARGIN * 2 + INFO_AREA_HEIGHT;
        window.setSize(panelWidth, panelHeight);
        Point location = resolveWindowLocation(panelWidth, panelHeight, virtualBounds);
        window.setLocation(location);

        window.setVisible(true);
        window.toFront();
        overlayPanel.requestFocusInWindow();
        overlayPanel.repaint();
    }

    public void dispose() {
        window.dispose();
    }

    private void installMouseHandlers() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (finished) {
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON3) {
                    cancelSelection();
                    return;
                }
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                if (!isPointInPreview(e.getPoint())) {
                    return;
                }
                dragStart = clampToPreview(e.getPoint());
                selectionPreviewRect = buildSelectionRect(dragStart, dragStart);
                overlayPanel.repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (finished || dragStart == null) {
                    return;
                }
                Point current = clampToPreview(e.getPoint());
                selectionPreviewRect = buildSelectionRect(dragStart, current);
                overlayPanel.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (finished || e.getButton() != MouseEvent.BUTTON1 || dragStart == null) {
                    return;
                }
                Point end = clampToPreview(e.getPoint());
                selectionPreviewRect = buildSelectionRect(dragStart, end);
                completeSelection();
            }
        };
        overlayPanel.addMouseListener(mouseAdapter);
        overlayPanel.addMouseMotionListener(mouseAdapter);
    }

    private void installKeyBindings() {
        overlayPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("ESCAPE"), "cancel-selection");
        overlayPanel.getActionMap().put("cancel-selection", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelSelection();
            }
        });
        overlayPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("ENTER"), "confirm-selection");
        overlayPanel.getActionMap().put("confirm-selection", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                completeSelection();
            }
        });
    }

    private void completeSelection() {
        if (finished) {
            return;
        }
        finished = true;
        window.setVisible(false);
        Rectangle selected = selectionPreviewRect;
        if (selected == null || selected.width <= 0 || selected.height <= 0 || backgroundImage == null) {
            if (callback != null) {
                callback.onCanceled();
            }
            return;
        }
        Rectangle mapped = mapPreviewToImage(selected);
        if (callback != null) {
            callback.onSelected(mapped);
        }
    }

    private void cancelSelection() {
        if (finished) {
            return;
        }
        finished = true;
        window.setVisible(false);
        if (callback != null) {
            callback.onCanceled();
        }
    }

    private Point clampToPreview(Point point) {
        if (point == null) {
            return new Point(imagePreviewRect.x, imagePreviewRect.y);
        }
        int minX = imagePreviewRect.x;
        int minY = imagePreviewRect.y;
        int maxX = imagePreviewRect.x + Math.max(1, imagePreviewRect.width) - 1;
        int maxY = imagePreviewRect.y + Math.max(1, imagePreviewRect.height) - 1;
        int x = Math.max(minX, Math.min(maxX, point.x));
        int y = Math.max(minY, Math.min(maxY, point.y));
        return new Point(x, y);
    }

    private boolean isPointInPreview(Point point) {
        return point != null && imagePreviewRect.contains(point);
    }

    private Rectangle buildSelectionRect(Point start, Point end) {
        int x = Math.min(start.x, end.x);
        int y = Math.min(start.y, end.y);
        int width = Math.abs(end.x - start.x) + 1;
        int height = Math.abs(end.y - start.y) + 1;
        return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
    }

    private Rectangle mapPreviewToImage(Rectangle previewRect) {
        if (backgroundImage == null || previewRect == null || imagePreviewRect.width <= 0 || imagePreviewRect.height <= 0) {
            return new Rectangle(0, 0, 1, 1);
        }
        int imageWidth = backgroundImage.getWidth();
        int imageHeight = backgroundImage.getHeight();
        int relativeX = previewRect.x - imagePreviewRect.x;
        int relativeY = previewRect.y - imagePreviewRect.y;

        int mappedX = (int) Math.floor((double) relativeX * (double) imageWidth / (double) imagePreviewRect.width);
        int mappedY = (int) Math.floor((double) relativeY * (double) imageHeight / (double) imagePreviewRect.height);
        int mappedW = (int) Math.round((double) previewRect.width * (double) imageWidth / (double) imagePreviewRect.width);
        int mappedH = (int) Math.round((double) previewRect.height * (double) imageHeight / (double) imagePreviewRect.height);

        mappedX = Math.max(0, Math.min(imageWidth - 1, mappedX));
        mappedY = Math.max(0, Math.min(imageHeight - 1, mappedY));
        mappedW = Math.max(1, mappedW);
        mappedH = Math.max(1, mappedH);
        if (mappedX + mappedW > imageWidth) {
            mappedW = imageWidth - mappedX;
        }
        if (mappedY + mappedH > imageHeight) {
            mappedH = imageHeight - mappedY;
        }
        return new Rectangle(mappedX, mappedY, Math.max(1, mappedW), Math.max(1, mappedH));
    }

    private BufferedImage resolveBackground(BufferedImage background, Rectangle fallbackBounds) {
        if (background != null && background.getWidth() > 0 && background.getHeight() > 0) {
            return background;
        }
        int width = 640;
        int height = 360;
        if (fallbackBounds != null && fallbackBounds.width > 0 && fallbackBounds.height > 0) {
            width = fallbackBounds.width;
            height = fallbackBounds.height;
        }
        BufferedImage placeholder = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = placeholder.createGraphics();
        try {
            g2.setColor(new Color(48, 48, 48));
            g2.fillRect(0, 0, placeholder.getWidth(), placeholder.getHeight());
        } finally {
            g2.dispose();
        }
        return placeholder;
    }

    private Rectangle resolveVirtualScreenBounds() {
        Rectangle virtual = null;
        java.awt.GraphicsEnvironment env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (java.awt.GraphicsDevice device : env.getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            if (bounds == null) {
                continue;
            }
            if (virtual == null) {
                virtual = new Rectangle(bounds);
            } else {
                virtual = virtual.union(bounds);
            }
        }
        if (virtual != null && virtual.width > 0 && virtual.height > 0) {
            return virtual;
        }
        Dimension fallback = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        return new Rectangle(0, 0, Math.max(1, fallback.width), Math.max(1, fallback.height));
    }

    private Rectangle buildPreviewRect(int sourceWidth, int sourceHeight, Rectangle virtualBounds) {
        int maxWidth = Math.max(320, (int) Math.floor(virtualBounds.width * MAX_PREVIEW_WIDTH_RATIO));
        int maxHeight = Math.max(220, (int) Math.floor(virtualBounds.height * MAX_PREVIEW_HEIGHT_RATIO));
        double scale = Math.min(1.0D, Math.min((double) maxWidth / (double) sourceWidth, (double) maxHeight / (double) sourceHeight));
        int previewWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int previewHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        return new Rectangle(PANEL_MARGIN, PANEL_MARGIN + INFO_AREA_HEIGHT, previewWidth, previewHeight);
    }

    private Point resolveWindowLocation(int panelWidth, int panelHeight, Rectangle virtualBounds) {
        int x;
        int y;
        if (owner != null && owner.isShowing()) {
            Point ownerLocation = owner.getLocationOnScreen();
            x = ownerLocation.x + (owner.getWidth() - panelWidth) / 2;
            y = ownerLocation.y + (owner.getHeight() - panelHeight) / 2;
        } else {
            x = virtualBounds.x + (virtualBounds.width - panelWidth) / 2;
            y = virtualBounds.y + (virtualBounds.height - panelHeight) / 2;
        }
        int maxX = virtualBounds.x + virtualBounds.width - panelWidth;
        int maxY = virtualBounds.y + virtualBounds.height - panelHeight;
        x = Math.max(virtualBounds.x, Math.min(maxX, x));
        y = Math.max(virtualBounds.y, Math.min(maxY, y));
        return new Point(x, y);
    }

    private final class OverlayPanel extends JPanel {

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PANEL_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (backgroundImage != null && imagePreviewRect.width > 0 && imagePreviewRect.height > 0) {
                    g2.drawImage(backgroundImage,
                            imagePreviewRect.x,
                            imagePreviewRect.y,
                            imagePreviewRect.width,
                            imagePreviewRect.height,
                            null);
                    g2.setColor(MASK_COLOR);
                    g2.fillRect(imagePreviewRect.x, imagePreviewRect.y, imagePreviewRect.width, imagePreviewRect.height);
                }

                Rectangle selected = selectionPreviewRect;
                if (selected != null && selected.width > 0 && selected.height > 0
                        && backgroundImage != null && imagePreviewRect.width > 0 && imagePreviewRect.height > 0) {
                    Shape oldClip = g2.getClip();
                    g2.setClip(selected);
                    g2.drawImage(backgroundImage,
                            imagePreviewRect.x,
                            imagePreviewRect.y,
                            imagePreviewRect.width,
                            imagePreviewRect.height,
                            null);
                    g2.setClip(oldClip);

                    g2.setColor(BORDER_COLOR);
                    g2.setStroke(new BasicStroke(2.0F));
                    g2.drawRect(selected.x, selected.y, selected.width, selected.height);

                    Rectangle mapped = mapPreviewToImage(selected);
                    String sizeText = String.format("x=%d y=%d w=%d h=%d",
                            mapped.x, mapped.y, mapped.width, mapped.height);
                    drawInfoText(g2, sizeText, Math.max(8, selected.x), Math.max(28, selected.y - 8));
                }

                if (!hintText.isBlank()) {
                    drawInfoText(g2, hintText, PANEL_MARGIN, 24);
                }
                if (backgroundImage != null) {
                    drawInfoText(g2,
                            "预览分辨率: " + backgroundImage.getWidth() + "x" + backgroundImage.getHeight(),
                            PANEL_MARGIN,
                            46);
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawInfoText(Graphics2D g2, String text, int x, int y) {
            FontMetrics metrics = g2.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getHeight();
            int drawX = Math.max(6, Math.min(x, Math.max(6, getWidth() - textWidth - 20)));
            int drawY = Math.max(textHeight, Math.min(y, Math.max(textHeight, getHeight() - 6)));

            g2.setColor(INFO_BG_COLOR);
            g2.fillRoundRect(drawX - 6, drawY - textHeight + 2, textWidth + 12, textHeight + 4, 10, 10);
            g2.setColor(INFO_TEXT_COLOR);
            g2.drawString(text, drawX, drawY);
        }
    }
}
