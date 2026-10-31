package io.cranial.platform.document.ocr;

import java.awt.image.BufferedImage;

public interface OcrEngine {

    String engineCode();

    boolean isAvailable();

    OcrPageResult recognize(BufferedImage preprocessedImage, byte[] rawSourceBytes, String ocrLanguage);
}

