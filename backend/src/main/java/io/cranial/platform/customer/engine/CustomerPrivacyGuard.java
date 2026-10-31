package io.cranial.platform.customer.engine;

import io.cranial.platform.customer.model.PiiEntityView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CustomerPrivacyGuard {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[ -]?){3}\\d{4}\\b"
    );

    private static final Pattern INDONESIAN_NIK_PATTERN = Pattern.compile(
            "\\b[1-9]\\d{15}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:(?:\\+62|62|0)8[1-9][0-9]{7,10}|\\+?[1-9]\\d{1,14})\\b"
    );

    public record RedactionResult(
            String redactedText,
            boolean piiDetected,
            List<PiiEntityView> piiEntities
    ) {}

    public RedactionResult sanitize(String input) {
        if (input == null || input.isBlank()) {
            return new RedactionResult(input, false, List.of());
        }

        List<PiiEntityView> entities = new ArrayList<>();
        String processed = input;

        // 1. Email Redaction
        Matcher emailMatcher = EMAIL_PATTERN.matcher(processed);
        StringBuilder sb = new StringBuilder();
        int lastIndex = 0;
        while (emailMatcher.find()) {
            String full = emailMatcher.group();
            String masked = maskEmail(full);
            entities.add(new PiiEntityView("EMAIL", masked, emailMatcher.start(), emailMatcher.end()));
            sb.append(processed, lastIndex, emailMatcher.start()).append("[EMAIL: ").append(masked).append("]");
            lastIndex = emailMatcher.end();
        }
        sb.append(processed.substring(lastIndex));
        processed = sb.toString();

        // 2. Card Number Redaction
        Matcher cardMatcher = CARD_PATTERN.matcher(processed);
        sb = new StringBuilder();
        lastIndex = 0;
        while (cardMatcher.find()) {
            String full = cardMatcher.group().replaceAll("[ -]", "");
            if (full.length() >= 13 && full.length() <= 19) {
                String masked = maskCard(cardMatcher.group());
                entities.add(new PiiEntityView("CREDIT_CARD", masked, cardMatcher.start(), cardMatcher.end()));
                sb.append(processed, lastIndex, cardMatcher.start()).append("[CARD: ").append(masked).append("]");
                lastIndex = cardMatcher.end();
            }
        }
        sb.append(processed.substring(lastIndex));
        processed = sb.toString();

        // 3. Indonesian NIK Redaction
        Matcher nikMatcher = INDONESIAN_NIK_PATTERN.matcher(processed);
        sb = new StringBuilder();
        lastIndex = 0;
        while (nikMatcher.find()) {
            String full = nikMatcher.group();
            String masked = full.substring(0, 6) + "******" + full.substring(12);
            entities.add(new PiiEntityView("ID_NIK", masked, nikMatcher.start(), nikMatcher.end()));
            sb.append(processed, lastIndex, nikMatcher.start()).append("[NIK: ").append(masked).append("]");
            lastIndex = nikMatcher.end();
        }
        sb.append(processed.substring(lastIndex));
        processed = sb.toString();

        // 4. Phone Number Redaction
        Matcher phoneMatcher = PHONE_PATTERN.matcher(processed);
        sb = new StringBuilder();
        lastIndex = 0;
        while (phoneMatcher.find()) {
            String full = phoneMatcher.group();
            if (full.length() >= 9) {
                String masked = maskPhone(full);
                entities.add(new PiiEntityView("PHONE_NUMBER", masked, phoneMatcher.start(), phoneMatcher.end()));
                sb.append(processed, lastIndex, phoneMatcher.start()).append("[PHONE: ").append(masked).append("]");
                lastIndex = phoneMatcher.end();
            }
        }
        sb.append(processed.substring(lastIndex));
        processed = sb.toString();

        return new RedactionResult(processed, !entities.isEmpty(), entities);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskCard(String card) {
        String digitsOnly = card.replaceAll("[ -]", "");
        if (digitsOnly.length() < 8) return "****";
        return digitsOnly.substring(0, 4) + "-****-****-" + digitsOnly.substring(digitsOnly.length() - 4);
    }

    private String maskPhone(String phone) {
        if (phone.length() <= 5) return "****";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}

