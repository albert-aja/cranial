package io.cranial.platform.document.ocr;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local deterministic fallback OCR engine used when native Tesseract CLI binary is not installed
 * on developer workstations or CI containers. Extracts embedded PNG tEXt OCR layers when present
 * and computes visual ink-density confidence diagnostics.
 */
@Component
public class LocalDeterministicOcrEngine implements OcrEngine {

    @Override
    public String engineCode() {
        return "local_deterministic_ocr";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public OcrPageResult recognize(BufferedImage preprocessedImage, byte[] rawSourceBytes, String ocrLanguage) {
        String embeddedText = extractPngTextChunk(rawSourceBytes);
        double inkRatio = calculateDarkInkRatio(preprocessedImage);

        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("ocr_language", ocrLanguage);
        diag.put("ink_density_ratio", Math.round(inkRatio * 10000.0) / 10000.0);
        diag.put("tesseract_native_present", false);
        diag.put("fallback_reason", "Native tesseract CLI not installed on host; used deterministic local OCR inspect");

        if (embeddedText != null && !embeddedText.isBlank()) {
            double confidence = inkRatio > 0.002 ? 0.91 : 0.84;
            diag.put("recognition_source", "png_text_layer");
            return new OcrPageResult(embeddedText.trim(), confidence, engineCode(), diag);
        }

        // Pure pixel image without native tesseract installed: flag low confidence for Human Review Queue
        diag.put("recognition_source", "pixel_density_only");
        String placeholder = inkRatio > 0.005
                ? "[IMAGE_SCAN_DETECTED: install tesseract-ocr (" + ocrLanguage + ") for glyph recognition; routed to human review]"
                : "";
        double lowConf = inkRatio > 0.005 ? 0.35 : 0.15;
        return new OcrPageResult(placeholder, lowConf, engineCode(), diag);
    }

    private static String extractPngTextChunk(byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            return null;
        }
        if ((bytes[0] & 0xFF) != 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
            return null;
        }
        int pos = 8;
        while (pos + 12 <= bytes.length) {
            int len = ByteBuffer.wrap(bytes, pos, 4).getInt();
            if (len < 0 || pos + 12 + len > bytes.length) {
                break;
            }
            String chunkType = new String(bytes, pos + 4, 4, StandardCharsets.US_ASCII);
            if ("tEXt".equals(chunkType) && len > 1) {
                int dataStart = pos + 8;
                int nulIdx = -1;
                for (int i = 0; i < len; i++) {
                    if (bytes[dataStart + i] == 0) {
                        nulIdx = i;
                        break;
                    }
                }
                if (nulIdx >= 0 && nulIdx + 1 < len) {
                    String key = new String(bytes, dataStart, nulIdx, StandardCharsets.ISO_8859_1);
                    if ("OCR-Text".equalsIgnoreCase(key) || "Comment".equalsIgnoreCase(key) || "Description".equalsIgnoreCase(key)) {
                        return new String(bytes, dataStart + nulIdx + 1, len - nulIdx - 1, StandardCharsets.UTF_8);
                    }
                }
            }
            if ("IEND".equals(chunkType)) {
                break;
            }
            pos += 12 + len;
        }
        return null;
    }

    private static double calculateDarkInkRatio(BufferedImage img) {
        if (img == null) {
            return 0.0;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        long dark = 0;
        long total = (long) w * h;
        if (total == 0) {
            return 0.0;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;
                if (lum < 150) {
                    dark++;
                }
            }
        }
        return (double) dark / total;
    }
}

