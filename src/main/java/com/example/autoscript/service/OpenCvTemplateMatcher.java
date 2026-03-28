package com.example.autoscript.service;

import com.example.autoscript.model.MatchResult;
import com.example.autoscript.util.ImageUtils;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.presets.javacpp;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_32FC1;
import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.TM_CCOEFF_NORMED;
import static org.bytedeco.opencv.global.opencv_imgproc.TM_SQDIFF_NORMED;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.matchTemplate;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

public class OpenCvTemplateMatcher implements ImageMatcher {

    private static final double[] TEMPLATE_SCALES = new double[]{
            1.0D, 0.95D, 1.05D, 0.90D, 1.10D, 0.85D, 1.15D, 0.80D, 1.20D, 0.75D, 1.25D, 0.67D, 1.33D, 0.60D, 1.50D
    };
    private static final int TARGET_COLOR_SAMPLE_COUNT = 1200;
    private static final double LUMA_DIFF_WEIGHT = 0.30D;
    private static final double CHROMA_DIFF_WEIGHT = 0.70D;
    private static final double MIN_COLOR_PENALTY_WEIGHT = 0.35D;
    private static final double MAX_COLOR_PENALTY_WEIGHT = 0.72D;
    private static final double TEMPLATE_COLOR_ACTIVITY_BASELINE = 0.25D;

    public OpenCvTemplateMatcher() {
        WindowsJavaCppRuntimeSupport.prepare();
        Loader.load(javacpp.class);
        Loader.load(opencv_core.class);
    }

