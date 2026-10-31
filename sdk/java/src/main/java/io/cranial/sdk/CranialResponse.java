package io.cranial.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CranialResponse<T>(
        boolean success,
        T data,
        List<ErrorItem> errors,
        Map<String, Object> metadata
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorItem(
            String code,
            String message,
            Map<String, Object> details
    ) {}
}

