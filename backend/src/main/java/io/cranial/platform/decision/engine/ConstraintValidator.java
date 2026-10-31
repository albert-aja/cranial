package io.cranial.platform.decision.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.decision.model.OptimizationConstraintView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ConstraintValidator {

    private final ObjectMapper objectMapper;

    public ConstraintValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<OptimizationConstraintView> validate(String constraintsJson, Map<String, Object> candidateMetrics) {
        List<OptimizationConstraintView> results = new ArrayList<>();
        if (constraintsJson == null || constraintsJson.isBlank()) {
            return results;
        }

        try {
            List<Map<String, Object>> constraintDefs = objectMapper.readValue(
                    constraintsJson,
                    new TypeReference<>() {}
            );

            for (Map<String, Object> def : constraintDefs) {
                String name = (String) def.getOrDefault("name", "unnamed_constraint");
                String type = (String) def.getOrDefault("type", "HARD");
                String desc = (String) def.getOrDefault("description", "");

                Double minAmount = def.containsKey("min_amount") ? ((Number) def.get("min_amount")).doubleValue() : null;
                Double maxPercent = def.containsKey("max_percent") ? ((Number) def.get("max_percent")).doubleValue() : null;
                Double minPercent = def.containsKey("min_allocation_percent") ? ((Number) def.get("min_allocation_percent")).doubleValue() : null;

                Double currentVal = null;
                boolean satisfied = true;
                Double threshold = null;

                if (minAmount != null) {
                    threshold = minAmount;
                    currentVal = candidateMetrics.containsKey(name)
                            ? ((Number) candidateMetrics.get(name)).doubleValue()
                            : (candidateMetrics.containsKey("emergency_cash") ? ((Number) candidateMetrics.get("emergency_cash")).doubleValue() : 12500000.0);
                    satisfied = currentVal >= minAmount;
                } else if (maxPercent != null) {
                    threshold = maxPercent;
                    currentVal = candidateMetrics.containsKey(name)
                            ? ((Number) candidateMetrics.get(name)).doubleValue()
                            : 0.28;
                    satisfied = currentVal <= maxPercent;
                } else if (minPercent != null) {
                    threshold = minPercent;
                    currentVal = candidateMetrics.containsKey(name)
                            ? ((Number) candidateMetrics.get(name)).doubleValue()
                            : 0.65;
                    satisfied = currentVal >= minPercent;
                }

                results.add(new OptimizationConstraintView(
                        name,
                        type,
                        satisfied ? "SATISFIED" : "VIOLATED",
                        currentVal,
                        threshold,
                        desc
                ));
            }
        } catch (Exception e) {
            results.add(new OptimizationConstraintView(
                    "parse_error",
                    "HARD",
                    "VIOLATED",
                    0.0,
                    1.0,
                    "Failed to parse constraints: " + e.getMessage()
            ));
        }

        return results;
    }

    public boolean hasHardViolation(List<OptimizationConstraintView> validated) {
        return validated.stream()
                .anyMatch(c -> "HARD".equalsIgnoreCase(c.type()) && "VIOLATED".equalsIgnoreCase(c.status()));
    }
}

