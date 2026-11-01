package io.cranial.platform.conversation.model;

import java.util.List;

public record SlangNormalizeResult(
        String originalText,
        String normalizedText,
        boolean hasSlangDetected,
        int replacementsCount,
        List<NormalizedSlangToken> tokensDetected
) {}

