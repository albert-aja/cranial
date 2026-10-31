package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record KnowledgeFeedbackRequest(
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @JsonAlias({"collectionCode", "collection_code"}) String collectionCode,
        @NotBlank @JsonAlias({"question", "questionText", "question_text"}) String questionText,
        @JsonAlias({"answer", "answerText", "answer_text"}) String answerText,
        @NotBlank String rating,
        @JsonAlias({"citedSourceCodes", "cited_source_codes"}) List<String> citedSourceCodes,
        @JsonAlias({"expectedSourceCode", "expected_source_code"}) String expectedSourceCode,
        String comment,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId
) {
}

