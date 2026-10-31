package io.cranial.platform.document.ocr;

import io.cranial.platform.document.model.PreprocessOptions;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImagePreprocessor {

    public record PreprocessResult(
            BufferedImage image,
            List<String> appliedSteps,
            double detectedSkewDegrees,
            Map<String, Object> diagnostics
    ) {
    }

    public PreprocessResult preprocess(BufferedImage input, PreprocessOptions options, boolean advancedFlagEnabled) {
        long start = System.currentTimeMillis();
        PreprocessOptions opts = options != null ? options : PreprocessOptions.defaults();
        List<String> steps = new ArrayList<>();
        BufferedImage current = input;

        int origW = input.getWidth();
        int origH = input.getHeight();

        // 1. Explicit Rotation (90 / 180 / 270 degrees)
        int rotateDeg = opts.effectiveRotateDegrees();
        if (rotateDeg != 0) {
            current = rotateOrthogonal(current, rotateDeg);
            steps.add("ROTATE_" + rotateDeg);
        }

        // Convert to Grayscale working buffer for advanced CV passes
        BufferedImage gray = toGrayscale(current);
        steps.add("GRAYSCALE");

        double skewDeg = 0.0;
        if (advancedFlagEnabled) {
            // 2. Deskew via Horizontal Projection Profile Variance
            if (opts.effectiveDeskew()) {
                skewDeg = estimateSkewDegrees(gray);
                if (Math.abs(skewDeg) >= 0.4) {
                    gray = rotateArbitrary(gray, -skewDeg);
                    steps.add(String.format("DESKEW(%.2f_deg)", -skewDeg));
                } else {
                    steps.add("DESKEW_CHECKED_ALIGNED");
                }
            }

            // 3. Denoise via 3x3 Median Filter (only on reasonably sized images to keep CPU fast)
            if (opts.effectiveDenoise() && (long) gray.getWidth() * gray.getHeight() <= 4_500_000L) {
                gray = medianFilter3x3(gray);
                steps.add("DENOISE_MEDIAN_3X3");
            }

            // 4. Auto-Contrast Histogram Stretching (2nd - 98th percentile)
            if (opts.effectiveContrastEnhance()) {
                gray = enhanceContrast(gray);
                steps.add("CONTRAST_STRETCH");
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("original_width", origW);
        diag.put("original_height", origH);
        diag.put("processed_width", gray.getWidth());
        diag.put("processed_height", gray.getHeight());
        diag.put("detected_skew_degrees", Math.round(skewDeg * 100.0) / 100.0);
        diag.put("advanced_preprocessing_enabled", advancedFlagEnabled);
        diag.put("preprocess_duration_ms", durationMs);

        return new PreprocessResult(gray, steps, skewDeg, diag);
    }

    private static BufferedImage rotateOrthogonal(BufferedImage src, int degrees) {
        int w = src.getWidth();
        int h = src.getHeight();
        int newW = (degrees == 90 || degrees == 270) ? h : w;
        int newH = (degrees == 90 || degrees == 270) ? w : h;

        BufferedImage dst = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dst.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newW, newH);
        AffineTransform at = new AffineTransform();
        if (degrees == 90) {
            at.translate(h, 0);
            at.rotate(Math.PI / 2);
        } else if (degrees == 180) {
            at.translate(w, h);
            at.rotate(Math.PI);
        } else if (degrees == 270) {
            at.translate(0, w);
            at.rotate(3 * Math.PI / 2);
        }
        g2d.drawRenderedImage(src, at);
        g2d.dispose();
        return dst;
    }

    private static BufferedImage toGrayscale(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    private static double estimateSkewDegrees(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        if (w < 20 || h < 20) {
            return 0.0;
        }
        int stepX = Math.max(1, w / 200);
        int stepY = Math.max(1, h / 200);
        double bestAngle = 0.0;
        double maxVariance = -1.0;

        for (double deg = -4.0; deg <= 4.0; deg += 0.5) {
            double rad = Math.toRadians(deg);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            int[] rowSums = new int[h];

            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int lum = gray.getRaster().getSample(x, y, 0);
                    if (lum < 160) { // dark ink pixel
                        int projY = (int) Math.round((y - h / 2.0) * cos - (x - w / 2.0) * sin + h / 2.0);
                        if (projY >= 0 && projY < h) {
                            rowSums[projY]++;
                        }
                    }
                }
            }
            double mean = 0.0;
            for (int s : rowSums) {
                mean += s;
            }
            mean /= h;
            double variance = 0.0;
            for (int s : rowSums) {
                double diff = s - mean;
                variance += diff * diff;
            }
            if (variance > maxVariance) {
                maxVariance = variance;
                bestAngle = deg;
            }
        }
        return bestAngle;
    }

    private static BufferedImage rotateArbitrary(BufferedImage src, double degrees) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = dst.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.rotate(Math.toRadians(degrees), w / 2.0, h / 2.0);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return dst;
    }

    private static BufferedImage medianFilter3x3(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w < 3 || h < 3) {
            return src;
        }
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        int[] window = new int[9];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    dst.getRaster().setSample(x, y, 0, src.getRaster().getSample(x, y, 0));
                    continue;
                }
                int idx = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        window[idx++] = src.getRaster().getSample(x + dx, y + dy, 0);
                    }
                }
                Arrays.sort(window);
                dst.getRaster().setSample(x, y, 0, window[4]);
            }
        }
        return dst;
    }

    private static BufferedImage enhanceContrast(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] hist = new int[256];
        int total = w * h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[src.getRaster().getSample(x, y, 0)]++;
            }
        }
        int lowCut = (int) (total * 0.02);
        int highCut = (int) (total * 0.98);
        int minLum = 0;
        int acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= lowCut) {
                minLum = i;
                break;
            }
        }
        int maxLum = 255;
        acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= highCut) {
                maxLum = i;
                break;
            }
        }
        if (maxLum - minLum < 10) {
            return src;
        }
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        double scale = 255.0 / (maxLum - minLum);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = src.getRaster().getSample(x, y, 0);
                int stretched = (int) Math.round((v - minLum) * scale);
                stretched = Math.max(0, Math.min(255, stretched));
                dst.getRaster().setSample(x, y, 0, stretched);
            }
        }
        return dst;
    }
}

