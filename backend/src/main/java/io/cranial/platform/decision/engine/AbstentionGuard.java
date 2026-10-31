package io.cranial.platform.decision.engine;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AbstentionGuard {

    private static final double CONFIDENCE_ABSTENTION_THRESHOLD = 0.65;

    public record AbstentionDecision(
            boolean abstained,
            String reason
    ) {
        public static AbstentionDecision proceed() {
            return new AbstentionDecision(false, null);
        }

        public static AbstentionDecision abstain(String reason) {
            return new AbstentionDecision(true, reason);
        }
    }

    public AbstentionDecision evaluate(
            Instant sourceFreshnessAt,
            int freshnessTtlHours,
            double confidenceScore,
            boolean hasHardConstraintViolation
    ) {
        // 1. Check Stale Data Guardrail
        if (sourceFreshnessAt != null) {
            Duration age = Duration.between(sourceFreshnessAt, Instant.now());
            if (age.toHours() > freshnessTtlHours) {
                return AbstentionDecision.abstain(
                        "Source data freshness is " + age.toHours() + " hours old, exceeding maximum freshness TTL of "
                                + freshnessTtlHours + " hours. Optimization abstained to prevent stale decisions."
                );
            }
        }

        // 2. Check Hard Constraint Violations
        if (hasHardConstraintViolation) {
            return AbstentionDecision.abstain(
                    "Hard optimization constraint violated (e.g. emergency fund floor breached). System abstained from consequential action."
            );
        }

        // 3. Check Confidence Threshold
        if (confidenceScore < CONFIDENCE_ABSTENTION_THRESHOLD) {
            return AbstentionDecision.abstain(
                    "Recommendation confidence score (" + String.format("%.2f", confidenceScore)
                            + ") is below minimum safety threshold of " + CONFIDENCE_ABSTENTION_THRESHOLD
                            + ". Human operator intervention required."
            );
        }

        return AbstentionDecision.proceed();
    }
}

