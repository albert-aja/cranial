package io.cranial.platform.knowledge.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class KnowledgeReranker {

    public double computeBoost(
            String rawQuery,
            List<String> queryTerms,
            String chunkContent,
            String sectionTitle,
            String sourceTitle,
            boolean stale
    ) {
        double boost = 0.0;
        String lowerQuery = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        String lowerContent = chunkContent == null ? "" : chunkContent.toLowerCase(Locale.ROOT);
        String lowerSection = sectionTitle == null ? "" : sectionTitle.toLowerCase(Locale.ROOT);
        String lowerTitle = sourceTitle == null ? "" : sourceTitle.toLowerCase(Locale.ROOT);

        // 1. Exact phrase / consecutive query bigram match
        if (lowerQuery.length() >= 6 && lowerContent.contains(lowerQuery)) {
            boost += 0.14;
        } else if (queryTerms.size() >= 2) {
            for (int i = 0; i < queryTerms.size() - 1; i++) {
                String bigram = queryTerms.get(i) + " " + queryTerms.get(i + 1);
                if (lowerContent.contains(bigram) || lowerSection.contains(bigram)) {
                    boost += 0.10;
                    break;
                }
            }
        }

        // 2. Heading / title semantic alignment
        int headingHits = 0;
        for (String term : queryTerms) {
            if (lowerSection.contains(term) || lowerTitle.contains(term)) {
                headingHits++;
            }
        }
        if (!queryTerms.isEmpty() && headingHits > 0) {
            boost += Math.min(0.12, 0.05 * headingHits);
        }

        // 3. Term proximity window bonus
        if (queryTerms.size() >= 2) {
            int firstPos = Integer.MAX_VALUE;
            int lastPos = -1;
            int found = 0;
            for (String term : queryTerms) {
                int idx = lowerContent.indexOf(term);
                if (idx >= 0) {
                    found++;
                    firstPos = Math.min(firstPos, idx);
                    lastPos = Math.max(lastPos, idx);
                }
            }
            if (found >= 2 && (lastPos - firstPos) <= 90) {
                boost += 0.06;
            }
        }

        // 4. Freshness adjustment
        boost += stale ? -0.05 : 0.02;
        return Math.round(boost * 10000.0) / 10000.0;
    }
}

