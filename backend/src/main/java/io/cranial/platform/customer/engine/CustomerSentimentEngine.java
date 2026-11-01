package io.cranial.platform.customer.engine;

import io.cranial.platform.customer.model.CustomerSentiment;
import io.cranial.platform.customer.model.CustomerUrgency;
import io.cranial.platform.customer.model.SentimentAspect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CustomerSentimentEngine {

    private static final List<String> POSITIVE_KEYWORDS_EN = List.of(
            "love", "great", "fantastic", "helpful", "clean", "smooth", "save", "saved", "savings",
            "excellent", "awesome", "perfect", "easy", "satisfied", "recommend", "best"
    );

    private static final List<String> POSITIVE_KEYWORDS_ID = List.of(
            "keren", "mantap", "bagus", "membantu", "suka", "hemat", "rapi", "puas", "bintang 5",
            "praktis", "berguna", "mudah", "terbaik", "senang", "mantul"
    );

    private static final List<String> NEGATIVE_KEYWORDS_EN = List.of(
            "freeze", "freezing", "crash", "crashes", "broken", "unacceptable", "frustrating",
            "frustrated", "terrible", "lost", "disappeared", "horrible", "unusable", "worst",
            "scam", "angry", "annoying", "hate", "slow", "buggy"
    );

    private static final List<String> NEGATIVE_KEYWORDS_ID = List.of(
            "kecewa", "ribet", "lemot", "lambat", "hancur", "rusak", "parah", "membingungkan", "rugi",
            "ampas", "jelek", "error", "hilang", "emosi", "kesal", "sulit", "payah", "buruk", "gagal"
    );

    private static final List<String> URGENT_TRIGGERS = List.of(
            "immediately", "unacceptable", "urgent", "asap", "disappeared", "lost money", "police",
            "sekarang juga", "detik ini", "lapor polisi", "parah sekali", "darurat", "hilang semua"
    );

    public record SentimentAnalysisOutput(
            CustomerSentiment sentiment,
            double score,
            double confidence,
            CustomerUrgency urgency,
            List<SentimentAspect> aspects
    ) {}

    public SentimentAnalysisOutput analyze(String text) {
        if (text == null || text.isBlank()) {
            return new SentimentAnalysisOutput(CustomerSentiment.NEUTRAL, 0.0, 0.90, CustomerUrgency.LOW, List.of());
        }

        String lower = text.toLowerCase(Locale.ROOT);

        int posCount = countMatches(lower, POSITIVE_KEYWORDS_EN) + countMatches(lower, POSITIVE_KEYWORDS_ID);
        int negCount = countMatches(lower, NEGATIVE_KEYWORDS_EN) + countMatches(lower, NEGATIVE_KEYWORDS_ID);

        double score;
        CustomerSentiment sentiment;

        if (posCount > 0 && negCount > 0) {
            score = (posCount - negCount) / (double) (posCount + negCount);
            sentiment = Math.abs(score) < 0.25 ? CustomerSentiment.MIXED : (score > 0 ? CustomerSentiment.POSITIVE : CustomerSentiment.NEGATIVE);
        } else if (posCount > 0) {
            score = Math.min(1.0, 0.35 + (posCount * 0.25));
            sentiment = CustomerSentiment.POSITIVE;
        } else if (negCount > 0) {
            score = Math.max(-1.0, -0.35 - (negCount * 0.25));
            sentiment = CustomerSentiment.NEGATIVE;
        } else {
            score = 0.0;
            sentiment = CustomerSentiment.NEUTRAL;
        }

        // Urgency Detection
        boolean hasUrgentTrigger = URGENT_TRIGGERS.stream().anyMatch(lower::contains);
        CustomerUrgency urgency;
        if (hasUrgentTrigger && score <= -0.5) {
            urgency = CustomerUrgency.CRITICAL;
        } else if (hasUrgentTrigger || score <= -0.4) {
            urgency = CustomerUrgency.HIGH;
        } else if (score < 0.0) {
            urgency = CustomerUrgency.NORMAL;
        } else {
            urgency = CustomerUrgency.LOW;
        }

        double confidence = Math.min(0.98, 0.82 + (Math.abs(score) * 0.15));
        List<SentimentAspect> aspects = extractAspects(lower, sentiment);

        return new SentimentAnalysisOutput(sentiment, Math.round(score * 10000.0) / 10000.0, Math.round(confidence * 10000.0) / 10000.0, urgency, aspects);
    }

    private List<SentimentAspect> extractAspects(String text, CustomerSentiment overall) {
        List<SentimentAspect> aspects = new ArrayList<>();

        if (text.contains("budget") || text.contains("50-30-20") || text.contains("dana darurat") || text.contains("tabungan") || text.contains("pos")) {
            aspects.add(new SentimentAspect("BUDGETING", overall, "Budgeting and pocket allocation experience"));
        }
        if (text.contains("scan") || text.contains("receipt") || text.contains("struk") || text.contains("expense") || text.contains("pengeluaran")) {
            aspects.add(new SentimentAspect("EXPENSE_TRACKING", overall, "Receipt OCR and transaction logging"));
        }
        if (text.contains("freeze") || text.contains("crash") || text.contains("force close") || text.contains("lemot") || text.contains("lambat") || text.contains("slow") || text.contains("sync")) {
            aspects.add(new SentimentAspect("PERFORMANCE_SYNC", overall, "Application responsiveness and synchronization"));
        }
        if (text.contains("login") || text.contains("password") || text.contains("pin") || text.contains("keamanan")) {
            aspects.add(new SentimentAspect("ACCOUNT_SECURITY", overall, "Authentication and account access security"));
        }
        if (text.contains("clean") || text.contains("design") || text.contains("interface") || text.contains("dark mode") || text.contains("rapi")) {
            aspects.add(new SentimentAspect("UI_UX", overall, "User interface and visual experience"));
        }
        if (text.contains("bot") || text.contains("human") || text.contains("agent") || text.contains("bantuan") || text.contains("cs")) {
            aspects.add(new SentimentAspect("CUSTOMER_SUPPORT", overall, "Customer service and guidance"));
        }

        return aspects;
    }

    private int countMatches(String text, List<String> keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) count++;
        }
        return count;
    }
}

