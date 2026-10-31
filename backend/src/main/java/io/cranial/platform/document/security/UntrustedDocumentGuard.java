package io.cranial.platform.document.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class UntrustedDocumentGuard {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+|previous\\s+|prior\\s+)?instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("abaikan\\s+(semua\\s+)?instruksi", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s*prompt\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"tool_call\"\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(execute_payment|wire_transfer|initiate_transfer|transfer_dana)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script\\b[^>]*>", Pattern.CASE_INSENSITIVE)
    );

    public record SanitizedDocumentText(
            String sanitizedText,
            boolean injectionSuspected,
            List<String> warnings
    ) {
    }

    public SanitizedDocumentText inspectAndSanitize(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new SanitizedDocumentText("", false, List.of());
        }

        // Strip dangerous control characters while preserving tabs and newlines
        String cleaned = rawText.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        List<String> warnings = new ArrayList<>();
        boolean suspected = false;

        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(cleaned).find()) {
                suspected = true;
                break;
            }
        }

        if (suspected) {
            warnings.add("PROMPT_INJECTION_OR_UNTRUSTED_DIRECTIVE_DETECTED: Document contains instruction-like or prohibited action patterns; treated strictly as inert data.");
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("wire transfer") || lower.contains("execute payment")) {
            warnings.add("BOUNDARY_NOTICE: CRANIAL is a read-only document & money management intelligence layer; payment or banking directives in documents are never executed.");
        }

        return new SanitizedDocumentText(cleaned, suspected, warnings);
    }

    public String wrapAsInertModelData(String sanitizedText) {
        String escaped = sanitizedText.replace("</untrusted_document_data>", "[ESCAPED_TAG]");
        return "<untrusted_document_data>\n" + escaped + "\n</untrusted_document_data>";
    }
}

