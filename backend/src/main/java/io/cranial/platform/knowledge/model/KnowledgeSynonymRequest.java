package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record KnowledgeSynonymRequest(
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        @NotBlank @JsonAlias({"canonicalTerm", "canonical_term"}) String canonicalTerm,
        @NotEmpty List<String> synonyms,
        @JsonAlias({"localePair", "locale_pair"}) String localePair,
        @JsonAlias({"domainTag", "domain_tag"}) String domainTag
) {
}

