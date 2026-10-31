package io.cranial.platform.admin;

import io.cranial.platform.document.model.DocumentBatchEstimateRequest;
import io.cranial.platform.document.model.DocumentBatchEstimateResult;
import io.cranial.platform.document.model.DocumentCompareRequest;
import io.cranial.platform.document.model.DocumentComparisonResult;
import io.cranial.platform.document.model.DocumentExtractionView;
import io.cranial.platform.document.model.DocumentJobView;
import io.cranial.platform.document.model.DocumentProcessRequest;
import io.cranial.platform.document.model.DocumentView;
import io.cranial.platform.document.model.ExtractionConfigRequest;
import io.cranial.platform.document.model.ExtractionConfigView;
import io.cranial.platform.document.model.ExtractionReviewRequest;
import io.cranial.platform.document.model.OcrBenchmarkResult;
import io.cranial.platform.document.service.DocumentIntelligenceService;
import io.cranial.platform.document.service.DocumentRetentionService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.cranial.platform.security.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/documents")
@Tag(name = "Admin — Document Intelligence & OCR", description = "Operator playground, human review queue, extraction configs, benchmarks, and retention governance")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminDocumentController {

    private final DocumentIntelligenceService documentService;
    private final DocumentRetentionService retentionService;

    public AdminDocumentController(
            DocumentIntelligenceService documentService,
            DocumentRetentionService retentionService
    ) {
        this.documentService = documentService;
        this.retentionService = retentionService;
    }

    @PostMapping("/playground")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Run interactive OCR & document extraction playground with short retention")
    public ApiResponse<DocumentView> runPlayground(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody DocumentProcessRequest request,
            HttpServletRequest servletRequest
    ) {
        DocumentProcessRequest scopedReq = new DocumentProcessRequest(
                request.fileName(),
                request.mimeType(),
                request.contentBase64(),
                request.rawText(),
                request.documentTypeHint(),
                request.ocrLanguage(),
                request.pageRange(),
                request.preprocess(),
                request.extractionSchemaCode(),
                request.retentionHours() != null ? request.retentionHours() : 24,
                false,
                request.applicationId(),
                request.simulateFailPages()
        );
        DocumentView view = documentService.intakeAndProcessRequest(
                request.applicationId(),
                principal.email(),
                null,
                principal.email(),
                scopedReq,
                servletRequest.getRemoteAddr()
        );
        return ApiResponse.ok(view);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List processed documents across applications (raw content redacted by default)")
    public ApiResponse<List<DocumentView>> listDocuments(
            @RequestParam(value = "applicationId", required = false) UUID applicationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(documentService.listDocuments(applicationId, null, status, limit));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Inspect document extraction details; revealing raw page text requires operator role and emits an audit log")
    public ApiResponse<DocumentView> getDocument(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID documentId,
            @RequestParam(value = "revealContent", defaultValue = "false") boolean revealContent,
            HttpServletRequest servletRequest
    ) {
        boolean canReveal = revealContent && (principal.roles().contains("SUPER_ADMIN") || principal.roles().contains("PLATFORM_OPERATOR"));
        return ApiResponse.ok(documentService.getDocument(
                documentId,
                null,
                null,
                canReveal,
                canReveal ? principal.email() : null,
                servletRequest.getRemoteAddr()
        ));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('app:write')")
    @Operation(summary = "Purge document blob and scrub text immediately")
    public ApiResponse<Map<String, Object>> deleteDocument(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID documentId,
            HttpServletRequest servletRequest
    ) {
        retentionService.deleteDocument(
                documentId,
                null,
                null,
                "OPERATOR",
                principal.email(),
                servletRequest.getRemoteAddr()
        );
        return ApiResponse.ok(Map.of("document_id", documentId, "deleted", true));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List durable & resumable document processing jobs")
    public ApiResponse<List<DocumentJobView>> listJobs(
            @RequestParam(value = "applicationId", required = false) UUID applicationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(documentService.listJobs(applicationId, status, limit));
    }

    @PostMapping("/jobs/{jobId}/resume")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Resume failed or partial-success document job from last checkpoint")
    public ApiResponse<DocumentJobView> resumeJob(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID jobId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(documentService.resumeJob(jobId, null, null, principal.email(), servletRequest.getRemoteAddr()));
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List low-confidence or incomplete extractions in the human review queue")
    public ApiResponse<List<DocumentExtractionView>> listReviewQueue(
            @RequestParam(value = "status", defaultValue = "PENDING_REVIEW") String status,
            @RequestParam(value = "limit", defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(documentService.listReviewQueue(status, limit));
    }

    @PostMapping("/review-queue/{extractionId}/decide")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Approve, correct, or reject an extraction and optionally promote to ground truth")
    public ApiResponse<DocumentExtractionView> decideReview(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID extractionId,
            @Valid @RequestBody ExtractionReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(documentService.submitReviewDecision(
                extractionId,
                null,
                null,
                request,
                "OPERATOR",
                principal.email(),
                servletRequest.getRemoteAddr()
        ));
    }

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "List configurable extraction schemas per application and document type")
    public ApiResponse<List<ExtractionConfigView>> listConfigs() {
        return ApiResponse.ok(documentService.listExtractionConfigs());
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('capability:manage') or hasAuthority('app:write')")
    @Operation(summary = "Create or update extraction schema configuration for an application and document type")
    public ApiResponse<ExtractionConfigView> saveConfig(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody ExtractionConfigRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(documentService.saveExtractionConfig(request, principal.email(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/compare")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Compare two documents and inspect field, line-item, and text diffs")
    public ApiResponse<DocumentComparisonResult> compare(
            @Valid @RequestBody DocumentCompareRequest request
    ) {
        return ApiResponse.ok(documentService.compareDocuments(request, null, null));
    }

    @PostMapping("/estimate")
    @PreAuthorize("hasAuthority('app:read')")
    @Operation(summary = "Estimate compute resources and execution mode for batch document jobs")
    public ApiResponse<DocumentBatchEstimateResult> estimate(
            @Valid @RequestBody DocumentBatchEstimateRequest request
    ) {
        return ApiResponse.ok(documentService.estimateBatch(request));
    }

    @PostMapping("/benchmarks/run")
    @PreAuthorize("hasAuthority('app:write') or hasAuthority('capability:manage')")
    @Operation(summary = "Execute OCR & structured extraction regression suite across receipts, invoices, delivery notes, and forms")
    public ApiResponse<OcrBenchmarkResult> runBenchmarks() {
        return ApiResponse.ok(documentService.runRegressionBenchmarks());
    }

    @PostMapping("/retention/purge")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Run immediate retention cleanup for expired documents and temporary transfer URLs")
    public ApiResponse<Map<String, Object>> purgeExpired() {
        return ApiResponse.ok(retentionService.cleanupExpired(Instant.now()));
    }
}

