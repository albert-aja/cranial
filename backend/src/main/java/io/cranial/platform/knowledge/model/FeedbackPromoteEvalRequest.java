package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record FeedbackPromoteEvalRequest(
        @JsonAlias({"caseCode", "case_code"}) String caseCode,
        String name,
        @JsonAlias({"expectedSourceCode", "expected_source_code"}) String expectedSourceCode,
        @JsonAlias({"expectedSectionContains", "expected_section_contains"}) String expectedSectionContains,
        @JsonAlias({"simulatedRoles", "simulated_roles"}) List<String> simulatedRoles,
        @JsonAlias({"activateImmediately", "activate_immediately"}) Boolean activateImmediately
) {
}

