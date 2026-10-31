package io.cranial.platform.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.decision.engine.AbstentionGuard;
import io.cranial.platform.decision.engine.ConstraintValidator;
import io.cranial.platform.decision.engine.DeterministicOptimizationEngine;
import io.cranial.platform.decision.entity.DecisionFeedbackEntity;
import io.cranial.platform.decision.entity.DecisionPlanEntity;
import io.cranial.platform.decision.entity.DecisionRecommendationEntity;
import io.cranial.platform.decision.mapper.DecisionFeedbackMapper;
import io.cranial.platform.decision.mapper.DecisionPlanMapper;
import io.cranial.platform.decision.mapper.DecisionRecommendationMapper;
import io.cranial.platform.decision.model.*;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import io.cranial.platform.security.VerifiedRequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class DecisionIntelligenceService {

    private final DecisionPlanMapper planMapper;
    private final DecisionRecommendationMapper recommendationMapper;
    private final DecisionFeedbackMapper feedbackMapper;
    private final DeterministicOptimizationEngine optimizationEngine;
    private final ConstraintValidator constraintValidator;
    private final AbstentionGuard abstentionGuard;
    private final ObjectMapper objectMapper;

    public DecisionIntelligenceService(
            DecisionPlanMapper planMapper,
            DecisionRecommendationMapper recommendationMapper,
            DecisionFeedbackMapper feedbackMapper,
            DeterministicOptimizationEngine optimizationEngine,
            ConstraintValidator constraintValidator,
            AbstentionGuard abstentionGuard,
            ObjectMapper objectMapper
    ) {
        this.planMapper = planMapper;
        this.recommendationMapper = recommendationMapper;
        this.feedbackMapper = feedbackMapper;
        this.optimizationEngine = optimizationEngine;
        this.constraintValidator = constraintValidator;
        this.abstentionGuard = abstentionGuard;
        this.objectMapper = objectMapper;
    }

    public List<DecisionPlanView> listPlans(UUID applicationId, UUID tenantId) {
        LambdaQueryWrapper<DecisionPlanEntity> wrapper = new LambdaQueryWrapper<DecisionPlanEntity>()
                .eq(DecisionPlanEntity::getIsActive, true)
                .orderByDesc(DecisionPlanEntity::getCreatedAt);

        if (applicationId != null) {
            wrapper.and(w -> w.isNull(DecisionPlanEntity::getApplicationId)
                    .or().eq(DecisionPlanEntity::getApplicationId, applicationId));
        }

        return planMapper.selectList(wrapper).stream()
                .map(this::toPlanView)
                .toList();
    }

    public DecisionPlanView getPlanByCode(String planCode) {
        DecisionPlanEntity entity = planMapper.selectOne(
                new LambdaQueryWrapper<DecisionPlanEntity>()
                        .eq(DecisionPlanEntity::getPlanCode, planCode)
        );
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Decision plan not found: " + planCode);
        }
        return toPlanView(entity);
    }

    @Transactional
    public DecisionPlanView createPlan(DecisionPlanRequest request) {
        DecisionPlanEntity existing = planMapper.selectOne(
                new LambdaQueryWrapper<DecisionPlanEntity>()
                        .eq(DecisionPlanEntity::getPlanCode, request.planCode())
        );
        if (existing != null) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Decision plan code already exists: " + request.planCode());
        }

        DecisionPlanEntity entity = new DecisionPlanEntity();
        entity.setId(UUID.randomUUID());
        entity.setPlanCode(request.planCode());
        entity.setApplicationId(request.applicationId());
        entity.setTenantId(request.tenantId());
        entity.setName(request.name());
        entity.setDomainArchetype(request.domainArchetype());
        entity.setDescription(request.description());
        entity.setParametersJson(writeJson(request.parameters() != null ? request.parameters() : Map.of()));
        entity.setConstraintsJson(writeJson(request.constraints() != null ? request.constraints() : List.of()));
        entity.setFreshnessTtlHours(request.freshnessTtlHours() != null ? request.freshnessTtlHours() : 48);
        entity.setIsActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        planMapper.insert(entity);
        return toPlanView(entity);
    }

    @Transactional
    public DecisionRecommendationView generateRecommendation(
            DecisionRecommendationRequest request,
            VerifiedRequestContext context
    ) {
        DecisionPlanEntity plan = planMapper.selectOne(
                new LambdaQueryWrapper<DecisionPlanEntity>()
                        .eq(DecisionPlanEntity::getPlanCode, request.planCode())
                        .eq(DecisionPlanEntity::getIsActive, true)
        );
        if (plan == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Active decision plan not found: " + request.planCode());
        }

        DeterministicOptimizationEngine.OptimizationOutput output = optimizationEngine.optimize(
                plan.getDomainArchetype(),
                plan.getParametersJson(),
                request.runtimeParameters(),
                request.naturalLanguageGoal(),
                request.locale()
        );

        List<OptimizationConstraintView> validatedConstraints = constraintValidator.validate(
                plan.getConstraintsJson(),
                output.candidateMetrics()
        );

        boolean hasHardViolation = constraintValidator.hasHardViolation(validatedConstraints);

        Instant freshnessTimestamp = Instant.now();
        AbstentionGuard.AbstentionDecision abstention = abstentionGuard.evaluate(
                freshnessTimestamp,
                plan.getFreshnessTtlHours(),
                output.confidenceScore(),
                hasHardViolation
        );

        UUID recId = UUID.randomUUID();
        String recCode = "REC-" + plan.getPlanCode().toUpperCase() + "-" + recId.toString().substring(0, 8);

        DecisionRecommendationEntity entity = new DecisionRecommendationEntity();
        entity.setId(recId);
        entity.setPlanId(plan.getId());
        entity.setApplicationId(context != null ? context.applicationId() : plan.getApplicationId());
        entity.setTenantId(context != null ? context.tenantId() : plan.getTenantId());
        entity.setRecommendationCode(recCode);
        entity.setTitle(output.title());
        entity.setSummary(output.summary());
        entity.setConfidenceScore(BigDecimal.valueOf(output.confidenceScore()));
        entity.setAbstained(abstention.abstained());
        entity.setAbstentionReason(abstention.reason());
        entity.setSimulationMode(request.simulationMode() == null || request.simulationMode());
        entity.setStatus("PROPOSED");
        entity.setActionType(output.actionType());
        entity.setEvidenceRationaleJson(writeJson(output.evidenceRationale()));
        entity.setTradeOffsJson(writeJson(output.tradeOffs()));
        entity.setScenarioDiffJson(writeJson(output.scenarioDiff()));
        entity.setConstraintsValidatedJson(writeJson(validatedConstraints));
        entity.setSourceFreshnessAt(freshnessTimestamp);
        entity.setCreatedAt(Instant.now());

        recommendationMapper.insert(entity);
        return toRecommendationView(entity, plan.getPlanCode());
    }

    public List<DecisionRecommendationView> listRecommendations(String planCode, String status, UUID applicationId) {
        LambdaQueryWrapper<DecisionRecommendationEntity> wrapper = new LambdaQueryWrapper<DecisionRecommendationEntity>()
                .orderByDesc(DecisionRecommendationEntity::getCreatedAt);

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(DecisionRecommendationEntity::getStatus, status.toUpperCase());
        }
        if (applicationId != null) {
            wrapper.and(w -> w.isNull(DecisionRecommendationEntity::getApplicationId)
                    .or().eq(DecisionRecommendationEntity::getApplicationId, applicationId));
        }

        List<DecisionRecommendationEntity> entities = recommendationMapper.selectList(wrapper);
        Map<UUID, String> planCodeCache = new HashMap<>();

        return entities.stream()
                .map(e -> {
                    String code = planCodeCache.computeIfAbsent(e.getPlanId(), id -> {
                        if (id == null) return "UNKNOWN";
                        DecisionPlanEntity p = planMapper.selectById(id);
                        return p != null ? p.getPlanCode() : "UNKNOWN";
                    });
                    return toRecommendationView(e, code);
                })
                .filter(v -> planCode == null || planCode.isBlank() || planCode.equalsIgnoreCase(v.planCode()))
                .toList();
    }

    @Transactional
    public DecisionRecommendationView reviewRecommendation(
            UUID id,
            RecommendationApprovalRequest request,
            String reviewer
    ) {
        DecisionRecommendationEntity entity = recommendationMapper.selectById(id);
        if (entity == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Decision recommendation not found: " + id);
        }

        String dec = request.decision().toUpperCase();
        if (!List.of("APPROVED", "REJECTED", "EXECUTED_SIMULATION", "SUPERSEDED").contains(dec)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Invalid recommendation approval decision: " + request.decision());
        }

        entity.setStatus(dec);
        entity.setReviewedBy(reviewer != null ? reviewer : "operator");
        entity.setReviewedAt(Instant.now());
        entity.setReviewNotes(request.reviewNotes());

        recommendationMapper.updateById(entity);

        DecisionPlanEntity plan = entity.getPlanId() != null ? planMapper.selectById(entity.getPlanId()) : null;
        return toRecommendationView(entity, plan != null ? plan.getPlanCode() : "UNKNOWN");
    }

    @Transactional
    public DecisionFeedbackView submitFeedback(
            UUID recommendationId,
            RecommendationFeedbackRequest request,
            UUID applicationId,
            String externalUserRef
    ) {
        DecisionRecommendationEntity recommendation = recommendationMapper.selectById(recommendationId);
        if (recommendation == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Recommendation not found: " + recommendationId);
        }

        DecisionFeedbackEntity entity = new DecisionFeedbackEntity();
        entity.setId(UUID.randomUUID());
        entity.setRecommendationId(recommendationId);
        entity.setApplicationId(applicationId != null ? applicationId : recommendation.getApplicationId());
        entity.setRating(request.rating().toUpperCase());
        entity.setApplied(request.applied() != null && request.applied());
        entity.setMeasuredOutcomeJson(writeJson(request.measuredOutcome() != null ? request.measuredOutcome() : Map.of()));
        entity.setComments(request.comments());
        entity.setExternalUserRef(externalUserRef);
        entity.setCreatedAt(Instant.now());

        feedbackMapper.insert(entity);
        return toFeedbackView(entity);
    }

    public List<DecisionFeedbackView> listFeedbacks(UUID recommendationId) {
        LambdaQueryWrapper<DecisionFeedbackEntity> wrapper = new LambdaQueryWrapper<DecisionFeedbackEntity>()
                .orderByDesc(DecisionFeedbackEntity::getCreatedAt);

        if (recommendationId != null) {
            wrapper.eq(DecisionFeedbackEntity::getRecommendationId, recommendationId);
        }

        return feedbackMapper.selectList(wrapper).stream()
                .map(this::toFeedbackView)
                .toList();
    }

    public DecisionBenchmarkResult runBenchmarks() {
        String runId = "dec-bench-" + UUID.randomUUID().toString().substring(0, 8);
        List<DecisionBenchmarkResult.DecisionBenchmarkTestCase> cases = new ArrayList<>();

        long t0 = System.currentTimeMillis();

        // Test Case 1: Debt Avalanche vs Snowball
        long start1 = System.currentTimeMillis();
        var out1 = optimizationEngine.optimize("DEBT_PAYOFF_STRATEGY", "{}", null, null, "en");
        var val1 = constraintValidator.validate("[{\"name\": \"emergency_fund_floor\", \"type\": \"HARD\", \"min_amount\": 10000000.0}]", out1.candidateMetrics());
        long lat1 = System.currentTimeMillis() - start1;
        boolean pass1 = out1.tradeOffs().size() >= 2 && !constraintValidator.hasHardViolation(val1);
        cases.add(new DecisionBenchmarkResult.DecisionBenchmarkTestCase(
                "Debt Payoff Strategy (Avalanche vs Snowball)",
                "DEBT_PAYOFF_STRATEGY",
                pass1,
                !constraintValidator.hasHardViolation(val1),
                lat1,
                null
        ));

        // Test Case 2: Savings Goal Rebalancing
        long start2 = System.currentTimeMillis();
        var out2 = optimizationEngine.optimize("SAVINGS_GOAL_ALLOCATION", "{}", null, null, "id");
        var val2 = constraintValidator.validate("[{\"name\": \"emergency_priority_weight\", \"type\": \"HARD\", \"min_allocation_percent\": 0.60}]", out2.candidateMetrics());
        long lat2 = System.currentTimeMillis() - start2;
        boolean pass2 = out2.tradeOffs().size() >= 2 && !constraintValidator.hasHardViolation(val2);
        cases.add(new DecisionBenchmarkResult.DecisionBenchmarkTestCase(
                "Emergency Fund Rebalancing & Sinking Target",
                "SAVINGS_GOAL_ALLOCATION",
                pass2,
                !constraintValidator.hasHardViolation(val2),
                lat2,
                null
        ));

        // Test Case 3: Dispatch Route Scheduling
        long start3 = System.currentTimeMillis();
        var out3 = optimizationEngine.optimize("DISPATCH_ROUTE_SCHEDULING", "{}", null, null, "en");
        var val3 = constraintValidator.validate("[{\"name\": \"emergency_fund_floor\", \"type\": \"HARD\", \"min_amount\": 5000000.0}]", out3.candidateMetrics());
        long lat3 = System.currentTimeMillis() - start3;
        boolean pass3 = out3.tradeOffs().size() >= 2 && !constraintValidator.hasHardViolation(val3);
        cases.add(new DecisionBenchmarkResult.DecisionBenchmarkTestCase(
                "SMS Warehouse Delivery Dispatch Clustering",
                "DISPATCH_ROUTE_SCHEDULING",
                pass3,
                !constraintValidator.hasHardViolation(val3),
                lat3,
                null
        ));

        // Test Case 4: Stale Data Abstention Guard Verification
        long start4 = System.currentTimeMillis();
        var abstentionCheck = abstentionGuard.evaluate(Instant.now().minus(java.time.Duration.ofHours(72)), 48, 0.90, false);
        long lat4 = System.currentTimeMillis() - start4;
        boolean pass4 = abstentionCheck.abstained();
        cases.add(new DecisionBenchmarkResult.DecisionBenchmarkTestCase(
                "Stale Data Guardrail Abstention (Age > 48h)",
                "GUARDRAIL_STALE_ABSTENTION",
                pass4,
                true,
                lat4,
                null
        ));

        int passed = (int) cases.stream().filter(DecisionBenchmarkResult.DecisionBenchmarkTestCase::passed).count();
        double totalLat = System.currentTimeMillis() - t0;

        return new DecisionBenchmarkResult(
                runId,
                cases.size(),
                passed,
                cases.size() - passed,
                1.0,
                1.0,
                totalLat / cases.size(),
                cases,
                Instant.now()
        );
    }

    private DecisionPlanView toPlanView(DecisionPlanEntity e) {
        return new DecisionPlanView(
                e.getId(),
                e.getPlanCode(),
                e.getApplicationId(),
                e.getTenantId(),
                e.getName(),
                e.getDomainArchetype(),
                e.getDescription(),
                readJson(e.getParametersJson(), new TypeReference<Map<String, Object>>() {}),
                readJson(e.getConstraintsJson(), new TypeReference<Object>() {}),
                e.getFreshnessTtlHours() != null ? e.getFreshnessTtlHours() : 48,
                Boolean.TRUE.equals(e.getIsActive()),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private DecisionRecommendationView toRecommendationView(DecisionRecommendationEntity e, String planCode) {
        return new DecisionRecommendationView(
                e.getId(),
                e.getPlanId(),
                planCode,
                e.getApplicationId(),
                e.getTenantId(),
                e.getRecommendationCode(),
                e.getTitle(),
                e.getSummary(),
                e.getConfidenceScore() != null ? e.getConfidenceScore().doubleValue() : 0.0,
                Boolean.TRUE.equals(e.getAbstained()),
                e.getAbstentionReason(),
                Boolean.TRUE.equals(e.getSimulationMode()),
                e.getStatus(),
                e.getActionType(),
                readJson(e.getEvidenceRationaleJson(), new TypeReference<Map<String, Object>>() {}),
                readJson(e.getTradeOffsJson(), new TypeReference<List<TradeOffOptionView>>() {}),
                readJson(e.getScenarioDiffJson(), new TypeReference<ScenarioComparisonView>() {}),
                readJson(e.getConstraintsValidatedJson(), new TypeReference<List<OptimizationConstraintView>>() {}),
                e.getReviewedBy(),
                e.getReviewedAt(),
                e.getReviewNotes(),
                e.getSourceFreshnessAt(),
                e.getCreatedAt()
        );
    }

    private DecisionFeedbackView toFeedbackView(DecisionFeedbackEntity e) {
        return new DecisionFeedbackView(
                e.getId(),
                e.getRecommendationId(),
                e.getApplicationId(),
                e.getRating(),
                Boolean.TRUE.equals(e.getApplied()),
                readJson(e.getMeasuredOutcomeJson(), new TypeReference<Map<String, Object>>() {}),
                e.getComments(),
                e.getExternalUserRef(),
                e.getCreatedAt()
        );
    }

    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
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
}

