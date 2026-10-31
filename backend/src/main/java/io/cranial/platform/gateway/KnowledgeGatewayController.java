package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.knowledge.model.DocumentKnowledgePromoteRequest;
import io.cranial.platform.knowledge.model.GlossaryLinkRequest;
import io.cranial.platform.knowledge.model.GlossaryLinkResult;
import io.cranial.platform.knowledge.model.GlossaryTermRequest;
import io.cranial.platform.knowledge.model.GlossaryTermView;
import io.cranial.platform.knowledge.model.KnowledgeAnswerResult;
import io.cranial.platform.knowledge.model.KnowledgeAskRequest;
import io.cranial.platform.knowledge.model.KnowledgeCollectionRequest;
import io.cranial.platform.knowledge.model.KnowledgeCollectionView;
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
import io.cranial.platform.knowledge.synonym.KnowledgeSynonymService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.security.VerifiedRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Intelligence & RAG Gateway", description = "Permission-aware hybrid retrieval, incremental indexing, citations, glossary linking, and grounded answers")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class KnowledgeGatewayController {

    private static final String CAPABILITY_CODE = "knowledge_intelligence";

    private final KnowledgeIntelligenceService knowledgeService;
    private final KnowledgeSynonymService synonymService;
    private final CapabilityEntitlementService entitlementService;

    public KnowledgeGatewayController(
            KnowledgeIntelligenceService knowledgeService,
            KnowledgeSynonymService synonymService,
            CapabilityEntitlementService entitlementService
    ) {
        this.knowledgeService = knowledgeService;
        this.synonymService = synonymService;
        this.entitlementService = entitlementService;
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an application-scoped or tenant-scoped knowledge collection")
    public ApiResponse<KnowledgeCollectionView> createCollection(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody KnowledgeCollectionRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        assertMatchingAppId(request.applicationId(), ctx);
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);

        return ApiResponse.ok(knowledgeService.createCollection(
                request,
                ctx.applicationId(),
                ctx.tenantId(),
                false,
                ctx.applicationCode(),
                ip
        ));
    }

    @GetMapping("/collections")
    @Operation(summary = "List accessible knowledge collections for the authenticated application/tenant")
    public ApiResponse<List<KnowledgeCollectionView>> listCollections(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.listCollections(ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/collections/{collectionId}/sources")
    @Operation(summary = "Ingest or incrementally update a knowledge source with configurable chunking and permissions")
    public ApiResponse<KnowledgeSourceView> ingestSource(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID collectionId,
            @Valid @RequestBody KnowledgeSourceIngestRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(knowledgeService.ingestOrUpdateSource(
                collectionId,
                request,
                ctx.applicationId(),
                ctx.tenantId(),
                false,
                ctx.applicationCode(),
                ip
        ));
    }

    @GetMapping("/collections/{collectionId}/sources")
    @Operation(summary = "List knowledge sources, indexing status, and freshness state for a collection")
    public ApiResponse<List<KnowledgeSourceView>> listSources(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID collectionId,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.listSources(collectionId, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/sources/{sourceId}/reindex")
    @Operation(summary = "Force full re-chunking and re-embedding of an existing knowledge source")
    public ApiResponse<KnowledgeSourceView> forceReindex(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(knowledgeService.forceReindexSource(
                sourceId, ctx.applicationId(), ctx.tenantId(), false, ctx.applicationCode(), ip
        ));
    }

    @PostMapping("/sources/{sourceId}/revoke")
    @Operation(summary = "Revoke a knowledge source and immediately propagate deletion of all chunks, embeddings, and glossary links")
    public ApiResponse<KnowledgeSourceView> revokeSource(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(knowledgeService.revokeSource(
                sourceId, ctx.applicationId(), ctx.tenantId(), false, ctx.applicationCode(), ip
        ));
    }

    @DeleteMapping("/sources/{sourceId}")
    @Operation(summary = "Delete a knowledge source and propagate purge across chunks, embeddings, and glossary entries")
    public ApiResponse<Map<String, Object>> deleteSource(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(knowledgeService.deleteSource(
                sourceId, ctx.applicationId(), ctx.tenantId(), false, ctx.applicationCode(), ip
        ));
    }

    @GetMapping("/sources/{sourceId}/history")
    @Operation(summary = "Inspect source versioning and incremental re-index audit history")
    public ApiResponse<List<KnowledgeSourceEventView>> getSourceHistory(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID sourceId,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.getSourceHistory(sourceId, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/retrieve")
    @Operation(summary = "Execute permission-aware hybrid BM25 + vector retrieval with metadata filtering, reranking, and citations")
    public ApiResponse<KnowledgeRetrievalResult> retrieve(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody KnowledgeRetrievalRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        assertMatchingAppId(request.applicationId(), ctx);
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);

        // Security invariant: Gateway NEVER trusts simulated_roles/simulated_user_ref from payload;
        // roles and userRef come strictly from cryptographically verified X-Cranial-Context!
        HybridRetrieverService.RetrievalAuthContext authCtx = new HybridRetrieverService.RetrievalAuthContext(
                ctx.applicationId(),
                ctx.applicationCode(),
                ctx.tenantId(),
                ctx.externalUserRef(),
                ctx.roles() != null ? ctx.roles() : List.of(),
                true,
                false
        );

        return ApiResponse.ok(knowledgeService.retrieve(request, authCtx, false));
    }

    @PostMapping("/ask")
    @Operation(summary = "Generate a grounded answer from authorized knowledge chunks with citations, glossary links, and abstention")
    public ApiResponse<KnowledgeAnswerResult> ask(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody KnowledgeAskRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        assertMatchingAppId(request.applicationId(), ctx);
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);

        HybridRetrieverService.RetrievalAuthContext authCtx = new HybridRetrieverService.RetrievalAuthContext(
                ctx.applicationId(),
                ctx.applicationCode(),
                ctx.tenantId(),
                ctx.externalUserRef(),
                ctx.roles() != null ? ctx.roles() : List.of(),
                true,
                false
        );

        return ApiResponse.ok(knowledgeService.ask(request, authCtx, ctx, false, ip));
    }

    @GetMapping("/promotable-documents")
    @Operation(summary = "List documents with OCR text eligible for promotion into knowledge collections")
    public ApiResponse<List<PromotableDocumentView>> listPromotableDocuments(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.listPromotableDocuments(ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/collections/{collectionId}/promote-document/{documentId}")
    @Operation(summary = "Promote OCR-extracted document pages directly into a target knowledge collection preserving [Page N] citations")
    public ApiResponse<KnowledgeSourceView> promoteDocument(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID collectionId,
            @PathVariable UUID documentId,
            @Valid @RequestBody(required = false) DocumentKnowledgePromoteRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(knowledgeService.promoteDocumentToKnowledge(
                collectionId,
                documentId,
                request != null ? request : new DocumentKnowledgePromoteRequest(null, null, null, null, null, null, null, null, null, false),
                ctx.applicationId(),
                ctx.tenantId(),
                false,
                ctx.applicationCode(),
                ip
        ));
    }

    @PostMapping("/feedback")
    @Operation(summary = "Submit user/operator feedback on RAG answers (helpful, unhelpful, citation mismatch, missing info)")
    public ApiResponse<KnowledgeFeedbackView> submitFeedback(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody KnowledgeFeedbackRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        assertMatchingAppId(request.applicationId(), ctx);
        return ApiResponse.ok(knowledgeService.submitFeedback(
                request,
                ctx.applicationId(),
                ctx.tenantId(),
                ctx.externalUserRef(),
                false
        ));
    }

    @GetMapping("/synonyms")
    @Operation(summary = "List active bilingual domain synonyms (EN <-> ID)")
    public ApiResponse<List<KnowledgeSynonymView>> listSynonyms(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(value = "collection_id", required = false) UUID collectionId,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(synonymService.listSynonyms(collectionId, ctx.applicationId(), false));
    }

    @PostMapping("/synonyms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register custom bilingual domain synonyms for an application/collection")
    public ApiResponse<KnowledgeSynonymView> createSynonym(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody KnowledgeSynonymRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        assertMatchingAppId(request.applicationId(), ctx);
        return ApiResponse.ok(synonymService.createSynonym(request, ctx.applicationId(), false));
    }

    @PostMapping("/glossary/terms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a glossary or encyclopedia term entry for educational term linking")
    public ApiResponse<GlossaryTermView> createGlossaryTerm(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody GlossaryTermRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.createGlossaryTerm(request, ctx.applicationId(), ctx.tenantId(), false));
    }

    @GetMapping("/collections/{collectionId}/glossary")
    @Operation(summary = "List glossary/encyclopedia terms for a collection")
    public ApiResponse<List<GlossaryTermView>> listGlossaryTerms(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID collectionId,
            @RequestParam(value = "locale", required = false) String locale,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.listGlossaryTerms(collectionId, locale, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/glossary/link")
    @Operation(summary = "Detect and link educational or money-management glossary terms inside arbitrary text")
    public ApiResponse<GlossaryLinkResult> linkGlossaryTerms(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody GlossaryLinkRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.linkGlossaryTerms(request, ctx.applicationId(), ctx.tenantId(), false));
    }

    @GetMapping("/collections/{collectionId}/export")
    @Operation(summary = "Export collection configuration, indexed sources, and glossary terms as a portable JSON bundle")
    public ApiResponse<KnowledgeExportBundle> exportCollection(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID collectionId,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(knowledgeService.exportCollection(collectionId, ctx.applicationId(), ctx.tenantId(), false));
    }

    @PostMapping("/collections/import")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import a portable knowledge export bundle and index all contained sources")
    public ApiResponse<KnowledgeCollectionView> importCollection(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(value = "target_collection_code", required = false) String targetCollectionCode,
            @Valid @RequestBody KnowledgeExportBundle bundle,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(knowledgeService.importCollection(
                bundle, targetCollectionCode, ctx.applicationId(), ctx.tenantId(), false, ctx.applicationCode(), ip
        ));
    }

    private void assertMatchingAppId(UUID payloadAppId, VerifiedRequestContext ctx) {
        if (payloadAppId != null && !payloadAppId.equals(ctx.applicationId())) {
            throw new CranialException(ErrorCode.APP_IDENTITY_MISMATCH,
                    "Payload application_id does not match authenticated credential");
        }
    }
}

