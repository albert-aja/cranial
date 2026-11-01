package io.cranial.platform.conversation.model;

import java.util.Map;

public record ConversationBenchmarkResult(
        int totalConversations,
        int totalMessages,
        double averageLatencyMs,
        double averageTurnCount,
        int totalSafetyViolationsIntercepted,
        int totalSlangExpressionsNormalized,
        int positiveFeedbackCount,
        int negativeFeedbackCount,
        double satisfactionScore,
        Map<String, Long> messagesByLocale,
        Map<String, Long> conversationsByApp
) {}

