package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.UUID;

public record SchemaView(
        UUID id,
        @JsonAlias({"schemaCode", "schema_code"}) String schemaCode,
        String name,
        String description,
        @JsonAlias({"schemaType", "schema_type"}) String schemaType,
        int version,
        @JsonAlias({"schemaDefinitionJson", "schema_definition_json"}) String schemaDefinitionJson,
        @JsonAlias({"isStrict", "is_strict"}) boolean isStrict,
        String status,
        @JsonAlias({"createdAt", "created_at"}) Instant createdAt,
        @JsonAlias({"updatedAt", "updated_at"}) Instant updatedAt
) {
}

