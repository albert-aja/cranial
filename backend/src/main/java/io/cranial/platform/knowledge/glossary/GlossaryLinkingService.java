package io.cranial.platform.knowledge.glossary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.knowledge.chunking.KnowledgeChunker;
import io.cranial.platform.knowledge.entity.KnowledgeChunkEntity;
import io.cranial.platform.knowledge.entity.KnowledgeCollectionEntity;
import io.cranial.platform.knowledge.entity.KnowledgeGlossaryTermEntity;
import io.cranial.platform.knowledge.entity.KnowledgeSourceEntity;
import io.cranial.platform.knowledge.mapper.KnowledgeGlossaryTermMapper;
import io.cranial.platform.knowledge.model.GlossaryLinkResult;
import io.cranial.platform.knowledge.model.GlossaryTermRequest;
import io.cranial.platform.knowledge.model.GlossaryTermView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GlossaryLinkingService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final KnowledgeGlossaryTermMapper glossaryMapper;
    private final ObjectMapper objectMapper;

    public GlossaryLinkingService(KnowledgeGlossaryTermMapper glossaryMapper, ObjectMapper objectMapper) {
        this.glossaryMapper = glossaryMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncSourceGlossaryTerms(
            KnowledgeCollectionEntity collection,
            KnowledgeSourceEntity source,
            List<KnowledgeChunker.GlossaryCandidate> candidates,
            Map<Integer, KnowledgeChunkEntity> chunksByIndex,
            String defaultLocale,
            String defaultCategory
    ) {
        glossaryMapper.delete(new LambdaQueryWrapper<KnowledgeGlossaryTermEntity>()
                .eq(KnowledgeGlossaryTermEntity::getSourceId, source.getId()));

        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (KnowledgeChunker.GlossaryCandidate cand : candidates) {
            if (cand.term() == null || cand.term().isBlank()) {
                continue;
            }
            KnowledgeChunkEntity chunk = chunksByIndex.get(cand.chunkIndex());
            KnowledgeGlossaryTermEntity entity = new KnowledgeGlossaryTermEntity();
            entity.setId(UUID.randomUUID());
            entity.setCollectionId(collection.getId());
            entity.setSourceId(source.getId());
            entity.setChunkId(chunk != null ? chunk.getId() : null);
            entity.setApplicationId(collection.getApplicationId());
            entity.setTenantId(collection.getTenantId());
            entity.setTerm(cand.term().trim());
            entity.setNormalizedTerm(normalizeTerm(cand.term()));
            entity.setAliasesJson(toJson(cand.aliases() != null ? cand.aliases() : List.of()));
            entity.setLocale(defaultLocale != null && !defaultLocale.isBlank() ? defaultLocale : "en");
            entity.setCategory(defaultCategory != null ? defaultCategory : "general");
            entity.setShortDefinition(truncate(cand.shortDefinition(), 980));
            entity.setRelatedTermsJson("[]");
            entity.setCreatedAt(now);
            glossaryMapper.insert(entity);
        }
    }

    @Transactional
    public void deleteTermsForSource(UUID sourceId) {
        glossaryMapper.delete(new LambdaQueryWrapper<KnowledgeGlossaryTermEntity>()
                .eq(KnowledgeGlossaryTermEntity::getSourceId, sourceId));
    }

    @Transactional
    public GlossaryTermView createManualTerm(KnowledgeCollectionEntity collection, GlossaryTermRequest request) {
        KnowledgeGlossaryTermEntity entity = new KnowledgeGlossaryTermEntity();
        entity.setId(UUID.randomUUID());
        entity.setCollectionId(collection.getId());
        entity.setSourceId(null);
        entity.setChunkId(null);
        entity.setApplicationId(collection.getApplicationId());
        entity.setTenantId(collection.getTenantId());
        entity.setTerm(request.term().trim());
        entity.setNormalizedTerm(normalizeTerm(request.term()));
        entity.setAliasesJson(toJson(request.aliases() != null ? request.aliases() : List.of()));
        entity.setLocale(request.locale() != null && !request.locale().isBlank() ? request.locale().trim().toLowerCase(Locale.ROOT) : "en");
        entity.setCategory(request.category() != null && !request.category().isBlank() ? request.category().trim() : "general");
        entity.setShortDefinition(truncate(request.shortDefinition().trim(), 980));
        entity.setRelatedTermsJson(toJson(request.relatedTerms() != null ? request.relatedTerms() : List.of()));
        entity.setCreatedAt(Instant.now());
        glossaryMapper.insert(entity);
        return toView(entity);
    }

    public List<GlossaryTermView> listTerms(UUID collectionId, String locale) {
        LambdaQueryWrapper<KnowledgeGlossaryTermEntity> q = new LambdaQueryWrapper<KnowledgeGlossaryTermEntity>()
                .eq(KnowledgeGlossaryTermEntity::getCollectionId, collectionId);
        if (locale != null && !locale.isBlank()) {
            q.eq(KnowledgeGlossaryTermEntity::getLocale, locale.trim().toLowerCase(Locale.ROOT));
        }
        q.orderByAsc(KnowledgeGlossaryTermEntity::getTerm);
        return glossaryMapper.selectList(q).stream().map(this::toView).toList();
    }

    public GlossaryLinkResult linkTermsInText(UUID collectionId, String text, String localeFilter) {
        if (text == null || text.isBlank()) {
            return new GlossaryLinkResult("", 0, List.of());
        }
        LambdaQueryWrapper<KnowledgeGlossaryTermEntity> q = new LambdaQueryWrapper<KnowledgeGlossaryTermEntity>()
                .eq(KnowledgeGlossaryTermEntity::getCollectionId, collectionId);
        if (localeFilter != null && !localeFilter.isBlank()) {
            q.eq(KnowledgeGlossaryTermEntity::getLocale, localeFilter.trim().toLowerCase(Locale.ROOT));
        }
        List<KnowledgeGlossaryTermEntity> terms = glossaryMapper.selectList(q);
        if (terms.isEmpty()) {
            return new GlossaryLinkResult(text, 0, List.of());
        }

        record SurfacePhrase(String phraseLower, KnowledgeGlossaryTermEntity entity) {}
        List<SurfacePhrase> phrases = new ArrayList<>();
        for (KnowledgeGlossaryTermEntity t : terms) {
            if (t.getTerm() != null && t.getTerm().trim().length() >= 3) {
                phrases.add(new SurfacePhrase(t.getTerm().trim().toLowerCase(Locale.ROOT), t));
            }
            for (String alias : parseList(t.getAliasesJson())) {
                if (alias != null && alias.trim().length() >= 3) {
                    phrases.add(new SurfacePhrase(alias.trim().toLowerCase(Locale.ROOT), t));
                }
            }
        }
        // Longest surface form first to avoid partial shadowing (e.g., "Emergency Fund" before "Fund")
        phrases.sort(Comparator.comparingInt((SurfacePhrase p) -> p.phraseLower().length()).reversed());

        String lowerText = text.toLowerCase(Locale.ROOT);
        boolean[] occupied = new boolean[text.length()];
        List<GlossaryLinkResult.LinkedTermOccurrence> occurrences = new ArrayList<>();

        for (SurfacePhrase sp : phrases) {
            String target = sp.phraseLower();
            int len = target.length();
            int fromIdx = 0;
            while (fromIdx <= lowerText.length() - len) {
                int hit = lowerText.indexOf(target, fromIdx);
                if (hit < 0) {
                    break;
                }
                int end = hit + len;
                if (isWordBoundary(lowerText, hit, end) && !isRangeOccupied(occupied, hit, end)) {
                    for (int k = hit; k < end; k++) {
                        occupied[k] = true;
                    }
                    KnowledgeGlossaryTermEntity ent = sp.entity();
                    occurrences.add(new GlossaryLinkResult.LinkedTermOccurrence(
                            ent.getId(),
                            ent.getTerm(),
                            text.substring(hit, end),
                            hit,
                            end,
                            ent.getLocale(),
                            ent.getCategory(),
                            ent.getShortDefinition(),
                            ent.getSourceId(),
                            ent.getChunkId()
                    ));
                    fromIdx = end;
                } else {
                    fromIdx = hit + 1;
                }
            }
        }

        occurrences.sort(Comparator.comparingInt(GlossaryLinkResult.LinkedTermOccurrence::startOffset));
        return new GlossaryLinkResult(text, occurrences.size(), occurrences);
    }

    public GlossaryTermView toView(KnowledgeGlossaryTermEntity entity) {
        return new GlossaryTermView(
                entity.getId(),
                entity.getCollectionId(),
                entity.getSourceId(),
                entity.getChunkId(),
                entity.getTerm(),
                entity.getNormalizedTerm(),
                parseList(entity.getAliasesJson()),
                entity.getLocale(),
                entity.getCategory(),
                entity.getShortDefinition(),
                parseList(entity.getRelatedTermsJson()),
                entity.getCreatedAt()
        );
    }

    private boolean isWordBoundary(String s, int start, int end) {
        boolean leftOk = start == 0 || !Character.isLetterOrDigit(s.charAt(start - 1));
        boolean rightOk = end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
        return leftOk && rightOk;
    }

    private boolean isRangeOccupied(boolean[] occupied, int start, int end) {
        for (int i = start; i < end; i++) {
            if (occupied[i]) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTerm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
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

