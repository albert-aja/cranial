package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReviewSummarizeRequest(
        @NotEmpty(message = "Reviews list cannot be empty")
        @JsonProperty("reviews") List<String> reviews,

        @JsonProperty("locale") String locale,
        @JsonProperty("domain_context") String domainContext
) {}

