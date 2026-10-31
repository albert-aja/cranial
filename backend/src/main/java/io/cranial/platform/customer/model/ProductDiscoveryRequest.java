package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ProductDiscoveryRequest(
        @NotBlank(message = "Customer goal cannot be blank")
        @JsonProperty("customer_goal") String customerGoal,

        @JsonProperty("current_persona") String currentPersona,
        @JsonProperty("locale") String locale
) {}

