package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record TicketTriageRequest(
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("external_user_ref") String externalUserRef,

        @NotBlank(message = "Subject cannot be blank")
        @JsonProperty("subject") String subject,

        @NotBlank(message = "Message cannot be blank")
        @JsonProperty("message") String message,

        @JsonProperty("locale") String locale,
        @JsonProperty("source_channel") String sourceChannel
) {}

