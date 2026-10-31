package io.cranial.sdk;

import java.time.Duration;

public record CranialConfig(
        String baseUrl,
        String apiKey,
        String signingSecret,
        Duration timeout
) {
    public CranialConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be empty");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be empty");
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl = "http://localhost:8080";
        private String apiKey;
        private String signingSecret;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder signingSecret(String signingSecret) {
            this.signingSecret = signingSecret;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CranialConfig build() {
            return new CranialConfig(baseUrl, apiKey, signingSecret, timeout);
        }
    }
}

