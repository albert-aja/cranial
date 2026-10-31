package io.cranial.platform.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PlatformMetrics {

    private final MeterRegistry registry;

    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAuthDecision(String plane, String outcome) {
        Counter.builder("cranial.auth.decisions")
                .tag("plane", plane)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordCapabilityCheck(String applicationCode, String capability, boolean entitled) {
        Counter.builder("cranial.capability.checks")
                .tag("application", applicationCode)
                .tag("capability", capability)
                .tag("entitled", String.valueOf(entitled))
                .register(registry)
                .increment();
    }

    public void recordRateLimitRejection(String scope) {
        Counter.builder("cranial.ratelimit.rejections")
                .tag("scope", scope)
                .register(registry)
                .increment();
    }

    public void recordIdempotencyHit(String applicationCode) {
        Counter.builder("cranial.idempotency.hits")
                .tag("application", applicationCode)
                .register(registry)
                .increment();
    }

    public void recordDocumentProcessed(String documentType, String status, int pages) {
        Counter.builder("cranial.document.processed")
                .tag("document_type", documentType != null ? documentType : "UNKNOWN")
                .tag("status", status)
                .register(registry)
                .increment();
        if (pages > 0) {
            Counter.builder("cranial.document.pages")
                    .tag("document_type", documentType != null ? documentType : "UNKNOWN")
                    .register(registry)
                    .increment(pages);
        }
    }

    public void recordKnowledgeIndexed(String collectionCode, String status, int chunks) {
        Counter.builder("cranial.knowledge.indexed")
                .tag("collection", collectionCode != null ? collectionCode : "UNKNOWN")
                .tag("status", status)
                .register(registry)
                .increment();
        if (chunks > 0) {
            Counter.builder("cranial.knowledge.chunks")
                    .tag("collection", collectionCode != null ? collectionCode : "UNKNOWN")
                    .register(registry)
                    .increment(chunks);
        }
    }

    public void recordKnowledgeRetrieved(String mode, boolean reranked, int hits) {
        Counter.builder("cranial.knowledge.retrievals")
                .tag("mode", mode != null ? mode : "HYBRID")
                .tag("reranked", String.valueOf(reranked))
                .register(registry)
                .increment();
        if (hits > 0) {
            Counter.builder("cranial.knowledge.hits")
                    .tag("mode", mode != null ? mode : "HYBRID")
                    .register(registry)
                    .increment(hits);
        }
    }
}

