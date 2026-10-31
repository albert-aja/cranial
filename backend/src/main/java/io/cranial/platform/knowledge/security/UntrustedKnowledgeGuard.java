package io.cranial.platform.knowledge.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class UntrustedKnowledgeGuard {

    private static final List<NamedPattern> PROMPT_INJECTION_PATTERNS = List.of(
            new NamedPattern("PROMPT_INJECTION_OVERRIDE",
                    Pattern.compile("(?i)(ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions|disregard\\s+system\\s+prompt|abaikan\\s+(semua\\s+)?instruksi\\s+sebelumnya)")),
            new NamedPattern("SYSTEM_ROLE_SPOOF",
                    Pattern.compile("(?i)(you\\s+are\\s+now\\s+in\\s+developer\\s+mode|\\[\\s*system\\s*override\\s*\\]|reveal\\s+your\\s+system\\s+prompt)")),
            new NamedPattern("TOOL_OR_PAYMENT_ABUSE",
                    Pattern.compile("(?i)(execute\\s+tool\\s*:|call_tool\\s*\\(|initiate\\s+wire\\s+transfer|execute\\s+bank\\s+payment|transfer\\s+dana\\s+rekening)"))
    );

    public record SanitizedKnowledgeText(
            String sanitizedText,
            List<String> warnings
    ) {
    }

    public SanitizedKnowledgeText inspectAndSanitize(String rawText, String sourceCode) {
        if (rawText == null || rawText.isBlank()) {
            return new SanitizedKnowledgeText("", List.of());
        }
        String sanitized = rawText;
        List<String> warnings = new ArrayList<>();

        for (NamedPattern rule : PROMPT_INJECTION_PATTERNS) {
            if (rule.pattern().matcher(sanitized).find()) {
                warnings.add("Untrusted directive neutralized [" + rule.code() + "] in source '" +
                        (sourceCode != null ? sourceCode : "unknown") + "'");
                sanitized = rule.pattern().matcher(sanitized).replaceAll("[NEUTRALIZED_UNTRUSTED_DIRECTIVE]");
            }
        }
        return new SanitizedKnowledgeText(sanitized, warnings);
    }

    private record NamedPattern(String code, Pattern pattern) {
    }
}

