package io.cranial.platform.conversation.engine;

import io.cranial.platform.conversation.entity.ConversationMessageEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ContextWindowManager {

    private static final int DEFAULT_MAX_TURNS = 20;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    public record WindowResult(
            List<ConversationMessageEntity> activeMessages,
            String updatedSummary,
            int totalActiveTokens
    ) {}

    public WindowResult manageWindow(
            List<ConversationMessageEntity> allMessages,
            String existingSummary,
            Integer maxTurns,
            Integer maxTokens
    ) {
        int turnsLimit = (maxTurns != null && maxTurns > 0) ? maxTurns : DEFAULT_MAX_TURNS;
        int tokensLimit = (maxTokens != null && maxTokens > 0) ? maxTokens : DEFAULT_MAX_TOKENS;

        if (allMessages == null || allMessages.isEmpty()) {
            return new WindowResult(Collections.emptyList(), existingSummary, 0);
        }

        // If turn count fits within limit, retain all
        if (allMessages.size() <= turnsLimit) {
            int totalTokens = allMessages.stream().mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : estimateTokens(m.getContent())).sum();
            if (totalTokens <= tokensLimit) {
                return new WindowResult(allMessages, existingSummary, totalTokens);
            }
        }

        // Exceeds turns or tokens -> sliding window keeping the latest turns
        int keepCount = Math.min(turnsLimit, allMessages.size());
        int startIndex = allMessages.size() - keepCount;

        List<ConversationMessageEntity> olderMessages = allMessages.subList(0, startIndex);
        List<ConversationMessageEntity> recentMessages = new ArrayList<>(allMessages.subList(startIndex, allMessages.size()));

        // Ensure token limit is respected in recentMessages
        int runningTokens = 0;
        List<ConversationMessageEntity> trimmedMessages = new ArrayList<>();
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ConversationMessageEntity msg = recentMessages.get(i);
            int msgTokens = (msg.getTokenCount() != null) ? msg.getTokenCount() : estimateTokens(msg.getContent());
            if (runningTokens + msgTokens > tokensLimit && !trimmedMessages.isEmpty()) {
                olderMessages.add(msg);
                break;
            }
            runningTokens += msgTokens;
            trimmedMessages.add(0, msg);
        }

        // Update rolling context summary from compacted older messages
        String newSummary = generateRollingSummary(existingSummary, olderMessages);

        return new WindowResult(trimmedMessages, newSummary, runningTokens);
    }

    private String generateRollingSummary(String existingSummary, List<ConversationMessageEntity> olderMessages) {
        if (olderMessages.isEmpty()) {
            return existingSummary;
        }

        StringBuilder sb = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append(existingSummary).append(" | ");
        }

        for (ConversationMessageEntity msg : olderMessages) {
            String role = msg.getRole() != null ? msg.getRole() : "USER";
            String preview = (msg.getContent() != null && msg.getContent().length() > 60)
                    ? msg.getContent().substring(0, 57) + "..."
                    : msg.getContent();
            sb.append(role).append(": ").append(preview).append("; ");
        }

        String summary = sb.toString();
        if (summary.length() > 500) {
            return summary.substring(summary.length() - 500);
        }
        return summary;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Heuristic: ~4 characters per token for English/Indonesian text
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}

