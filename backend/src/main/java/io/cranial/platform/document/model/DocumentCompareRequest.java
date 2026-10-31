package io.cranial.platform.document.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentCompareRequest(
        @NotNull @JsonAlias({"leftDocumentId", "left_document_id"}) UUID leftDocumentId,
        @NotNull @JsonAlias({"rightDocumentId", "right_document_id"}) UUID rightDocumentId
) {
}

