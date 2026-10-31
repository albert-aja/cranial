package io.cranial.platform.knowledge.synonym;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.knowledge.entity.KnowledgeSynonymEntity;
import io.cranial.platform.knowledge.mapper.KnowledgeSynonymMapper;
import io.cranial.platform.knowledge.model.KnowledgeSynonymRequest;
import io.cranial.platform.knowledge.model.KnowledgeSynonymView;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeSynonymService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final KnowledgeSynonymMapper synonymMapper;
    private final ObjectMapper objectMapper;
    private volatile List<KnowledgeSynonymEntity> cachedActive = null;

    public KnowledgeSynonymService(KnowledgeSynonymMapper synonymMapper, ObjectMapper objectMapper) {
        this.synonymMapper = synonymMapper;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeSynonymView> listSynonyms(UUID collectionId, UUID callerAppId, boolean isAdmin) {
        List<KnowledgeSynonymEntity> all = synonymMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSynonymEntity>()
                        .orderByAsc(KnowledgeSynonymEntity::getDomainTag)
                        .orderByAsc(KnowledgeSynonymEntity::getCanonicalTerm)
        );
        return all.stream()
                .filter(e -> isVisible(e, collectionId, callerAppId, isAdmin))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public KnowledgeSynonymView createSynonym(KnowledgeSynonymRequest request, UUID callerAppId, boolean isAdmin) {
        String canonical = request.canonicalTerm().trim().toLowerCase(Locale.ROOT);
        List<String> cleanedSynonyms = request.synonyms().stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty() && !s.equals(canonical))
                .distinct()
                .toList();
        if (cleanedSynonyms.isEmpty()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "At least one distinct synonym is required");
        }

        UUID effectiveAppId = isAdmin ? request.applicationId() : callerAppId;
        KnowledgeSynonymEntity entity = new KnowledgeSynonymEntity();
        entity.setId(UUID.randomUUID());
        entity.setCollectionId(request.collectionId());
        entity.setApplicationId(effectiveAppId);
        entity.setCanonicalTerm(canonical);
        entity.setSynonymsJson(toJson(cleanedSynonyms));
        entity.setLocalePair(request.localePair() != null && !request.localePair().isBlank()
                ? request.localePair().trim().toLowerCase(Locale.ROOT)
                : "en_id");
        entity.setDomainTag(request.domainTag() != null && !request.domainTag().isBlank()
                ? request.domainTag().trim().toLowerCase(Locale.ROOT)
                : "general");
        entity.setIsActive(true);
        entity.setCreatedAt(Instant.now());

        synonymMapper.insert(entity);
        cachedActive = null;
        return toView(entity);
    }

    @Transactional
    public void deleteSynonym(UUID id, UUID callerAppId, boolean isAdmin) {
        KnowledgeSynonymEntity existing = synonymMapper.selectById(id);
        if (existing == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Synonym entry not found");
        }
        if (!isAdmin && existing.getApplicationId() != null && !existing.getApplicationId().equals(callerAppId)) {
            throw new CranialException(ErrorCode.FORBIDDEN, "Cannot delete synonym owned by another application");
        }
        synonymMapper.deleteById(id);
        cachedActive = null;
    }

    public String expandText(String text, UUID collectionId, UUID applicationId) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        List<KnowledgeSynonymEntity> active = getActiveEntries();
        if (active.isEmpty()) {
            return text;
        }

        String normalizedHaystack = " " + normalizePhrase(text) + " ";
        Set<String> matchedExpansions = new LinkedHashSet<>();

        for (KnowledgeSynonymEntity entry : active) {
            if (entry.getCollectionId() != null && !entry.getCollectionId().equals(collectionId)) {
                continue;
            }
            if (entry.getApplicationId() != null && applicationId != null && !entry.getApplicationId().equals(applicationId)) {
                continue;
            }
            String canonicalNorm = normalizePhrase(entry.getCanonicalTerm());
            List<String> syns = parseList(entry.getSynonymsJson());

            boolean hit = !canonicalNorm.isEmpty() && normalizedHaystack.contains(" " + canonicalNorm + " ");
            if (!hit) {
                for (String syn : syns) {
                    String synNorm = normalizePhrase(syn);
                    if (!synNorm.isEmpty() && normalizedHaystack.contains(" " + synNorm + " ")) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) {
                if (!canonicalNorm.isEmpty()) {
                    matchedExpansions.add(canonicalNorm);
                }
                for (String syn : syns) {
                    String synNorm = normalizePhrase(syn);
                    if (!synNorm.isEmpty()) {
                        matchedExpansions.add(synNorm);
                    }
                }
            }
        }

        if (matchedExpansions.isEmpty()) {
            return text;
        }
        return text + " " + String.join(" ", matchedExpansions);
    }

    private List<KnowledgeSynonymEntity> getActiveEntries() {
        List<KnowledgeSynonymEntity> local = cachedActive;
        if (local != null) {
            return local;
        }
        List<KnowledgeSynonymEntity> loaded = synonymMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSynonymEntity>()
                        .eq(KnowledgeSynonymEntity::getIsActive, true)
        );
        cachedActive = loaded;
        return loaded;
    }

    private boolean isVisible(KnowledgeSynonymEntity e, UUID collectionId, UUID callerAppId, boolean isAdmin) {
        if (collectionId != null && e.getCollectionId() != null && !e.getCollectionId().equals(collectionId)) {
            return false;
        }
        if (!isAdmin && e.getApplicationId() != null && !e.getApplicationId().equals(callerAppId)) {
            return false;
        }
        return true;
    }

    private KnowledgeSynonymView toView(KnowledgeSynonymEntity e) {
        return new KnowledgeSynonymView(
                e.getId(),
                e.getCollectionId(),
                e.getApplicationId(),
                e.getCanonicalTerm(),
                parseList(e.getSynonymsJson()),
                e.getLocalePair(),
                e.getDomainTag(),
                Boolean.TRUE.equals(e.getIsActive()),
                e.getCreatedAt()
        );
    }

    private static String normalizePhrase(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }
}

