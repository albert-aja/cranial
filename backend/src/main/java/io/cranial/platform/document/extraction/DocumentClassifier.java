package io.cranial.platform.document.extraction;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DocumentClassifier {

    public record ClassificationResult(
            String documentType,
            double confidence,
            List<String> matchedSignals
    ) {
    }

    private static final Map<String, List<String>> TYPE_SIGNALS = new LinkedHashMap<>();

    static {
        TYPE_SIGNALS.put("INVOICE", List.of(
                "faktur pembelian", "faktur", "invoice number", "no invoice", "due date",
                "jatuh tempo", "vendor:", "purchase order", "tagihan", "invoice date"
        ));
        TYPE_SIGNALS.put("DELIVERY_NOTE", List.of(
                "surat jalan", "delivery note", "proof of delivery", "bukti pengiriman",
                "recipient:", "penerima:", "sender:", "pengirim:", "delivery date", "total quantity"
        ));
        TYPE_SIGNALS.put("FORM", List.of(
                "application form", "formulir", "applicant name", "nama pemohon",
                "reference number", "submission date", "tanggal pengajuan", "inspection form"
        ));
        TYPE_SIGNALS.put("RECEIPT", List.of(
                "no struk", "struk", "receipt #", "receipt", "tunai", "kembalian",
                "subtotal", "ppn", "toko ", "mart", "roasters", "cafe", "warung"
        ));
    }

    public ClassificationResult classify(String fullText, String documentTypeHint) {
        if (documentTypeHint != null && !documentTypeHint.isBlank() && !"AUTO".equalsIgnoreCase(documentTypeHint)) {
            String normalizedHint = documentTypeHint.trim().toUpperCase(Locale.ROOT);
            return new ClassificationResult(normalizedHint, 0.99, List.of("explicit_hint:" + normalizedHint));
        }

        if (fullText == null || fullText.isBlank()) {
            return new ClassificationResult("UNKNOWN", 0.0, List.of());
        }

        String lower = fullText.toLowerCase(Locale.ROOT);
        String bestType = "GENERAL_DOCUMENT";
        int bestScore = 0;
        List<String> bestSignals = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : TYPE_SIGNALS.entrySet()) {
            String candidateType = entry.getKey();
            int score = 0;
            List<String> matched = new ArrayList<>();
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    score += kw.contains(" ") ? 3 : 2;
                    matched.add(kw);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestType = candidateType;
                bestSignals = matched;
            }
        }

        if (bestScore == 0) {
            return new ClassificationResult("GENERAL_DOCUMENT", 0.55, List.of("fallback_general_text"));
        }

        double confidence = Math.min(0.98, 0.65 + (bestScore * 0.05));
        return new ClassificationResult(bestType, Math.round(confidence * 1000.0) / 1000.0, bestSignals);
    }
}

