-- ============================================================================
-- CRANIAL Phase 8: Decision Intelligence and Optimization (Section 20.7)
-- Reusable recommendation response schema with evidence rationale,
-- confidence scoring and abstention rules, human approval for consequential recommendations,
-- feedback and outcome tracking, deterministic constraint validation,
-- trade-offs explanation, scenario comparison, stale data safeguards,
-- and non-mutating simulation mode for money management and operations.
-- ============================================================================

-- 1. Decision Optimization Plans & Scenarios
CREATE TABLE IF NOT EXISTS ai_decision_plans (
    id                          UUID PRIMARY KEY,
    plan_code                   VARCHAR(64) NOT NULL UNIQUE,
    application_id              UUID REFERENCES applications(id) ON DELETE SET NULL,
    tenant_id                   UUID REFERENCES tenants(id) ON DELETE SET NULL,
    name                        VARCHAR(128) NOT NULL,
    domain_archetype            VARCHAR(32) NOT NULL CHECK (domain_archetype IN (
                                    'DEBT_PAYOFF_STRATEGY',
                                    'BUDGET_REBALANCING',
                                    'SAVINGS_GOAL_ALLOCATION',
                                    'DISPATCH_ROUTE_SCHEDULING',
                                    'CUSTOM_OPTIMIZATION'
                                )),
    description                 TEXT,
    parameters_json             TEXT NOT NULL,
    constraints_json            TEXT NOT NULL,
    freshness_ttl_hours         INTEGER NOT NULL DEFAULT 48 CHECK (freshness_ttl_hours > 0),
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_decision_plans_app ON ai_decision_plans(application_id);
CREATE INDEX IF NOT EXISTS idx_decision_plans_tenant ON ai_decision_plans(tenant_id);

-- 2. Consequential Recommendations & Human Approval Flow
CREATE TABLE IF NOT EXISTS ai_decision_recommendations (
    id                          UUID PRIMARY KEY,
    plan_id                     UUID REFERENCES ai_decision_plans(id) ON DELETE SET NULL,
    application_id              UUID REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id                   UUID REFERENCES tenants(id) ON DELETE SET NULL,
    recommendation_code         VARCHAR(64) NOT NULL,
    title                       VARCHAR(255) NOT NULL,
    summary                     TEXT NOT NULL,
    confidence_score            NUMERIC(5, 4) NOT NULL,
    abstained                   BOOLEAN NOT NULL DEFAULT FALSE,
    abstention_reason           TEXT,
    simulation_mode             BOOLEAN NOT NULL DEFAULT TRUE,
    status                      VARCHAR(32) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN (
                                    'PROPOSED',
                                    'APPROVED',
                                    'REJECTED',
                                    'SUPERSEDED',
                                    'EXECUTED_SIMULATION'
                                )),
    action_type                 VARCHAR(64) NOT NULL,
    evidence_rationale_json     TEXT,
    trade_offs_json             TEXT,
    scenario_diff_json          TEXT,
    constraints_validated_json  TEXT,
    reviewed_by                 VARCHAR(128),
    reviewed_at                 TIMESTAMP WITH TIME ZONE,
    review_notes                TEXT,
    source_freshness_at         TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_decision_rec_plan ON ai_decision_recommendations(plan_id);
CREATE INDEX IF NOT EXISTS idx_decision_rec_app ON ai_decision_recommendations(application_id);
CREATE INDEX IF NOT EXISTS idx_decision_rec_status ON ai_decision_recommendations(status);

-- 3. Recommendation Feedback & Outcome Tracking
CREATE TABLE IF NOT EXISTS ai_decision_feedbacks (
    id                          UUID PRIMARY KEY,
    recommendation_id           UUID NOT NULL REFERENCES ai_decision_recommendations(id) ON DELETE CASCADE,
    application_id              UUID REFERENCES applications(id) ON DELETE CASCADE,
    rating                      VARCHAR(32) NOT NULL CHECK (rating IN ('HELPFUL', 'UNHELPFUL', 'OVERRIDDEN', 'REJECTED')),
    applied                     BOOLEAN NOT NULL DEFAULT FALSE,
    measured_outcome_json       TEXT,
    comments                    TEXT,
    external_user_ref           VARCHAR(128),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_decision_feedback_rec ON ai_decision_feedbacks(recommendation_id);

-- 4. Seed Primary Route & Feature Flags for Decision Intelligence
INSERT INTO ai_model_capabilities (model_id, capability_code) VALUES
    ('00000000-0000-0000-0006-000000000001', 'decision_intelligence'),
    ('00000000-0000-0000-0006-000000000002', 'decision_intelligence');

INSERT INTO ai_routing_policies (
    id, name, capability_code, task_code, preferred_model_id, fallback_model_id, allowed_data_sensitivity, is_active
) VALUES (
    '00000000-0000-0000-0007-000000000008',
    'Default Decision Intelligence Route',
    'decision_intelligence',
    'decision_recommendation',
    '00000000-0000-0000-0006-000000000001',
    '00000000-0000-0000-0006-000000000002',
    'RESTRICTED',
    TRUE
);

INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES (
    '00000000-0000-0000-0004-000000000009',
    'decision.human_approval.required',
    'Consequential Decision Human Approval',
    'Requires operator or user approval before consequential money management actions take effect',
    TRUE,
    100,
    '[]'
);

INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES (
    '00000000-0000-0000-0004-00000000000a',
    'decision.simulation_mode.default',
    'Decision Simulation Mode Default',
    'Enforces non-mutating simulation dry-run by default for all optimization strategies',
    TRUE,
    100,
    '[]'
);

-- 5. Seed Reference Money Management Optimization Plans & Recommendations
-- Plan A: Peggy Debt Payoff Optimizer (Avalanche vs Snowball vs 50-30-20)
INSERT INTO ai_decision_plans (
    id,
    plan_code,
    application_id,
    tenant_id,
    name,
    domain_archetype,
    description,
    parameters_json,
    constraints_json,
    freshness_ttl_hours,
    is_active,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-000a-000000000001',
    'peggy_debt_snowball_vs_avalanche',
    NULL,
    NULL,
    'Peggy Household Debt Payoff Strategy Optimizer',
    'DEBT_PAYOFF_STRATEGY',
    'Evaluates mathematical Avalanche (highest interest first) vs psychological Snowball (smallest balance first) for household debt elimination with emergency fund floor constraints.',
    '{"monthly_extra_allocation": 1500000.0, "debts": [{"name": "Credit Card A", "balance": 4500000.0, "interest_rate_apr": 0.24, "min_payment": 350000.0}, {"name": "Electronics Installment", "balance": 1800000.0, "interest_rate_apr": 0.12, "min_payment": 200000.0}, {"name": "Vehicle Loan", "balance": 18000000.0, "interest_rate_apr": 0.08, "min_payment": 950000.0}]}',
    '[{"name": "emergency_fund_floor", "type": "HARD", "min_amount": 10000000.0, "description": "Emergency cash must never drop below Rp 10.000.000"}, {"name": "max_debt_repayment_ratio", "type": "SOFT", "max_percent": 0.35, "description": "Total debt repayment must not exceed 35% of monthly income"}]',
    48,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Plan B: Peggy Emergency Fund & Sinking Fund Rebalance
INSERT INTO ai_decision_plans (
    id,
    plan_code,
    application_id,
    tenant_id,
    name,
    domain_archetype,
    description,
    parameters_json,
    constraints_json,
    freshness_ttl_hours,
    is_active,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-000a-000000000002',
    'peggy_savings_rebalance_optimizer',
    NULL,
    NULL,
    'Peggy Emergency Fund & Sinking Fund Allocation',
    'SAVINGS_GOAL_ALLOCATION',
    'Optimizes reallocation of excess discretionary lifestyle funds into 3-month essential emergency reserves and sinking funds.',
    '{"monthly_savings_budget": 4000000.0, "targets": [{"goal": "Emergency Fund", "target": 30000000.0, "current": 18500000.0, "priority": 1}, {"goal": "Annual Vehicle Tax & Insurance", "target": 4500000.0, "current": 2000000.0, "priority": 2}, {"goal": "Holiday Trip", "target": 6000000.0, "current": 1500000.0, "priority": 3}]}',
    '[{"name": "emergency_priority_weight", "type": "HARD", "min_allocation_percent": 0.60, "description": "At least 60% of monthly savings must fund Emergency Reserve until target reached"}]',
    72,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Seed Reference Proposed Recommendation with Trade-offs
INSERT INTO ai_decision_recommendations (
    id,
    plan_id,
    application_id,
    tenant_id,
    recommendation_code,
    title,
    summary,
    confidence_score,
    abstained,
    abstention_reason,
    simulation_mode,
    status,
    action_type,
    evidence_rationale_json,
    trade_offs_json,
    scenario_diff_json,
    constraints_validated_json,
    reviewed_by,
    reviewed_at,
    review_notes,
    source_freshness_at,
    created_at
) VALUES (
    '00000000-0000-0000-000b-000000000001',
    '00000000-0000-0000-000a-000000000001',
    NULL,
    NULL,
    'REC-DEBT-AVALANCHE-2026',
    'Prioritize Credit Card A Payoff via Debt Avalanche',
    'Allocate the Rp 1.500.000 monthly surplus strictly to Credit Card A (24% APR) while paying minimums on others. Saves Rp 2.840.000 in total interest charges compared to Snowball method.',
    0.9400,
    FALSE,
    NULL,
    TRUE,
    'PROPOSED',
    'REALLOCATE_EXTRA_PAYMENT',
    '{"primary_driver": "Interest APR disparity (24% vs 12% vs 8%)", "months_to_debt_free": 14, "baseline_months": 26, "total_interest_saved": 2840000.0}',
    '[{"strategy": "Debt Avalanche (Recommended)", "interest_paid": 1650000.0, "months": 14, "pros": ["Minimizes lifetime interest paid", "Faster total debt-free date"], "cons": ["Credit Card A balance takes 4 months to close (slower initial milestone)"]}, {"strategy": "Debt Snowball", "interest_paid": 4490000.0, "months": 16, "pros": ["Electronics installment closed in month 1 (immediate psychological win)"], "cons": ["Costs Rp 2.840.000 more in credit card finance charges"]}]',
    '{"baseline": {"total_months": 26, "total_interest": 6200000.0}, "recommended": {"total_months": 14, "total_interest": 1650000.0}, "delta_interest": -4550000.0, "delta_months": -12}',
    '[{"constraint": "emergency_fund_floor", "status": "SATISFIED", "current_value": 12500000.0, "threshold": 10000000.0}, {"constraint": "max_debt_repayment_ratio", "status": "SATISFIED", "current_value": 0.28, "threshold": 0.35}]',
    NULL,
    NULL,
    NULL,
    TIMESTAMP '2026-10-31 00:00:00+00',
    CURRENT_TIMESTAMP
);

