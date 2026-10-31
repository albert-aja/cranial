package io.cranial.platform.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.application.maintenance.MaintenanceCleanupContributor;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.document.entity.DocumentEntity;
import io.cranial.platform.document.entity.DocumentPageEntity;
import io.cranial.platform.document.entity.DocumentTransferTokenEntity;
import io.cranial.platform.document.mapper.DocumentMapper;
import io.cranial.platform.document.mapper.DocumentPageMapper;
import io.cranial.platform.document.mapper.DocumentTransferTokenMapper;
import io.cranial.platform.document.security.DocumentStorageService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentRetentionService implements MaintenanceCleanupContributor {

    private final DocumentMapper documentMapper;
    private final DocumentPageMapper pageMapper;
    private final DocumentTransferTokenMapper transferTokenMapper;
    private final DocumentStorageService storageService;
    private final AuditService auditService;

    public DocumentRetentionService(
            DocumentMapper documentMapper,
            DocumentPageMapper pageMapper,
            DocumentTransferTokenMapper transferTokenMapper,
            DocumentStorageService storageService,
            AuditService auditService
    ) {
        this.documentMapper = documentMapper;
        this.pageMapper = pageMapper;
        this.transferTokenMapper = transferTokenMapper;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Override
    public String contributorName() {
        return "document_retention";
    }

    @Override
    @Transactional
    public Map<String, Object> cleanupExpired(Instant now) {
        List<DocumentEntity> expiredDocs = documentMapper.selectList(
                new LambdaQueryWrapper<DocumentEntity>()
                        .notIn(DocumentEntity::getStatus, List.of("EXPIRED", "DELETED"))
                        .isNotNull(DocumentEntity::getRetentionUntil)
                        .lt(DocumentEntity::getRetentionUntil, now)
        );

        int blobsDeleted = 0;
        for (DocumentEntity doc : expiredDocs) {
            if (storageService.deleteBlobIfExists(doc.getStorageKey())) {
                blobsDeleted++;
            }
            doc.setStorageKey(null);
            doc.setStatus("EXPIRED");
            doc.setUpdatedAt(now);
            documentMapper.updateById(doc);
        }

        int purgedTokens = transferTokenMapper.delete(
                new LambdaQueryWrapper<DocumentTransferTokenEntity>()
                        .lt(DocumentTransferTokenEntity::getExpiresAt, now)
        );

        return Map.of(
                "expired_documents", expiredDocs.size(),
                "encrypted_blobs_deleted", blobsDeleted,
                "expired_transfer_tokens_purged", purgedTokens
        );
    }

    @Transactional
    public void deleteDocument(
            UUID documentId,
            UUID requiredApplicationId,
            UUID requiredTenantId,
            String actorType,
            String actorId,
            String ipAddress
    ) {
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null || "DELETED".equals(doc.getStatus())) {
            throw new CranialException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found: " + documentId);
        }
        if (requiredApplicationId != null && !requiredApplicationId.equals(doc.getApplicationId())) {
            throw new CranialException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found in caller application scope");
        }
        if (requiredTenantId != null && doc.getTenantId() != null && !requiredTenantId.equals(doc.getTenantId())) {
            throw new CranialException(ErrorCode.TENANT_NOT_AUTHORIZED, "Document belongs to a different tenant");
        }

        boolean blobRemoved = storageService.deleteBlobIfExists(doc.getStorageKey());
        doc.setStorageKey(null);
        doc.setStatus("DELETED");
        doc.setDeletedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        documentMapper.updateById(doc);

        // Scrub page text on explicit deletion
        List<DocumentPageEntity> pages = pageMapper.selectList(
                new LambdaQueryWrapper<DocumentPageEntity>().eq(DocumentPageEntity::getDocumentId, documentId)
        );
        for (DocumentPageEntity p : pages) {
            p.setExtractedText(null);
            p.setCharCount(0);
            p.setWordCount(0);
            pageMapper.updateById(p);
        }

        auditService.record(new AuditEvent(
                actorType,
                actorId,
                doc.getApplicationId(),
                doc.getTenantId(),
                "DOCUMENT_DELETED",
                "DOCUMENT",
                documentId.toString(),
                "SUCCESS",
                Map.of("blob_removed", blobRemoved, "pages_scrubbed", pages.size()),
                ipAddress
        ));
    }
}

