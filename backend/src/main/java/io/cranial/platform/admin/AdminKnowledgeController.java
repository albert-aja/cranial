package io.cranial.platform.admin;

import io.cranial.platform.knowledge.connector.KnowledgeFeedSyncService;
import io.cranial.platform.knowledge.entity.KnowledgeEvalCaseEntity;
import io.cranial.platform.knowledge.model.ChunkPreviewComparisonResult;
import io.cranial.platform.knowledge.model.ChunkPreviewRequest;
import io.cranial.platform.knowledge.model.DocumentKnowledgePromoteRequest;
import io.cranial.platform.knowledge.model.FeedbackPromoteEvalRequest;
import io.cranial.platform.knowledge.model.GlossaryLinkRequest;
import io.cranial.platform.knowledge.model.GlossaryLinkResult;
import io.cranial.platform.knowledge.model.GlossaryTermRequest;
import io.cranial.platform.knowledge.model.GlossaryTermView;
import io.cranial.platform.knowledge.model.HnswTuningUpdateRequest;
import io.cranial.platform.knowledge.model.KnowledgeAnswerResult;
import io.cranial.platform.knowledge.model.KnowledgeAskRequest;
import io.cranial.platform.knowledge.model.KnowledgeCollectionRequest;
import io.cranial.platform.knowledge.model.KnowledgeCollectionView;
import io.cranial.platform.knowledge.model.KnowledgeConnectorRequest;
import io.cranial.platform.knowledge.model.KnowledgeConnectorSyncResult;
import io.cranial.platform.knowledge.model.KnowledgeConnectorView;
import io.cranial.platform.knowledge.model.KnowledgeEvalResult;
import io.cranial.platform.knowledge.model.KnowledgeExportBundle;
import io.cranial.platform.knowledge.model.KnowledgeFeedbackRequest;
import io.cranial.platform.knowledge.model.KnowledgeFeedbackView;
import io.cranial.platform.knowledge.model.KnowledgeRetrievalRequest;
import io.cranial.platform.knowledge.model.KnowledgeRetrievalResult;
import io.cranial.platform.knowledge.model.KnowledgeSourceEventView;
import io.cranial.platform.knowledge.model.KnowledgeSourceIngestRequest;
import io.cranial.platform.knowledge.model.KnowledgeSourceView;
import io.cranial.platform.knowledge.model.KnowledgeSynonymRequest;
import io.cranial.platform.knowledge.model.KnowledgeSynonymView;
import io.cranial.platform.knowledge.model.PromotableDocumentView;
import io.cranial.platform.knowledge.retrieval.HybridRetrieverService;
import io.cranial.platform.knowledge.service.KnowledgeIntelligenceService;
import io.cranial.platform.knowledge.service.KnowledgeRetentionService;
import io.cranial.platform.knowledge.synonym.KnowledgeSynonymService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/knowledge")
@Tag(name = "Admin — Knowledge Intelligence & RAG", description = "Collection wizard, incremental indexing, retrieval debugger, glossary linker, freshness, export/import, and IR evaluation")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminKnowledgeController {

    private final KnowledgeIntelligenceService knowledgeService;
    private final KnowledgeSynonymService synonymService;
    private final KnowledgeFeedSyncService feedSyncService;
    private final KnowledgeRetentionService retentionService;

    public AdminKnowledgeController(
            KnowledgeIntelligenceService knowledgeService,
            KnowledgeSynonymService synonymService,
            KnowledgeFeedSyncService feedSyncService,
            KnowledgeRetentionService retentionService
    ) {
        this.knowledgeService = knowledgeService;
        this.synonymService = synonymService;
        this.feedSyncService = feedSyncService;
        this.retentionService = retentionService;
    }

    @PatchMapping("/collections/{collectionId}/hnsw-tuning")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Tune collection PostgreSQL pgvector HNSW index parameters (m, ef_construction, ef_search)")
    public ApiResponse<KnowledgeCollectionView> updateHnswTuning(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID collectionId,
            @Valid @RequestBody HnswTuningUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.updateHnswTuning(
                collectionId, request, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/promotable-documents")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List processed OCR documents eligible for direct promotion into knowledge collections")
    public ApiResponse<List<PromotableDocumentView>> listPromotableDocuments() {
        return ApiResponse.ok(knowledgeService.listPromotableDocuments(null, null, true));
    }

    @PostMapping("/collections/{collectionId}/promote-document/{documentId}")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Directly promote an OCR document into a target knowledge collection preserving [Page N] citations")
    public ApiResponse<KnowledgeSourceView> promoteDocument(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID collectionId,
            @PathVariable UUID documentId,
            @Valid @RequestBody(required = false) DocumentKnowledgePromoteRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.promoteDocumentToKnowledge(
                collectionId,
                documentId,
                request != null ? request : new DocumentKnowledgePromoteRequest(null, null, null, null, null, null, null, null, null, false),
                null,
                null,
                true,
                principal.email(),
                servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/chunk-preview")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Interactive side-by-side chunk boundary and token count previewer")
    public ApiResponse<ChunkPreviewComparisonResult> previewChunkBoundaries(
            @Valid @RequestBody ChunkPreviewRequest request
    ) {
        return ApiResponse.ok(knowledgeService.previewChunkBoundaries(request));
    }

    @GetMapping("/synonyms")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List bilingual domain synonyms (EN <-> ID)")
    public ApiResponse<List<KnowledgeSynonymView>> listSynonyms(
            @RequestParam(value = "collection_id", required = false) UUID collectionId
    ) {
        return ApiResponse.ok(synonymService.listSynonyms(collectionId, null, true));
    }

    @PostMapping("/synonyms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Create a bilingual domain synonym entry")
    public ApiResponse<KnowledgeSynonymView> createSynonym(
            @Valid @RequestBody KnowledgeSynonymRequest request
    ) {
        return ApiResponse.ok(synonymService.createSynonym(request, null, true));
    }

    @DeleteMapping("/synonyms/{id}")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Delete a bilingual domain synonym entry")
    public ApiResponse<Map<String, Object>> deleteSynonym(@PathVariable UUID id) {
        synonymService.deleteSynonym(id, null, true);
        return ApiResponse.ok(Map.of("deleted", true, "id", id));
    }

    @GetMapping("/connectors")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List scheduled/conditional feed connectors")
    public ApiResponse<List<KnowledgeConnectorView>> listConnectors(
            @RequestParam(value = "collection_id", required = false) UUID collectionId
    ) {
        return ApiResponse.ok(feedSyncService.listConnectors(collectionId, null, true));
    }

    @PostMapping("/connectors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Register a scheduled/conditional webhook or URI pull feed connector (ETag / If-Modified-Since)")
    public ApiResponse<KnowledgeConnectorView> createConnector(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody KnowledgeConnectorRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(feedSyncService.createConnector(
                request, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/connectors/{id}/sync")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Trigger conditional pull sync on a connector (checks ETag/Last-Modified; skips re-indexing if 304)")
    public ApiResponse<KnowledgeConnectorSyncResult> syncConnector(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(value = "force", defaultValue = "false") boolean force,
            @RequestParam(value = "simulated_revision", required = false) String simulatedRevision,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(feedSyncService.syncConnector(
                id, force, simulatedRevision, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/feedback")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List RAG answer user/operator feedback queue")
    public ApiResponse<List<KnowledgeFeedbackView>> listFeedback(
            @RequestParam(value = "collection_id", required = false) UUID collectionId,
            @RequestParam(value = "rating", required = false) String rating
    ) {
        return ApiResponse.ok(knowledgeService.listFeedback(collectionId, rating, null, true));
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Submit RAG answer feedback from operator playground")
    public ApiResponse<KnowledgeFeedbackView> submitFeedback(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody KnowledgeFeedbackRequest request
    ) {
        return ApiResponse.ok(knowledgeService.submitFeedback(request, null, null, principal.email(), true));
    }

    @PostMapping("/feedback/{id}/promote-eval")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Promote a low-rated or citation-mismatch feedback query into the evaluation benchmark suite")
    public ApiResponse<KnowledgeEvalCaseEntity> promoteFeedbackToEval(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) FeedbackPromoteEvalRequest request
    ) {
        return ApiResponse.ok(knowledgeService.promoteFeedbackToEvalCase(
                id, request, null, true, principal.email()
        ));
    }

    @GetMapping("/collections")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List all knowledge collections with source, chunk, stale, and glossary counts")
    public ApiResponse<List<KnowledgeCollectionView>> listCollections() {
        return ApiResponse.ok(knowledgeService.listCollections(null, null, true));
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Create a knowledge collection from the Admin Collection Wizard")
    public ApiResponse<KnowledgeCollectionView> createCollection(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody KnowledgeCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.createCollection(
                request,
                request.applicationId(),
                request.tenantId(),
                true,
                principal.email(),
                servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/collections/{collectionId}/sources")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List sources, versions, indexing status, error details, and stale badges in a collection")
    public ApiResponse<List<KnowledgeSourceView>> listSources(@PathVariable UUID collectionId) {
        return ApiResponse.ok(knowledgeService.listSources(collectionId, null, null, true));
    }

    @PostMapping("/collections/{collectionId}/sources")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Ingest or incrementally update a source in a collection")
    public ApiResponse<KnowledgeSourceView> ingestSource(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID collectionId,
            @Valid @RequestBody KnowledgeSourceIngestRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.ingestOrUpdateSource(
                collectionId,
                request,
                null,
                null,
                true,
                principal.email(),
                servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/sources/{sourceId}/reindex")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Force full re-indexing of a source")
    public ApiResponse<KnowledgeSourceView> reindexSource(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.forceReindexSource(
                sourceId, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/sources/{sourceId}/revoke")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Revoke a source and immediately purge all chunks, embeddings, and glossary term links")
    public ApiResponse<KnowledgeSourceView> revokeSource(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.revokeSource(
                sourceId, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @DeleteMapping("/sources/{sourceId}")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Delete a source and propagate purge across chunks and embeddings")
    public ApiResponse<Map<String, Object>> deleteSource(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.deleteSource(
                sourceId, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/sources/{sourceId}/history")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Inspect source version and re-index history events")
    public ApiResponse<List<KnowledgeSourceEventView>> getSourceHistory(@PathVariable UUID sourceId) {
        return ApiResponse.ok(knowledgeService.getSourceHistory(sourceId, null, null, true));
    }

    @GetMapping("/stale-sources")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List indexed knowledge sources exceeding their collection freshness TTL")
    public ApiResponse<List<KnowledgeSourceView>> listStaleSources() {
        return ApiResponse.ok(knowledgeService.listStaleSources(null, null, true));
    }

    @PostMapping("/retrieve")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Operator Retrieval Debugger with simulated role ACLs, metadata filters, score breakdown, and candidate funnel trace")
    public ApiResponse<KnowledgeRetrievalResult> debugRetrieve(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody KnowledgeRetrievalRequest request
    ) {
        List<String> effectiveRoles = (request.simulatedRoles() != null && !request.simulatedRoles().isEmpty())
                ? request.simulatedRoles()
                : List.of("OWNER", "WORKER", "MEMBER");

        KnowledgeRetrievalRequest withDebug = new KnowledgeRetrievalRequest(
                request.collectionId(),
                request.collectionCode(),
                request.query(),
                request.retrievalMode(),
                request.topK(),
                request.minScore(),
                request.vectorWeight(),
                request.keywordWeight(),
                request.rerank(),
                request.includeStale(),
                request.expandParentContext(),
                request.metadataFilters(),
                request.debug() != null ? request.debug() : true,
                effectiveRoles,
                request.simulatedUserRef(),
                request.applicationId()
        );

        HybridRetrieverService.RetrievalAuthContext authCtx = new HybridRetrieverService.RetrievalAuthContext(
                request.applicationId(),
                "admin_debugger",
                null,
                request.simulatedUserRef() != null ? request.simulatedUserRef() : principal.email(),
                effectiveRoles,
                true,
                true
        );

        return ApiResponse.ok(knowledgeService.retrieve(withDebug, authCtx, true));
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Interactive RAG Grounded Answer Playground with simulated roles, citations, and glossary linking")
    public ApiResponse<KnowledgeAnswerResult> debugAsk(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody KnowledgeAskRequest request,
            HttpServletRequest servletRequest
    ) {
        List<String> effectiveRoles = (request.simulatedRoles() != null && !request.simulatedRoles().isEmpty())
                ? request.simulatedRoles()
                : List.of("OWNER", "WORKER", "MEMBER");

        KnowledgeAskRequest withDebug = new KnowledgeAskRequest(
                request.collectionId(),
                request.collectionCode(),
                request.question(),
                request.locale(),
                request.retrievalMode(),
                request.topK(),
                request.rerank(),
                request.expandParentContext(),
                request.metadataFilters(),
                request.modelOverride(),
                request.debug() != null ? request.debug() : true,
                effectiveRoles,
                request.simulatedUserRef(),
                request.applicationId()
        );

        HybridRetrieverService.RetrievalAuthContext authCtx = new HybridRetrieverService.RetrievalAuthContext(
                request.applicationId(),
                "admin_debugger",
                null,
                request.simulatedUserRef() != null ? request.simulatedUserRef() : principal.email(),
                effectiveRoles,
                true,
                true
        );

        return ApiResponse.ok(knowledgeService.ask(withDebug, authCtx, null, true, servletRequest.getRemoteAddr()));
    }

    @GetMapping("/collections/{collectionId}/glossary")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List glossary & encyclopedia terms for a collection")
    public ApiResponse<List<GlossaryTermView>> listGlossaryTerms(
            @PathVariable UUID collectionId,
            @RequestParam(value = "locale", required = false) String locale
    ) {
        return ApiResponse.ok(knowledgeService.listGlossaryTerms(collectionId, locale, null, null, true));
    }

    @PostMapping("/glossary/terms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Create a manual glossary/encyclopedia term")
    public ApiResponse<GlossaryTermView> createGlossaryTerm(@Valid @RequestBody GlossaryTermRequest request) {
        return ApiResponse.ok(knowledgeService.createGlossaryTerm(request, null, null, true));
    }

    @PostMapping("/glossary/link")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Preview glossary/encyclopedia term linking across arbitrary EN/ID text")
    public ApiResponse<GlossaryLinkResult> linkGlossaryTerms(@Valid @RequestBody GlossaryLinkRequest request) {
        return ApiResponse.ok(knowledgeService.linkGlossaryTerms(request, null, null, true));
    }

    @GetMapping("/collections/{collectionId}/export")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Export knowledge collection bundle as JSON")
    public ApiResponse<KnowledgeExportBundle> exportCollection(@PathVariable UUID collectionId) {
        return ApiResponse.ok(knowledgeService.exportCollection(collectionId, null, null, true));
    }

    @PostMapping("/collections/import")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Import knowledge collection bundle from JSON")
    public ApiResponse<KnowledgeCollectionView> importCollection(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(value = "target_collection_code", required = false) String targetCollectionCode,
            @Valid @RequestBody KnowledgeExportBundle bundle,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(knowledgeService.importCollection(
                bundle, targetCollectionCode, null, null, true, principal.email(), servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/benchmarks/run")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Execute RAG retrieval relevance & permission-regression benchmark suite (Hit@K, MRR, Precision@K, Citation Accuracy, Permission Pass Rate)")
    public ApiResponse<KnowledgeEvalResult> runBenchmarks() {
        return ApiResponse.ok(knowledgeService.runEvaluationBenchmarks());
    }

    @PostMapping("/retention/purge")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Immediately purge expired knowledge sources, chunks, and embeddings")
    public ApiResponse<Map<String, Object>> purgeExpired() {
        return ApiResponse.ok(retentionService.cleanupExpired(Instant.now()));
    }
}

