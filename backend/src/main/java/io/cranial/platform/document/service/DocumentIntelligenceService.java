package io.cranial.platform.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.featureflag.FeatureFlagService;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.document.config.DocumentProperties;
import io.cranial.platform.document.entity.DocumentEntity;
import io.cranial.platform.document.entity.DocumentEvalCaseEntity;
import io.cranial.platform.document.entity.DocumentExtractionConfigEntity;
import io.cranial.platform.document.entity.DocumentExtractionEntity;
import io.cranial.platform.document.entity.DocumentJobEntity;
import io.cranial.platform.document.entity.DocumentPageEntity;
import io.cranial.platform.document.entity.DocumentTransferTokenEntity;
import io.cranial.platform.document.extraction.DocumentClassifier;
import io.cranial.platform.document.extraction.StructuredFieldExtractor;
import io.cranial.platform.document.extraction.TableAndLineItemExtractor;
import io.cranial.platform.document.mapper.DocumentEvalCaseMapper;
import io.cranial.platform.document.mapper.DocumentExtractionConfigMapper;
import io.cranial.platform.document.mapper.DocumentExtractionMapper;
import io.cranial.platform.document.mapper.DocumentJobMapper;
import io.cranial.platform.document.mapper.DocumentMapper;
import io.cranial.platform.document.mapper.DocumentPageMapper;
import io.cranial.platform.document.model.DocumentBatchEstimateRequest;
import io.cranial.platform.document.model.DocumentBatchEstimateResult;
import io.cranial.platform.document.model.DocumentCompareRequest;
import io.cranial.platform.document.model.DocumentComparisonResult;
import io.cranial.platform.document.model.DocumentExtractionView;
import io.cranial.platform.document.model.DocumentJobView;
import io.cranial.platform.document.model.DocumentPageView;
import io.cranial.platform.document.model.DocumentProcessRequest;
import io.cranial.platform.document.model.DocumentView;
import io.cranial.platform.document.model.ExtractedFieldValue;
import io.cranial.platform.document.model.ExtractedLineItem;
import io.cranial.platform.document.model.ExtractedTable;
import io.cranial.platform.document.model.ExtractionConfigRequest;
import io.cranial.platform.document.model.ExtractionConfigView;
import io.cranial.platform.document.model.ExtractionReviewRequest;
import io.cranial.platform.document.model.OcrBenchmarkResult;
import io.cranial.platform.document.model.PreprocessOptions;
import io.cranial.platform.document.model.TransferUrlRequest;
import io.cranial.platform.document.model.TransferUrlView;
import io.cranial.platform.document.ocr.DocumentParserService;
import io.cranial.platform.document.security.DocumentSecurityValidator;
import io.cranial.platform.document.security.DocumentStorageService;
import io.cranial.platform.document.security.DocumentTransferTokenService;
import io.cranial.platform.document.security.UntrustedDocumentGuard;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.mapper.SchemaMapper;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import io.cranial.platform.orchestration.schema.SchemaValidator;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.platform.observability.CorrelationIdHolder;
import io.cranial.platform.platform.observability.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class DocumentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceService.class);
    private static final String FLAG_ADVANCED_PREPROCESS = "ocr.preprocessing.advanced";

    private final DocumentMapper documentMapper;
    private final DocumentPageMapper pageMapper;
    private final DocumentExtractionConfigMapper configMapper;
    private final DocumentExtractionMapper extractionMapper;
    private final DocumentJobMapper jobMapper;
    private final DocumentEvalCaseMapper evalCaseMapper;
    private final SchemaMapper schemaMapper;
    private final SchemaValidator schemaValidator;
    private final DocumentSecurityValidator securityValidator;
    private final UntrustedDocumentGuard untrustedGuard;
    private final DocumentStorageService storageService;
    private final DocumentTransferTokenService transferTokenService;
    private final DocumentParserService parserService;
    private final DocumentClassifier classifier;
    private final TableAndLineItemExtractor tableExtractor;
    private final StructuredFieldExtractor fieldExtractor;
    private final FeatureFlagService featureFlagService;
    private final CryptoService cryptoService;
    private final AuditService auditService;
    private final PlatformMetrics metrics;
    private final DocumentProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor documentTaskExecutor;

    public DocumentIntelligenceService(
            DocumentMapper documentMapper,
            DocumentPageMapper pageMapper,
            DocumentExtractionConfigMapper configMapper,
            DocumentExtractionMapper extractionMapper,
            DocumentJobMapper jobMapper,
            DocumentEvalCaseMapper evalCaseMapper,
            SchemaMapper schemaMapper,
            SchemaValidator schemaValidator,
            DocumentSecurityValidator securityValidator,
            UntrustedDocumentGuard untrustedGuard,
            DocumentStorageService storageService,
            DocumentTransferTokenService transferTokenService,
            DocumentParserService parserService,
            DocumentClassifier classifier,
            TableAndLineItemExtractor tableExtractor,
            StructuredFieldExtractor fieldExtractor,
            FeatureFlagService featureFlagService,
            CryptoService cryptoService,
            AuditService auditService,
            PlatformMetrics metrics,
            DocumentProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("documentTaskExecutor") Executor documentTaskExecutor
    ) {
        this.documentMapper = documentMapper;
        this.pageMapper = pageMapper;
        this.configMapper = configMapper;
        this.extractionMapper = extractionMapper;
        this.jobMapper = jobMapper;
        this.evalCaseMapper = evalCaseMapper;
        this.schemaMapper = schemaMapper;
        this.schemaValidator = schemaValidator;
        this.securityValidator = securityValidator;
        this.untrustedGuard = untrustedGuard;
        this.storageService = storageService;
        this.transferTokenService = transferTokenService;
        this.parserService = parserService;
        this.classifier = classifier;
        this.tableExtractor = tableExtractor;
        this.fieldExtractor = fieldExtractor;
        this.featureFlagService = featureFlagService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.documentTaskExecutor = documentTaskExecutor;
    }

    public DocumentView intakeAndProcessRequest(
            UUID applicationId,
            String applicationCode,
            UUID tenantId,
            String externalUserRef,
            DocumentProcessRequest request,
            String ipAddress
    ) {
        byte[] rawBytes = decodeRequestBytes(request);
        return intakeAndProcessBytes(
                applicationId,
                applicationCode,
                tenantId,
                externalUserRef,
                request.fileName(),
                request.mimeType(),
                rawBytes,
                request,
                ipAddress
        );
    }

    public DocumentView intakeAndProcessBytes(
            UUID applicationId,
            String applicationCode,
            UUID tenantId,
            String externalUserRef,
            String fileName,
            String claimedMimeType,
            byte[] rawBytes,
            DocumentProcessRequest options,
            String ipAddress
    ) {
        DocumentSecurityValidator.ValidatedDocumentInput validated;
        try {
            validated = securityValidator.validate(fileName, claimedMimeType, rawBytes, null);
        } catch (CranialException ex) {
            auditService.record(new AuditEvent(
                    applicationId != null ? "APPLICATION" : "ADMIN",
                    applicationCode != null ? applicationCode : "operator",
                    applicationId,
                    tenantId,
                    "DOCUMENT_INTAKE_REJECTED",
                    "DOCUMENT",
                    fileName != null ? DocumentSecurityValidator.sanitizeFileName(fileName) : "unknown",
                    "DENIED",
                    Map.of("reason", ex.errorCode().name(), "message", ex.getMessage()),
                    ipAddress
            ));
            throw ex;
        }

        int totalPages = parserService.inspectTotalPages(rawBytes, validated.detectedMimeType());
        String pageRange = options != null ? options.pageRange() : null;
        List<Integer> targetPages = parserService.resolveTargetPages(totalPages, pageRange);

        String sha256 = cryptoService.sha256Hex(rawBytes);
        Optional<DocumentEntity> exactDup = findExactDuplicate(applicationId, tenantId, sha256, null);

        String storageKey = storageService.storeEncrypted(rawBytes);
        Instant now = Instant.now();
        int retentionHours = (options != null && options.retentionHours() != null && options.retentionHours() > 0)
                ? options.retentionHours()
                : 168;

        DocumentEntity doc = new DocumentEntity();
        doc.setId(UUID.randomUUID());
        doc.setApplicationId(applicationId);
        doc.setTenantId(tenantId);
        doc.setExternalUserRef(externalUserRef);
        doc.setFileName(validated.sanitizedFileName());
        doc.setMimeType(validated.claimedMimeType());
        doc.setDetectedMimeType(validated.detectedMimeType());
        doc.setFileSizeBytes((long) rawBytes.length);
        doc.setSha256Hash(sha256);
        doc.setStorageKey(storageKey);
        doc.setPageCount(totalPages);
        doc.setDocumentType("UNKNOWN");
        doc.setOcrLanguage(options != null ? options.effectiveOcrLanguage() : "eng+ind");
        doc.setIsDuplicate(exactDup.isPresent());
        doc.setDuplicateOfDocumentId(exactDup.map(DocumentEntity::getId).orElse(null));
        doc.setDuplicateType(exactDup.isPresent() ? "EXACT_HASH_MATCH" : "NONE");
        doc.setStatus("UPLOADED");
        doc.setRetentionUntil(now.plus(retentionHours, ChronoUnit.HOURS));
        doc.setMetadataJson("{}");
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        documentMapper.insert(doc);

        String corrId = CorrelationIdHolder.currentOrGenerate();
        DocumentJobEntity job = new DocumentJobEntity();
        job.setId(UUID.randomUUID());
        job.setDocumentId(doc.getId());
        job.setApplicationId(applicationId);
        job.setTenantId(tenantId);
        job.setCorrelationId(corrId);
        job.setStatus("QUEUED");
        job.setPageRange(pageRange);
        job.setTotalPages(targetPages.size());
        job.setProcessedPages(0);
        job.setFailedPages(0);
        job.setLastCompletedPage(0);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.setOptionsJson(toJson(buildJobOptionsMap(options)));
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobMapper.insert(job);

        boolean runAsync = options != null && Boolean.TRUE.equals(options.async());
        if (runAsync) {
            documentTaskExecutor.execute(() -> {
                try {
                    executeJob(job.getId(), rawBytes, applicationCode, options != null ? options.simulateFailPages() : null, ipAddress);
                } catch (Exception ex) {
                    log.error("Async document processing failed for job {}: {}", job.getId(), ex.getMessage());
                }
            });
            return toDocumentView(doc, true);
        }

        executeJob(job.getId(), rawBytes, applicationCode, options != null ? options.simulateFailPages() : null, ipAddress);
        DocumentEntity refreshed = documentMapper.selectById(doc.getId());
        return toDocumentView(refreshed, true);
    }

    public DocumentJobView resumeJob(
            UUID jobId,
            UUID requiredApplicationId,
            UUID requiredTenantId,
            String actorCode,
            String ipAddress
    ) {
        DocumentJobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new CranialException(ErrorCode.JOB_NOT_FOUND, "Document processing job not found: " + jobId);
        }
        if (requiredApplicationId != null && !requiredApplicationId.equals(job.getApplicationId())) {
            throw new CranialException(ErrorCode.JOB_NOT_FOUND, "Job not found in application scope");
        }
        if (requiredTenantId != null && job.getTenantId() != null && !requiredTenantId.equals(job.getTenantId())) {
            throw new CranialException(ErrorCode.TENANT_NOT_AUTHORIZED, "Job belongs to a different tenant");
        }
        if (!"FAILED".equals(job.getStatus()) && !"PARTIAL_SUCCESS".equals(job.getStatus())) {
            throw new CranialException(
                    ErrorCode.JOB_NOT_RESUMABLE,
                    "Only FAILED or PARTIAL_SUCCESS jobs can be resumed (current status: " + job.getStatus() + ")"
            );
        }
        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            throw new CranialException(ErrorCode.JOB_NOT_RESUMABLE, "Maximum job retry attempts (" + job.getMaxAttempts() + ") exhausted");
        }

        DocumentEntity doc = documentMapper.selectById(job.getDocumentId());
        if (doc == null || "DELETED".equals(doc.getStatus()) || "EXPIRED".equals(doc.getStatus())) {
            throw new CranialException(ErrorCode.DOCUMENT_EXPIRED, "Cannot resume job for deleted or expired document");
        }

        byte[] rawBytes = storageService.loadDecrypted(doc.getStorageKey());
        // On resume, do not re-inject simulated failures so recovery succeeds from checkpoint
        executeJob(job.getId(), rawBytes, actorCode, null, ipAddress);
        return toJobView(jobMapper.selectById(job.getId()));
    }

    public TransferUrlView createTemporaryUploadSession(
            UUID applicationId,
            UUID tenantId,
            String externalUserRef,
            TransferUrlRequest request,
            String ipAddress
    ) {
        Instant now = Instant.now();
        DocumentEntity placeholder = new DocumentEntity();
        placeholder.setId(UUID.randomUUID());
        placeholder.setApplicationId(applicationId);
        placeholder.setTenantId(tenantId);
        placeholder.setExternalUserRef(externalUserRef);
        placeholder.setFileName(DocumentSecurityValidator.sanitizeFileName(request.fileName()));
        placeholder.setMimeType(request.mimeType() != null ? request.mimeType() : "application/octet-stream");
        placeholder.setDetectedMimeType("application/octet-stream");
        placeholder.setFileSizeBytes(0L);
        placeholder.setSha256Hash("pending");
        placeholder.setPageCount(1);
        placeholder.setDocumentType(request.documentTypeHint() != null ? request.documentTypeHint().toUpperCase(Locale.ROOT) : "UNKNOWN");
        placeholder.setOcrLanguage(request.ocrLanguage() != null ? request.ocrLanguage() : "eng+ind");
        placeholder.setIsDuplicate(false);
        placeholder.setDuplicateType("NONE");
        placeholder.setStatus("PENDING_UPLOAD");
        placeholder.setRetentionUntil(now.plus(168, ChronoUnit.HOURS));
        placeholder.setMetadataJson("{}");
        placeholder.setCreatedAt(now);
        placeholder.setUpdatedAt(now);
        documentMapper.insert(placeholder);

        return transferTokenService.issueUploadUrl(
                applicationId,
                tenantId,
                placeholder.getId(),
                placeholder.getFileName(),
                request.mimeType(),
                request.maxBytes(),
                request.ttlMinutes(),
                ipAddress
        );
    }

    public DocumentView completeTemporaryUpload(String signedToken, byte[] rawBytes, String contentTypeHeader, String ipAddress) {
        DocumentTransferTokenEntity token = transferTokenService.verifyAndConsume(signedToken, "UPLOAD", ipAddress);
        if (rawBytes == null || rawBytes.length > token.getMaxBytes()) {
            throw new CranialException(ErrorCode.PAYLOAD_TOO_LARGE, "Upload exceeds signed URL max_bytes limit");
        }

        DocumentEntity doc = documentMapper.selectById(token.getDocumentId());
        if (doc == null || !"PENDING_UPLOAD".equals(doc.getStatus())) {
            throw new CranialException(ErrorCode.DOCUMENT_NOT_FOUND, "Upload target document is not in PENDING_UPLOAD state");
        }

        String claimedMime = contentTypeHeader != null && !contentTypeHeader.isBlank() ? contentTypeHeader : token.getAllowedMimeType();
        DocumentSecurityValidator.ValidatedDocumentInput validated =
                securityValidator.validate(doc.getFileName(), claimedMime, rawBytes, token.getMaxBytes());

        int totalPages = parserService.inspectTotalPages(rawBytes, validated.detectedMimeType());
        String sha256 = cryptoService.sha256Hex(rawBytes);
        Optional<DocumentEntity> exactDup = findExactDuplicate(doc.getApplicationId(), doc.getTenantId(), sha256, doc.getId());
        String storageKey = storageService.storeEncrypted(rawBytes);

        doc.setMimeType(validated.claimedMimeType());
        doc.setDetectedMimeType(validated.detectedMimeType());
        doc.setFileSizeBytes((long) rawBytes.length);
        doc.setSha256Hash(sha256);
        doc.setStorageKey(storageKey);
        doc.setPageCount(totalPages);
        doc.setIsDuplicate(exactDup.isPresent());
        doc.setDuplicateOfDocumentId(exactDup.map(DocumentEntity::getId).orElse(null));
        doc.setDuplicateType(exactDup.isPresent() ? "EXACT_HASH_MATCH" : "NONE");
        doc.setStatus("UPLOADED");
        doc.setUpdatedAt(Instant.now());
        documentMapper.updateById(doc);

        DocumentJobEntity job = new DocumentJobEntity();
        job.setId(UUID.randomUUID());
        job.setDocumentId(doc.getId());
        job.setApplicationId(doc.getApplicationId());
        job.setTenantId(doc.getTenantId());
        job.setCorrelationId(CorrelationIdHolder.currentOrGenerate());
        job.setStatus("QUEUED");
        job.setTotalPages(totalPages);
        job.setProcessedPages(0);
        job.setFailedPages(0);
        job.setLastCompletedPage(0);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.setOptionsJson(toJson(Map.of(
                "ocr_language", doc.getOcrLanguage(),
                "document_type_hint", "UNKNOWN".equals(doc.getDocumentType()) ? "" : doc.getDocumentType()
        )));
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobMapper.insert(job);

        executeJob(job.getId(), rawBytes, "signed-upload", null, ipAddress);
        return toDocumentView(documentMapper.selectById(doc.getId()), true);
    }

    public TransferUrlView createTemporaryDownloadUrl(
            UUID documentId,
            UUID requiredApplicationId,
            UUID requiredTenantId,
            Integer ttlMinutes,
            String actorType,
            String actorId,
            String ipAddress
    ) {
        DocumentEntity doc = requireAuthorizedDocument(documentId, requiredApplicationId, requiredTenantId);
        if ("DELETED".equals(doc.getStatus()) || "EXPIRED".equals(doc.getStatus()) || doc.getStorageKey() == null) {
            throw new CranialException(ErrorCode.DOCUMENT_EXPIRED, "Document binary is expired or deleted");
        }
        return transferTokenService.issueDownloadUrl(
                doc.getApplicationId(),
                doc.getTenantId(),
                doc.getId(),
                doc.getFileName(),
                doc.getFileSizeBytes(),
                ttlMinutes,
                actorType,
                actorId,
                ipAddress
        );
    }

    public record DownloadedDocumentPayload(
            String fileName,
            String mimeType,
            byte[] bytes
    ) {
    }

    public DownloadedDocumentPayload downloadByToken(String signedToken, String ipAddress) {
        DocumentTransferTokenEntity token = transferTokenService.verifyAndConsume(signedToken, "DOWNLOAD", ipAddress);
        DocumentEntity doc = documentMapper.selectById(token.getDocumentId());
        if (doc == null || "DELETED".equals(doc.getStatus()) || "EXPIRED".equals(doc.getStatus())) {
            throw new CranialException(ErrorCode.DOCUMENT_EXPIRED, "Document is expired or deleted");
        }
        byte[] bytes = storageService.loadDecrypted(doc.getStorageKey());
        return new DownloadedDocumentPayload(doc.getFileName(), doc.getDetectedMimeType(), bytes);
    }

    public DocumentView getDocument(
            UUID documentId,
            UUID requiredApplicationId,
            UUID requiredTenantId,
            boolean includeRawText,
            String operatorActorForAudit,
            String ipAddress
    ) {
        DocumentEntity doc = requireAuthorizedDocument(documentId, requiredApplicationId, requiredTenantId);
        if (includeRawText && operatorActorForAudit != null) {
            auditService.record(new AuditEvent(
                    "OPERATOR",
                    operatorActorForAudit,
                    doc.getApplicationId(),
                    doc.getTenantId(),
                    "OPERATOR_DOCUMENT_CONTENT_REVEALED",
                    "DOCUMENT",
                    doc.getId().toString(),
                    "SUCCESS",
                    Map.of("file_name", doc.getFileName()),
                    ipAddress
            ));
        }
        return toDocumentView(doc, includeRawText);
    }

    public List<DocumentView> listDocuments(UUID applicationId, UUID tenantId, String status, int limit) {
        LambdaQueryWrapper<DocumentEntity> q = new LambdaQueryWrapper<>();
        if (applicationId != null) {
            q.eq(DocumentEntity::getApplicationId, applicationId);
        }
        if (tenantId != null) {
            q.eq(DocumentEntity::getTenantId, tenantId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(DocumentEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        } else {
            q.ne(DocumentEntity::getStatus, "DELETED");
        }
        q.orderByDesc(DocumentEntity::getCreatedAt).last("LIMIT " + Math.min(Math.max(1, limit), 100));
        return documentMapper.selectList(q).stream()
                .map(d -> toDocumentView(d, false))
                .toList();
    }

    public DocumentJobView getJob(UUID jobId, UUID requiredApplicationId, UUID requiredTenantId) {
        DocumentJobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new CranialException(ErrorCode.JOB_NOT_FOUND, "Document job not found: " + jobId);
        }
        if (requiredApplicationId != null && !requiredApplicationId.equals(job.getApplicationId())) {
            throw new CranialException(ErrorCode.JOB_NOT_FOUND, "Document job not found in application scope");
        }
        if (requiredTenantId != null && job.getTenantId() != null && !requiredTenantId.equals(job.getTenantId())) {
            throw new CranialException(ErrorCode.TENANT_NOT_AUTHORIZED, "Document job belongs to a different tenant");
        }
        return toJobView(job);
    }

    public List<DocumentJobView> listJobs(UUID applicationId, String status, int limit) {
        LambdaQueryWrapper<DocumentJobEntity> q = new LambdaQueryWrapper<>();
        if (applicationId != null) {
            q.eq(DocumentJobEntity::getApplicationId, applicationId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(DocumentJobEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        q.orderByDesc(DocumentJobEntity::getCreatedAt).last("LIMIT " + Math.min(Math.max(1, limit), 100));
        return jobMapper.selectList(q).stream().map(this::toJobView).toList();
    }

    public DocumentComparisonResult compareDocuments(
            DocumentCompareRequest request,
            UUID requiredApplicationId,
            UUID requiredTenantId
    ) {
        DocumentEntity left = requireAuthorizedDocument(request.leftDocumentId(), requiredApplicationId, requiredTenantId);
        DocumentEntity right = requireAuthorizedDocument(request.rightDocumentId(), requiredApplicationId, requiredTenantId);

        boolean identicalBytes = Objects.equals(left.getSha256Hash(), right.getSha256Hash());
        int pageDelta = (right.getPageCount() != null ? right.getPageCount() : 0)
                - (left.getPageCount() != null ? left.getPageCount() : 0);

        String leftText = assembleFullText(left.getId());
        String rightText = assembleFullText(right.getId());
        double similarity = identicalBytes ? 1.0 : computeJaccardSimilarity(leftText, rightText);

        DocumentExtractionEntity leftExt = findLatestExtraction(left.getId()).orElse(null);
        DocumentExtractionEntity rightExt = findLatestExtraction(right.getId()).orElse(null);

        Map<String, Object> leftFields = leftExt != null ? parseMap(leftExt.getExtractedFieldsJson()) : Map.of();
        Map<String, Object> rightFields = rightExt != null ? parseMap(rightExt.getExtractedFieldsJson()) : Map.of();

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(leftFields.keySet());
        allKeys.addAll(rightFields.keySet());

        List<DocumentComparisonResult.FieldChange> fieldChanges = new ArrayList<>();
        for (String key : allKeys) {
            Object lv = leftFields.get(key);
            Object rv = rightFields.get(key);
            String changeType;
            if (lv == null && rv != null) {
                changeType = "ADDED";
            } else if (lv != null && rv == null) {
                changeType = "REMOVED";
            } else if (!valuesEquivalent(lv, rv)) {
                changeType = "MODIFIED";
            } else {
                changeType = "UNCHANGED";
            }
            fieldChanges.add(new DocumentComparisonResult.FieldChange(key, changeType, lv, rv));
        }

        List<ExtractedLineItem> leftItems = leftExt != null ? parseLineItems(leftExt.getLineItemsJson()) : List.of();
        List<ExtractedLineItem> rightItems = rightExt != null ? parseLineItems(rightExt.getLineItemsJson()) : List.of();
        List<String> lineItemChanges = summarizeLineItemChanges(leftItems, rightItems);
        List<String> textDiff = summarizeTextDiff(leftText, rightText);

        return new DocumentComparisonResult(
                left.getId(),
                right.getId(),
                left.getFileName(),
                right.getFileName(),
                identicalBytes,
                similarity,
                pageDelta,
                fieldChanges,
                lineItemChanges,
                textDiff
        );
    }

    public DocumentBatchEstimateResult estimateBatch(DocumentBatchEstimateRequest req) {
        boolean ocr = req.ocrEnabled() == null || req.ocrEnabled();
        boolean prep = req.preprocessEnabled() == null || req.preprocessEnabled();
        boolean llm = Boolean.TRUE.equals(req.llmAssistEnabled());

        long perPageMs = (ocr ? 420L : 45L) + (prep ? 110L : 0L) + (llm ? 650L : 0L);
        long estimatedDurationMs = req.estimatedPages() * perPageMs;
        int estimatedMemoryMb = Math.max(64, (int) Math.min(2048, 48 + (req.totalBytes() / (1024 * 1024)) * 4 + (ocr ? 96 : 16)));
        long estimatedTokens = llm ? req.estimatedPages() * 550L : 0L;
        String mode = (req.documentCount() > 3 || req.estimatedPages() > properties.effectiveAsyncPageThreshold())
                ? "ASYNC_RESUMABLE_BATCH"
                : "SYNC_IMMEDIATE";

        return new DocumentBatchEstimateResult(
                req.documentCount(),
                req.estimatedPages(),
                req.totalBytes(),
                estimatedDurationMs,
                estimatedMemoryMb,
                estimatedTokens,
                0L,
                true,
                mode,
                "Local-first OCR & deterministic schema extraction incur zero external cloud charges. Use ASYNC_RESUMABLE_BATCH for multi-page documents."
        );
    }

    public List<DocumentExtractionView> listReviewQueue(String reviewStatus, int limit) {
        String targetStatus = (reviewStatus != null && !reviewStatus.isBlank())
                ? reviewStatus.trim().toUpperCase(Locale.ROOT)
                : "PENDING_REVIEW";
        List<DocumentExtractionEntity> list = extractionMapper.selectList(
                new LambdaQueryWrapper<DocumentExtractionEntity>()
                        .eq(DocumentExtractionEntity::getReviewStatus, targetStatus)
                        .orderByDesc(DocumentExtractionEntity::getCreatedAt)
                        .last("LIMIT " + Math.min(Math.max(1, limit), 100))
        );
        return list.stream().map(this::toExtractionView).toList();
    }

    @Transactional
    public DocumentExtractionView submitReviewDecision(
            UUID extractionId,
            UUID requiredApplicationId,
            UUID requiredTenantId,
            ExtractionReviewRequest request,
            String actorType,
            String actorId,
            String ipAddress
    ) {
        DocumentExtractionEntity ext = extractionMapper.selectById(extractionId);
        if (ext == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Document extraction record not found: " + extractionId);
        }
        DocumentEntity doc = requireAuthorizedDocument(ext.getDocumentId(), requiredApplicationId, requiredTenantId);

        String decision = request.decision().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED", "CORRECTED", "REJECTED").contains(decision)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Invalid review decision; must be APPROVED, CORRECTED, or REJECTED");
        }

        Map<String, Object> finalFields = request.correctedFields() != null && !request.correctedFields().isEmpty()
                ? request.correctedFields()
                : parseMap(ext.getExtractedFieldsJson());

        if (ext.getSchemaId() != null && !"REJECTED".equals(decision)) {
            SchemaEntity schema = schemaMapper.selectById(ext.getSchemaId());
            if (schema != null) {
                SchemaValidationResult val = schemaValidator.validate(schema.getSchemaDefinitionJson(), finalFields);
                ext.setSchemaValid(val.valid());
                ext.setValidationErrorsJson(toJson(val.errors()));
            }
        }

        ext.setReviewStatus(decision);
        ext.setReviewedFieldsJson(toJson(finalFields));
        ext.setReviewerActor(actorId);
        ext.setReviewerNotes(request.reviewerNotes());
        ext.setReviewedAt(Instant.now());
        ext.setUpdatedAt(Instant.now());

        boolean promoted = false;
        if (Boolean.TRUE.equals(request.promoteToGroundTruth()) && !"REJECTED".equals(decision)) {
            String fullText = assembleFullText(doc.getId());
            if (!fullText.isBlank()) {
                DocumentEvalCaseEntity gt = new DocumentEvalCaseEntity();
                gt.setId(UUID.randomUUID());
                gt.setCaseCode("gt_" + doc.getDocumentType().toLowerCase(Locale.ROOT) + "_" + doc.getId().toString().substring(0, 8));
                gt.setName("Reviewed Ground Truth (" + doc.getDocumentType() + ")");
                gt.setDocumentType(doc.getDocumentType());
                gt.setOcrLanguage(doc.getOcrLanguage());
                gt.setSampleText(fullText);
                gt.setExpectedFieldsJson(toJson(finalFields));
                gt.setExpectedLineItemCount(parseLineItems(ext.getLineItemsJson()).size());
                gt.setSourceType("PROMOTED_GROUND_TRUTH");
                gt.setIsActive(true);
                gt.setCreatedAt(Instant.now());
                evalCaseMapper.insert(gt);
                promoted = true;
            }
        }
        ext.setPromotedToGroundTruth(promoted);
        extractionMapper.updateById(ext);

        auditService.record(new AuditEvent(
                actorType,
                actorId,
                doc.getApplicationId(),
                doc.getTenantId(),
                "DOCUMENT_EXTRACTION_REVIEWED",
                "DOCUMENT_EXTRACTION",
                ext.getId().toString(),
                "SUCCESS",
                Map.of("decision", decision, "promoted_to_ground_truth", promoted),
                ipAddress
        ));

        return toExtractionView(ext);
    }

    public List<ExtractionConfigView> listExtractionConfigs() {
        return configMapper.selectList(
                new LambdaQueryWrapper<DocumentExtractionConfigEntity>()
                        .orderByAsc(DocumentExtractionConfigEntity::getDocumentType)
        ).stream().map(this::toConfigView).toList();
    }

    @Transactional
    public ExtractionConfigView saveExtractionConfig(ExtractionConfigRequest req, String actorId, String ipAddress) {
        SchemaEntity schema = schemaMapper.findActiveByCode(req.schemaCode().trim())
                .orElseThrow(() -> new CranialException(ErrorCode.SCHEMA_NOT_FOUND, "Active schema not found: " + req.schemaCode()));

        DocumentExtractionConfigEntity existing = configMapper.selectOne(
                new LambdaQueryWrapper<DocumentExtractionConfigEntity>()
                        .eq(DocumentExtractionConfigEntity::getConfigCode, req.configCode().trim())
        );

        Instant now = Instant.now();
        DocumentExtractionConfigEntity entity = existing != null ? existing : new DocumentExtractionConfigEntity();
        if (existing == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(now);
        }
        entity.setConfigCode(req.configCode().trim());
        entity.setApplicationId(req.applicationId());
        entity.setDocumentType(req.documentType().trim().toUpperCase(Locale.ROOT));
        entity.setSchemaId(schema.getId());
        entity.setDefaultOcrLanguage(req.defaultOcrLanguage() != null && !req.defaultOcrLanguage().isBlank() ? req.defaultOcrLanguage().trim() : "eng+ind");
        entity.setConfidenceThreshold(req.confidenceThreshold() != null ? req.confidenceThreshold() : 0.75);
        entity.setAutoReviewLowConfidence(req.autoReviewLowConfidence() == null || req.autoReviewLowConfidence());
        entity.setDefaultRetentionHours(req.defaultRetentionHours() != null && req.defaultRetentionHours() > 0 ? req.defaultRetentionHours() : 168);
        entity.setPreprocessDefaultsJson("{\"deskew\":true,\"denoise\":true,\"contrast_enhance\":true}");
        entity.setIsActive(true);
        entity.setUpdatedAt(now);

        if (existing == null) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }

        auditService.record(new AuditEvent(
                "OPERATOR",
                actorId,
                req.applicationId(),
                null,
                "DOCUMENT_EXTRACTION_CONFIG_SAVED",
                "EXTRACTION_CONFIG",
                entity.getConfigCode(),
                "SUCCESS",
                Map.of("document_type", entity.getDocumentType(), "schema_code", schema.getSchemaCode()),
                ipAddress
        ));

        return toConfigView(entity);
    }

    public OcrBenchmarkResult runRegressionBenchmarks() {
        long suiteStart = System.currentTimeMillis();
        List<DocumentEvalCaseEntity> cases = evalCaseMapper.selectList(
                new LambdaQueryWrapper<DocumentEvalCaseEntity>()
                        .eq(DocumentEvalCaseEntity::getIsActive, true)
                        .orderByAsc(DocumentEvalCaseEntity::getCreatedAt)
        );

        int typeMatches = 0;
        int schemaValidCount = 0;
        int totalExpectedFieldsAll = 0;
        int totalMatchedFieldsAll = 0;
        List<OcrBenchmarkResult.CaseOutcome> outcomes = new ArrayList<>();

        for (DocumentEvalCaseEntity evalCase : cases) {
            long caseStart = System.currentTimeMillis();
            DocumentPageEntity syntheticPage = new DocumentPageEntity();
            syntheticPage.setId(UUID.randomUUID());
            syntheticPage.setPageNumber(1);
            syntheticPage.setStatus("SUCCESS");
            syntheticPage.setEngineUsed("benchmark_eval");
            syntheticPage.setOcrConfidence(0.95);
            syntheticPage.setExtractedText(evalCase.getSampleText());

            DocumentClassifier.ClassificationResult cls = classifier.classify(evalCase.getSampleText(), null);
            boolean typeMatched = evalCase.getDocumentType().equalsIgnoreCase(cls.documentType());
            if (typeMatched) {
                typeMatches++;
            }

            DocumentExtractionConfigEntity cfg = resolveConfig(null, evalCase.getDocumentType());
            SchemaEntity schema = cfg != null ? schemaMapper.selectById(cfg.getSchemaId()) : null;

            TableAndLineItemExtractor.TableExtractionOutcome tblOut = tableExtractor.extract(List.of(syntheticPage), "IDR");
            StructuredFieldExtractor.StructuredExtractionOutcome extOut = fieldExtractor.extract(
                    List.of(syntheticPage),
                    evalCase.getDocumentType(),
                    schema,
                    0.75,
                    true,
                    tblOut.lineItems(),
                    List.of()
            );

            if (extOut.schemaValid()) {
                schemaValidCount++;
            }

            Map<String, Object> expected = parseMap(evalCase.getExpectedFieldsJson());
            int matched = 0;
            for (Map.Entry<String, Object> exp : expected.entrySet()) {
                Object actual = extOut.extractedFields().get(exp.getKey());
                if (valuesEquivalent(exp.getValue(), actual)) {
                    matched++;
                }
            }
            totalExpectedFieldsAll += expected.size();
            totalMatchedFieldsAll += matched;
            double caseFieldAcc = expected.isEmpty() ? 1.0 : Math.round((matched * 1000.0 / expected.size())) / 1000.0;
            long caseDur = Math.max(1L, System.currentTimeMillis() - caseStart);

            outcomes.add(new OcrBenchmarkResult.CaseOutcome(
                    evalCase.getCaseCode(),
                    evalCase.getName(),
                    evalCase.getSourceType(),
                    evalCase.getDocumentType(),
                    cls.documentType(),
                    typeMatched,
                    extOut.schemaValid(),
                    matched,
                    expected.size(),
                    caseFieldAcc,
                    tblOut.lineItems().size(),
                    caseDur,
                    extOut.extractedFields()
            ));
        }

        int n = cases.size();
        double clsAcc = n > 0 ? Math.round((typeMatches * 1000.0 / n)) / 1000.0 : 1.0;
        double fldAcc = totalExpectedFieldsAll > 0 ? Math.round((totalMatchedFieldsAll * 1000.0 / totalExpectedFieldsAll)) / 1000.0 : 1.0;
        double schemaRate = n > 0 ? Math.round((schemaValidCount * 1000.0 / n)) / 1000.0 : 1.0;
        long totalDur = Math.max(1L, System.currentTimeMillis() - suiteStart);

        return new OcrBenchmarkResult(
                n,
                typeMatches,
                schemaValidCount,
                clsAcc,
                fldAcc,
                schemaRate,
                totalDur,
                Instant.now(),
                outcomes
        );
    }

    // =========================================================================
    // Internal Execution & Helpers
    // =========================================================================

    private void executeJob(
            UUID jobId,
            byte[] rawBytes,
            String actorCode,
            List<Integer> simulateFailPages,
            String ipAddress
    ) {
        long start = System.currentTimeMillis();
        DocumentJobEntity job = jobMapper.selectById(jobId);
        DocumentEntity doc = documentMapper.selectById(job.getDocumentId());

        job.setStatus("PROCESSING");
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setStartedAt(Instant.now());
        job.setErrorMessage(null);
        job.setUpdatedAt(Instant.now());
        jobMapper.updateById(job);

        doc.setStatus("PROCESSING");
        doc.setUpdatedAt(Instant.now());
        documentMapper.updateById(doc);

        Map<String, Object> optsMap = parseMap(job.getOptionsJson());
        String ocrLang = Objects.toString(optsMap.getOrDefault("ocr_language", doc.getOcrLanguage()), "eng+ind");
        String docTypeHint = Objects.toString(optsMap.getOrDefault("document_type_hint", ""), "");
        String explicitSchemaCode = Objects.toString(optsMap.getOrDefault("extraction_schema_code", ""), "");
        PreprocessOptions prepOpts = extractPreprocessFromMap(optsMap);
        boolean advancedFlag = featureFlagService.isEnabled(FLAG_ADVANCED_PREPROCESS, actorCode);

        // Resumable checkpoint discovery: find pages that already succeeded in previous attempts
        List<DocumentPageEntity> existingPages = pageMapper.selectList(
                new LambdaQueryWrapper<DocumentPageEntity>().eq(DocumentPageEntity::getDocumentId, doc.getId())
        );
        Set<Integer> completedPageNums = existingPages.stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .map(DocumentPageEntity::getPageNumber)
                .collect(Collectors.toSet());

        Set<Integer> failSet = simulateFailPages != null ? new HashSet<>(simulateFailPages) : Set.of();

        try {
            parserService.parsePages(
                    rawBytes,
                    doc.getDetectedMimeType(),
                    ocrLang,
                    job.getPageRange(),
                    prepOpts,
                    advancedFlag,
                    completedPageNums,
                    failSet,
                    pageOutcome -> persistPageCheckpoint(doc.getId(), job, pageOutcome)
            );

            List<DocumentPageEntity> allPages = pageMapper.selectList(
                    new LambdaQueryWrapper<DocumentPageEntity>()
                            .eq(DocumentPageEntity::getDocumentId, doc.getId())
                            .orderByAsc(DocumentPageEntity::getPageNumber)
            );

            long successCount = allPages.stream().filter(p -> "SUCCESS".equals(p.getStatus())).count();
            long failedCount = allPages.stream().filter(p -> "FAILED".equals(p.getStatus())).count();

            // Guard against prompt injection & untrusted document content
            List<String> combinedWarnings = new ArrayList<>();
            StringBuilder fullTextBuilder = new StringBuilder();
            for (DocumentPageEntity p : allPages) {
                if ("SUCCESS".equals(p.getStatus()) && p.getExtractedText() != null) {
                    UntrustedDocumentGuard.SanitizedDocumentText sanitized = untrustedGuard.inspectAndSanitize(p.getExtractedText());
                    if (!Objects.equals(sanitized.sanitizedText(), p.getExtractedText())) {
                        p.setExtractedText(sanitized.sanitizedText());
                        pageMapper.updateById(p);
                    }
                    combinedWarnings.addAll(sanitized.warnings());
                    if (!fullTextBuilder.isEmpty()) {
                        fullTextBuilder.append('\n');
                    }
                    fullTextBuilder.append(sanitized.sanitizedText());
                }
            }

            String fullText = fullTextBuilder.toString();

            // Near/content duplicate detection on normalized text
            if (!fullText.isBlank()) {
                String normHash = cryptoService.sha256Hex(fullText.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim());
                doc.setNormalizedTextHash(normHash);
                if (!Boolean.TRUE.equals(doc.getIsDuplicate())) {
                    Optional<DocumentEntity> textDup = findContentDuplicate(doc.getApplicationId(), doc.getTenantId(), normHash, doc.getId());
                    if (textDup.isPresent()) {
                        doc.setIsDuplicate(true);
                        doc.setDuplicateOfDocumentId(textDup.get().getId());
                        doc.setDuplicateType("CONTENT_TEXT_MATCH");
                        combinedWarnings.add("DUPLICATE_CONTENT_DETECTED: Extracted text matches existing document " + textDup.get().getId());
                    }
                } else {
                    combinedWarnings.add("DUPLICATE_FILE_DETECTED: Exact SHA-256 byte match with document " + doc.getDuplicateOfDocumentId());
                }
            }

            // Classify document
            DocumentClassifier.ClassificationResult cls = classifier.classify(fullText, docTypeHint);
            doc.setDocumentType(cls.documentType());
            doc.setClassificationConfidence(cls.confidence());

            // Resolve schema & config per (application_id, document_type)
            DocumentExtractionConfigEntity cfg = resolveConfig(doc.getApplicationId(), cls.documentType());
            SchemaEntity schema = null;
            if (!explicitSchemaCode.isBlank()) {
                schema = schemaMapper.findActiveByCode(explicitSchemaCode).orElse(null);
            } else if (cfg != null && cfg.getSchemaId() != null) {
                schema = schemaMapper.selectById(cfg.getSchemaId());
            }

            double confThreshold = cfg != null && cfg.getConfidenceThreshold() != null ? cfg.getConfidenceThreshold() : 0.75;
            boolean autoReview = cfg == null || Boolean.TRUE.equals(cfg.getAutoReviewLowConfidence());

            TableAndLineItemExtractor.TableExtractionOutcome tableOut = tableExtractor.extract(allPages, "IDR");
            StructuredFieldExtractor.StructuredExtractionOutcome extOut = fieldExtractor.extract(
                    allPages,
                    cls.documentType(),
                    schema,
                    confThreshold,
                    autoReview,
                    tableOut.lineItems(),
                    combinedWarnings
            );

            // Replace previous extraction on resume if one exists
            extractionMapper.delete(
                    new LambdaQueryWrapper<DocumentExtractionEntity>().eq(DocumentExtractionEntity::getDocumentId, doc.getId())
            );

            long durationMs = Math.max(1L, System.currentTimeMillis() - start);
            DocumentExtractionEntity extEntity = new DocumentExtractionEntity();
            extEntity.setId(UUID.randomUUID());
            extEntity.setDocumentId(doc.getId());
            extEntity.setJobId(job.getId());
            extEntity.setSchemaId(schema != null ? schema.getId() : null);
            extEntity.setSchemaCode(schema != null ? schema.getSchemaCode() : null);
            extEntity.setDocumentType(cls.documentType());
            extEntity.setOverallConfidence(extOut.overallConfidence());
            extEntity.setSchemaValid(extOut.schemaValid());
            extEntity.setValidationErrorsJson(toJson(extOut.validationErrors()));
            extEntity.setExtractedFieldsJson(toJson(extOut.extractedFields()));
            extEntity.setFieldDetailsJson(toJson(extOut.fieldDetails()));
            extEntity.setTablesJson(toJson(tableOut.tables()));
            extEntity.setLineItemsJson(toJson(tableOut.lineItems()));
            extEntity.setWarningsJson(toJson(extOut.warnings().stream().distinct().toList()));
            extEntity.setReviewStatus(extOut.reviewStatus());
            extEntity.setReviewReasonsJson(toJson(extOut.reviewReasons()));
            extEntity.setPromotedToGroundTruth(false);
            extEntity.setDurationMs(durationMs);
            extEntity.setCreatedAt(Instant.now());
            extEntity.setUpdatedAt(Instant.now());
            extractionMapper.insert(extEntity);

            String docFinalStatus;
            String jobFinalStatus;
            if (successCount > 0 && failedCount == 0) {
                docFinalStatus = "PROCESSED";
                jobFinalStatus = "COMPLETED";
            } else if (successCount > 0) {
                docFinalStatus = "PARTIAL_SUCCESS";
                jobFinalStatus = "PARTIAL_SUCCESS";
                job.setErrorMessage(failedCount + " page(s) failed extraction; resumable via /resume");
            } else {
                docFinalStatus = "FAILED";
                jobFinalStatus = "FAILED";
                job.setErrorMessage("All target pages failed extraction");
            }

            doc.setStatus(docFinalStatus);
            doc.setUpdatedAt(Instant.now());
            documentMapper.updateById(doc);

            job.setProcessedPages((int) successCount);
            job.setFailedPages((int) failedCount);
            job.setStatus(jobFinalStatus);
            job.setCompletedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            jobMapper.updateById(job);

            metrics.recordDocumentProcessed(cls.documentType(), docFinalStatus, (int) successCount);
            auditService.record(new AuditEvent(
                    doc.getApplicationId() != null ? "APPLICATION" : "OPERATOR",
                    actorCode != null ? actorCode : "document-engine",
                    doc.getApplicationId(),
                    doc.getTenantId(),
                    "DOCUMENT_PROCESSED",
                    "DOCUMENT",
                    doc.getId().toString(),
                    "FAILED".equals(docFinalStatus) ? "FAILURE" : "SUCCESS",
                    Map.of(
                            "document_type", cls.documentType(),
                            "status", docFinalStatus,
                            "pages_succeeded", successCount,
                            "pages_failed", failedCount,
                            "review_status", extOut.reviewStatus(),
                            "is_duplicate", Boolean.TRUE.equals(doc.getIsDuplicate())
                    ),
                    ipAddress
            ));
        } catch (Exception ex) {
            doc.setStatus("FAILED");
            doc.setUpdatedAt(Instant.now());
            documentMapper.updateById(doc);

            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(480, ex.getMessage().length())) : "Processing error");
            job.setCompletedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            jobMapper.updateById(job);
            metrics.recordDocumentProcessed(doc.getDocumentType(), "FAILED", 0);
            throw ex;
        }
    }

    private void persistPageCheckpoint(
            UUID documentId,
            DocumentJobEntity job,
            DocumentParserService.ParsedPageOutcome outcome
    ) {
        pageMapper.delete(
                new LambdaQueryWrapper<DocumentPageEntity>()
                        .eq(DocumentPageEntity::getDocumentId, documentId)
                        .eq(DocumentPageEntity::getPageNumber, outcome.pageNumber())
        );

        String txt = outcome.extractedText() != null ? outcome.extractedText() : "";
        int words = txt.isBlank() ? 0 : txt.trim().split("\\s+").length;

        DocumentPageEntity page = new DocumentPageEntity();
        page.setId(UUID.randomUUID());
        page.setDocumentId(documentId);
        page.setPageNumber(outcome.pageNumber());
        page.setStatus(outcome.status());
        page.setEngineUsed(outcome.engineUsed());
        page.setOcrConfidence(outcome.confidence());
        page.setExtractedText(txt);
        page.setCharCount(txt.length());
        page.setWordCount(words);
        page.setPreprocessAppliedJson(toJson(outcome.preprocessApplied()));
        page.setDiagnosticsJson(toJson(outcome.diagnostics()));
        page.setDurationMs(outcome.durationMs());
        page.setCreatedAt(Instant.now());
        pageMapper.insert(page);

        if ("SUCCESS".equals(outcome.status())) {
            job.setProcessedPages((job.getProcessedPages() != null ? job.getProcessedPages() : 0) + 1);
            job.setLastCompletedPage(Math.max(job.getLastCompletedPage() != null ? job.getLastCompletedPage() : 0, outcome.pageNumber()));
            job.setUpdatedAt(Instant.now());
            jobMapper.updateById(job);
        }
    }

    private DocumentEntity requireAuthorizedDocument(UUID documentId, UUID requiredAppId, UUID requiredTenantId) {
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null || "DELETED".equals(doc.getStatus())) {
            throw new CranialException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found: " + documentId);
        }
        if (requiredAppId != null && !requiredAppId.equals(doc.getApplicationId())) {
            throw new CranialException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found in caller application scope");
        }
        if (requiredTenantId != null && doc.getTenantId() != null && !requiredTenantId.equals(doc.getTenantId())) {
            throw new CranialException(ErrorCode.TENANT_NOT_AUTHORIZED, "Document belongs to a different tenant");
        }
        return doc;
    }

    private Optional<DocumentEntity> findExactDuplicate(UUID appId, UUID tenantId, String sha256, UUID excludeDocId) {
        LambdaQueryWrapper<DocumentEntity> q = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getSha256Hash, sha256)
                .notIn(DocumentEntity::getStatus, List.of("DELETED", "EXPIRED", "PENDING_UPLOAD"));
        if (appId != null) {
            q.eq(DocumentEntity::getApplicationId, appId);
        } else {
            q.isNull(DocumentEntity::getApplicationId);
        }
        if (tenantId != null) {
            q.eq(DocumentEntity::getTenantId, tenantId);
        }
        if (excludeDocId != null) {
            q.ne(DocumentEntity::getId, excludeDocId);
        }
        q.orderByAsc(DocumentEntity::getCreatedAt).last("LIMIT 1");
        return Optional.ofNullable(documentMapper.selectOne(q));
    }

    private Optional<DocumentEntity> findContentDuplicate(UUID appId, UUID tenantId, String normTextHash, UUID excludeDocId) {
        LambdaQueryWrapper<DocumentEntity> q = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getNormalizedTextHash, normTextHash)
                .notIn(DocumentEntity::getStatus, List.of("DELETED", "EXPIRED", "PENDING_UPLOAD"));
        if (appId != null) {
            q.eq(DocumentEntity::getApplicationId, appId);
        } else {
            q.isNull(DocumentEntity::getApplicationId);
        }
        if (tenantId != null) {
            q.eq(DocumentEntity::getTenantId, tenantId);
        }
        if (excludeDocId != null) {
            q.ne(DocumentEntity::getId, excludeDocId);
        }
        q.orderByAsc(DocumentEntity::getCreatedAt).last("LIMIT 1");
        return Optional.ofNullable(documentMapper.selectOne(q));
    }

    private DocumentExtractionConfigEntity resolveConfig(UUID applicationId, String documentType) {
        if (applicationId != null) {
            DocumentExtractionConfigEntity appSpecific = configMapper.selectOne(
                    new LambdaQueryWrapper<DocumentExtractionConfigEntity>()
                            .eq(DocumentExtractionConfigEntity::getApplicationId, applicationId)
                            .eq(DocumentExtractionConfigEntity::getDocumentType, documentType)
                            .eq(DocumentExtractionConfigEntity::getIsActive, true)
                            .last("LIMIT 1")
            );
            if (appSpecific != null) {
                return appSpecific;
            }
        }
        return configMapper.selectOne(
                new LambdaQueryWrapper<DocumentExtractionConfigEntity>()
                        .isNull(DocumentExtractionConfigEntity::getApplicationId)
                        .eq(DocumentExtractionConfigEntity::getDocumentType, documentType)
                        .eq(DocumentExtractionConfigEntity::getIsActive, true)
                        .last("LIMIT 1")
        );
    }

    private Optional<DocumentExtractionEntity> findLatestExtraction(UUID documentId) {
        return Optional.ofNullable(extractionMapper.selectOne(
                new LambdaQueryWrapper<DocumentExtractionEntity>()
                        .eq(DocumentExtractionEntity::getDocumentId, documentId)
                        .orderByDesc(DocumentExtractionEntity::getCreatedAt)
                        .last("LIMIT 1")
        ));
    }

    private String assembleFullText(UUID documentId) {
        return pageMapper.selectList(
                        new LambdaQueryWrapper<DocumentPageEntity>()
                                .eq(DocumentPageEntity::getDocumentId, documentId)
                                .eq(DocumentPageEntity::getStatus, "SUCCESS")
                                .orderByAsc(DocumentPageEntity::getPageNumber)
                ).stream()
                .map(DocumentPageEntity::getExtractedText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private DocumentView toDocumentView(DocumentEntity doc, boolean includeRawText) {
        DocumentJobEntity latestJob = jobMapper.selectOne(
                new LambdaQueryWrapper<DocumentJobEntity>()
                        .eq(DocumentJobEntity::getDocumentId, doc.getId())
                        .orderByDesc(DocumentJobEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
        DocumentExtractionEntity ext = findLatestExtraction(doc.getId()).orElse(null);
        List<DocumentPageEntity> pages = pageMapper.selectList(
                new LambdaQueryWrapper<DocumentPageEntity>()
                        .eq(DocumentPageEntity::getDocumentId, doc.getId())
                        .orderByAsc(DocumentPageEntity::getPageNumber)
        );

        List<DocumentPageView> pageViews = pages.stream().map(p -> new DocumentPageView(
                p.getId(),
                p.getPageNumber(),
                p.getStatus(),
                p.getEngineUsed(),
                p.getOcrConfidence(),
                includeRawText ? p.getExtractedText() : (p.getExtractedText() != null && !p.getExtractedText().isEmpty() ? "[REDACTED — operator content reveal audited]" : ""),
                p.getCharCount() != null ? p.getCharCount() : 0,
                p.getWordCount() != null ? p.getWordCount() : 0,
                parseStringList(p.getPreprocessAppliedJson()),
                parseMap(p.getDiagnosticsJson()),
                p.getDurationMs() != null ? p.getDurationMs() : 0L
        )).toList();

        return new DocumentView(
                doc.getId(),
                doc.getApplicationId(),
                doc.getTenantId(),
                doc.getFileName(),
                doc.getMimeType(),
                doc.getDetectedMimeType(),
                doc.getFileSizeBytes() != null ? doc.getFileSizeBytes() : 0L,
                doc.getSha256Hash(),
                doc.getPageCount() != null ? doc.getPageCount() : 1,
                doc.getDocumentType(),
                doc.getClassificationConfidence(),
                doc.getOcrLanguage(),
                Boolean.TRUE.equals(doc.getIsDuplicate()),
                doc.getDuplicateOfDocumentId(),
                doc.getDuplicateType(),
                doc.getStatus(),
                doc.getRetentionUntil(),
                doc.getCreatedAt(),
                latestJob != null ? toJobView(latestJob) : null,
                ext != null ? toExtractionView(ext) : null,
                pageViews
        );
    }

    private DocumentJobView toJobView(DocumentJobEntity j) {
        return new DocumentJobView(
                j.getId(),
                j.getDocumentId(),
                j.getApplicationId(),
                j.getCorrelationId(),
                j.getStatus(),
                j.getPageRange(),
                j.getTotalPages() != null ? j.getTotalPages() : 0,
                j.getProcessedPages() != null ? j.getProcessedPages() : 0,
                j.getFailedPages() != null ? j.getFailedPages() : 0,
                j.getLastCompletedPage() != null ? j.getLastCompletedPage() : 0,
                j.getAttemptCount() != null ? j.getAttemptCount() : 0,
                j.getMaxAttempts() != null ? j.getMaxAttempts() : 3,
                j.getErrorMessage(),
                j.getStartedAt(),
                j.getCompletedAt(),
                j.getCreatedAt()
        );
    }

    private DocumentExtractionView toExtractionView(DocumentExtractionEntity e) {
        return new DocumentExtractionView(
                e.getId(),
                e.getDocumentId(),
                e.getJobId(),
                e.getSchemaCode(),
                e.getDocumentType(),
                e.getOverallConfidence() != null ? e.getOverallConfidence() : 0.0,
                Boolean.TRUE.equals(e.getSchemaValid()),
                parseStringList(e.getValidationErrorsJson()),
                parseMap(e.getExtractedFieldsJson()),
                parseFieldDetails(e.getFieldDetailsJson()),
                parseTables(e.getTablesJson()),
                parseLineItems(e.getLineItemsJson()),
                parseStringList(e.getWarningsJson()),
                e.getReviewStatus(),
                parseStringList(e.getReviewReasonsJson()),
                e.getReviewedFieldsJson() != null ? parseMap(e.getReviewedFieldsJson()) : null,
                e.getReviewerActor(),
                e.getReviewerNotes(),
                e.getReviewedAt(),
                Boolean.TRUE.equals(e.getPromotedToGroundTruth()),
                e.getDurationMs() != null ? e.getDurationMs() : 0L,
                e.getCreatedAt()
        );
    }

    private ExtractionConfigView toConfigView(DocumentExtractionConfigEntity c) {
        SchemaEntity s = c.getSchemaId() != null ? schemaMapper.selectById(c.getSchemaId()) : null;
        return new ExtractionConfigView(
                c.getId(),
                c.getConfigCode(),
                c.getApplicationId(),
                c.getDocumentType(),
                c.getSchemaId(),
                s != null ? s.getSchemaCode() : "unknown",
                c.getDefaultOcrLanguage(),
                c.getConfidenceThreshold() != null ? c.getConfidenceThreshold() : 0.75,
                Boolean.TRUE.equals(c.getAutoReviewLowConfidence()),
                c.getDefaultRetentionHours() != null ? c.getDefaultRetentionHours() : 168,
                Boolean.TRUE.equals(c.getIsActive()),
                c.getUpdatedAt()
        );
    }

    private static byte[] decodeRequestBytes(DocumentProcessRequest req) {
        if (req.contentBase64() != null && !req.contentBase64().isBlank()) {
            try {
                String clean = req.contentBase64().trim();
                int comma = clean.indexOf(',');
                if (clean.startsWith("data:") && comma > 0) {
                    clean = clean.substring(comma + 1);
                }
                return Base64.getDecoder().decode(clean);
            } catch (IllegalArgumentException ex) {
                throw new CranialException(ErrorCode.VALIDATION_ERROR, "Invalid base64 document payload");
            }
        }
        if (req.rawText() != null && !req.rawText().isBlank()) {
            return req.rawText().getBytes(StandardCharsets.UTF_8);
        }
        throw new CranialException(ErrorCode.VALIDATION_ERROR, "Either content_base64 or raw_text must be provided");
    }

    private static Map<String, Object> buildJobOptionsMap(DocumentProcessRequest options) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (options == null) {
            return m;
        }
        m.put("ocr_language", options.effectiveOcrLanguage());
        if (options.documentTypeHint() != null) {
            m.put("document_type_hint", options.documentTypeHint());
        }
        if (options.extractionSchemaCode() != null) {
            m.put("extraction_schema_code", options.extractionSchemaCode());
        }
        if (options.preprocess() != null) {
            m.put("rotate_degrees", options.preprocess().effectiveRotateDegrees());
            m.put("deskew", options.preprocess().effectiveDeskew());
            m.put("denoise", options.preprocess().effectiveDenoise());
            m.put("contrast_enhance", options.preprocess().effectiveContrastEnhance());
        }
        return m;
    }

    private static PreprocessOptions extractPreprocessFromMap(Map<String, Object> m) {
        int rot = m.get("rotate_degrees") instanceof Number n ? n.intValue() : 0;
        boolean deskew = !(m.get("deskew") instanceof Boolean b) || b;
        boolean denoise = !(m.get("denoise") instanceof Boolean b) || b;
        boolean contrast = !(m.get("contrast_enhance") instanceof Boolean b) || b;
        return new PreprocessOptions(rot, deskew, denoise, contrast);
    }

    private static double computeJaccardSimilarity(String a, String b) {
        if (a.isBlank() && b.isBlank()) {
            return 1.0;
        }
        if (a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        Set<String> setA = Arrays.stream(a.toLowerCase(Locale.ROOT).split("\\s+")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        Set<String> setB = Arrays.stream(b.toLowerCase(Locale.ROOT).split("\\s+")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        Set<String> inter = new HashSet<>(setA);
        inter.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 1.0 : Math.round((inter.size() * 1000.0 / union.size())) / 1000.0;
    }

    private static boolean valuesEquivalent(Object a, Object b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Math.abs(na.doubleValue() - nb.doubleValue()) < 0.001;
        }
        if (a != null && b != null) {
            return a.toString().trim().equalsIgnoreCase(b.toString().trim());
        }
        return false;
    }

    private static List<String> summarizeLineItemChanges(List<ExtractedLineItem> left, List<ExtractedLineItem> right) {
        List<String> out = new ArrayList<>();
        Map<String, ExtractedLineItem> leftByDesc = new LinkedHashMap<>();
        for (ExtractedLineItem li : left) {
            leftByDesc.put(li.description().toLowerCase(Locale.ROOT), li);
        }
        Map<String, ExtractedLineItem> rightByDesc = new LinkedHashMap<>();
        for (ExtractedLineItem ri : right) {
            rightByDesc.put(ri.description().toLowerCase(Locale.ROOT), ri);
        }
        for (Map.Entry<String, ExtractedLineItem> e : rightByDesc.entrySet()) {
            ExtractedLineItem prev = leftByDesc.get(e.getKey());
            if (prev == null) {
                out.add("ADDED_ITEM: " + e.getValue().description() + " (amount=" + e.getValue().amount() + ")");
            } else if (!valuesEquivalent(prev.amount(), e.getValue().amount()) || !valuesEquivalent(prev.quantity(), e.getValue().quantity())) {
                out.add("MODIFIED_ITEM: " + e.getValue().description() + " (qty " + prev.quantity() + " -> " + e.getValue().quantity() + ", amount " + prev.amount() + " -> " + e.getValue().amount() + ")");
            }
        }
        for (Map.Entry<String, ExtractedLineItem> e : leftByDesc.entrySet()) {
            if (!rightByDesc.containsKey(e.getKey())) {
                out.add("REMOVED_ITEM: " + e.getValue().description() + " (amount=" + e.getValue().amount() + ")");
            }
        }
        return out;
    }

    private static List<String> summarizeTextDiff(String leftText, String rightText) {
        Set<String> leftLines = Arrays.stream(leftText.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> rightLines = Arrays.stream(rightText.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> diff = new ArrayList<>();
        for (String l : leftLines) {
            if (!rightLines.contains(l) && diff.size() < 20) {
                diff.add("- " + l);
            }
        }
        for (String r : rightLines) {
            if (!leftLines.contains(r) && diff.size() < 20) {
                diff.add("+ " + r);
            }
        }
        return diff;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, ExtractedFieldValue> parseFieldDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, ExtractedFieldValue>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<ExtractedTable> parseTables(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExtractedTable>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<ExtractedLineItem> parseLineItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExtractedLineItem>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}

