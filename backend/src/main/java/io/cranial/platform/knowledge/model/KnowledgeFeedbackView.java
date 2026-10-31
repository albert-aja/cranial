package io.cranial.platform.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeFeedbackView(
        UUID id,
        UUID collectionId,
        String collectionCode,
        UUID applicationId,
        UUID tenantId,
        String externalUserRef,
        String questionText,
        String answerText,
        String rating,
        List<String> citedSourceCodes,
        String expectedSourceCode,
        String comment,
        UUID promotedEvalCaseId,
        Instant createdAt
) {
}

