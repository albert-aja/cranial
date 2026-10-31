package io.cranial.platform.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.application.maintenance.MaintenanceCleanupContributor;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.knowledge.entity.KnowledgeChunkEntity;
import io.cranial.platform.knowledge.entity.KnowledgeGlossaryTermEntity;
import io.cranial.platform.knowledge.entity.KnowledgeSourceEntity;
import io.cranial.platform.knowledge.entity.KnowledgeSourceEventEntity;
import io.cranial.platform.knowledge.mapper.KnowledgeChunkMapper;
import io.cranial.platform.knowledge.mapper.KnowledgeGlossaryTermMapper;
import io.cranial.platform.knowledge.mapper.KnowledgeSourceEventMapper;
import io.cranial.platform.knowledge.mapper.KnowledgeSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeRetentionService implements MaintenanceCleanupContributor {

    private final KnowledgeSourceMapper sourceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeGlossaryTermMapper glossaryMapper;
    private final KnowledgeSourceEventMapper eventMapper;
    private final AuditService auditService;

    public KnowledgeRetentionService(
            KnowledgeSourceMapper sourceMapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeGlossaryTermMapper glossaryMapper,
            KnowledgeSourceEventMapper eventMapper,
            AuditService auditService
    ) {
        this.sourceMapper = sourceMapper;
        this.chunkMapper = chunkMapper;
        this.glossaryMapper = glossaryMapper;
        this.eventMapper = eventMapper;
        this.auditService = auditService;
    }

    @Override
    public String contributorName() {
        return "knowledge_retention";
    }

    @Override
    @Transactional
    public Map<String, Object> cleanupExpired(Instant now) {
        Instant cutoff = now != null ? now : Instant.now();

        List<KnowledgeSourceEntity> expiredSources = sourceMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSourceEntity>()
                        .isNotNull(KnowledgeSourceEntity::getExpiresAt)
                        .le(KnowledgeSourceEntity::getExpiresAt, cutoff)
                        .ne(KnowledgeSourceEntity::getIndexingStatus, "DELETED")
        );

        int purgedSources = 0;
        int purgedChunks = 0;
        int purgedGlossaryTerms = 0;

        for (KnowledgeSourceEntity source : expiredSources) {
            int deletedChunks = chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                    .eq(KnowledgeChunkEntity::getSourceId, source.getId()));
            int deletedTerms = glossaryMapper.delete(new LambdaQueryWrapper<KnowledgeGlossaryTermEntity>()
                    .eq(KnowledgeGlossaryTermEntity::getSourceId, source.getId()));

            source.setRawContent(null);
            source.setChunkCount(0);
            source.setTokenCount(0);
            source.setIndexingStatus("DELETED");
            source.setErrorDetail("Purged by retention policy at " + cutoff);
            source.setUpdatedAt(cutoff);
            sourceMapper.updateById(source);

            KnowledgeSourceEventEntity ev = new KnowledgeSourceEventEntity();
            ev.setId(UUID.randomUUID());
            ev.setCollectionId(source.getCollectionId());
            ev.setSourceId(source.getId());
            ev.setSourceCode(source.getSourceCode());
            ev.setEventType("RETENTION_PURGED");
            ev.setSourceVersion(source.getVersion() != null ? source.getVersion() : 1);
            ev.setPreviousSha256(source.getContentSha256());
            ev.setNewSha256(null);
            ev.setChunksAdded(0);
            ev.setChunksReused(0);
            ev.setChunksRemoved(deletedChunks);
            ev.setDurationMs(1L);
            ev.setActor("system_retention");
            ev.setDetailsJson("{\"reason\":\"retention_expired\"}");
            ev.setCreatedAt(cutoff);
            eventMapper.insert(ev);

            purgedSources++;
            purgedChunks += deletedChunks;
            purgedGlossaryTerms += deletedTerms;
        }

        // Also purge any standalone expired chunks
        int orphanExpiredChunks = chunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .isNotNull(KnowledgeChunkEntity::getExpiresAt)
                        .le(KnowledgeChunkEntity::getExpiresAt, cutoff)
        );
        purgedChunks += orphanExpiredChunks;

        if (purgedSources > 0 || orphanExpiredChunks > 0) {
            auditService.record(new AuditEvent(
                    "SYSTEM",
                    "knowledge_retention",
                    null,
                    null,
                    "KNOWLEDGE_RETENTION_PURGED",
                    "KNOWLEDGE_STORE",
                    "expired_knowledge",
                    "SUCCESS",
                    Map.of(
                            "purged_sources", purgedSources,
                            "purged_chunks", purgedChunks,
                            "purged_glossary_terms", purgedGlossaryTerms
                    ),
                    "127.0.0.1"
            ));
        }

        return Map.of(
                "purged_sources", purgedSources,
                "purged_chunks", purgedChunks,
                "purged_glossary_terms", purgedGlossaryTerms,
                "executed_at", cutoff.toString()
        );
    }
}

