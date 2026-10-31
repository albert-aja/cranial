package io.cranial.platform.provider.service;

import io.cranial.platform.provider.entity.ModelEntity;
import io.cranial.platform.provider.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContextPruner {

    private static final Logger log = LoggerFactory.getLogger(ContextPruner.class);

    public List<ChatMessage> pruneMessages(ModelEntity model, List<ChatMessage> messages, Integer requestedMaxTokens) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int contextWindow = model.getContextWindowTokens() != null && model.getContextWindowTokens() > 0
                ? model.getContextWindowTokens()
                : 8192;

        int maxOutput = requestedMaxTokens != null && requestedMaxTokens > 0
                ? requestedMaxTokens
                : (model.getMaxOutputTokens() != null && model.getMaxOutputTokens() > 0 ? model.getMaxOutputTokens() : 2048);

        int tokenBudget = Math.max(512, contextWindow - maxOutput);
        int estimatedTotal = estimateTokenCount(messages);

        if (estimatedTotal <= tokenBudget) {
            return messages;
        }

        log.info("Prompt messages estimated tokens ({}) exceeds budget ({}) for model {}. Pruning context...",
                estimatedTotal, tokenBudget, model.getModelIdentifier());

        // Separate system messages, latest message, and middle history
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> historyMessages = new ArrayList<>();
        ChatMessage latestUserMessage = null;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if ("system".equalsIgnoreCase(m.role())) {
                systemMessages.add(m);
            } else if (i == messages.size() - 1) {
                latestUserMessage = m;
            } else {
                historyMessages.add(m);
            }
        }

        if (latestUserMessage == null && !historyMessages.isEmpty()) {
            latestUserMessage = historyMessages.remove(historyMessages.size() - 1);
        }

        int reservedTokens = estimateTokenCount(systemMessages) + (latestUserMessage != null ? estimateTokenCount(List.of(latestUserMessage)) : 0);

        // If reserved alone exceeds budget, truncate the latest message text
        if (reservedTokens > tokenBudget && latestUserMessage != null) {
            int allowedChars = Math.max(100, (tokenBudget - estimateTokenCount(systemMessages)) * 4);
            String truncatedContent = latestUserMessage.content();
            if (truncatedContent.length() > allowedChars) {
                truncatedContent = truncatedContent.substring(0, allowedChars) + "\n... [truncated to fit context window]";
            }
            List<ChatMessage> result = new ArrayList<>(systemMessages);
            result.add(new ChatMessage(latestUserMessage.role(), truncatedContent));
            return result;
        }

        int remainingBudget = tokenBudget - reservedTokens;
        List<ChatMessage> keptHistory = new ArrayList<>();
        int accumulatedTokens = 0;

        // Iterate backwards through history, keeping the most recent history turns
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            ChatMessage h = historyMessages.get(i);
            int hTokens = estimateTokenCount(List.of(h));
            if (accumulatedTokens + hTokens <= remainingBudget) {
                keptHistory.add(0, h);
                accumulatedTokens += hTokens;
            } else {
                break;
            }
        }

        List<ChatMessage> result = new ArrayList<>(systemMessages);
        if (keptHistory.size() < historyMessages.size()) {
            int prunedCount = historyMessages.size() - keptHistory.size();
            result.add(new ChatMessage("system", "[System Notice: " + prunedCount
                    + " earlier conversation turns pruned to comply with context window limit / "
                    + "Pemberitahuan Sistem: " + prunedCount
                    + " putaran percakapan sebelumnya dipangkas untuk mematuhi batas token konteks]"));
        }
        result.addAll(keptHistory);
        if (latestUserMessage != null) {
            result.add(latestUserMessage);
        }

        return result;
    }

    public int estimateTokenCount(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int charCount = 0;
        for (ChatMessage m : messages) {
            if (m.content() != null) {
                charCount += m.content().length();
            }
            charCount += 10;
        }
        return Math.max(1, (int) Math.ceil(charCount / 3.8));
    }
}