    @Override
    public MatchResult match(BufferedImage searchImage, BufferedImage templateImage) {
        Mat source = null;
        Mat template = null;
        Mat sourceGray = null;
        Mat templateGray = null;
        List<TemplateVariant> variants = null;
        try {
            source = ImageUtils.toMat(searchImage);
            template = ImageUtils.toMat(templateImage);

            sourceGray = new Mat();
            templateGray = new Mat();
            cvtColor(source, sourceGray, COLOR_BGR2GRAY);
            cvtColor(template, templateGray, COLOR_BGR2GRAY);

            variants = buildTemplateVariants(
                    templateGray,
                    sourceGray.cols(),
                    sourceGray.rows(),
                    templateImage.getWidth(),
                    templateImage.getHeight()
            );
            if (variants == null || variants.isEmpty()) {
                return new MatchResult(0.0D, new java.awt.Point(0, 0),
                        new Dimension(templateImage.getWidth(), templateImage.getHeight()));
            }
            MatchCandidate best = new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateImage.getWidth(), templateImage.getHeight());
            for (TemplateVariant variant : variants) {
                MatchCandidate candidate = runBestMethods(sourceGray, variant.mat(), variant.width(), variant.height());
                ColorMetrics colorMetrics = estimateColorMetrics(
                        searchImage,
                        templateImage,
                        candidate.location(),
                        variant.width(),
                        variant.height()
                );
                double adjustedScore = applyColorPenalty(candidate.score(), colorMetrics);
                candidate = candidate.withScore(adjustedScore);
                if (candidate.score() > best.score()) {
                    best = candidate;
                }
            }

            return new MatchResult(
                    best.score(),
                    best.location(),
                    new Dimension(best.templateWidth(), best.templateHeight())
            );
        } finally {
            releaseTemplateVariants(variants);
            releaseQuietly(templateGray, sourceGray, template, source);
        }
    }

    private MatchCandidate runBestMethods(Mat sourceGray,
                                          Mat templateGray,
                                          int templateWidth,
                                          int templateHeight) {
        int resultCols = sourceGray.cols() - templateGray.cols() + 1;
        int resultRows = sourceGray.rows() - templateGray.rows() + 1;
        if (resultCols <= 0 || resultRows <= 0) {
            return new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateWidth, templateHeight);
        }
        MatchCandidate best = runMatch(sourceGray, templateGray, resultRows, resultCols,
                TM_CCOEFF_NORMED, false, templateWidth, templateHeight);
        MatchCandidate sqdiff = runMatch(sourceGray, templateGray, resultRows, resultCols,
                TM_SQDIFF_NORMED, true, templateWidth, templateHeight);
        if (sqdiff.score() > best.score()) {
            best = sqdiff;
        }
        return best;
    }

    private ColorMetrics estimateColorMetrics(BufferedImage sourceImage,
                                              BufferedImage templateImage,
                                              java.awt.Point location,
                                              int scaledTemplateWidth,
                                              int scaledTemplateHeight) {
        if (sourceImage == null || templateImage == null || location == null) {
            return new ColorMetrics(0.0D, 0.0D);
        }
        if (scaledTemplateWidth <= 0 || scaledTemplateHeight <= 0) {
            return new ColorMetrics(0.0D, 0.0D);
        }
        int sourceStartX = location.x;
        int sourceStartY = location.y;
        if (sourceStartX < 0 || sourceStartY < 0
                || sourceStartX + scaledTemplateWidth > sourceImage.getWidth()
                || sourceStartY + scaledTemplateHeight > sourceImage.getHeight()) {
            return new ColorMetrics(0.0D, 0.0D);
        }

        int templateWidth = templateImage.getWidth();
        int templateHeight = templateImage.getHeight();
        if (templateWidth <= 0 || templateHeight <= 0) {
            return new ColorMetrics(0.0D, 0.0D);
        }

        long templateArea = (long) scaledTemplateWidth * (long) scaledTemplateHeight;
        int sampleStep = (int) Math.ceil(Math.sqrt((double) templateArea / (double) TARGET_COLOR_SAMPLE_COUNT));
        sampleStep = Math.max(1, sampleStep);

        double diffSum = 0.0D;
        double colorActivitySum = 0.0D;
        int sampleCount = 0;
        for (int y = 0; y < scaledTemplateHeight; y += sampleStep) {
            int sourceY = sourceStartY + y;
            int templateY = Math.min(templateHeight - 1, (int) ((long) y * (long) templateHeight / (long) scaledTemplateHeight));
            for (int x = 0; x < scaledTemplateWidth; x += sampleStep) {
                int sourceX = sourceStartX + x;
                int templateX = Math.min(templateWidth - 1, (int) ((long) x * (long) templateWidth / (long) scaledTemplateWidth));

                int sourceRgb = sourceImage.getRGB(sourceX, sourceY);
                int templateRgb = templateImage.getRGB(templateX, templateY);
                int sourceR = (sourceRgb >> 16) & 0xFF;
                int sourceG = (sourceRgb >> 8) & 0xFF;
                int sourceB = sourceRgb & 0xFF;
                int templateR = (templateRgb >> 16) & 0xFF;
                int templateG = (templateRgb >> 8) & 0xFF;
                int templateB = templateRgb & 0xFF;

                int sourceLuma = computeLuma(sourceR, sourceG, sourceB);
                int templateLuma = computeLuma(templateR, templateG, templateB);
                int sourceCb = sourceB - sourceLuma;
                int sourceCr = sourceR - sourceLuma;
                int templateCb = templateB - templateLuma;
                int templateCr = templateR - templateLuma;

                double lumaDiff = Math.abs(sourceLuma - templateLuma) / 255.0D;
                double chromaDiff = (Math.abs(sourceCb - templateCb) + Math.abs(sourceCr - templateCr)) / 1020.0D;
                diffSum += LUMA_DIFF_WEIGHT * lumaDiff + CHROMA_DIFF_WEIGHT * chromaDiff;
                colorActivitySum += (Math.abs(templateCb) + Math.abs(templateCr)) / 510.0D;
                sampleCount++;
            }
        }

        if (sampleCount <= 0) {
            return new ColorMetrics(0.0D, 0.0D);
        }
        double avgDiff = diffSum / sampleCount;
        double similarity = normalizeScore(1.0D - avgDiff);
        double templateColorActivity = normalizeScore(colorActivitySum / sampleCount);
        return new ColorMetrics(similarity, templateColorActivity);
    }

    private int computeLuma(int r, int g, int b) {
        return (77 * r + 150 * g + 29 * b) >> 8;
    }

    private double applyColorPenalty(double structureScore, ColorMetrics colorMetrics) {
        if (colorMetrics == null) {
            return normalizeScore(structureScore);
        }
        double colorPenaltyWeight = resolveColorPenaltyWeight(colorMetrics.templateColorActivity());
        double penaltyFactor = 1.0D - colorPenaltyWeight * (1.0D - colorMetrics.similarity());
        return normalizeScore(structureScore * penaltyFactor);
    }

    private double resolveColorPenaltyWeight(double templateColorActivity) {
        double normalizedActivity = normalizeScore(templateColorActivity / TEMPLATE_COLOR_ACTIVITY_BASELINE);
        return MIN_COLOR_PENALTY_WEIGHT
                + (MAX_COLOR_PENALTY_WEIGHT - MIN_COLOR_PENALTY_WEIGHT) * normalizedActivity;
    }

    private MatchCandidate runMatch(Mat sourceGray,
                                    Mat templateGray,
                                    int resultRows,
                                    int resultCols,
                                    int method,
                                    boolean lowerIsBetter,
                                    int templateWidth,
                                    int templateHeight) {
        Mat result = null;
        DoublePointer minVal = null;
        DoublePointer maxVal = null;
        try {
            result = new Mat(resultRows, resultCols, CV_32FC1);
            matchTemplate(sourceGray, templateGray, result, method);

            minVal = new DoublePointer(1L);
            maxVal = new DoublePointer(1L);
            Point minLoc = new Point();
            Point maxLoc = new Point();
            minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);

            if (lowerIsBetter) {
                double similarity = 1.0D - minVal.get(0);
                return new MatchCandidate(normalizeScore(similarity),
                        new java.awt.Point(minLoc.x(), minLoc.y()),
                        templateWidth,
                        templateHeight);
            }
            return new MatchCandidate(normalizeScore(maxVal.get(0)),
                    new java.awt.Point(maxLoc.x(), maxLoc.y()),
                    templateWidth,
                    templateHeight);
        } finally {
            if (minVal != null) {
                minVal.close();
            }
            if (maxVal != null) {
                maxVal.close();
            }
            releaseQuietly(result);
        }
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

    private List<TemplateVariant> buildTemplateVariants(Mat templateGray,
                                                        int sourceWidth,
                                                        int sourceHeight,
                                                        int originalWidth,
                                                        int originalHeight) {
        List<TemplateVariant> variants = new ArrayList<>();
        for (double scale : TEMPLATE_SCALES) {
            int scaledWidth = Math.max(1, (int) Math.round(originalWidth * scale));
            int scaledHeight = Math.max(1, (int) Math.round(originalHeight * scale));
            if (scaledWidth > sourceWidth || scaledHeight > sourceHeight) {
                continue;
            }
            if (scaledWidth == templateGray.cols() && scaledHeight == templateGray.rows()) {
                variants.add(new TemplateVariant(templateGray, scaledWidth, scaledHeight, false));
                continue;
            }
            Mat scaled = new Mat();
            int interpolation = scale < 1.0D ? INTER_AREA : INTER_LINEAR;
            resize(templateGray, scaled, new org.bytedeco.opencv.opencv_core.Size(scaledWidth, scaledHeight), 0.0D, 0.0D, interpolation);
            variants.add(new TemplateVariant(scaled, scaledWidth, scaledHeight, true));
        }
        return variants;
    }

    private void releaseQuietly(Mat... mats) {
        if (mats == null) {
            return;
        }
        for (Mat mat : mats) {
            if (mat != null) {
                mat.release();
                mat.close();
            }
        }
    }

    private void releaseTemplateVariants(List<TemplateVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }
        for (TemplateVariant variant : variants) {
            if (variant != null && variant.shouldRelease() && variant.mat() != null) {
                variant.mat().release();
                variant.mat().close();
            }
        }
    }

    private record MatchCandidate(double score, java.awt.Point location, int templateWidth, int templateHeight) {
        private MatchCandidate withScore(double updatedScore) {
            return new MatchCandidate(updatedScore, location, templateWidth, templateHeight);
        }
    }

    private record TemplateVariant(Mat mat, int width, int height, boolean shouldRelease) {
    }

    private record ColorMetrics(double similarity, double templateColorActivity) {
    }
}
