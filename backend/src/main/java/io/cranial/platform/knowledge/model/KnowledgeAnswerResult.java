package io.cranial.platform.knowledge.model;

import java.util.List;

public record KnowledgeAnswerResult(
        String question,
        String answer,
        boolean abstained,
        String abstentionReason,
        String modelUsed,
        List<SourceCitation> citations,
        List<GlossaryLinkResult.LinkedTermOccurrence> glossaryLinks,
        List<String> staleWarnings,
        List<String> securityWarnings,
        long durationMs,
        RetrievalDebugTrace debugTrace
) {
}

