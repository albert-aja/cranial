package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PiiEntityView(
        @JsonProperty("type") String type,
        @JsonProperty("masked") String masked,
        @JsonProperty("start_offset") int startOffset,
        @JsonProperty("end_offset") int endOffset
) {}

