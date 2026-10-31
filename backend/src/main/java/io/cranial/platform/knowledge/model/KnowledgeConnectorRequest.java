package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record KnowledgeConnectorRequest(
        @NotBlank @JsonAlias({"connectorCode", "connector_code"}) String connectorCode,
        @NotNull @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @NotBlank String name,
        @JsonAlias({"connectorType", "connector_type"}) String connectorType,
        @NotBlank @JsonAlias({"feedUri", "feed_uri"}) String feedUri,
        @NotBlank @JsonAlias({"targetSourceCode", "target_source_code"}) String targetSourceCode,
        @JsonAlias({"contentType", "content_type"}) String contentType,
        @JsonAlias({"permissionScope", "permission_scope"}) String permissionScope,
        @JsonAlias({"allowedRoles", "allowed_roles"}) List<String> allowedRoles,
        @JsonAlias({"pollIntervalMinutes", "poll_interval_minutes"}) Integer pollIntervalMinutes
) {
}

