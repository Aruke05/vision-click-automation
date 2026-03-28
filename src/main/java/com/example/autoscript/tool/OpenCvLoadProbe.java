package com.example.autoscript.tool;

import com.example.autoscript.service.OpenCvTemplateMatcher;

public class OpenCvLoadProbe {
    public static void main(String[] args) {
        try {
            new OpenCvTemplateMatcher();
            System.out.println("OPENCV_LOAD_OK");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("OPENCV_LOAD_FAILED: " + throwable.getClass().getName()
                    + (throwable.getMessage() == null ? "" : " :: " + throwable.getMessage()));
            System.exit(1);
        }
    }
}
