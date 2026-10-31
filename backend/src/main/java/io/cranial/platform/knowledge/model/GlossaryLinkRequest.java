package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record GlossaryLinkRequest(
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @JsonAlias({"collectionCode", "collection_code"}) String collectionCode,
        @NotBlank String text,
        String locale
) {
}

