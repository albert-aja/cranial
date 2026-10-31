package io.cranial.platform.document.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.document.config.DocumentProperties;
import io.cranial.platform.document.entity.DocumentTransferTokenEntity;
import io.cranial.platform.document.mapper.DocumentTransferTokenMapper;
import io.cranial.platform.document.model.TransferUrlView;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentTransferTokenService {

    private final DocumentTransferTokenMapper tokenMapper;
    private final CryptoService cryptoService;
    private final DocumentProperties properties;
    private final AuditService auditService;

    public DocumentTransferTokenService(
            DocumentTransferTokenMapper tokenMapper,
            CryptoService cryptoService,
            DocumentProperties properties,
            AuditService auditService
    ) {
        this.tokenMapper = tokenMapper;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public TransferUrlView issueUploadUrl(
            UUID applicationId,
            UUID tenantId,
            UUID placeholderDocumentId,
            String fileName,
            String allowedMimeType,
            Long requestedMaxBytes,
            Integer requestedTtlMinutes,
            String ipAddress
    ) {
        int ttlMin = requestedTtlMinutes != null && requestedTtlMinutes > 0 && requestedTtlMinutes <= 60
                ? requestedTtlMinutes
                : properties.effectiveUploadUrlTtlMinutes();
        long maxBytes = requestedMaxBytes != null && requestedMaxBytes > 0
                ? Math.min(requestedMaxBytes, properties.effectiveMaxDocumentBytes())
                : properties.effectiveMaxDocumentBytes();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlMin, ChronoUnit.MINUTES);
        String nonce = cryptoService.generateRandomToken(24);

        DocumentTransferTokenEntity entity = new DocumentTransferTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setTokenNonce(nonce);
        entity.setOperation("UPLOAD");
        entity.setApplicationId(applicationId);
        entity.setTenantId(tenantId);
        entity.setDocumentId(placeholderDocumentId);
        entity.setFileName(DocumentSecurityValidator.sanitizeFileName(fileName));
        entity.setAllowedMimeType(allowedMimeType);
        entity.setMaxBytes(maxBytes);
        entity.setUsedCount(0);
        entity.setMaxUses(1);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        tokenMapper.insert(entity);

        String signedToken = buildSignedToken(nonce, "UPLOAD", applicationId, placeholderDocumentId, expiresAt.getEpochSecond());

        auditService.record(new AuditEvent(
                "APPLICATION",
                applicationId.toString(),
                applicationId,
                tenantId,
                "DOCUMENT_UPLOAD_URL_ISSUED",
                "DOCUMENT",
                placeholderDocumentId.toString(),
                "SUCCESS",
                Map.of("expires_at", expiresAt.toString(), "max_bytes", maxBytes),
                ipAddress
        ));

        return new TransferUrlView(
                entity.getId(),
                placeholderDocumentId,
                "UPLOAD",
                "PUT",
                "/api/v1/documents/transfer/upload/" + signedToken,
                maxBytes,
                expiresAt
        );
    }

    @Transactional
    public TransferUrlView issueDownloadUrl(
            UUID applicationId,
            UUID tenantId,
            UUID documentId,
            String fileName,
            long fileSizeBytes,
            Integer requestedTtlMinutes,
            String actorType,
            String actorId,
            String ipAddress
    ) {
        int ttlMin = requestedTtlMinutes != null && requestedTtlMinutes > 0 && requestedTtlMinutes <= 30
                ? requestedTtlMinutes
                : properties.effectiveDownloadUrlTtlMinutes();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlMin, ChronoUnit.MINUTES);
        String nonce = cryptoService.generateRandomToken(24);

        DocumentTransferTokenEntity entity = new DocumentTransferTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setTokenNonce(nonce);
        entity.setOperation("DOWNLOAD");
        entity.setApplicationId(applicationId);
        entity.setTenantId(tenantId);
        entity.setDocumentId(documentId);
        entity.setFileName(fileName);
        entity.setMaxBytes(fileSizeBytes);
        entity.setUsedCount(0);
        entity.setMaxUses(3);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        tokenMapper.insert(entity);

        String signedToken = buildSignedToken(nonce, "DOWNLOAD", applicationId, documentId, expiresAt.getEpochSecond());

        auditService.record(new AuditEvent(
                actorType,
                actorId,
                applicationId,
                tenantId,
                "DOCUMENT_DOWNLOAD_URL_ISSUED",
                "DOCUMENT",
                documentId.toString(),
                "SUCCESS",
                Map.of("expires_at", expiresAt.toString()),
                ipAddress
        ));

        return new TransferUrlView(
                entity.getId(),
                documentId,
                "DOWNLOAD",
                "GET",
                "/api/v1/documents/transfer/download/" + signedToken,
                fileSizeBytes,
                expiresAt
        );
    }

    @Transactional
    public DocumentTransferTokenEntity verifyAndConsume(String signedToken, String expectedOperation, String ipAddress) {
        if (signedToken == null || signedToken.isBlank()) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_INVALID, "Missing transfer token");
        }
        String[] parts = signedToken.split("\\.");
        if (parts.length != 3) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_INVALID, "Malformed signed transfer token");
        }

        String nonce = parts[0];
        long expiresEpoch;
        try {
            expiresEpoch = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_INVALID, "Invalid transfer token expiry");
        }
        String providedSig = parts[2];

        DocumentTransferTokenEntity entity = tokenMapper.selectOne(
                new LambdaQueryWrapper<DocumentTransferTokenEntity>()
                        .eq(DocumentTransferTokenEntity::getTokenNonce, nonce)
        );
        if (entity == null || !expectedOperation.equals(entity.getOperation())) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_INVALID, "Transfer token not recognized for operation " + expectedOperation);
        }

        String canonical = canonicalString(
                nonce,
                entity.getOperation(),
                entity.getApplicationId(),
                entity.getDocumentId(),
                expiresEpoch
        );
        if (!cryptoService.verifyServerToken(canonical, providedSig)) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_INVALID, "Transfer token cryptographic signature invalid");
        }

        Instant now = Instant.now();
        if (now.isAfter(entity.getExpiresAt()) || now.getEpochSecond() > expiresEpoch) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_EXPIRED, "Temporary transfer URL has expired");
        }
        if (entity.getUsedCount() >= entity.getMaxUses()) {
            throw new CranialException(ErrorCode.TRANSFER_TOKEN_EXPIRED, "Temporary transfer URL has already been redeemed");
        }

        entity.setUsedCount(entity.getUsedCount() + 1);
        entity.setConsumedAt(now);
        tokenMapper.updateById(entity);

        auditService.record(new AuditEvent(
                "TRANSFER_TOKEN",
                nonce.substring(0, Math.min(8, nonce.length())),
                entity.getApplicationId(),
                entity.getTenantId(),
                "DOCUMENT_" + expectedOperation + "_URL_REDEEMED",
                "DOCUMENT",
                entity.getDocumentId() != null ? entity.getDocumentId().toString() : "none",
                "SUCCESS",
                Map.of("used_count", entity.getUsedCount(), "max_uses", entity.getMaxUses()),
                ipAddress
        ));

        return entity;
    }

    private String buildSignedToken(String nonce, String operation, UUID appId, UUID docId, long expiresEpoch) {
        String canonical = canonicalString(nonce, operation, appId, docId, expiresEpoch);
        String sig = cryptoService.signServerToken(canonical);
        return nonce + "." + expiresEpoch + "." + sig;
    }

    private static String canonicalString(String nonce, String operation, UUID appId, UUID docId, long expiresEpoch) {
        return nonce + "|" + operation + "|" + appId + "|" + docId + "|" + expiresEpoch;
    }
}
