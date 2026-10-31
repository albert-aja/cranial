package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PreprocessOptions(
        @JsonAlias({"rotateDegrees", "rotate_degrees"}) Integer rotateDegrees,
        Boolean deskew,
        Boolean denoise,
        @JsonAlias({"contrastEnhance", "contrast_enhance"}) Boolean contrastEnhance
) {
    public static PreprocessOptions defaults() {
        return new PreprocessOptions(0, true, true, true);
    }

    public int effectiveRotateDegrees() {
        if (rotateDegrees == null) {
            return 0;
        }
        int norm = ((rotateDegrees % 360) + 360) % 360;
        return (norm == 90 || norm == 180 || norm == 270) ? norm : 0;
    }

    public boolean effectiveDeskew() {
        return deskew == null || deskew;
    }

    public boolean effectiveDenoise() {
        return denoise == null || denoise;
    }

    public boolean effectiveContrastEnhance() {
        return contrastEnhance == null || contrastEnhance;
    }
}

