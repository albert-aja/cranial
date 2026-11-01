package io.cranial.platform.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.customer.engine.CustomerPrivacyGuard;
import io.cranial.platform.customer.engine.CustomerPromiseGuard;
import io.cranial.platform.customer.engine.CustomerSentimentEngine;
import io.cranial.platform.customer.engine.CustomerSlangNormalizer;
import io.cranial.platform.customer.engine.CustomerTriageEngine;
import io.cranial.platform.customer.entity.CustomerEscalationEntity;
import io.cranial.platform.customer.entity.CustomerEvalCaseEntity;
import io.cranial.platform.customer.entity.CustomerSlangLexiconEntity;
import io.cranial.platform.customer.entity.CustomerTicketEntity;
import io.cranial.platform.customer.mapper.CustomerEscalationMapper;
import io.cranial.platform.customer.mapper.CustomerEvalCaseMapper;
import io.cranial.platform.customer.mapper.CustomerSlangLexiconMapper;
import io.cranial.platform.customer.mapper.CustomerTicketMapper;
import io.cranial.platform.customer.model.*;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CustomerIntelligenceService {

    private static final Set<String> ALLOWED_SLANG_CATEGORIES = Set.of(
            "ACTION_COLLOQUIALISM",
            "BANKING_REFUND_SLANG",
            "PERFORMANCE_SLANG",
            "AUTH_ACCESS_SLANG",
            "CHAT_ABBREVIATION",
            "NEGATION_SHORTHAND",
            "FINANCIAL_SLANG",
            "ESCALATION_SLANG",
            "OCR_SUPPORT_SLANG"
    );

    private final CustomerTicketMapper ticketMapper;
    private final CustomerEscalationMapper escalationMapper;
    private final CustomerEvalCaseMapper evalCaseMapper;
    private final CustomerSlangLexiconMapper slangLexiconMapper;
    private final CustomerPrivacyGuard privacyGuard;
    private final CustomerPromiseGuard promiseGuard;
    private final CustomerSlangNormalizer slangNormalizer;
    private final CustomerSentimentEngine sentimentEngine;
    private final CustomerTriageEngine triageEngine;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final AtomicLong TICKET_COUNTER = new AtomicLong(1000);

    public CustomerIntelligenceService(
            CustomerTicketMapper ticketMapper,
            CustomerEscalationMapper escalationMapper,
            CustomerEvalCaseMapper evalCaseMapper,
            CustomerSlangLexiconMapper slangLexiconMapper,
            CustomerPrivacyGuard privacyGuard,
            CustomerPromiseGuard promiseGuard,
            CustomerSlangNormalizer slangNormalizer,
            CustomerSentimentEngine sentimentEngine,
            CustomerTriageEngine triageEngine,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.escalationMapper = escalationMapper;
        this.evalCaseMapper = evalCaseMapper;
        this.slangLexiconMapper = slangLexiconMapper;
        this.privacyGuard = privacyGuard;
        this.promiseGuard = promiseGuard;
        this.slangNormalizer = slangNormalizer;
        this.sentimentEngine = sentimentEngine;
        this.triageEngine = triageEngine;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public CustomerSlangNormalizeResult normalizeSupportChat(CustomerSlangNormalizeRequest request) {
        String effectiveLocale = (request.locale() != null && !request.locale().isBlank()) ? request.locale() : "id";
        boolean shouldRedact = request.redactPii() == null || Boolean.TRUE.equals(request.redactPii());

        var redaction = shouldRedact
                ? privacyGuard.sanitize(request.text())
                : new CustomerPrivacyGuard.RedactionResult(request.text(), false, List.of());

        var norm = slangNormalizer.normalize(redaction.redactedText(), effectiveLocale);
        var promiseEval = promiseGuard.evaluate(norm.normalizedText(), effectiveLocale);
        boolean bankingBoundaryDetected = norm.bankingBoundaryHintDetected() || promiseEval.flagged();

        return new CustomerSlangNormalizeResult(
                request.text(),
                redaction.redactedText(),
                norm.normalizedText(),
                effectiveLocale,
                redaction.piiDetected(),
                redaction.piiEntities(),
                norm.slangDetected(),
                norm.elongationCollapsedCount(),
                norm.replacements().size(),
                norm.replacements(),
                bankingBoundaryDetected,
                bankingBoundaryDetected ? promiseGuard.getDisclaimer(effectiveLocale) : null
        );
    }

    public List<CustomerSlangLexiconView> listSlangLexicon(String category, String search) {
        return slangNormalizer.listBuiltInAndCustomLexicon(category, search);
    }

    @Transactional
    public CustomerSlangLexiconView saveSlangLexiconEntry(CustomerSlangLexiconRequest request, String actorEmail) {
        String normalizedTerm = request.slangTerm().trim().toLowerCase(Locale.ROOT);
        String canonical = request.canonicalForm().trim();
        String category = (request.category() != null && ALLOWED_SLANG_CATEGORIES.contains(request.category().toUpperCase(Locale.ROOT)))
                ? request.category().toUpperCase(Locale.ROOT)
                : "ACTION_COLLOQUIALISM";
        String locale = (request.locale() != null && !request.locale().isBlank()) ? request.locale().trim() : "id";
        boolean bankingHint = Boolean.TRUE.equals(request.bankingBoundaryHint());
        boolean active = request.isActive() == null || Boolean.TRUE.equals(request.isActive());

        CustomerSlangLexiconEntity existing = slangLexiconMapper.selectOne(
                new LambdaQueryWrapper<CustomerSlangLexiconEntity>()
                        .eq(CustomerSlangLexiconEntity::getSlangTerm, normalizedTerm)
        );

        Instant now = Instant.now();
        CustomerSlangLexiconEntity entity;
        if (existing != null) {
            entity = existing;
            entity.setCanonicalForm(canonical);
            entity.setCategory(category);
            entity.setLocale(locale);
            entity.setIsBankingBoundaryHint(bankingHint);
            entity.setExplanation(request.explanation());
            entity.setIsActive(active);
            entity.setUpdatedAt(now);
            slangLexiconMapper.updateById(entity);
        } else {
            entity = new CustomerSlangLexiconEntity();
            entity.setId(UUID.randomUUID());
            entity.setSlangTerm(normalizedTerm);
            entity.setCanonicalForm(canonical);
            entity.setCategory(category);
            entity.setLocale(locale);
            entity.setIsBankingBoundaryHint(bankingHint);
            entity.setExplanation(request.explanation());
            entity.setIsActive(active);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            slangLexiconMapper.insert(entity);
        }

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorEmail != null ? actorEmail : "operator",
                null,
                null,
                "CUSTOMER_SLANG_LEXICON_SAVED",
                "ai_customer_slang_lexicon",
                entity.getId().toString(),
                "SUCCESS",
                Map.of(
                        "slang_term", normalizedTerm,
                        "canonical_form", canonical,
                        "category", category,
                        "banking_boundary_hint", bankingHint
                ),
                null
        ));

        return new CustomerSlangLexiconView(
                entity.getId(),
                entity.getSlangTerm(),
                entity.getCanonicalForm(),
                entity.getCategory(),
                entity.getLocale(),
                Boolean.TRUE.equals(entity.getIsBankingBoundaryHint()),
                entity.getExplanation(),
                "CUSTOM_DB",
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getUpdatedAt()
        );
    }

    @Transactional
    public void deleteSlangLexiconEntry(UUID id, String actorEmail) {
        CustomerSlangLexiconEntity existing = slangLexiconMapper.selectById(id);
        if (existing == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Custom slang lexicon entry not found: " + id);
        }
        slangLexiconMapper.deleteById(id);

        auditService.record(new AuditEvent(
                "PLATFORM_USER",
                actorEmail != null ? actorEmail : "operator",
                null,
                null,
                "CUSTOMER_SLANG_LEXICON_DELETED",
                "ai_customer_slang_lexicon",
                id.toString(),
                "SUCCESS",
                Map.of("slang_term", existing.getSlangTerm()),
                null
        ));
    }

    public SentimentAnalysisResult analyzeSentiment(
            SentimentAnalysisRequest request,
            UUID applicationId,
            UUID tenantId,
            boolean isAdmin
    ) {
        boolean shouldRedact = request.redactPii() == null || Boolean.TRUE.equals(request.redactPii());
        var redaction = shouldRedact
                ? privacyGuard.sanitize(request.text())
                : new CustomerPrivacyGuard.RedactionResult(request.text(), false, List.of());
        var norm = slangNormalizer.normalize(redaction.redactedText(), request.locale());
        var sentimentOutput = sentimentEngine.analyze(norm.normalizedText());

        return new SentimentAnalysisResult(
                request.text(),
                redaction.redactedText(),
                norm.normalizedText(),
                norm.slangDetected(),
                norm.replacements(),
                sentimentOutput.sentiment(),
                sentimentOutput.score(),
                sentimentOutput.confidence(),
                sentimentOutput.urgency(),
                redaction.piiDetected(),
                redaction.piiEntities(),
                sentimentOutput.aspects()
        );
    }

    @Transactional
    public TicketTriageResult triageTicket(
            TicketTriageRequest request,
            UUID applicationId,
            UUID tenantId,
            boolean isAdmin,
            String applicationCode
    ) {
        String effectiveLocale = request.locale() != null ? request.locale() : "en";
        var redaction = privacyGuard.sanitize(request.message());
        var norm = slangNormalizer.normalize(redaction.redactedText(), effectiveLocale);
        var sentimentOutput = sentimentEngine.analyze(norm.normalizedText());
        var triageDecision = triageEngine.triage(
                norm.normalizedText(),
                effectiveLocale,
                sentimentOutput.sentiment(),
                sentimentOutput.score(),
                sentimentOutput.urgency()
        );

        UUID ticketId = UUID.randomUUID();
        String ticketNumber = "TKT-" + System.currentTimeMillis() % 100000000 + "-" + TICKET_COUNTER.incrementAndGet();

        CustomerTicketEntity ticket = new CustomerTicketEntity();
        ticket.setId(ticketId);
        ticket.setTicketNumber(ticketNumber);
        ticket.setApplicationId(applicationId);
        ticket.setTenantId(tenantId);
        ticket.setExternalUserRef(request.externalUserRef());
        ticket.setCustomerName(request.customerName());
        ticket.setSubject(request.subject());
        ticket.setRawMessage(request.message());
        ticket.setRedactedMessage(redaction.redactedText());
        ticket.setNormalizedMessage(norm.normalizedText());
        ticket.setSlangNormalized(norm.slangDetected());
        ticket.setSlangReplacementsJson(writeJson(norm.replacements()));
        ticket.setPiiDetected(redaction.piiDetected());
        ticket.setPiiEntitiesJson(writeJson(redaction.piiEntities()));
        ticket.setLocale(effectiveLocale);
        ticket.setCategory(triageDecision.category().name());
        ticket.setSentiment(sentimentOutput.sentiment().name());
        ticket.setSentimentScore(sentimentOutput.score());
        ticket.setUrgency(sentimentOutput.urgency().name());
        ticket.setPriority(triageDecision.priority().name());
        ticket.setRoutingQueue(triageDecision.routingQueue().name());
        ticket.setStatus(triageDecision.escalationRecommended() ? TicketStatus.ESCALATED.name() : TicketStatus.TRIAGED.name());
        ticket.setPromiseGuardFlagged(triageDecision.promiseGuardFlagged());
        ticket.setPromiseGuardReasons(writeJson(triageDecision.promiseGuardReasons()));
        ticket.setSuggestedCannedResponse(triageDecision.suggestedCannedResponse());
        ticket.setConfidenceScore(triageDecision.confidenceScore());
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());

        ticketMapper.insert(ticket);

        EscalationHandoffPackage handoffPackage = null;
        if (triageDecision.escalationRecommended()) {
            handoffPackage = createEscalation(
                    ticketId,
                    ticketNumber,
                    request.customerName(),
                    applicationId,
                    tenantId,
                    triageDecision.escalationReason(),
                    norm.normalizedText(),
                    norm.slangDetected(),
                    sentimentOutput.score(),
                    triageDecision.promiseGuardFlagged()
            );
        }

        auditService.record(new AuditEvent(
                isAdmin ? "PLATFORM_USER" : "APPLICATION",
                isAdmin ? "admin-console" : (applicationCode != null ? applicationCode : "system"),
                applicationId,
                tenantId,
                "CUSTOMER_TICKET_TRIAGED",
                "ai_customer_tickets",
                ticketId.toString(),
                "SUCCESS",
                Map.of(
                        "ticket_number", ticketNumber,
                        "category", triageDecision.category().name(),
                        "priority", triageDecision.priority().name(),
                        "escalated", triageDecision.escalationRecommended(),
                        "slang_normalized", norm.slangDetected(),
                        "slang_replacements_count", norm.replacements().size()
                ),
                null
        ));

        return new TicketTriageResult(
                ticketId,
                ticketNumber,
                triageDecision.category(),
                triageDecision.priority(),
                sentimentOutput.urgency(),
                sentimentOutput.sentiment(),
                sentimentOutput.score(),
                triageDecision.routingQueue(),
                redaction.redactedText(),
                norm.normalizedText(),
                norm.slangDetected(),
                norm.replacements(),
                redaction.piiDetected(),
                redaction.piiEntities(),
                triageDecision.promiseGuardFlagged(),
                triageDecision.promiseGuardReasons(),
                triageDecision.suggestedCannedResponse(),
                triageDecision.confidenceScore(),
                triageDecision.escalationRecommended(),
                triageDecision.escalationReason(),
                handoffPackage
        );
    }

    private EscalationHandoffPackage createEscalation(
            UUID ticketId,
            String ticketNumber,
            String customerName,
            UUID applicationId,
            UUID tenantId,
            EscalationReason reason,
            String messageText,
            boolean slangNormalized,
            double sentimentScore,
            boolean promiseViolation
    ) {
        UUID escalationId = UUID.randomUUID();
        String summary = "Customer ticket " + ticketNumber + " escalated due to: " + reason.name() +
                (slangNormalized ? " [Colloquial ID chat normalized]" : "") +
                ". Message snippet: \"" + truncate(messageText, 140) + "\".";

        String sentimentSummary = "Detected sentiment score: " + sentimentScore +
                (sentimentScore < -0.5 ? " (Severe customer frustration detected)" : " (Elevated friction)");

        String attempts = "Automated policy evaluation conducted. Colloquial spelling normalization & Promise safety check applied.";

        List<String> actions = new ArrayList<>();
        if (promiseViolation) {
            actions.add("Reiterate that platform is strictly a money management app with no direct fund transfers or cash refunds.");
            actions.add("Review transaction records and provide ledger clarification.");
        } else if (reason == EscalationReason.USER_REQUESTED_AGENT) {
            actions.add("Greet customer promptly via live support desk.");
            actions.add("Inquire about specific pocket or ledger sync issue.");
        } else {
            actions.add("Review customer issue logs and apologize for friction.");
            actions.add("Assist with pocket configuration or receipt OCR guidance.");
        }

        CustomerEscalationEntity escalation = new CustomerEscalationEntity();
        escalation.setId(escalationId);
        escalation.setTicketId(ticketId);
        escalation.setApplicationId(applicationId);
        escalation.setTenantId(tenantId);
        escalation.setEscalationReason(reason.name());
        escalation.setExecutiveSummary(summary);
        escalation.setCustomerSentimentSummary(sentimentSummary);
        escalation.setAttemptsMade(attempts);
        escalation.setRecommendedActionsJson(writeJson(actions));
        escalation.setStatus(EscalationStatus.PENDING_HANDOFF.name());
        escalation.setCreatedAt(Instant.now());
        escalation.setUpdatedAt(Instant.now());

        escalationMapper.insert(escalation);

        return new EscalationHandoffPackage(
                escalationId,
                ticketId,
                ticketNumber,
                reason,
                summary,
                sentimentSummary,
                attempts,
                actions,
                EscalationStatus.PENDING_HANDOFF,
                Instant.now()
        );
    }

    public ReviewSummaryResult summarizeReviews(
            ReviewSummarizeRequest request,
            UUID applicationId,
            UUID tenantId,
            boolean isAdmin
    ) {
        List<String> reviews = request.reviews();
        if (reviews == null || reviews.isEmpty()) {
            return new ReviewSummaryResult(0, CustomerSentiment.NEUTRAL, 0.0, List.of(), List.of(), List.of(), List.of(), "No reviews provided.");
        }

        int total = reviews.size();
        double sumScore = 0.0;
        List<String> pros = new ArrayList<>();
        List<String> cons = new ArrayList<>();
        List<String> featureRequests = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        for (String raw : reviews) {
            var sanitized = privacyGuard.sanitize(raw);
            var norm = slangNormalizer.normalize(sanitized.redactedText(), request.locale());
            var result = sentimentEngine.analyze(norm.normalizedText());
            sumScore += result.score();

            String lower = norm.normalizedText().toLowerCase(Locale.ROOT);
            if (result.sentiment() == CustomerSentiment.POSITIVE) {
                pros.add(norm.normalizedText());
            } else if (result.sentiment() == CustomerSentiment.NEGATIVE) {
                cons.add(norm.normalizedText());
            }

            if (lower.contains("dark mode") || lower.contains("add") || lower.contains("tambahkan") || lower.contains("fitur") || lower.contains("tolong")) {
                featureRequests.add(norm.normalizedText());
            }

            evidence.add("\"" + truncate(sanitized.redactedText(), 80) + "\"");
        }

        double avgScore = Math.round((sumScore / total) * 10000.0) / 10000.0;
        CustomerSentiment overall;
        if (avgScore > 0.2) overall = CustomerSentiment.POSITIVE;
        else if (avgScore < -0.2) overall = CustomerSentiment.NEGATIVE;
        else overall = CustomerSentiment.NEUTRAL;

        String summary = String.format(
                "Analyzed %d reviews. Overall sentiment is %s (avg score: %.2f). Users appreciate %s, while noting friction with %s.",
                total,
                overall.name(),
                avgScore,
                pros.isEmpty() ? "general interface" : "budgeting and pocket management features",
                cons.isEmpty() ? "none" : "occasional performance latency or receipt scanning errors"
        );

        return new ReviewSummaryResult(
                total,
                overall,
                avgScore,
                pros.stream().limit(5).toList(),
                cons.stream().limit(5).toList(),
                featureRequests.stream().limit(5).toList(),
                evidence.stream().limit(6).toList(),
                summary
        );
    }

    public ProductDiscoveryResult discoverProducts(
            ProductDiscoveryRequest request,
            UUID applicationId,
            UUID tenantId,
            boolean isAdmin
    ) {
        var normGoal = slangNormalizer.normalize(request.customerGoal(), request.locale());
        String goal = normGoal.normalizedText().toLowerCase(Locale.ROOT);
        boolean isId = request.locale() != null && request.locale().equalsIgnoreCase("id");

        List<RecommendedFeatureView> features = new ArrayList<>();

        if (goal.contains("save") || goal.contains("tabungan") || goal.contains("emergency") || goal.contains("darurat") || goal.contains("wedding") || goal.contains("nikah")) {
            features.add(new RecommendedFeatureView(
                    "EMERGENCY_AND_GOAL_POCKETS",
                    isId ? "Kantong Alokasi Dana Darurat & Tabungan Target" : "Emergency Fund & Goal Savings Pockets",
                    "MONEY_MANAGEMENT",
                    0.95,
                    isId ? "Membantu memisahkan alokasi dana secara virtual sehingga tidak tercampur dengan pengeluaran harian." : "Separates dedicated savings buffers from daily operational expenses.",
                    isId ? "Pencatatan alokasi ini berbasis buku kas internal dan tidak mengunci saldo bank pengguna." : "Allocations exist inside your budgeting ledger without freezing bank accounts.",
                    List.of(
                            isId ? "1. Masuk ke menu 'Kantong Keuangan'" : "1. Open 'Pockets' dashboard",
                            isId ? "2. Buat kantong baru dengan target nominal" : "2. Create target allocation pocket",
                            isId ? "3. Hubungkan dengan aturan autodebit catatan" : "3. Enable virtual allocation tracking"
                    )
            ));
        }

        if (goal.contains("budget") || goal.contains("anggaran") || goal.contains("50-30-20") || goal.contains("gaji") || goal.contains("rugi")) {
            features.add(new RecommendedFeatureView(
                    "RULE_50_30_20_BUDGETING",
                    isId ? "Panduan Anggaran 50/30/20 Otomatis" : "Automated 50/30/20 Budgeting Rule",
                    "MONEY_MANAGEMENT",
                    0.92,
                    isId ? "Membagi pemasukan bulanan ke dalam 50% Kebutuhan, 30% Keinginan, dan 20% Tabungan/Investasi." : "Categorizes monthly inflow into 50% Needs, 30% Wants, and 20% Savings.",
                    isId ? "Metode ini bersifat rekomendasi pengelolaan dan dapat disesuaikan fleksibel oleh pengguna." : "Flexible budgeting framework that adapts to personal spending goals.",
                    List.of(
                            isId ? "1. Atur nominal pemasukan bulanan" : "1. Input monthly income baseline",
                            isId ? "2. Konfirmasi pembagian persentase" : "2. Confirm category distribution"
                    )
            ));
        }

        if (goal.contains("shared") || goal.contains("spouse") || goal.contains("keluarga") || goal.contains("patungan") || goal.contains("partner")) {
            features.add(new RecommendedFeatureView(
                    "SHARED_FAMILY_POCKET",
                    isId ? "Kantong Bersama (Shared Pockets)" : "Collaborative Shared Pockets",
                    "COLLABORATIVE_FINANCE",
                    0.89,
                    isId ? "Memungkinkan pasangan atau anggota keluarga memantau catatan belanja bersama dengan izin terbatas." : "Allows spouses or team members to collaboratively track shared expenses.",
                    isId ? "Setiap anggota hanya dapat melihat catatan yang diotorisasi, tanpa akses ke rekening pribadi." : "Role-based visibility ensures personal finances stay private.",
                    List.of(
                            isId ? "1. Undang anggota via email atau nomor kontak" : "1. Invite collaborator with read-only/editor role",
                            isId ? "2. Bagikan hanya kantong belanja keluarga" : "2. Designate specific shared expense pocket"
                    )
            ));
        }

        if (features.isEmpty()) {
            features.add(new RecommendedFeatureView(
                    "SMART_EXPENSE_TRACKER",
                    isId ? "Pencatat Pengeluaran Cerdas" : "Smart Money Ledger & Categorization",
                    "CORE_TRACKING",
                    0.85,
                    isId ? "Pencatatan transaksi harian dengan kategori cerdas dan pelacakan arus kas deterministik." : "Comprehensive transaction logging with deterministic cashflow breakdowns.",
                    isId ? "Sistem tidak melakukan transaksi perbankan langsung." : "Read-only budgeting ledger.",
                    List.of(isId ? "Mulai catat transaksi pertama Anda" : "Start logging your daily transactions")
            ));
        }

        return new ProductDiscoveryResult(
                request.customerGoal(),
                features,
                promiseGuard.getDisclaimer(request.locale())
        );
    }

    public List<CustomerTicketView> listTickets(
            UUID applicationId,
            UUID tenantId,
            String status,
            String priority,
            String category,
            boolean isAdmin
    ) {
        var query = new LambdaQueryWrapper<CustomerTicketEntity>();

        if (!isAdmin && applicationId != null) {
            query.eq(CustomerTicketEntity::getApplicationId, applicationId);
            if (tenantId != null) {
                query.eq(CustomerTicketEntity::getTenantId, tenantId);
            }
        } else if (applicationId != null) {
            query.eq(CustomerTicketEntity::getApplicationId, applicationId);
        }

        if (status != null && !status.isBlank()) {
            query.eq(CustomerTicketEntity::getStatus, status);
        }
        if (priority != null && !priority.isBlank()) {
            query.eq(CustomerTicketEntity::getPriority, priority);
        }
        if (category != null && !category.isBlank()) {
            query.eq(CustomerTicketEntity::getCategory, category);
        }

        query.orderByDesc(CustomerTicketEntity::getCreatedAt);

        return ticketMapper.selectList(query).stream().map(this::toTicketView).toList();
    }

    public CustomerTicketView getTicket(UUID ticketId, UUID applicationId, UUID tenantId, boolean isAdmin) {
        CustomerTicketEntity entity = ticketMapper.selectById(ticketId);
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Customer ticket not found: " + ticketId);
        }
        if (!isAdmin && applicationId != null && !applicationId.equals(entity.getApplicationId())) {
            throw new CranialException(ErrorCode.FORBIDDEN, "Access denied to cross-application customer ticket");
        }
        return toTicketView(entity);
    }

    public List<CustomerEscalationView> listEscalations(
            UUID applicationId,
            UUID tenantId,
            String status,
            boolean isAdmin
    ) {
        var query = new LambdaQueryWrapper<CustomerEscalationEntity>();

        if (!isAdmin && applicationId != null) {
            query.eq(CustomerEscalationEntity::getApplicationId, applicationId);
            if (tenantId != null) {
                query.eq(CustomerEscalationEntity::getTenantId, tenantId);
            }
        } else if (applicationId != null) {
            query.eq(CustomerEscalationEntity::getApplicationId, applicationId);
        }

        if (status != null && !status.isBlank()) {
            query.eq(CustomerEscalationEntity::getStatus, status);
        }

        query.orderByDesc(CustomerEscalationEntity::getCreatedAt);

        return escalationMapper.selectList(query).stream().map(this::toEscalationView).toList();
    }

    @Transactional
    public CustomerEscalationView resolveEscalation(
            UUID escalationId,
            String agentName,
            String resolutionNotes,
            UUID applicationId,
            UUID tenantId,
            boolean isAdmin
    ) {
        CustomerEscalationEntity entity = escalationMapper.selectById(escalationId);
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Customer escalation not found: " + escalationId);
        }
        if (!isAdmin && applicationId != null && !applicationId.equals(entity.getApplicationId())) {
            throw new CranialException(ErrorCode.FORBIDDEN, "Access denied to cross-application customer escalation");
        }

        entity.setStatus(EscalationStatus.RESOLVED.name());
        entity.setAssignedAgent(agentName != null ? agentName : "admin-operator");
        entity.setResolutionNotes(resolutionNotes != null ? resolutionNotes : "Resolved by operator.");
        entity.setResolvedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        escalationMapper.updateById(entity);

        if (entity.getTicketId() != null) {
            CustomerTicketEntity ticket = ticketMapper.selectById(entity.getTicketId());
            if (ticket != null) {
                ticket.setStatus(TicketStatus.RESOLVED.name());
                ticket.setUpdatedAt(Instant.now());
                ticketMapper.updateById(ticket);
            }
        }

        return toEscalationView(entity);
    }

    public CustomerBenchmarkResult runEvaluationBenchmarks() {
        List<CustomerEvalCaseEntity> cases = evalCaseMapper.selectList(
                new LambdaQueryWrapper<CustomerEvalCaseEntity>().orderByAsc(CustomerEvalCaseEntity::getSuiteCode)
        );

        int total = cases.size();
        int passed = 0;
        int failed = 0;
        int sentimentCorrect = 0;
        int sentimentTotal = 0;
        int promiseTested = 0;
        int promisePassed = 0;
        int escalationTested = 0;
        int escalationPassed = 0;
        int slangTested = 0;
        int slangPassed = 0;

        Map<String, Integer> breakdowns = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (CustomerEvalCaseEntity c : cases) {
            breakdowns.put(c.getSuiteCode(), breakdowns.getOrDefault(c.getSuiteCode(), 0) + 1);

            var redaction = privacyGuard.sanitize(c.getInputText());
            var norm = slangNormalizer.normalize(redaction.redactedText(), c.getLocale());
            var sentimentOut = sentimentEngine.analyze(norm.normalizedText());
            var triage = triageEngine.triage(
                    norm.normalizedText(),
                    c.getLocale(),
                    sentimentOut.sentiment(),
                    sentimentOut.score(),
                    sentimentOut.urgency()
            );

            boolean caseSuccess = true;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", c.getId().toString());
            detail.put("suite_code", c.getSuiteCode());
            detail.put("locale", c.getLocale());
            detail.put("input", truncate(c.getInputText(), 50));
            detail.put("normalized_input", truncate(norm.normalizedText(), 60));
            detail.put("slang_normalized", norm.slangDetected());

            // Sentiment check
            if (c.getExpectedSentiment() != null) {
                sentimentTotal++;
                boolean match = sentimentOut.sentiment().name().equalsIgnoreCase(c.getExpectedSentiment());
                if (match) {
                    sentimentCorrect++;
                } else {
                    caseSuccess = false;
                }
                detail.put("sentiment_match", match);
            }

            // Promise guard check
            if (c.getExpectedPromiseViolation() != null) {
                promiseTested++;
                boolean matchesExpected = triage.promiseGuardFlagged() == c.getExpectedPromiseViolation();
                if (matchesExpected) {
                    promisePassed++;
                } else {
                    caseSuccess = false;
                }
                detail.put("promise_safety_match", matchesExpected);
            }

            // Escalation check
            if (c.getExpectedEscalation() != null) {
                escalationTested++;
                boolean match = triage.escalationRecommended() == c.getExpectedEscalation();
                if (match) {
                    escalationPassed++;
                } else {
                    caseSuccess = false;
                }
                detail.put("escalation_match", match);
            }

            // Colloquial / slang normalization accuracy tracking
            boolean isSlangCase = c.getNotes() != null && c.getNotes().contains("[ID-SLANG]");
            if (isSlangCase) {
                slangTested++;
                boolean slangOk = norm.slangDetected() && caseSuccess;
                if (slangOk) {
                    slangPassed++;
                } else {
                    caseSuccess = false;
                }
                detail.put("slang_case_passed", slangOk);
            }

            detail.put("passed", caseSuccess);
            details.add(detail);

            if (caseSuccess) passed++;
            else failed++;
        }

        double sentimentAcc = sentimentTotal > 0 ? (sentimentCorrect * 100.0 / sentimentTotal) : 100.0;
        double promiseRate = promiseTested > 0 ? (promisePassed * 100.0 / promiseTested) : 100.0;
        double escalationPrec = escalationTested > 0 ? (escalationPassed * 100.0 / escalationTested) : 100.0;
        double slangAcc = slangTested > 0 ? (slangPassed * 100.0 / slangTested) : 100.0;
        double f1Score = (sentimentAcc + promiseRate + escalationPrec) / 300.0;

        return new CustomerBenchmarkResult(
                total,
                passed,
                failed,
                Math.round(sentimentAcc * 100.0) / 100.0,
                Math.round(f1Score * 10000.0) / 10000.0,
                Math.round(promiseRate * 100.0) / 100.0,
                Math.round(escalationPrec * 100.0) / 100.0,
                slangTested,
                Math.round(slangAcc * 100.0) / 100.0,
                breakdowns,
                details,
                Instant.now()
        );
    }

    @Transactional
    public CustomerEvalCaseEntity createEvalCase(CustomerEvalCaseRequest request) {
        CustomerEvalCaseEntity entity = new CustomerEvalCaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setSuiteCode(request.suiteCode());
        entity.setLocale(request.locale() != null ? request.locale() : "en");
        entity.setInputText(request.inputText());
        entity.setExpectedCategory(request.expectedCategory());
        entity.setExpectedSentiment(request.expectedSentiment());
        entity.setExpectedEscalation(request.expectedEscalation());
        entity.setExpectedPromiseViolation(request.expectedPromiseViolation());
        entity.setNotes(request.notes());
        entity.setCreatedAt(Instant.now());

        evalCaseMapper.insert(entity);
        return entity;
    }

    private CustomerTicketView toTicketView(CustomerTicketEntity e) {
        List<PiiEntityView> piiList = readJson(e.getPiiEntitiesJson(), new TypeReference<>() {});
        List<String> promiseReasons = readJson(e.getPromiseGuardReasons(), new TypeReference<>() {});
        List<CustomerSlangReplacementView> slangList = readJson(e.getSlangReplacementsJson(), new TypeReference<>() {});

        return new CustomerTicketView(
                e.getId(),
                e.getTicketNumber(),
                e.getApplicationId(),
                e.getTenantId(),
                e.getExternalUserRef(),
                e.getCustomerName(),
                e.getSubject(),
                e.getRedactedMessage(),
                e.getNormalizedMessage() != null ? e.getNormalizedMessage() : e.getRedactedMessage(),
                Boolean.TRUE.equals(e.getSlangNormalized()),
                slangList != null ? slangList : List.of(),
                Boolean.TRUE.equals(e.getPiiDetected()),
                piiList != null ? piiList : List.of(),
                e.getLocale(),
                e.getCategory() != null ? TicketCategory.valueOf(e.getCategory()) : TicketCategory.GENERAL_INQUIRY,
                e.getSentiment() != null ? CustomerSentiment.valueOf(e.getSentiment()) : CustomerSentiment.NEUTRAL,
                e.getSentimentScore() != null ? e.getSentimentScore() : 0.0,
                e.getUrgency() != null ? CustomerUrgency.valueOf(e.getUrgency()) : CustomerUrgency.NORMAL,
                e.getPriority() != null ? CustomerPriority.valueOf(e.getPriority()) : CustomerPriority.NORMAL,
                e.getRoutingQueue() != null ? RoutingQueue.valueOf(e.getRoutingQueue()) : RoutingQueue.TIER1_SUPPORT,
                e.getStatus() != null ? TicketStatus.valueOf(e.getStatus()) : TicketStatus.TRIAGED,
                Boolean.TRUE.equals(e.getPromiseGuardFlagged()),
                promiseReasons != null ? promiseReasons : List.of(),
                e.getSuggestedCannedResponse(),
                e.getConfidenceScore() != null ? e.getConfidenceScore() : 0.85,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private CustomerEscalationView toEscalationView(CustomerEscalationEntity e) {
        List<String> actions = readJson(e.getRecommendedActionsJson(), new TypeReference<>() {});
        CustomerTicketEntity linkedTicket = e.getTicketId() != null ? ticketMapper.selectById(e.getTicketId()) : null;

        return new CustomerEscalationView(
                e.getId(),
                e.getTicketId(),
                linkedTicket != null ? linkedTicket.getTicketNumber() : "TKT-UNKNOWN",
                linkedTicket != null ? linkedTicket.getCustomerName() : "Anonymous",
                linkedTicket != null ? linkedTicket.getSubject() : "Customer Support Inquiry",
                e.getApplicationId(),
                e.getTenantId(),
                e.getEscalationReason() != null ? EscalationReason.valueOf(e.getEscalationReason()) : EscalationReason.COMPLEX_UNRESOLVED,
                e.getExecutiveSummary(),
                e.getCustomerSentimentSummary(),
                e.getAttemptsMade(),
                actions != null ? actions : List.of(),
                e.getStatus() != null ? EscalationStatus.valueOf(e.getStatus()) : EscalationStatus.PENDING_HANDOFF,
                e.getAssignedAgent(),
                e.getResolutionNotes(),
                e.getResolvedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private <T> T readJson(String json, TypeReference<T> ref) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
