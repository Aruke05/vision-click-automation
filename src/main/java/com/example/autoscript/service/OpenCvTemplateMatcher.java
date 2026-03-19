package com.example.autoscript.service;

import com.example.autoscript.model.MatchResult;
import com.example.autoscript.util.ImageUtils;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_32FC1;
import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA;
import static org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR;
import static org.bytedeco.opencv.global.opencv_imgproc.TM_CCORR_NORMED;
import static org.bytedeco.opencv.global.opencv_imgproc.TM_CCOEFF_NORMED;
import static org.bytedeco.opencv.global.opencv_imgproc.TM_SQDIFF_NORMED;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.matchTemplate;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

public class OpenCvTemplateMatcher implements ImageMatcher {

    private static final double[] TEMPLATE_SCALES = new double[]{
            1.0D, 0.95D, 1.05D, 0.90D, 1.10D, 0.85D, 1.15D, 0.80D, 1.20D, 0.75D, 1.25D, 0.67D, 1.33D, 0.60D, 1.50D
    };

    public OpenCvTemplateMatcher() {
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
        List<MatchCandidate> candidates = new ArrayList<>(3);
        candidates.add(runMatch(sourceGray, templateGray, resultRows, resultCols, TM_CCOEFF_NORMED, false, templateWidth, templateHeight));
        candidates.add(runMatch(sourceGray, templateGray, resultRows, resultCols, TM_CCORR_NORMED, false, templateWidth, templateHeight));
        candidates.add(runMatch(sourceGray, templateGray, resultRows, resultCols, TM_SQDIFF_NORMED, true, templateWidth, templateHeight));
        return candidates.stream()
                .max(Comparator.comparingDouble(MatchCandidate::score))
                .orElse(new MatchCandidate(0.0D, new java.awt.Point(0, 0), templateWidth, templateHeight));
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
    }

    private record TemplateVariant(Mat mat, int width, int height, boolean shouldRelease) {
    }
}
