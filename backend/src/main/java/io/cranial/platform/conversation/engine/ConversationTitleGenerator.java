package io.cranial.platform.conversation.engine;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ConversationTitleGenerator {

    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    public String generateTitle(String initialMessage, String locale) {
        if (initialMessage == null || initialMessage.isBlank()) {
            return "id".equalsIgnoreCase(locale) ? "Sesi Percakapan Baru" : "New Conversation";
        }

        String cleaned = CLEAN_PATTERN.matcher(initialMessage).replaceAll("").trim();
        String[] words = cleaned.split("\\s+");

        if (words.length == 0 || words[0].isBlank()) {
            return "id".equalsIgnoreCase(locale) ? "Sesi Percakapan Baru" : "New Conversation";
        }

        // Take first 3 to 6 words
        int takeCount = Math.min(words.length, 5);
        StringBuilder titleBuilder = new StringBuilder();
        for (int i = 0; i < takeCount; i++) {
            if (i > 0) titleBuilder.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                titleBuilder.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    titleBuilder.append(word.substring(1).toLowerCase());
                }
            }
        }

        String result = titleBuilder.toString();
        return result.length() > 60 ? result.substring(0, 57) + "..." : result;
    }
}

