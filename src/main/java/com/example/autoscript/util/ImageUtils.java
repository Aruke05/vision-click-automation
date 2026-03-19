package com.example.autoscript.util;

import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;

public final class ImageUtils {

    private ImageUtils() {
    }

    public static Mat toMat(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image 不能为空");
        }
        BufferedImage bgr = ensureBgr(image);
        byte[] data = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bgr.getHeight(), bgr.getWidth(), CV_8UC3);
        mat.data().put(data);
        return mat;
    }

    private static BufferedImage ensureBgr(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return src;
        }
        BufferedImage converted = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = converted.createGraphics();
        try {
            g2d.drawImage(src, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return converted;
    }
}
