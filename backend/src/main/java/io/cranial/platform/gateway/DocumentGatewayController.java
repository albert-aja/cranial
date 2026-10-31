package io.cranial.platform.gateway;

import io.cranial.platform.application.capability.CapabilityEntitlementService;
import io.cranial.platform.document.model.DocumentBatchEstimateRequest;
import io.cranial.platform.document.model.DocumentBatchEstimateResult;
import io.cranial.platform.document.model.DocumentCompareRequest;
import io.cranial.platform.document.model.DocumentComparisonResult;
import io.cranial.platform.document.model.DocumentExtractionView;
import io.cranial.platform.document.model.DocumentJobView;
import io.cranial.platform.document.model.DocumentProcessRequest;
import io.cranial.platform.document.model.DocumentView;
import io.cranial.platform.document.model.ExtractionReviewRequest;
import io.cranial.platform.document.model.PreprocessOptions;
import io.cranial.platform.document.model.TransferUrlRequest;
import io.cranial.platform.document.model.TransferUrlView;
import io.cranial.platform.document.service.DocumentIntelligenceService;
import io.cranial.platform.document.service.DocumentRetentionService;
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
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Intelligence Gateway", description = "Secure OCR, PDF page-range parsing, structured extraction, provenance, comparison, and retention")
@SecurityRequirement(name = OpenApiConfig.SCHEME_APP_API_KEY)
public class DocumentGatewayController {

    private static final String CAPABILITY_CODE = "document_intelligence";

    private final DocumentIntelligenceService documentService;
    private final DocumentRetentionService retentionService;
    private final CapabilityEntitlementService entitlementService;

    public DocumentGatewayController(
            DocumentIntelligenceService documentService,
            DocumentRetentionService retentionService,
            CapabilityEntitlementService entitlementService
    ) {
        this.documentService = documentService;
        this.retentionService = retentionService;
        this.entitlementService = entitlementService;
    }

    @PostMapping
    @Operation(summary = "Ingest and process a document (JSON Base64 or text) with OCR, preprocessing, classification, and schema validation")
    public ApiResponse<DocumentView> processDocument(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody DocumentProcessRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        if (request.applicationId() != null && !request.applicationId().equals(ctx.applicationId())) {
            throw new CranialException(ErrorCode.APP_IDENTITY_MISMATCH, "Payload application_id does not match authenticated credential");
        }
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);

        DocumentView view = documentService.intakeAndProcessRequest(
                ctx.applicationId(),
                ctx.applicationCode(),
                ctx.tenantId(),
                ctx.externalUserRef(),
                request,
                ip
        );
        return ApiResponse.ok(view);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Multipart file intake for OCR and structured extraction")
    public ApiResponse<DocumentView> uploadMultipart(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ocr_language", required = false) String ocrLanguage,
            @RequestParam(value = "document_type_hint", required = false) String documentTypeHint,
            @RequestParam(value = "page_range", required = false) String pageRange,
            @RequestParam(value = "rotate_degrees", required = false) Integer rotateDegrees,
            @RequestParam(value = "async", required = false) Boolean async,
            HttpServletRequest servletRequest
    ) throws IOException {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);

        DocumentProcessRequest opts = new DocumentProcessRequest(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin",
                file.getContentType(),
                null,
                null,
                documentTypeHint,
                ocrLanguage,
                pageRange,
                new PreprocessOptions(rotateDegrees, true, true, true),
                null,
                168,
                async,
                ctx.applicationId(),
                null
        );

