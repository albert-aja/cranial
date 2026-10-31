package io.cranial.platform.document.ocr;

import io.cranial.platform.document.model.PreprocessOptions;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class DocumentParserService {

    private final ImagePreprocessor imagePreprocessor;
    private final TesseractCliOcrEngine tesseractEngine;
    private final LocalDeterministicOcrEngine fallbackOcrEngine;

    public DocumentParserService(
            ImagePreprocessor imagePreprocessor,
            TesseractCliOcrEngine tesseractEngine,
            LocalDeterministicOcrEngine fallbackOcrEngine
    ) {
        this.imagePreprocessor = imagePreprocessor;
        this.tesseractEngine = tesseractEngine;
        this.fallbackOcrEngine = fallbackOcrEngine;
    }

    public record ParsedPageOutcome(
            int pageNumber,
            String status,
            String engineUsed,
            double confidence,
            String extractedText,
            List<String> preprocessApplied,
            Map<String, Object> diagnostics,
            long durationMs
    ) {
    }

    public record ParsedDocumentResult(
            int totalDocumentPages,
            List<ParsedPageOutcome> pages
    ) {
    }

    public int inspectTotalPages(byte[] bytes, String detectedMimeType) {
        if ("application/pdf".equalsIgnoreCase(detectedMimeType)) {
            try (PDDocument pdf = PDDocument.load(bytes)) {
                return Math.max(1, pdf.getNumberOfPages());
            } catch (Exception ex) {
                throw new CranialException(ErrorCode.DOCUMENT_PROCESSING_FAILED, "Corrupted or unreadable PDF document: " + ex.getMessage());
            }
        }
        if (detectedMimeType != null && detectedMimeType.startsWith("text/")) {
            String raw = new String(bytes, StandardCharsets.UTF_8);
            String[] split = raw.split("(\\f|---\\s*PAGE\\s*BREAK\\s*---)");
            return Math.max(1, split.length);
        }
        return 1;
    }

    public List<Integer> resolveTargetPages(int totalPages, String pageRangeSpec) {
        if (pageRangeSpec == null || pageRangeSpec.isBlank()) {
            List<Integer> all = new ArrayList<>(totalPages);
            for (int i = 1; i <= totalPages; i++) {
                all.add(i);
            }
            return all;
        }
        Set<Integer> selected = new LinkedHashSet<>();
        String[] parts = pageRangeSpec.split(",");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                int start = parsePageNum(bounds[0], totalPages, pageRangeSpec);
                int end = parsePageNum(bounds[1], totalPages, pageRangeSpec);
                if (start > end) {
                    throw new CranialException(ErrorCode.INVALID_PAGE_RANGE, "Invalid page_range '" + pageRangeSpec + "': start > end");
                }
                for (int p = start; p <= end; p++) {
                    selected.add(p);
                }
            } else {
                selected.add(parsePageNum(part, totalPages, pageRangeSpec));
            }
        }
        if (selected.isEmpty()) {
            throw new CranialException(ErrorCode.INVALID_PAGE_RANGE, "Empty page_range specification: " + pageRangeSpec);
        }
        return new ArrayList<>(selected);
    }

    public ParsedDocumentResult parsePages(
            byte[] bytes,
            String detectedMimeType,
            String ocrLanguage,
            String pageRange,
            PreprocessOptions preprocessOptions,
            boolean advancedPreprocessFlag,
            Set<Integer> alreadyCompletedPages,
            Set<Integer> simulateFailPages,
            Consumer<ParsedPageOutcome> pageCheckpointCallback
    ) {
        if ("application/pdf".equalsIgnoreCase(detectedMimeType)) {
            return parsePdf(bytes, ocrLanguage, pageRange, preprocessOptions, advancedPreprocessFlag,
                    alreadyCompletedPages, simulateFailPages, pageCheckpointCallback);
        }
        if (detectedMimeType != null && detectedMimeType.startsWith("image/")) {
            return parseImage(bytes, ocrLanguage, pageRange, preprocessOptions, advancedPreprocessFlag,
                    alreadyCompletedPages, simulateFailPages, pageCheckpointCallback);
        }
        return parseTextDocument(bytes, detectedMimeType, pageRange,
                alreadyCompletedPages, simulateFailPages, pageCheckpointCallback);
    }

    private ParsedDocumentResult parsePdf(
            byte[] bytes,
            String ocrLanguage,
            String pageRange,
            PreprocessOptions preprocessOptions,
            boolean advancedPreprocessFlag,
            Set<Integer> alreadyCompletedPages,
            Set<Integer> simulateFailPages,
            Consumer<ParsedPageOutcome> callback
    ) {
        try (PDDocument pdf = PDDocument.load(bytes)) {
            int totalPages = Math.max(1, pdf.getNumberOfPages());
            List<Integer> targetPages = resolveTargetPages(totalPages, pageRange);
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<ParsedPageOutcome> results = new ArrayList<>();

            for (int pageNum : targetPages) {
                if (alreadyCompletedPages != null && alreadyCompletedPages.contains(pageNum)) {
                    continue;
                }
                long pageStart = System.currentTimeMillis();
                try {
                    if (simulateFailPages != null && simulateFailPages.contains(pageNum)) {
                        throw new IllegalStateException("Simulated page extraction failure on page " + pageNum);
                    }
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    String nativeText = stripper.getText(pdf);
                    String trimmed = nativeText != null ? nativeText.trim() : "";

                    ParsedPageOutcome outcome;
                    if (trimmed.length() >= 10) {
                        long dur = Math.max(1L, System.currentTimeMillis() - pageStart);
                        Map<String, Object> diag = new LinkedHashMap<>();
                        diag.put("extraction_mode", "pdfbox_embedded_text");
                        diag.put("ocr_fallback_needed", false);
                        outcome = new ParsedPageOutcome(
                                pageNum,
                                "SUCCESS",
                                "pdfbox_native",
                                0.98,
                                trimmed,
                                List.of(),
                                diag,
                                dur
                        );
                    } else {
                        BufferedImage rendered = renderer.renderImageWithDPI(pageNum - 1, 150f, ImageType.RGB);
                        ImagePreprocessor.PreprocessResult prep = imagePreprocessor.preprocess(rendered, preprocessOptions, advancedPreprocessFlag);
                        OcrPageResult ocrRes = runOcrWithFallback(prep.image(), null, ocrLanguage);
                        Map<String, Object> diag = new LinkedHashMap<>(prep.diagnostics());
                        diag.putAll(ocrRes.diagnostics());
                        diag.put("extraction_mode", "pdf_rasterized_ocr");
                        diag.put("render_dpi", 150);
                        String combinedText = !ocrRes.text().isBlank() ? ocrRes.text() : trimmed;
                        long dur = Math.max(1L, System.currentTimeMillis() - pageStart);
                        outcome = new ParsedPageOutcome(
                                pageNum,
                                "SUCCESS",
                                ocrRes.engineUsed(),
                                ocrRes.confidence(),
                                combinedText,
                                prep.appliedSteps(),
                                diag,
                                dur
                        );
                    }
                    results.add(outcome);
                    if (callback != null) {
                        callback.accept(outcome);
                    }
                } catch (Exception pageEx) {
                    long dur = Math.max(1L, System.currentTimeMillis() - pageStart);
                    Map<String, Object> errDiag = new LinkedHashMap<>();
                    errDiag.put("error", pageEx.getMessage());
                    ParsedPageOutcome failedOutcome = new ParsedPageOutcome(
                            pageNum,
                            "FAILED",
                            "pdfbox_error",
                            0.0,
                            "",
                            List.of(),
                            errDiag,
                            dur
                    );
                    results.add(failedOutcome);
                    if (callback != null) {
                        callback.accept(failedOutcome);
                    }
                }
            }
            return new ParsedDocumentResult(totalPages, results);
        } catch (CranialException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.DOCUMENT_PROCESSING_FAILED, "Failed to parse PDF document: " + ex.getMessage());
        }
    }

    private ParsedDocumentResult parseImage(
            byte[] bytes,
            String ocrLanguage,
            String pageRange,
            PreprocessOptions preprocessOptions,
            boolean advancedPreprocessFlag,
            Set<Integer> alreadyCompletedPages,
            Set<Integer> simulateFailPages,
            Consumer<ParsedPageOutcome> callback
    ) {
        resolveTargetPages(1, pageRange);
        if (alreadyCompletedPages != null && alreadyCompletedPages.contains(1)) {
            return new ParsedDocumentResult(1, List.of());
        }
        long start = System.currentTimeMillis();
        try {
            if (simulateFailPages != null && simulateFailPages.contains(1)) {
                throw new IllegalStateException("Simulated image OCR failure on page 1");
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new CranialException(ErrorCode.DOCUMENT_PROCESSING_FAILED, "Image payload could not be decoded");
            }
            ImagePreprocessor.PreprocessResult prep = imagePreprocessor.preprocess(img, preprocessOptions, advancedPreprocessFlag);
            OcrPageResult ocrRes = runOcrWithFallback(prep.image(), bytes, ocrLanguage);
            Map<String, Object> diag = new LinkedHashMap<>(prep.diagnostics());
            diag.putAll(ocrRes.diagnostics());
            long dur = Math.max(1L, System.currentTimeMillis() - start);
            ParsedPageOutcome outcome = new ParsedPageOutcome(
                    1,
                    "SUCCESS",
                    ocrRes.engineUsed(),
                    ocrRes.confidence(),
                    ocrRes.text(),
                    prep.appliedSteps(),
                    diag,
                    dur
            );
            if (callback != null) {
                callback.accept(outcome);
            }
            return new ParsedDocumentResult(1, List.of(outcome));
        } catch (CranialException ex) {
            throw ex;
        } catch (Exception ex) {
            long dur = Math.max(1L, System.currentTimeMillis() - start);
            ParsedPageOutcome failed = new ParsedPageOutcome(
                    1,
                    "FAILED",
                    "image_ocr_error",
                    0.0,
                    "",
                    List.of(),
                    Map.of("error", ex.getMessage()),
                    dur
            );
            if (callback != null) {
                callback.accept(failed);
            }
            return new ParsedDocumentResult(1, List.of(failed));
        }
    }

    private ParsedDocumentResult parseTextDocument(
            byte[] bytes,
            String detectedMimeType,
            String pageRange,
            Set<Integer> alreadyCompletedPages,
            Set<Integer> simulateFailPages,
            Consumer<ParsedPageOutcome> callback
    ) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        if ("text/html".equalsIgnoreCase(detectedMimeType)) {
            raw = raw.replaceAll("(?is)<script.*?>.*?</script>", "")
                    .replaceAll("(?is)<style.*?>.*?</style>", "")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("[ \\t]+", " ");
        }
        String[] rawPages = raw.split("(\\f|---\\s*PAGE\\s*BREAK\\s*---)");
        int totalPages = Math.max(1, rawPages.length);
        List<Integer> targetPages = resolveTargetPages(totalPages, pageRange);
        List<ParsedPageOutcome> outcomes = new ArrayList<>();

        for (int pageNum : targetPages) {
            if (alreadyCompletedPages != null && alreadyCompletedPages.contains(pageNum)) {
                continue;
            }
            long start = System.currentTimeMillis();
            if (simulateFailPages != null && simulateFailPages.contains(pageNum)) {
                ParsedPageOutcome failed = new ParsedPageOutcome(
                        pageNum,
                        "FAILED",
                        "tika_text_parser",
                        0.0,
                        "",
                        List.of(),
                        Map.of("error", "Simulated failure on page " + pageNum),
                        1L
                );
                outcomes.add(failed);
                if (callback != null) {
                    callback.accept(failed);
                }
                continue;
            }
            String pageText = pageNum <= rawPages.length ? rawPages[pageNum - 1].trim() : "";
            long dur = Math.max(1L, System.currentTimeMillis() - start);
            ParsedPageOutcome ok = new ParsedPageOutcome(
                    pageNum,
                    "SUCCESS",
                    "tika_text_parser",
                    0.99,
                    pageText,
                    List.of(),
                    Map.of("mime_type", detectedMimeType),
                    dur
            );
            outcomes.add(ok);
            if (callback != null) {
                callback.accept(ok);
            }
        }
        return new ParsedDocumentResult(totalPages, outcomes);
    }

    private OcrPageResult runOcrWithFallback(BufferedImage image, byte[] rawBytes, String ocrLanguage) {
        if (tesseractEngine.isAvailable()) {
            try {
                return tesseractEngine.recognize(image, rawBytes, ocrLanguage);
            } catch (Exception ignored) {
                // Fall back to deterministic local OCR
            }
        }
        return fallbackOcrEngine.recognize(image, rawBytes, ocrLanguage);
    }

    private static int parsePageNum(String token, int totalPages, String spec) {
        try {
            int p = Integer.parseInt(token.trim());
            if (p < 1 || p > totalPages) {
                throw new CranialException(
                        ErrorCode.INVALID_PAGE_RANGE,
                        "Page " + p + " in page_range '" + spec + "' is outside document bounds (1.." + totalPages + ")"
                );
            }
            return p;
        } catch (NumberFormatException ex) {
            throw new CranialException(ErrorCode.INVALID_PAGE_RANGE, "Malformed page number in page_range: " + spec);
        }
    }
}

