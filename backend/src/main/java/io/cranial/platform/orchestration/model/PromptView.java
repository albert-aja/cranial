package io.cranial.platform.orchestration.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromptView(
        UUID id,
        @JsonAlias({"promptCode", "prompt_code"}) String promptCode,
        String name,
        String description,
        @JsonAlias({"capabilityCode", "capability_code"}) String capabilityCode,
        @JsonAlias({"taskCode", "task_code"}) String taskCode,
        @JsonAlias({"applicationId", "application_id"}) UUID applicationId,
        int version,
        String status,
        @JsonAlias({"systemTemplate", "system_template"}) String systemTemplate,
        @JsonAlias({"userTemplate", "user_template"}) String userTemplate,
        @JsonAlias({"inputVariables", "input_variables"}) List<String> inputVariables,
        @JsonAlias({"createdAt", "created_at"}) Instant createdAt,
        @JsonAlias({"updatedAt", "updated_at"}) Instant updatedAt
) {
}

