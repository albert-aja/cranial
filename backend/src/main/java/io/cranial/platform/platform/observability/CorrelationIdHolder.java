package io.cranial.platform.platform.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationIdHolder {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_APP_KEY = "applicationCode";

    private CorrelationIdHolder() {
    }

    public static String currentOrGenerate() {
        String existing = MDC.get(MDC_KEY);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }
}

