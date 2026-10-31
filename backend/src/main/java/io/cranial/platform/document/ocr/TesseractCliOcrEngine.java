package io.cranial.platform.document.ocr;

import io.cranial.platform.document.config.DocumentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class TesseractCliOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractCliOcrEngine.class);
    private static final Pattern SAFE_LANG_PATTERN = Pattern.compile("^[a-z]{3}(\\+[a-z]{3})*$");

    private final DocumentProperties properties;
    private volatile Boolean cachedAvailability;

    public TesseractCliOcrEngine(DocumentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String engineCode() {
        return "tesseract_cli";
    }

    @Override
    public boolean isAvailable() {
        if (cachedAvailability != null) {
            return cachedAvailability;
        }
        try {
            Process proc = new ProcessBuilder(properties.effectiveTesseractCommand(), "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = proc.waitFor(3, TimeUnit.SECONDS);
            boolean ok = finished && proc.exitValue() == 0;
            cachedAvailability = ok;
            return ok;
        } catch (Exception ex) {
            cachedAvailability = false;
            return false;
        }
    }

    @Override
    public OcrPageResult recognize(BufferedImage preprocessedImage, byte[] rawSourceBytes, String ocrLanguage) {
        String lang = normalizeLang(ocrLanguage);
        Path tempImg = null;
        try {
            tempImg = Files.createTempFile("cranial-ocr-", ".png");
            ImageIO.write(preprocessedImage, "png", tempImg.toFile());

            Process proc = new ProcessBuilder(
                    properties.effectiveTesseractCommand(),
                    tempImg.toAbsolutePath().toString(),
                    "stdout",
                    "-l",
                    lang,
                    "tsv"
            ).start();

            byte[] stdout = proc.getInputStream().readAllBytes();
            boolean finished = proc.waitFor(properties.effectiveTesseractTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new IllegalStateException("Tesseract OCR timed out");
            }
            if (proc.exitValue() != 0) {
                // Retry fallback to eng if ind tessdata is missing on host
                if (!"eng".equals(lang)) {
                    return recognize(preprocessedImage, rawSourceBytes, "eng");
                }
                throw new IllegalStateException("Tesseract exited with status " + proc.exitValue());
            }

            String tsv = new String(stdout, StandardCharsets.UTF_8);
            return parseTesseractTsv(tsv, lang);
        } catch (Exception ex) {
            log.warn("Tesseract CLI OCR failed: {}", ex.getMessage());
            throw new IllegalStateException("Tesseract OCR execution failed: " + ex.getMessage(), ex);
        } finally {
            if (tempImg != null) {
                try {
                    Files.deleteIfExists(tempImg);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private OcrPageResult parseTesseractTsv(String tsv, String lang) {
        String[] lines = tsv.split("\\R");
        StringBuilder textBuilder = new StringBuilder();
        double confSum = 0.0;
        int wordCount = 0;
        int lastLineNum = -1;
        int lastBlockNum = -1;

        for (int i = 1; i < lines.length; i++) {
            String[] cols = lines[i].split("\t", -1);
            if (cols.length < 12) {
                continue;
            }
            int level = parseIntSafe(cols[0], 0);
            int blockNum = parseIntSafe(cols[2], 0);
            int lineNum = parseIntSafe(cols[4], 0);
            double conf = parseDoubleSafe(cols[10], -1.0);
            String token = cols[11];

            if (level == 5 && !token.isBlank()) {
                if (lastLineNum != -1 && (lineNum != lastLineNum || blockNum != lastBlockNum)) {
                    textBuilder.append('\n');
                } else if (!textBuilder.isEmpty() && textBuilder.charAt(textBuilder.length() - 1) != '\n') {
                    textBuilder.append(' ');
                }
                textBuilder.append(token);
                lastLineNum = lineNum;
                lastBlockNum = blockNum;
                if (conf >= 0) {
                    confSum += (conf / 100.0);
                    wordCount++;
                }
            }
        }

        double avgConf = wordCount > 0 ? Math.min(1.0, Math.max(0.0, confSum / wordCount)) : 0.45;
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("ocr_language", lang);
        diag.put("recognized_words", wordCount);
        diag.put("engine_mode", "tesseract_tsv");
        return new OcrPageResult(textBuilder.toString().trim(), avgConf, engineCode(), diag);
    }

    private static String normalizeLang(String raw) {
        if (raw == null || raw.isBlank()) {
            return "eng+ind";
        }
        String clean = raw.trim().toLowerCase();
        return SAFE_LANG_PATTERN.matcher(clean).matches() ? clean : "eng+ind";
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static double parseDoubleSafe(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}

