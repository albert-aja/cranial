package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record GlossaryTermRequest(
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @NotBlank String term,
        List<String> aliases,
        String locale,
        String category,
        @NotBlank @JsonAlias({"shortDefinition", "short_definition"}) String shortDefinition,
        @JsonAlias({"relatedTerms", "related_terms"}) List<String> relatedTerms
) {
}

