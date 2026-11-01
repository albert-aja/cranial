package io.cranial.platform.conversation.engine;

import io.cranial.platform.conversation.model.NormalizedSlangToken;
import io.cranial.platform.conversation.model.SlangNormalizeResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SlangNormalizerEngine {

    private static final Map<Pattern, SlangRule> RULES = new LinkedHashMap<>();

    private record SlangRule(String replacement, String category, String explanation) {}

    static {
        // Hokkien / Indonesian colloquial currency terms
        addRule("(?i)\\bgotiau\\b", "Rp 50.000.000", "CURRENCY_SLANG", "Colloquial term for fifty million rupiah (50.000.000 IDR)");
        addRule("(?i)\\bpekgo\\b", "Rp 150.000", "CURRENCY_SLANG", "Colloquial term for one hundred fifty thousand rupiah (150.000 IDR)");
        addRule("(?i)\\bgoban\\b", "Rp 50.000", "CURRENCY_SLANG", "Colloquial term for fifty thousand rupiah (50.000 IDR)");
        addRule("(?i)\\bgocap\\b", "Rp 50.000", "CURRENCY_SLANG", "Colloquial term for fifty thousand rupiah (50.000 IDR)");
        addRule("(?i)\\bceban\\b", "Rp 10.000", "CURRENCY_SLANG", "Colloquial term for ten thousand rupiah (10.000 IDR)");
        addRule("(?i)\\bnoceng\\b", "Rp 2.000", "CURRENCY_SLANG", "Colloquial term for two thousand rupiah (2.000 IDR)");
        addRule("(?i)\\bseceng\\b", "Rp 1.000", "CURRENCY_SLANG", "Colloquial term for one thousand rupiah (1.000 IDR)");
        addRule("(?i)\\bpego\\b", "Rp 150", "CURRENCY_SLANG", "Colloquial term for one hundred fifty rupiah (150 IDR)");
        addRule("(?i)\\bcepek\\b", "Rp 100", "CURRENCY_SLANG", "Colloquial term for one hundred rupiah (100 IDR)");

        // Suffix abbreviations (e.g. 8jt -> Rp 8.000.000, 50k -> Rp 50.000)
        addRule("(?i)\\b(\\d+)\\s*(?:jt|juta)\\b", "Rp $1.000.000", "NUMERICAL_ABBREVIATION", "Abbreviated 'juta' (million) expanded to standard IDR format");
        addRule("(?i)\\b(\\d+)\\s*(?:rb|rebu|ribu|k)\\b", "Rp $1.000", "NUMERICAL_ABBREVIATION", "Abbreviated 'ribu/k' (thousand) expanded to standard IDR format");

        // Informal Indonesian financial & conversational colloquialisms
        addRule("(?i)\\bbalikin duit\\b", "pengembalian dana (refund)", "COLLOQUIALISM", "Informal customer phrasing for requesting financial refund");
        addRule("(?i)\\bbatalin\\b", "batalkan", "COLLOQUIALISM", "Informal Indonesian word for cancel");
        addRule("(?i)\\bboncos\\b", "pemborosan/rugi", "FINANCIAL_SLANG", "Indonesian financial slang for budget deficit or losing money");
        addRule("(?i)\\bbokek\\b", "saldo menipis", "FINANCIAL_SLANG", "Indonesian slang for being broke or having critically low balance");
        addRule("(?i)\\bduit\\b", "uang", "COLLOQUIALISM", "Common informal term for money");
        addRule("(?i)\\bgajian\\b", "penghasilan bulanan", "FINANCIAL_SLANG", "Informal term for monthly salary/income");
        addRule("(?i)\\bnabung\\b", "menabung", "COLLOQUIALISM", "Informal Indonesian word for saving money");
        addRule("(?i)\\bsisa duit\\b", "sisa surplus anggaran", "FINANCIAL_SLANG", "Informal term for remaining budget surplus");
    }

    private static void addRule(String regex, String replacement, String category, String explanation) {
        RULES.put(Pattern.compile(regex), new SlangRule(replacement, category, explanation));
    }

    public SlangNormalizeResult normalize(String input) {
        if (input == null || input.isBlank()) {
            return new SlangNormalizeResult("", "", false, 0, Collections.emptyList());
        }

        String currentText = input;
        List<NormalizedSlangToken> detectedTokens = new ArrayList<>();
        int count = 0;

        for (Map.Entry<Pattern, SlangRule> entry : RULES.entrySet()) {
            Pattern pattern = entry.getKey();
            SlangRule rule = entry.getValue();
            Matcher matcher = pattern.matcher(currentText);

            boolean matched = false;
            while (matcher.find()) {
                matched = true;
                count++;
                String original = matcher.group();
                
                detectedTokens.add(new NormalizedSlangToken(
                        original,
                        rule.replacement,
                        rule.category,
                        rule.explanation
                ));
            }

            if (matched) {
                currentText = pattern.matcher(currentText).replaceAll(rule.replacement);
            }
        }

        return new SlangNormalizeResult(
                input,
                currentText,
                !detectedTokens.isEmpty(),
                count,
                detectedTokens
        );
    }
}
