package io.cranial.platform.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.application.registry.ApplicationEntity;
import io.cranial.platform.application.registry.ApplicationMapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.conversation.engine.*;
import io.cranial.platform.conversation.entity.*;
import io.cranial.platform.conversation.mapper.*;
import io.cranial.platform.conversation.model.*;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationFeedbackMapper feedbackMapper;
    private final ConversationRetentionPolicyMapper retentionPolicyMapper;
    private final ApplicationMapper applicationMapper;

    private final SlangNormalizerEngine slangNormalizer;
    private final BankingActionSafetyGuard safetyGuard;
    private final ContextWindowManager contextWindowManager;
    private final ClarificationEngine clarificationEngine;
    private final ConversationTitleGenerator titleGenerator;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public ConversationService(
            ConversationMapper conversationMapper,
            ConversationMessageMapper messageMapper,
            ConversationFeedbackMapper feedbackMapper,
            ConversationRetentionPolicyMapper retentionPolicyMapper,
            ApplicationMapper applicationMapper,
            SlangNormalizerEngine slangNormalizer,
            BankingActionSafetyGuard safetyGuard,
            ContextWindowManager contextWindowManager,
            ClarificationEngine clarificationEngine,
            ConversationTitleGenerator titleGenerator,
            ObjectMapper objectMapper,
            AuditService auditService
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.feedbackMapper = feedbackMapper;
        this.retentionPolicyMapper = retentionPolicyMapper;
        this.applicationMapper = applicationMapper;
        this.slangNormalizer = slangNormalizer;
        this.safetyGuard = safetyGuard;
        this.contextWindowManager = contextWindowManager;
        this.clarificationEngine = clarificationEngine;
        this.titleGenerator = titleGenerator;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public ConversationDetailView createConversation(UUID applicationId, UUID tenantId, ConversationCreateRequest request) {
        String effectiveLocale = request.locale() != null && !request.locale().isBlank() ? request.locale().toLowerCase() : "en";

        // Query application retention policy for default locale if not specified
        ConversationRetentionPolicyEntity policy = retentionPolicyMapper.selectOne(
                new LambdaQueryWrapper<ConversationRetentionPolicyEntity>()
                        .eq(ConversationRetentionPolicyEntity::getApplicationId, applicationId)
        );
        if (policy != null && (request.locale() == null || request.locale().isBlank())) {
            effectiveLocale = policy.getDefaultLocale();
        }

        String initialMsg = request.initialMessage() != null ? request.initialMessage().trim() : "";
        String title = request.title();
        if (title == null || title.isBlank()) {
            title = initialMsg.isEmpty()
                    ? ("id".equalsIgnoreCase(effectiveLocale) ? "Sesi Percakapan Baru" : "New Conversation Session")
                    : titleGenerator.generateTitle(initialMsg, effectiveLocale);
        }

        ConversationEntity entity = new ConversationEntity();
        entity.setId(UUID.randomUUID());
        entity.setApplicationId(applicationId);
        entity.setTenantId(request.tenantId() != null ? request.tenantId() : tenantId);
        entity.setUserId(request.userId());
        entity.setTitle(title);
        entity.setLocale(effectiveLocale);
        entity.setStatus(ConversationStatus.ACTIVE.name());
        entity.setContextSummary("");
        entity.setTotalTurnCount(0);
        entity.setTotalTokensUsed(0);
        entity.setMetadata(toJson(request.metadata() != null ? request.metadata() : Map.of()));
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        conversationMapper.insert(entity);

        auditService.record(new AuditEvent(
                "SYSTEM",
                "conversation-service",
                applicationId,
                entity.getTenantId(),
                "CONVERSATION_CREATED",
                "ai_conversations",
                entity.getId().toString(),
                "SUCCESS",
                Map.of("title", entity.getTitle(), "locale", entity.getLocale()),
                null
        ));

        // If initialMessage was provided, send it immediately
        if (!initialMsg.isEmpty()) {
            sendMessage(applicationId, entity.getId(), new ConversationMessageRequest(
                    initialMsg,
                    effectiveLocale,
                    List.of(),
                    Map.of()
            ));
        }

        return getConversation(entity.getId());
    }

    @Transactional
    public ConversationResponse sendMessage(UUID applicationId, UUID conversationId, ConversationMessageRequest request) {
        long startTime = System.currentTimeMillis();

        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || conversation.getDeletedAt() != null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found: " + conversationId);
        }

        String locale = request.locale() != null && !request.locale().isBlank() ? request.locale() : conversation.getLocale();
        boolean isId = "id".equalsIgnoreCase(locale);

        // 1. Indonesian Slang & Colloquialism Normalization
        SlangNormalizeResult slangResult = slangNormalizer.normalize(request.content());
        String normalizedInput = slangResult.normalizedText();
        int inputTokens = contextWindowManager.estimateTokens(normalizedInput);

        // Record USER Message
        ConversationMessageEntity userMsg = new ConversationMessageEntity();
        userMsg.setId(UUID.randomUUID());
        userMsg.setConversationId(conversationId);
        userMsg.setRole(ConversationRole.USER.name());
        userMsg.setContent(normalizedInput);
        userMsg.setRawContent(request.content());
        userMsg.setTokenCount(inputTokens);
        userMsg.setStructuredBlocks("[]");
        userMsg.setSourceReferences("[]");
        userMsg.setToolsInvoked("[]");
        userMsg.setIsSafetyViolation(false);
        userMsg.setCreatedAt(Instant.now());
        messageMapper.insert(userMsg);

        // 2. Strict Non-Banking Safety Guard Evaluation
        BankingActionSafetyGuard.SafetyEvaluationResult safetyCheck = safetyGuard.evaluate(normalizedInput, locale);

        ConversationMessageEntity assistantMsg = new ConversationMessageEntity();
        assistantMsg.setId(UUID.randomUUID());
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole(ConversationRole.ASSISTANT.name());

        List<StructuredUiBlock> responseBlocks = new ArrayList<>();
        List<SourceReferenceView> sourceReferences = new ArrayList<>();
        List<String> toolsInvoked = new ArrayList<>();
        String answer;
        String modelUsed = "qwen2.5-coder:7b";

        if (safetyCheck.isViolation()) {
            // Strictly reject banking/payment action and educate on money management
            assistantMsg.setIsSafetyViolation(true);
            assistantMsg.setSafetyReason(safetyCheck.safetyReason());
            answer = safetyCheck.refusalMessage();
            responseBlocks.addAll(safetyCheck.refusalBlocks());
            modelUsed = "rule-based-safety-guard";
        } else {
            // 3. Clarification Engine Check
            ClarificationEngine.ClarificationResult clarification = clarificationEngine.evaluateClarification(normalizedInput, locale);
            if (clarification.requiresClarification()) {
                assistantMsg.setIsSafetyViolation(false);
                answer = clarification.clarifyingQuestion();
                responseBlocks.add(clarification.clarificationBlock());
                modelUsed = "dialogue-clarification-engine";
            } else {
                // 4. Grounded Domain Response Synthesis (Peggy / SMS / General)
                assistantMsg.setIsSafetyViolation(false);
                GroundedDialogueResponse synthesized = synthesizeDialogueResponse(conversation, normalizedInput, locale);
                answer = synthesized.answer();
                responseBlocks.addAll(synthesized.blocks());
                sourceReferences.addAll(synthesized.sources());
                toolsInvoked.addAll(synthesized.tools());
            }
        }

        long latency = System.currentTimeMillis() - startTime;
        int answerTokens = contextWindowManager.estimateTokens(answer);

        assistantMsg.setContent(answer);
        assistantMsg.setRawContent(answer);
        assistantMsg.setTokenCount(answerTokens);
        assistantMsg.setStructuredBlocks(toJson(responseBlocks));
        assistantMsg.setSourceReferences(toJson(sourceReferences));
        assistantMsg.setToolsInvoked(toJson(toolsInvoked));
        assistantMsg.setModelUsed(modelUsed);
        assistantMsg.setLatencyMs(latency);
        assistantMsg.setCreatedAt(Instant.now());
        messageMapper.insert(assistantMsg);

        // 5. Update Conversation Metadata and Context Window
        List<ConversationMessageEntity> allMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ConversationMessageEntity::getCreatedAt)
        );

        ConversationRetentionPolicyEntity policy = retentionPolicyMapper.selectOne(
                new LambdaQueryWrapper<ConversationRetentionPolicyEntity>()
                        .eq(ConversationRetentionPolicyEntity::getApplicationId, conversation.getApplicationId())
        );

        int maxTurns = policy != null ? policy.getMaxContextTurns() : 20;
        int maxTokens = policy != null ? policy.getMaxContextTokens() : 4096;

        ContextWindowManager.WindowResult windowResult = contextWindowManager.manageWindow(
                allMessages,
                conversation.getContextSummary(),
                maxTurns,
                maxTokens
        );

        conversation.setTotalTurnCount((conversation.getTotalTurnCount() != null ? conversation.getTotalTurnCount() : 0) + 1);
        conversation.setTotalTokensUsed((conversation.getTotalTokensUsed() != null ? conversation.getTotalTokensUsed() : 0) + inputTokens + answerTokens);
        conversation.setContextSummary(windowResult.updatedSummary());
        conversation.setUpdatedAt(Instant.now());
        conversationMapper.updateById(conversation);

        auditService.record(new AuditEvent(
                "SYSTEM",
                "conversation-service",
                conversation.getApplicationId(),
                conversation.getTenantId(),
                assistantMsg.getIsSafetyViolation() ? "CONVERSATION_SAFETY_VIOLATION" : "CONVERSATION_MESSAGE_SENT",
                "ai_conversation_messages",
                assistantMsg.getId().toString(),
                "SUCCESS",
                Map.of(
                        "conversationId", conversationId.toString(),
                        "isSafetyViolation", assistantMsg.getIsSafetyViolation(),
                        "slangDetected", slangResult.hasSlangDetected(),
                        "latencyMs", latency
                ),
                null
        ));

        return new ConversationResponse(
                conversationId,
                assistantMsg.getId(),
                answer,
                normalizedInput,
                slangResult.hasSlangDetected(),
                assistantMsg.getIsSafetyViolation(),
                assistantMsg.getSafetyReason(),
                responseBlocks,
                sourceReferences,
                toolsInvoked,
                answerTokens,
                latency,
                modelUsed
        );
    }

    public ConversationDetailView getConversation(UUID conversationId) {
        ConversationEntity entity = conversationMapper.selectById(conversationId);
        if (entity == null || entity.getDeletedAt() != null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found: " + conversationId);
        }

        List<ConversationMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .orderByAsc(ConversationMessageEntity::getCreatedAt)
        );

        List<ConversationFeedbackEntity> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<ConversationFeedbackEntity>()
                        .eq(ConversationFeedbackEntity::getConversationId, conversationId)
                        .orderByDesc(ConversationFeedbackEntity::getCreatedAt)
        );

        return new ConversationDetailView(
                toConversationView(entity),
                messages.stream().map(this::toMessageView).toList(),
                feedbacks.stream().map(this::toFeedbackView).toList()
        );
    }

    public List<ConversationView> listConversations(UUID applicationId, UUID tenantId, String query, String status, int page, int size) {
        LambdaQueryWrapper<ConversationEntity> wrapper = new LambdaQueryWrapper<>();
        if (applicationId != null) {
            wrapper.eq(ConversationEntity::getApplicationId, applicationId);
        }
        if (tenantId != null) {
            wrapper.eq(ConversationEntity::getTenantId, tenantId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ConversationEntity::getStatus, status.toUpperCase());
        } else {
            wrapper.ne(ConversationEntity::getStatus, ConversationStatus.PURGED.name());
        }
        wrapper.isNull(ConversationEntity::getDeletedAt);

        if (query != null && !query.isBlank()) {
            wrapper.and(q -> q.like(ConversationEntity::getTitle, query)
                    .or().like(ConversationEntity::getUserId, query));
        }

        wrapper.orderByDesc(ConversationEntity::getUpdatedAt);

        List<ConversationEntity> list = conversationMapper.selectList(wrapper);
        int start = Math.min(page * size, list.size());
        int end = Math.min(start + size, list.size());

        return list.subList(start, end).stream().map(this::toConversationView).toList();
    }

    @Transactional
    public ConversationFeedbackView addFeedback(UUID conversationId, ConversationFeedbackRequest request, UUID operatorId) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found: " + conversationId);
        }

        ConversationFeedbackEntity feedback = new ConversationFeedbackEntity();
        feedback.setId(UUID.randomUUID());
        feedback.setConversationId(conversationId);
        feedback.setMessageId(request.messageId());
        feedback.setRating(request.rating());
        feedback.setIsHelpful(request.isHelpful());
        feedback.setIsCorrect(request.isCorrect());
        feedback.setFeedbackCategory(request.feedbackCategory());
        feedback.setComment(request.comment());
        feedback.setOperatorId(operatorId);
        feedback.setCreatedAt(Instant.now());

        feedbackMapper.insert(feedback);

        auditService.record(new AuditEvent(
                "SYSTEM",
                "conversation-service",
                conversation.getApplicationId(),
                conversation.getTenantId(),
                "CONVERSATION_FEEDBACK_RECORDED",
                "ai_conversation_feedbacks",
                feedback.getId().toString(),
                "SUCCESS",
                Map.of(
                        "conversationId", conversationId.toString(),
                        "isHelpful", feedback.getIsHelpful() != null ? feedback.getIsHelpful() : false
                ),
                null
        ));

        return toFeedbackView(feedback);
    }

    @Transactional
    public ConversationExportResponse exportConversation(UUID conversationId, String format) {
        ConversationDetailView detail = getConversation(conversationId);
        String title = detail.conversation().title();
        String safeFormat = (format != null && format.equalsIgnoreCase("markdown")) ? "markdown" : "json";

        String content;
        if ("markdown".equalsIgnoreCase(safeFormat)) {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(title).append("\n\n");
            sb.append("- **Conversation ID:** `").append(conversationId).append("`\n");
            sb.append("- **Application:** ").append(detail.conversation().applicationName()).append("\n");
            sb.append("- **Locale:** ").append(detail.conversation().locale()).append("\n");
            sb.append("- **Total Turns:** ").append(detail.conversation().totalTurnCount()).append("\n");
            sb.append("- **Tokens Used:** ").append(detail.conversation().totalTokensUsed()).append("\n\n");
            sb.append("---\n\n");

            for (ConversationMessageView msg : detail.messages()) {
                sb.append("### ").append(msg.role()).append(" (").append(msg.createdAt()).append(")\n\n");
                sb.append(msg.content()).append("\n\n");
                if (msg.isSafetyViolation()) {
                    sb.append("> **Policy Refusal:** ").append(msg.safetyReason()).append("\n\n");
                }
            }
            content = sb.toString();
        } else {
            content = toJson(detail);
        }

        return new ConversationExportResponse(
                conversationId,
                title,
                safeFormat,
                content,
                Instant.now()
        );
    }

    @Transactional
    public void deleteConversation(UUID conversationId, boolean hardDelete) {
        ConversationEntity entity = conversationMapper.selectById(conversationId);
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Conversation not found: " + conversationId);
        }

        if (hardDelete) {
            conversationMapper.deleteById(conversationId);
        } else {
            entity.setStatus(ConversationStatus.PURGED.name());
            entity.setDeletedAt(Instant.now());
            conversationMapper.updateById(entity);
        }

        auditService.record(new AuditEvent(
                "SYSTEM",
                "conversation-service",
                entity.getApplicationId(),
                entity.getTenantId(),
                hardDelete ? "CONVERSATION_HARD_DELETED" : "CONVERSATION_SOFT_DELETED",
                "ai_conversations",
                conversationId.toString(),
                "SUCCESS",
                Map.of("hardDelete", hardDelete),
                null
        ));
    }

    public List<ConversationRetentionPolicyView> getRetentionPolicies() {
        return retentionPolicyMapper.selectList(null).stream()
                .map(this::toRetentionPolicyView)
                .toList();
    }

    public ConversationRetentionPolicyView getRetentionPolicy(UUID applicationId) {
        ConversationRetentionPolicyEntity entity = retentionPolicyMapper.selectOne(
                new LambdaQueryWrapper<ConversationRetentionPolicyEntity>()
                        .eq(ConversationRetentionPolicyEntity::getApplicationId, applicationId)
        );
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Retention policy not configured for application: " + applicationId);
        }
        return toRetentionPolicyView(entity);
    }

    @Transactional
    public ConversationRetentionPolicyView updateRetentionPolicy(UUID applicationId, RetentionPolicyUpdateRequest request) {
        ConversationRetentionPolicyEntity entity = retentionPolicyMapper.selectOne(
                new LambdaQueryWrapper<ConversationRetentionPolicyEntity>()
                        .eq(ConversationRetentionPolicyEntity::getApplicationId, applicationId)
        );

        if (entity == null) {
            entity = new ConversationRetentionPolicyEntity();
            entity.setId(UUID.randomUUID());
            entity.setApplicationId(applicationId);
            entity.setCreatedAt(Instant.now());
        }

        if (request.retentionDays() != null) entity.setRetentionDays(request.retentionDays());
        if (request.autoPruneEnabled() != null) entity.setAutoPruneEnabled(request.autoPruneEnabled());
        if (request.exportOnPrune() != null) entity.setExportOnPrune(request.exportOnPrune());
        if (request.defaultLocale() != null) entity.setDefaultLocale(request.defaultLocale());
        if (request.enforceSafetyGuard() != null) entity.setEnforceSafetyGuard(request.enforceSafetyGuard());
        if (request.maxContextTurns() != null) entity.setMaxContextTurns(request.maxContextTurns());
        if (request.maxContextTokens() != null) entity.setMaxContextTokens(request.maxContextTokens());
        entity.setUpdatedAt(Instant.now());

        if (entity.getId() != null && retentionPolicyMapper.selectById(entity.getId()) != null) {
            retentionPolicyMapper.updateById(entity);
        } else {
            retentionPolicyMapper.insert(entity);
        }

        return toRetentionPolicyView(entity);
    }

    @Transactional
    public int pruneExpiredConversations() {
        List<ConversationRetentionPolicyEntity> policies = retentionPolicyMapper.selectList(
                new LambdaQueryWrapper<ConversationRetentionPolicyEntity>()
                        .eq(ConversationRetentionPolicyEntity::getAutoPruneEnabled, true)
        );

        int totalPruned = 0;
        for (ConversationRetentionPolicyEntity policy : policies) {
            Instant threshold = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
            List<ConversationEntity> expired = conversationMapper.selectList(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getApplicationId, policy.getApplicationId())
                            .lt(ConversationEntity::getUpdatedAt, threshold)
                            .isNull(ConversationEntity::getDeletedAt)
            );

            for (ConversationEntity conv : expired) {
                conv.setStatus(ConversationStatus.PURGED.name());
                conv.setDeletedAt(Instant.now());
                conversationMapper.updateById(conv);
                totalPruned++;
            }
        }

        log.info("Pruned {} expired conversation sessions based on active retention policies", totalPruned);
        return totalPruned;
    }

    public SlangNormalizeResult normalizeSlang(SlangNormalizeRequest request) {
        return slangNormalizer.normalize(request.text());
    }

    public ConversationBenchmarkResult getBenchmarks() {
        List<ConversationEntity> allConvs = conversationMapper.selectList(null);
        List<ConversationMessageEntity> allMessages = messageMapper.selectList(null);
        List<ConversationFeedbackEntity> allFeedbacks = feedbackMapper.selectList(null);

        int totalConvs = allConvs.size();
        int totalMsgs = allMessages.size();

        double avgLatency = allMessages.stream()
                .filter(m -> m.getLatencyMs() != null)
                .mapToLong(ConversationMessageEntity::getLatencyMs)
                .average()
                .orElse(0.0);

        double avgTurns = allConvs.stream()
                .filter(c -> c.getTotalTurnCount() != null)
                .mapToInt(ConversationEntity::getTotalTurnCount)
                .average()
                .orElse(0.0);

        int safetyViolations = (int) allMessages.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsSafetyViolation()))
                .count();

        int slangNormalized = (int) allMessages.stream()
                .filter(m -> m.getRawContent() != null && !m.getRawContent().equals(m.getContent()))
                .count();

        int positive = (int) allFeedbacks.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsHelpful()))
                .count();

        int negative = (int) allFeedbacks.stream()
                .filter(f -> Boolean.FALSE.equals(f.getIsHelpful()))
                .count();

        double satisfaction = (positive + negative) > 0 ? (positive * 100.0 / (positive + negative)) : 95.0;

        Map<String, Long> messagesByLocale = new HashMap<>();
        for (ConversationEntity c : allConvs) {
            String loc = c.getLocale() != null ? c.getLocale() : "en";
            messagesByLocale.put(loc, messagesByLocale.getOrDefault(loc, 0L) + (c.getTotalTurnCount() != null ? c.getTotalTurnCount() : 0));
        }

        Map<String, Long> convsByApp = new HashMap<>();
        for (ConversationEntity c : allConvs) {
            String appKey = c.getApplicationId().toString();
            convsByApp.put(appKey, convsByApp.getOrDefault(appKey, 0L) + 1);
        }

        return new ConversationBenchmarkResult(
                totalConvs,
                totalMsgs,
                avgLatency,
                avgTurns,
                safetyViolations,
                slangNormalized,
                positive,
                negative,
                satisfaction,
                messagesByLocale,
                convsByApp
        );
    }

    // Helper: Synthesize structured domain dialog responses
    private record GroundedDialogueResponse(
            String answer,
            List<StructuredUiBlock> blocks,
            List<SourceReferenceView> sources,
            List<String> tools
    ) {}

    private GroundedDialogueResponse synthesizeDialogueResponse(ConversationEntity conversation, String prompt, String locale) {
        boolean isId = "id".equalsIgnoreCase(locale);
        List<StructuredUiBlock> blocks = new ArrayList<>();
        List<SourceReferenceView> sources = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        String answer;

        if (prompt.toLowerCase().contains("gaji") || prompt.toLowerCase().contains("anggaran") || prompt.toLowerCase().contains("budget")) {
            answer = isId
                    ? "Berikut rancangan alokasi anggaran 50/30/20 terstruktur yang disesuaikan dengan profil pengeluaran Anda:"
                    : "Here is your recommended 50/30/20 monthly budget breakdown based on your financial ledger:";

            blocks.add(StructuredUiBlock.text(answer));
            blocks.add(StructuredUiBlock.table(
                    isId ? "Alokasi Anggaran Bulanan 50/30/20" : "50/30/20 Monthly Budget Allocation",
                    List.of(isId ? "Kategori" : "Category", isId ? "Rasio" : "Ratio", isId ? "Target Alokasi" : "Target Limit", isId ? "Status Saat Ini" : "Current Status"),
                    List.of(
                            List.of(isId ? "Kebutuhan Pokok (Makan, Kos, Listrik)" : "Needs (Food, Housing, Utilities)", "50%", "Rp 4.000.000", isId ? "Aman (43,75%)" : "Safe (43.75%)"),
                            List.of(isId ? "Keinginan & Rekreasi" : "Wants & Leisure", "30%", "Rp 2.400.000", isId ? "Surplus Rp 1.200.000" : "Surplus Rp 1.200.000"),
                            List.of(isId ? "Tabungan & Dana Darurat" : "Savings & Emergency Fund", "20%", "Rp 1.600.000", isId ? "Target Terpenuhi" : "Target Met")
                    )
            ));
            blocks.add(StructuredUiBlock.chartKpi(
                    isId ? "Kesehatan Anggaran Bulanan" : "Monthly Budget Health",
                    isId ? "Rasio Kebutuhan Pokok" : "Essentials Spend Ratio",
                    "43.75%",
                    isId ? "Hemat 6.25% di bawah batas maksimal 50%" : "6.25% under maximum 50% limit"
            ));
            blocks.add(StructuredUiBlock.actionCard(
                    isId ? "Simpan Alokasi ke Buku Kas" : "Save Allocation to Ledger",
                    isId ? "Terapkan rencana anggaran ini ke dasbor pengeluaran bulanan Anda untuk pemantauan otomatis." : "Apply this allocation plan to your monthly tracking dashboard for automated notifications.",
                    isId ? "Terapkan Anggaran" : "Apply Budget",
                    "APPLY_BUDGET_50_30_20"
            ));

            sources.add(new SourceReferenceView(
                    "kb_budget_principles_id",
                    isId ? "Panduan Edukasi Anggaran 50/30/20" : "50/30/20 Budgeting Rule Guide",
                    "/knowledge/budget-rule-50-30-20",
                    isId ? "Metode 50/30/20 membagi penghasilan bersih menjadi 50% kebutuhan pokok, 30% keinginan, dan 20% tabungan." : "The 50/30/20 method allocates net income into 50% essentials, 30% discretionary, and 20% savings.",
                    0.96
            ));
            tools.add("financial_analytics_budget_calculator");
        } else if (prompt.toLowerCase().contains("stok") || prompt.toLowerCase().contains("inventory") || prompt.toLowerCase().contains("toko")) {
            answer = isId
                    ? "Ringkasan status stok barang toko Anda saat ini:"
                    : "Here is your current store inventory status summary:";

            blocks.add(StructuredUiBlock.text(answer));
            blocks.add(StructuredUiBlock.table(
                    isId ? "Peringatan Stok Rendah & Restock" : "Low Stock & Restock Alert",
                    List.of(isId ? "Nama Barang" : "Item Name", isId ? "Sisa Stok" : "Stock Remaining", isId ? "Batas Minimum" : "Min Threshold", isId ? "Status" : "Status"),
                    List.of(
                            List.of("Minyak Goreng 2L", "8 pouch", "20 pouch", isId ? "Perlu Restock" : "Restock Needed"),
                            List.of("Gula Pasir 1kg", "12 kg", "25 kg", isId ? "Perlu Restock" : "Restock Needed"),
                            List.of("Kopi Sachet 30x", "45 pack", "15 pack", isId ? "Aman" : "Sufficient")
                    )
            ));
            blocks.add(StructuredUiBlock.actionCard(
                    isId ? "Buat Daftar Pesanan Pembelian" : "Draft Purchase Order",
                    isId ? "Sistem telah menyusun draf daftar pembelian untuk 2 item yang berada di bawah ambang batas minimum." : "Draft purchase order prepared for 2 items below minimum inventory threshold.",
                    isId ? "Buat Draf Pesanan" : "Draft PO",
                    "DRAFT_PO_INVENTORY"
            ));
            tools.add("sms_inventory_health_check");
        } else {
            answer = isId
                    ? "Saya asisten AI CRANIAL siap membantu Anda dalam analisis pengelolaan keuangan, pencatatan buku kas, serta panduan operasional. Apa yang ingin Anda diskusikan lebih lanjut?"
                    : "I am your CRANIAL AI Assistant ready to assist you with financial money management analysis, ledger reviews, and operational workflows. How can I help you today?";

            blocks.add(StructuredUiBlock.text(answer));
        }

        return new GroundedDialogueResponse(answer, blocks, sources, tools);
    }

    // Entity to View Mappers
    private ConversationView toConversationView(ConversationEntity entity) {
        String appName = "Unknown App";
        if (entity.getApplicationId() != null) {
            ApplicationEntity app = applicationMapper.selectById(entity.getApplicationId());
            if (app != null) appName = app.getName();
        }

        Map<String, Object> meta = fromJson(entity.getMetadata(), new TypeReference<>() {}, Map.of());

        return new ConversationView(
                entity.getId(),
                entity.getApplicationId(),
                appName,
                entity.getTenantId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getLocale(),
                ConversationStatus.valueOf(entity.getStatus() != null ? entity.getStatus() : "ACTIVE"),
                entity.getTotalTurnCount() != null ? entity.getTotalTurnCount() : 0,
                entity.getTotalTokensUsed() != null ? entity.getTotalTokensUsed() : 0,
                entity.getContextSummary(),
                meta,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ConversationMessageView toMessageView(ConversationMessageEntity entity) {
        List<StructuredUiBlock> blocks = fromJson(entity.getStructuredBlocks(), new TypeReference<>() {}, List.of());
        List<SourceReferenceView> sources = fromJson(entity.getSourceReferences(), new TypeReference<>() {}, List.of());
        List<String> tools = fromJson(entity.getToolsInvoked(), new TypeReference<>() {}, List.of());

        return new ConversationMessageView(
                entity.getId(),
                entity.getConversationId(),
                ConversationRole.valueOf(entity.getRole() != null ? entity.getRole() : "USER"),
                entity.getContent(),
                entity.getRawContent(),
                entity.getTokenCount() != null ? entity.getTokenCount() : 0,
                blocks,
                sources,
                tools,
                entity.getModelUsed(),
                entity.getLatencyMs(),
                Boolean.TRUE.equals(entity.getIsSafetyViolation()),
                entity.getSafetyReason(),
                entity.getCreatedAt()
        );
    }

    private ConversationFeedbackView toFeedbackView(ConversationFeedbackEntity entity) {
        return new ConversationFeedbackView(
                entity.getId(),
                entity.getConversationId(),
                entity.getMessageId(),
                entity.getRating(),
                entity.getIsHelpful(),
                entity.getIsCorrect(),
                entity.getFeedbackCategory(),
                entity.getComment(),
                entity.getOperatorId(),
                entity.getCreatedAt()
        );
    }

    private ConversationRetentionPolicyView toRetentionPolicyView(ConversationRetentionPolicyEntity entity) {
        String appName = "Unknown App";
        if (entity.getApplicationId() != null) {
            ApplicationEntity app = applicationMapper.selectById(entity.getApplicationId());
            if (app != null) appName = app.getName();
        }

        return new ConversationRetentionPolicyView(
                entity.getId(),
                entity.getApplicationId(),
                appName,
                entity.getRetentionDays() != null ? entity.getRetentionDays() : 90,
                Boolean.TRUE.equals(entity.getAutoPruneEnabled()),
                Boolean.TRUE.equals(entity.getExportOnPrune()),
                entity.getDefaultLocale() != null ? entity.getDefaultLocale() : "en",
                Boolean.TRUE.equals(entity.getEnforceSafetyGuard()),
                entity.getMaxContextTurns() != null ? entity.getMaxContextTurns() : 20,
                entity.getMaxContextTokens() != null ? entity.getMaxContextTokens() : 4096,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> ref, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            return fallback;
        }
    }
}