        DocumentView view = documentService.intakeAndProcessBytes(
                ctx.applicationId(),
                ctx.applicationCode(),
                ctx.tenantId(),
                ctx.externalUserRef(),
                opts.fileName(),
                file.getContentType(),
                file.getBytes(),
                opts,
                ip
        );
        return ApiResponse.ok(view);
    }

    @GetMapping
    @Operation(summary = "List application-scoped documents")
    public ApiResponse<List<DocumentView>> listDocuments(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "25") int limit
    ) {
        return ApiResponse.ok(documentService.listDocuments(ctx.applicationId(), ctx.tenantId(), status, limit));
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Retrieve document metadata, page-level OCR diagnostics, and extraction preview")
    public ApiResponse<DocumentView> getDocument(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID documentId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(documentService.getDocument(
                documentId,
                ctx.applicationId(),
                ctx.tenantId(),
                true,
                null,
                servletRequest.getRemoteAddr()
        ));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete document binary blob and scrub extracted text immediately")
    public ApiResponse<Map<String, Object>> deleteDocument(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID documentId,
            HttpServletRequest servletRequest
    ) {
        retentionService.deleteDocument(
                documentId,
                ctx.applicationId(),
                ctx.tenantId(),
                "APPLICATION",
                ctx.applicationCode(),
                servletRequest.getRemoteAddr()
        );
        return ApiResponse.ok(Map.of("document_id", documentId, "deleted", true));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Poll durable document processing job status and page checkpoints")
    public ApiResponse<DocumentJobView> getJob(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(documentService.getJob(jobId, ctx.applicationId(), ctx.tenantId()));
    }

    @PostMapping("/jobs/{jobId}/resume")
    @Operation(summary = "Resume a FAILED or PARTIAL_SUCCESS document job from its last completed page checkpoint")
    public ApiResponse<DocumentJobView> resumeJob(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID jobId,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(documentService.resumeJob(jobId, ctx.applicationId(), ctx.tenantId(), ctx.applicationCode(), ip));
    }

    @PostMapping("/compare")
    @Operation(summary = "Compare two documents and detect field, line-item, and text changes")
    public ApiResponse<DocumentComparisonResult> compareDocuments(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody DocumentCompareRequest request,
            HttpServletRequest servletRequest
    ) {
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, servletRequest.getRemoteAddr());
        return ApiResponse.ok(documentService.compareDocuments(request, ctx.applicationId(), ctx.tenantId()));
    }

    @PostMapping("/estimate")
    @Operation(summary = "Estimate processing duration, memory, and zero-cloud-cost local compute before batch jobs")
    public ApiResponse<DocumentBatchEstimateResult> estimateBatch(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody DocumentBatchEstimateRequest request
    ) {
        return ApiResponse.ok(documentService.estimateBatch(request));
    }

    @PostMapping("/upload-urls")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a scoped, expiring temporary signed URL for direct binary upload")
    public ApiResponse<TransferUrlView> createUploadUrl(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @Valid @RequestBody TransferUrlRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        entitlementService.assertEntitled(ctx.applicationId(), CAPABILITY_CODE, ip);
        return ApiResponse.ok(documentService.createTemporaryUploadSession(
                ctx.applicationId(),
                ctx.tenantId(),
                ctx.externalUserRef(),
                request,
                ip
        ));
    }

    @PostMapping("/{documentId}/download-url")
    @Operation(summary = "Issue a scoped, expiring temporary signed URL for secure document download")
    public ApiResponse<TransferUrlView> createDownloadUrl(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID documentId,
            @RequestParam(value = "ttl_minutes", required = false) Integer ttlMinutes,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(documentService.createTemporaryDownloadUrl(
                documentId,
                ctx.applicationId(),
                ctx.tenantId(),
                ttlMinutes,
                "APPLICATION",
                ctx.applicationCode(),
                servletRequest.getRemoteAddr()
        ));
    }

    @PostMapping("/extractions/{extractionId}/feedback")
    @Operation(summary = "Submit application/user extraction correction feedback and optional ground-truth promotion")
    public ApiResponse<DocumentExtractionView> submitFeedback(
            @AuthenticationPrincipal VerifiedRequestContext ctx,
            @PathVariable UUID extractionId,
            @Valid @RequestBody ExtractionReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(documentService.submitReviewDecision(
                extractionId,
                ctx.applicationId(),
                ctx.tenantId(),
                request,
                "APPLICATION",
                ctx.applicationCode(),
                servletRequest.getRemoteAddr()
        ));
    }
}

