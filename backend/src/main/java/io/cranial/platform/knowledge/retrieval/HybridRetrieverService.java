package io.cranial.platform.knowledge.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.featureflag.FeatureFlagService;
import io.cranial.platform.knowledge.chunking.KnowledgeChunker;
import io.cranial.platform.knowledge.embedding.KnowledgeEmbeddingService;
import io.cranial.platform.knowledge.entity.KnowledgeChunkEntity;
import io.cranial.platform.knowledge.entity.KnowledgeCollectionEntity;
import io.cranial.platform.knowledge.entity.KnowledgeSourceEntity;
import io.cranial.platform.knowledge.mapper.KnowledgeChunkMapper;
import io.cranial.platform.knowledge.mapper.KnowledgeSourceMapper;
import io.cranial.platform.knowledge.model.KnowledgeRetrievalRequest;
import io.cranial.platform.knowledge.model.KnowledgeRetrievalResult;
import io.cranial.platform.knowledge.model.RetrievalDebugTrace;
import io.cranial.platform.knowledge.model.RetrievedChunkHit;
import io.cranial.platform.knowledge.model.SourceCitation;
import io.cranial.platform.knowledge.security.UntrustedKnowledgeGuard;
import io.cranial.platform.knowledge.synonym.KnowledgeSynonymService;
import io.cranial.platform.platform.observability.PlatformMetrics;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class HybridRetrieverService {

    public static final String RERANK_FEATURE_FLAG = "rag.hybrid_rerank.enabled";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final KnowledgeSourceMapper sourceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final KnowledgeReranker reranker;
    private final KnowledgeSynonymService synonymService;
    private final UntrustedKnowledgeGuard untrustedGuard;
    private final FeatureFlagService featureFlagService;
    private final PlatformMetrics metrics;
    private final ObjectMapper objectMapper;

    public HybridRetrieverService(
            KnowledgeSourceMapper sourceMapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeEmbeddingService embeddingService,
            KnowledgeReranker reranker,
            KnowledgeSynonymService synonymService,
            UntrustedKnowledgeGuard untrustedGuard,
            FeatureFlagService featureFlagService,
            PlatformMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.sourceMapper = sourceMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.reranker = reranker;
        this.synonymService = synonymService;
        this.untrustedGuard = untrustedGuard;
        this.featureFlagService = featureFlagService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public record RetrievalAuthContext(
            UUID applicationId,
            String applicationCode,
            UUID tenantId,
            String externalUserRef,
            List<String> roles,
            boolean authenticated,
            boolean adminDebugInspection
    ) {
    }

    public KnowledgeRetrievalResult retrieve(
            KnowledgeCollectionEntity collection,
            KnowledgeRetrievalRequest request,
            RetrievalAuthContext authCtx
    ) {
        long startMs = System.currentTimeMillis();
        Instant now = Instant.now();

        String mode = normalizeMode(request.retrievalMode());
        int topK = request.topK() != null ? Math.max(1, Math.min(25, request.topK())) : 5;
        double minScore = request.minScore() != null ? request.minScore() : 0.08;
        boolean includeStale = request.includeStale() == null || request.includeStale();
        boolean expandParent = Boolean.TRUE.equals(request.expandParentContext());
        boolean includeDebug = Boolean.TRUE.equals(request.debug());

        embeddingService.applyEfSearchHint(collection.getHnswEfSearch());

        double rawVecWeight = "KEYWORD".equals(mode) ? 0.0 : ("VECTOR".equals(mode) ? 1.0 : (request.vectorWeight() != null ? request.vectorWeight() : 0.55));
        double rawKwWeight = "VECTOR".equals(mode) ? 0.0 : ("KEYWORD".equals(mode) ? 1.0 : (request.keywordWeight() != null ? request.keywordWeight() : 0.45));
        double weightSum = (rawVecWeight + rawKwWeight) > 1e-6 ? (rawVecWeight + rawKwWeight) : 1.0;
        double vecWeight = Math.round((rawVecWeight / weightSum) * 1000.0) / 1000.0;
        double kwWeight = Math.round((rawKwWeight / weightSum) * 1000.0) / 1000.0;

        boolean wantRerank = request.rerank() != null
                ? request.rerank()
                : Boolean.TRUE.equals(collection.getRerankByDefault());
        boolean rerankEnabled = wantRerank && featureFlagService.isEnabled(RERANK_FEATURE_FLAG, authCtx.applicationCode());

        // Load active INDEXED sources for collection
        List<KnowledgeSourceEntity> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSourceEntity>()
                        .eq(KnowledgeSourceEntity::getCollectionId, collection.getId())
                        .eq(KnowledgeSourceEntity::getIndexingStatus, "INDEXED")
        );
        Map<UUID, KnowledgeSourceEntity> sourceMap = new LinkedHashMap<>();
        for (KnowledgeSourceEntity s : sources) {
            if (s.getExpiresAt() == null || s.getExpiresAt().isAfter(now)) {
                sourceMap.put(s.getId(), s);
            }
        }

        List<KnowledgeChunkEntity> allChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getCollectionId, collection.getId())
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
        );
        int totalChunksInCollection = allChunks.size();

        // Expand query with custom EN<->ID domain synonyms and embed locally
        String synonymExpandedQuery = synonymService.expandText(request.query(), collection.getId(), authCtx.applicationId());
        int dims = collection.getEmbeddingDimensions() != null ? collection.getEmbeddingDimensions() : KnowledgeEmbeddingService.DEFAULT_DIMENSIONS;
        KnowledgeEmbeddingService.EmbeddingBatchOutput queryEmbed = embeddingService.embedTexts(
                List.of(synonymExpandedQuery),
                collection.getEmbeddingModelCode(),
                dims
        );
        List<Double> queryVector = queryEmbed.vectors().get(0);
        List<String> queryTerms = KnowledgeChunker.distinctQueryTerms(synonymExpandedQuery);
        List<String> expandedQueryTokens = List.of(KnowledgeChunker.normalizeQueryOrChunkTerms(synonymExpandedQuery).split("\\s+"))
                .stream().filter(s -> !s.isBlank()).distinct().toList();

        // Pre-retrieval authorization, isolation, and metadata filtering BEFORE scoring
        List<AuthorizedCandidate> authorizedCandidates = new ArrayList<>();
        List<RetrievalDebugTrace.CandidateScoreTrace> debugTraces = new ArrayList<>();
        int afterIsolationCount = 0;
        int permissionFilteredCount = 0;
        int metadataFilteredCount = 0;

        Set<String> callerUpperRoles = new LinkedHashSet<>();
        if (authCtx.roles() != null) {
            for (String r : authCtx.roles()) {
                if (r != null && !r.isBlank()) {
                    callerUpperRoles.add(r.trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        List<String> collectionRoles = parseList(collection.getAllowedRolesJson());
        boolean collectionRoleAuthorized = collectionRoles.isEmpty() || intersectsIgnoreCase(callerUpperRoles, collectionRoles)
                || callerUpperRoles.contains("SUPER_ADMIN");

        for (KnowledgeChunkEntity chunk : allChunks) {
            KnowledgeSourceEntity source = sourceMap.get(chunk.getSourceId());
            if (source == null) {
                continue;
            }
            if (chunk.getExpiresAt() != null && !chunk.getExpiresAt().isAfter(now)) {
                continue;
            }
            if (!matchesIsolation(collection, source, chunk, authCtx)) {
                continue;
            }
            afterIsolationCount++;

            String denyReason = collectionRoleAuthorized
                    ? evaluatePermissionDenyReason(source, authCtx, callerUpperRoles)
                    : "COLLECTION_ROLE_RESTRICTED";
            if (denyReason != null) {
                permissionFilteredCount++;
                if (includeDebug && authCtx.adminDebugInspection()) {
                    debugTraces.add(new RetrievalDebugTrace.CandidateScoreTrace(
                            chunk.getId(), source.getSourceCode(), chunk.getSectionTitle(), chunk.getPageNumber(),
                            source.getPermissionScope(), false, false, 0, 0, 0, 0, 0, List.of(), denyReason
                    ));
                }
                continue;
            }

            Map<String, Object> mergedMeta = mergeMetadata(source.getMetadataJson(), chunk.getMetadataJson());
            if (!matchesMetadataFilters(mergedMeta, request.metadataFilters())) {
                metadataFilteredCount++;
                if (includeDebug) {
                    debugTraces.add(new RetrievalDebugTrace.CandidateScoreTrace(
                            chunk.getId(), source.getSourceCode(), chunk.getSectionTitle(), chunk.getPageNumber(),
                            source.getPermissionScope(), true, false, 0, 0, 0, 0, 0, List.of(), "METADATA_MISMATCH"
                    ));
                }
                continue;
            }

            Instant effectiveTime = source.getEffectiveUpdatedAt() != null ? source.getEffectiveUpdatedAt() : source.getUpdatedAt();
            long ageDays = effectiveTime != null ? Math.max(0L, Duration.between(effectiveTime, now).toDays()) : 0L;
            int ttlDays = collection.getFreshnessTtlDays() != null ? collection.getFreshnessTtlDays() : 90;
            boolean stale = ageDays > ttlDays;
            if (stale && !includeStale) {
                metadataFilteredCount++;
                continue;
            }

            authorizedCandidates.add(new AuthorizedCandidate(chunk, source, mergedMeta, stale, ageDays, ttlDays));
        }

        // Compute BM25 stats across authorized corpus ONLY
        Map<String, Integer> docFreq = new HashMap<>();
        double totalDocLen = 0.0;
        Map<UUID, List<String>> chunkTermLists = new HashMap<>();

        for (AuthorizedCandidate cand : authorizedCandidates) {
            String norm = cand.chunk().getNormalizedTerms() != null ? cand.chunk().getNormalizedTerms() : "";
            List<String> terms = norm.isBlank() ? List.of() : List.of(norm.split("\\s+"));
            chunkTermLists.put(cand.chunk().getId(), terms);
            totalDocLen += Math.max(1, terms.size());
            Set<String> uniqueInDoc = new LinkedHashSet<>(terms);
            for (String qTok : expandedQueryTokens) {
                if (uniqueInDoc.contains(qTok)) {
                    docFreq.merge(qTok, 1, Integer::sum);
                }
            }
        }
        double avgDocLen = authorizedCandidates.isEmpty() ? 1.0 : Math.max(1.0, totalDocLen / authorizedCandidates.size());
        int corpusN = authorizedCandidates.size();

        double maxRawBm25 = 0.0;
        Map<UUID, Double> rawBm25ByChunk = new HashMap<>();
        Map<UUID, Double> vecScoreByChunk = new HashMap<>();
        Map<UUID, List<String>> matchedTermsByChunk = new HashMap<>();

        for (AuthorizedCandidate cand : authorizedCandidates) {
            UUID cid = cand.chunk().getId();
            List<String> docTerms = chunkTermLists.getOrDefault(cid, List.of());
            double bm25 = computeBm25(expandedQueryTokens, docTerms, docFreq, corpusN, avgDocLen);
            rawBm25ByChunk.put(cid, bm25);
            if (bm25 > maxRawBm25) {
                maxRawBm25 = bm25;
            }

            List<Double> chunkVec = embeddingService.parseJsonVector(cand.chunk().getEmbeddingJson());
            double vecSim = "KEYWORD".equals(mode) ? 0.0 : embeddingService.cosineSimilarity(queryVector, chunkVec);
            vecScoreByChunk.put(cid, round4(vecSim));

            List<String> matched = new ArrayList<>();
            String lowerRaw = (cand.chunk().getContent() + " " + Objects.toString(cand.chunk().getSectionTitle(), "")).toLowerCase(Locale.ROOT);
            for (String qt : queryTerms) {
                if (lowerRaw.contains(qt) || docTerms.contains(qt)) {
                    matched.add(qt);
                }
            }
            matchedTermsByChunk.put(cid, matched);
        }

        List<ScoredCandidate> scored = new ArrayList<>();
        int belowThresholdCount = 0;

        for (AuthorizedCandidate cand : authorizedCandidates) {
            UUID cid = cand.chunk().getId();
            double vecScore = vecScoreByChunk.getOrDefault(cid, 0.0);
            double rawBm25 = rawBm25ByChunk.getOrDefault(cid, 0.0);
            double kwScore = "VECTOR".equals(mode)
                    ? 0.0
                    : (maxRawBm25 > 1e-6 ? round4(Math.min(1.0, (rawBm25 / maxRawBm25) * (1.0 - Math.exp(-rawBm25 / 1.8)))) : 0.0);
            double hybridScore = round4((vecWeight * vecScore) + (kwWeight * kwScore));
            if ("HYBRID".equals(mode) && kwScore == 0.0 && vecScore < 0.38) {
                hybridScore = round4(hybridScore * 0.25);
            }

            double boost = rerankEnabled
                    ? reranker.computeBoost(
                            synonymExpandedQuery,
                            queryTerms,
                            cand.chunk().getContent(),
                            cand.chunk().getSectionTitle(),
                            cand.source().getTitle(),
                            cand.stale()
                    )
                    : 0.0;
            double finalScore = round4(Math.max(0.0, Math.min(1.0, hybridScore + boost)));
            List<String> matchedTerms = matchedTermsByChunk.getOrDefault(cid, List.of());

            if (finalScore < minScore && hybridScore < minScore) {
                belowThresholdCount++;
                if (includeDebug) {
                    debugTraces.add(new RetrievalDebugTrace.CandidateScoreTrace(
                            cid, cand.source().getSourceCode(), cand.chunk().getSectionTitle(), cand.chunk().getPageNumber(),
                            cand.source().getPermissionScope(), true, true,
                            vecScore, kwScore, hybridScore, boost, finalScore, matchedTerms, "BELOW_MIN_SCORE"
                    ));
                }
                continue;
            }

            scored.add(new ScoredCandidate(cand, vecScore, kwScore, hybridScore, boost, finalScore, matchedTerms, 0));
            if (includeDebug) {
                debugTraces.add(new RetrievalDebugTrace.CandidateScoreTrace(
                        cid, cand.source().getSourceCode(), cand.chunk().getSectionTitle(), cand.chunk().getPageNumber(),
                        cand.source().getPermissionScope(), true, true,
                        vecScore, kwScore, hybridScore, boost, finalScore, matchedTerms, "SELECTED_CANDIDATE"
                ));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::hybridScore).reversed());
        List<ScoredCandidate> withPreRank = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate sc = scored.get(i);
            withPreRank.add(new ScoredCandidate(
                    sc.candidate(), sc.vectorScore(), sc.keywordScore(), sc.hybridScore(),
                    sc.rerankBoost(), sc.finalScore(), sc.matchedTerms(), i + 1
            ));
        }

        if (rerankEnabled) {
            withPreRank.sort(Comparator.comparingDouble(ScoredCandidate::finalScore).reversed()
                    .thenComparingInt(ScoredCandidate::preRerankRank));
        }

        List<RetrievedChunkHit> hits = new ArrayList<>();
        Set<String> staleWarnings = new LinkedHashSet<>();
        Set<String> securityWarnings = new LinkedHashSet<>();

        int limit = Math.min(topK, withPreRank.size());
        for (int i = 0; i < limit; i++) {
            ScoredCandidate sc = withPreRank.get(i);
            AuthorizedCandidate ac = sc.candidate();
            KnowledgeChunkEntity chunk = ac.chunk();
            KnowledgeSourceEntity src = ac.source();

            UntrustedKnowledgeGuard.SanitizedKnowledgeText guarded =
                    untrustedGuard.inspectAndSanitize(chunk.getContent(), src.getSourceCode());
            securityWarnings.addAll(guarded.warnings());

            String expandedContext = null;
            if (expandParent) {
                String rawParent = (chunk.getParentSectionContent() != null && !chunk.getParentSectionContent().isBlank())
                        ? chunk.getParentSectionContent()
                        : chunk.getContent();
                UntrustedKnowledgeGuard.SanitizedKnowledgeText guardedParent =
                        untrustedGuard.inspectAndSanitize(rawParent, src.getSourceCode());
                securityWarnings.addAll(guardedParent.warnings());
                expandedContext = guardedParent.sanitizedText();
            }

            Object storedSecWarnings = ac.metadata().get("security_warnings");
            if (storedSecWarnings instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        securityWarnings.add(String.valueOf(item));
                    }
                }
            }

            String citationId = "C" + (i + 1);
            String staleWarn = null;
            if (ac.stale()) {
                staleWarn = "Source '" + src.getSourceCode() + "' is stale (" + ac.ageDays()
                        + "d old > freshness TTL " + ac.ttlDays() + "d)";
                staleWarnings.add(staleWarn);
            }

            SourceCitation citation = new SourceCitation(
                    citationId,
                    chunk.getId(),
                    src.getId(),
                    src.getSourceCode(),
                    src.getTitle(),
                    src.getSourceUri(),
                    chunk.getSourceVersion() != null ? chunk.getSourceVersion() : 1,
                    chunk.getPageNumber(),
                    chunk.getSectionTitle(),
                    chunk.getCharStart() != null ? chunk.getCharStart() : 0,
                    chunk.getCharEnd() != null ? chunk.getCharEnd() : 0,
                    ac.stale(),
                    ac.ageDays(),
                    staleWarn
            );

            hits.add(new RetrievedChunkHit(
                    i + 1,
                    sc.preRerankRank(),
                    sc.finalScore(),
                    sc.vectorScore(),
                    sc.keywordScore(),
                    sc.hybridScore(),
                    sc.rerankBoost(),
                    guarded.sanitizedText(),
                    expandedContext,
                    sc.matchedTerms(),
                    ac.metadata(),
                    citation
            ));
        }

        long totalDurationMs = Math.max(1L, System.currentTimeMillis() - startMs);
        metrics.recordKnowledgeRetrieved(mode, rerankEnabled, hits.size());

        RetrievalDebugTrace trace = includeDebug
                ? new RetrievalDebugTrace(
                        queryTerms,
                        mode,
                        rerankEnabled,
                        queryEmbed.modelCode(),
                        queryEmbed.durationMs(),
                        totalChunksInCollection,
                        afterIsolationCount,
                        permissionFilteredCount,
                        metadataFilteredCount,
                        belowThresholdCount,
                        hits.size(),
                        vecWeight,
                        kwWeight,
                        totalDurationMs,
                        debugTraces
                )
                : null;

        return new KnowledgeRetrievalResult(
                collection.getId(),
                collection.getCollectionCode(),
                request.query(),
                mode,
                rerankEnabled,
                hits,
                List.copyOf(staleWarnings),
                List.copyOf(securityWarnings),
                totalDurationMs,
                trace
        );
    }

    private boolean matchesIsolation(
            KnowledgeCollectionEntity collection,
            KnowledgeSourceEntity source,
            KnowledgeChunkEntity chunk,
            RetrievalAuthContext authCtx
    ) {
        if ("GLOBAL_BENCHMARK".equalsIgnoreCase(collection.getVisibilityScope())) {
            return true;
        }
        if (authCtx.adminDebugInspection() && authCtx.applicationId() == null && authCtx.tenantId() == null) {
            return true;
        }
        if (collection.getApplicationId() != null && authCtx.applicationId() != null
                && !collection.getApplicationId().equals(authCtx.applicationId())) {
            return false;
        }
        if (source.getApplicationId() != null && authCtx.applicationId() != null
                && !source.getApplicationId().equals(authCtx.applicationId())) {
            return false;
        }
        if ("TENANT".equalsIgnoreCase(collection.getVisibilityScope()) || source.getTenantId() != null) {
            UUID requiredTenant = source.getTenantId() != null ? source.getTenantId() : collection.getTenantId();
            if (requiredTenant != null && !requiredTenant.equals(authCtx.tenantId())) {
                return false;
            }
        }
        return true;
    }

    private String evaluatePermissionDenyReason(
            KnowledgeSourceEntity source,
            RetrievalAuthContext authCtx,
            Set<String> callerUpperRoles
    ) {
        String scope = source.getPermissionScope() != null ? source.getPermissionScope().toUpperCase(Locale.ROOT) : "AUTHENTICATED";
        return switch (scope) {
            case "PUBLIC" -> null;
            case "AUTHENTICATED" -> authCtx.authenticated() ? null : "PERMISSION_SCOPE:AUTHENTICATED_REQUIRED";
            case "TENANT_MEMBERS" -> {
                if (source.getTenantId() != null && !source.getTenantId().equals(authCtx.tenantId()) && !authCtx.adminDebugInspection()) {
                    yield "PERMISSION_SCOPE:TENANT_MISMATCH";
                }
                yield authCtx.authenticated() ? null : "PERMISSION_SCOPE:TENANT_MEMBER_REQUIRED";
            }
            case "ROLE_RESTRICTED" -> {
                List<String> allowedRoles = parseList(source.getAllowedRolesJson());
                if (intersectsIgnoreCase(callerUpperRoles, allowedRoles)) {
                    yield null;
                }
                List<String> allowedUsers = parseList(source.getAllowedUserRefsJson());
                if (authCtx.externalUserRef() != null && allowedUsers.contains(authCtx.externalUserRef())) {
                    yield null;
                }
                yield "PERMISSION_SCOPE:ROLE_RESTRICTED" + allowedRoles;
            }
            case "OWNER_ONLY" -> {
                if (callerUpperRoles.contains("OWNER")) {
                    yield null;
                }
                List<String> allowedUsers = parseList(source.getAllowedUserRefsJson());
                if (authCtx.externalUserRef() != null && !authCtx.externalUserRef().isBlank()
                        && allowedUsers.contains(authCtx.externalUserRef())) {
                    yield null;
                }
                yield "PERMISSION_SCOPE:OWNER_ONLY";
            }
            default -> "PERMISSION_SCOPE:UNKNOWN";
        };
    }

    private boolean matchesMetadataFilters(Map<String, Object> metadata, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object expected = entry.getValue();
            if (expected == null || String.valueOf(expected).isBlank()) {
                continue;
            }
            Object actual = metadata.get(key);
            if (actual == null) {
                return false;
            }
            String expectedStr = String.valueOf(expected).trim().toLowerCase(Locale.ROOT);
            if (actual instanceof List<?> list) {
                boolean any = list.stream().anyMatch(v -> String.valueOf(v).trim().toLowerCase(Locale.ROOT).equals(expectedStr));
                if (!any) {
                    return false;
                }
            } else {
                String actualStr = String.valueOf(actual).trim().toLowerCase(Locale.ROOT);
                if (!actualStr.equals(expectedStr)) {
                    return false;
                }
            }
        }
        return true;
    }

    private double computeBm25(
            List<String> queryTokens,
            List<String> docTokens,
            Map<String, Integer> docFreq,
            int corpusN,
            double avgDocLen
    ) {
        if (queryTokens.isEmpty() || docTokens.isEmpty() || corpusN <= 0) {
            return 0.0;
        }
        double k1 = 1.5;
        double b = 0.75;
        Map<String, Integer> tfMap = new HashMap<>();
        for (String t : docTokens) {
            tfMap.merge(t, 1, Integer::sum);
        }
        double dl = docTokens.size();
        double score = 0.0;
        for (String qt : queryTokens) {
            int tf = tfMap.getOrDefault(qt, 0);
            if (tf == 0) {
                continue;
            }
            int df = docFreq.getOrDefault(qt, 0);
            double idf = Math.log(1.0 + ((corpusN - df + 0.5) / (df + 0.5)));
            double denom = tf + k1 * (1.0 - b + b * (dl / avgDocLen));
            score += idf * ((tf * (k1 + 1.0)) / denom);
        }
        return score;
    }

    private String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "HYBRID";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "VECTOR", "KEYWORD", "HYBRID" -> u;
            default -> "HYBRID";
        };
    }

    private boolean intersectsIgnoreCase(Set<String> upperCallerRoles, List<String> allowedRoles) {
        for (String r : allowedRoles) {
            if (r != null && upperCallerRoles.contains(r.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    private Map<String, Object> mergeMetadata(String sourceJson, String chunkJson) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(parseMap(sourceJson));
        merged.putAll(parseMap(chunkJson));
        return merged;
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private record AuthorizedCandidate(
            KnowledgeChunkEntity chunk,
            KnowledgeSourceEntity source,
            Map<String, Object> metadata,
            boolean stale,
            long ageDays,
            int ttlDays
    ) {
    }

    private record ScoredCandidate(
            AuthorizedCandidate candidate,
            double vectorScore,
            double keywordScore,
            double hybridScore,
            double rerankBoost,
            double finalScore,
            List<String> matchedTerms,
            int preRerankRank
    ) {
    }
}
