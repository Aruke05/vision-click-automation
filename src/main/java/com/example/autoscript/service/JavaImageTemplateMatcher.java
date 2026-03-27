package com.example.autoscript.service;

import com.example.autoscript.model.MatchResult;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class JavaImageTemplateMatcher implements ImageMatcher {

    private static final double[] TEMPLATE_SCALES = new double[]{
            1.0D, 0.95D, 1.05D, 0.90D, 1.10D, 0.85D, 1.15D, 0.80D, 1.20D, 0.75D, 1.25D, 0.67D, 1.33D, 0.60D, 1.50D
    };
    private static final int TARGET_SAMPLE_COUNT = 1600;
    private static final double GRAY_DIFF_WEIGHT = 0.42D;
    private static final double COLOR_DIFF_WEIGHT = 0.58D;

    @Override
    public MatchResult match(BufferedImage searchImage, BufferedImage templateImage) {
        if (searchImage == null || templateImage == null) {
            return new MatchResult(0.0D, new java.awt.Point(0, 0), new Dimension(0, 0));
        }
        int searchWidth = searchImage.getWidth();
        int searchHeight = searchImage.getHeight();
        int templateWidth = templateImage.getWidth();
        int templateHeight = templateImage.getHeight();
        if (searchWidth <= 0 || searchHeight <= 0 || templateWidth <= 0 || templateHeight <= 0) {
            return new MatchResult(0.0D, new java.awt.Point(0, 0), new Dimension(templateWidth, templateHeight));
        }

        ImageData searchData = ImageData.from(searchImage);
        MatchCandidate best = new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateWidth, templateHeight);

        for (double scale : TEMPLATE_SCALES) {
            int scaledWidth = Math.max(1, (int) Math.round(templateWidth * scale));
            int scaledHeight = Math.max(1, (int) Math.round(templateHeight * scale));
            if (scaledWidth > searchWidth || scaledHeight > searchHeight) {
                continue;
            }
            BufferedImage scaledTemplate = scaleImage(templateImage, scaledWidth, scaledHeight);
            ImageData templateData = ImageData.from(scaledTemplate);
            MatchCandidate candidate = matchVariant(searchData, templateData);
            if (candidate.score() > best.score()) {
                best = candidate;
            }
        }

        return new MatchResult(
                best.score(),
                best.location(),
                new Dimension(best.templateWidth(), best.templateHeight())
        );
    }

    private MatchCandidate matchVariant(ImageData searchData, ImageData templateData) {
        int maxX = searchData.width() - templateData.width();
        int maxY = searchData.height() - templateData.height();
        if (maxX < 0 || maxY < 0) {
            return new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateData.width(), templateData.height());
        }

        int sampleStep = resolveSampleStep(templateData.width(), templateData.height());
        int totalSamples = countSamples(templateData.width(), templateData.height(), sampleStep);
        if (totalSamples <= 0) {
            return new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateData.width(), templateData.height());
        }

        MatchCandidate best = new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateData.width(), templateData.height());
        for (int startY = 0; startY <= maxY; startY++) {
            for (int startX = 0; startX <= maxX; startX++) {
                double diffSum = 0.0D;
                boolean pruned = false;
                for (int y = 0; y < templateData.height() && !pruned; y += sampleStep) {
                    int searchRowBase = (startY + y) * searchData.width() + startX;
                    int templateRowBase = y * templateData.width();
                    for (int x = 0; x < templateData.width(); x += sampleStep) {
                        int searchIndex = searchRowBase + x;
                        int templateIndex = templateRowBase + x;
                        double grayDiff = Math.abs(searchData.gray()[searchIndex] - templateData.gray()[templateIndex]) / 255.0D;
                        double colorDiff = computeColorDiff(searchData.rgb()[searchIndex], templateData.rgb()[templateIndex]);
                        diffSum += GRAY_DIFF_WEIGHT * grayDiff + COLOR_DIFF_WEIGHT * colorDiff;
                        double maxPossibleScore = 1.0D - diffSum / totalSamples;
                        if (maxPossibleScore <= best.score()) {
                            pruned = true;
                            break;
                        }
                    }
                }
                if (pruned) {
                    continue;
                }
                double score = normalizeScore(1.0D - diffSum / totalSamples);
                if (score > best.score()) {
                    best = new MatchCandidate(score, new java.awt.Point(startX, startY), templateData.width(), templateData.height());
                }
            }
        }
        return best;
    }

    private double computeColorDiff(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;
        return (Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)) / 765.0D;
    }

    private int resolveSampleStep(int width, int height) {
        long area = (long) width * (long) height;
        int step = (int) Math.ceil(Math.sqrt((double) area / (double) TARGET_SAMPLE_COUNT));
        return Math.max(1, step);
    }

    private int countSamples(int width, int height, int step) {
        int count = 0;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                count++;
            }
        }
        return count;
    }

    private double normalizeScore(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private BufferedImage scaleImage(BufferedImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private record MatchCandidate(double score, java.awt.Point location, int templateWidth, int templateHeight) {
    }

    private record ImageData(int width, int height, int[] rgb, int[] gray) {
        private static ImageData from(BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
            int[] gray = new int[rgb.length];
            for (int i = 0; i < rgb.length; i++) {
                int value = rgb[i];
                int r = (value >> 16) & 0xFF;
                int g = (value >> 8) & 0xFF;
                int b = value & 0xFF;
                gray[i] = (77 * r + 150 * g + 29 * b) >> 8;
            }
            return new ImageData(width, height, rgb, gray);
        }
    }
}
