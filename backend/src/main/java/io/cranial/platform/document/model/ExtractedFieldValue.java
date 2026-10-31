package io.cranial.platform.document.model;

public record ExtractedFieldValue(
        Object value,
        String status,
        double confidence,
        FieldProvenance provenance
) {
    public static ExtractedFieldValue extracted(Object value, double confidence, FieldProvenance provenance) {
        return new ExtractedFieldValue(value, "EXTRACTED", round(confidence), provenance);
    }

    public static ExtractedFieldValue inferred(Object value, double confidence, FieldProvenance provenance) {
        return new ExtractedFieldValue(value, "INFERRED", round(confidence), provenance);
    }

    public static ExtractedFieldValue uncertain(Object value, double confidence, FieldProvenance provenance) {
        return new ExtractedFieldValue(value, "UNCERTAIN", round(confidence), provenance);
    }

    public static ExtractedFieldValue missing() {
        return new ExtractedFieldValue(null, "MISSING", 0.0, null);
    }

    private static double round(double val) {
        return Math.round(Math.max(0.0, Math.min(1.0, val)) * 1000.0) / 1000.0;
    }
}

