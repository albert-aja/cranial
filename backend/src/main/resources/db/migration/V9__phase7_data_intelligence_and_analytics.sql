-- ============================================================================
-- CRANIAL Phase 7: Data Intelligence and Analytics (Section 20.6)
-- Authoritative application reporting connectors, deterministic calculation provenance,
-- typed reporting result schemas, budget vs actual trends, statistical anomaly detection (IQR/Z-Score),
-- data sufficiency forecasting guardrails, natural-language query clarification, and safe caching.
-- ============================================================================

-- 1. Analytics Datasets Registry
CREATE TABLE IF NOT EXISTS ai_analytics_datasets (
    id                      UUID PRIMARY KEY,
    dataset_code            VARCHAR(64) NOT NULL UNIQUE,
    application_id          UUID REFERENCES applications(id) ON DELETE SET NULL,
    tenant_id               UUID REFERENCES tenants(id) ON DELETE SET NULL,
    name                    VARCHAR(128) NOT NULL,
    description             TEXT,
    domain_archetype        VARCHAR(32) NOT NULL CHECK (domain_archetype IN (
                                'MONEY_BUDGET_SUMMARY',
                                'CASHFLOW_LEDGER',
                                'CATEGORY_EXPENSES',
                                'INVENTORY_OPS',
                                'KPI_METRICS',
                                'CUSTOM_TABULAR'
                            )),
    schema_definition_json  TEXT NOT NULL,
    connector_type          VARCHAR(32) NOT NULL CHECK (connector_type IN (
                                'INTERNAL_TABLE',
                                'REPORT_TOOL',
                                'SAMPLE_SERIES'
                            )),
    source_uri              VARCHAR(255),
    refresh_policy          VARCHAR(32) NOT NULL DEFAULT 'READ_THROUGH' CHECK (refresh_policy IN (
                                'READ_THROUGH',
                                'DAILY_SNAPSHOT',
                                'HOURLY_PUSH',
                                'MANUAL'
                            )),
    data_points_json        TEXT,
    max_query_window_days   INTEGER NOT NULL DEFAULT 365 CHECK (max_query_window_days > 0),
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    source_freshness_at     TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analytics_datasets_app ON ai_analytics_datasets(application_id);
CREATE INDEX IF NOT EXISTS idx_analytics_datasets_tenant ON ai_analytics_datasets(tenant_id);

-- 2. Statistical Anomalies Queue & False-Positive Tracking
CREATE TABLE IF NOT EXISTS ai_analytics_anomalies (
    id                      UUID PRIMARY KEY,
    dataset_id              UUID NOT NULL REFERENCES ai_analytics_datasets(id) ON DELETE CASCADE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID REFERENCES tenants(id) ON DELETE SET NULL,
    metric_name             VARCHAR(64) NOT NULL,
    period_timestamp        TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_value            NUMERIC(16, 4) NOT NULL,
    expected_baseline       NUMERIC(16, 4) NOT NULL,
    deviation_score         NUMERIC(8, 4) NOT NULL,
    direction               VARCHAR(16) NOT NULL CHECK (direction IN ('SPIKE', 'DROP')),
    severity                VARCHAR(16) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    algorithm               VARCHAR(32) NOT NULL CHECK (algorithm IN ('IQR_OUTLIER', 'Z_SCORE', 'MOVING_WINDOW')),
    status                  VARCHAR(32) NOT NULL DEFAULT 'DETECTED' CHECK (status IN (
                                'DETECTED',
                                'VERIFIED_ANOMALY',
                                'FALSE_POSITIVE',
                                'RESOLVED',
                                'IGNORED'
                            )),
    review_notes            TEXT,
    reviewed_by             VARCHAR(128),
    reviewed_at             TIMESTAMP WITH TIME ZONE,
    details_json            TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analytics_anomalies_dataset ON ai_analytics_anomalies(dataset_id);
CREATE INDEX IF NOT EXISTS idx_analytics_anomalies_app ON ai_analytics_anomalies(application_id);
CREATE INDEX IF NOT EXISTS idx_analytics_anomalies_status ON ai_analytics_anomalies(status);

-- 3. Query Result Cache with TTL & Invalidation
CREATE TABLE IF NOT EXISTS ai_analytics_query_cache (
    id                      UUID PRIMARY KEY,
    query_hash              VARCHAR(64) NOT NULL UNIQUE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    dataset_code            VARCHAR(64) NOT NULL,
    query_payload_json      TEXT NOT NULL,
    result_payload_json     TEXT NOT NULL,
    hit_count               INTEGER NOT NULL DEFAULT 1,
    expires_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analytics_query_cache_app ON ai_analytics_query_cache(application_id);
CREATE INDEX IF NOT EXISTS idx_analytics_query_cache_dataset ON ai_analytics_query_cache(dataset_code);
CREATE INDEX IF NOT EXISTS idx_analytics_query_cache_expires ON ai_analytics_query_cache(expires_at);

-- 4. Seed Primary Route & Feature Flags for Data Intelligence
INSERT INTO ai_model_capabilities (model_id, capability_code) VALUES
    ('00000000-0000-0000-0006-000000000002', 'data_intelligence');

INSERT INTO ai_routing_policies (
    id, name, capability_code, task_code, preferred_model_id, fallback_model_id, allowed_data_sensitivity, is_active
) VALUES (
    '00000000-0000-0000-0007-000000000007',
    'Default Data Intelligence Route',
    'data_intelligence',
    'analytics_reporting',
    '00000000-0000-0000-0006-000000000001',
    '00000000-0000-0000-0006-000000000002',
    'RESTRICTED',
    TRUE
);

INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES (
    '00000000-0000-0000-0004-000000000007',
    'analytics.statistical_anomalies.enabled',
    'Statistical Anomaly Detection',
    'Enables deterministic IQR and Z-Score anomaly detection in reporting queries',
    TRUE,
    100,
    '[]'
);

INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES (
    '00000000-0000-0000-0004-000000000008',
    'analytics.query_cache.enabled',
    'Analytics Query Cache',
    'Enables deterministic analytical result caching with SHA-256 query hashing',
    TRUE,
    100,
    '[]'
);

-- 5. Seed Reference Money Management Datasets
-- Dataset A: Peggy 50-30-20 Monthly Budget vs Actual Summary 2026
INSERT INTO ai_analytics_datasets (
    id,
    dataset_code,
    application_id,
    tenant_id,
    name,
    description,
    domain_archetype,
    schema_definition_json,
    connector_type,
    source_uri,
    refresh_policy,
    data_points_json,
    max_query_window_days,
    is_active,
    source_freshness_at,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0008-000000000001',
    'peggy_budget_monthly_2026',
    NULL,
    NULL,
    'Peggy Household Budget vs Actuals (2026)',
    'Authoritative monthly money management metrics tracking 50% Essentials (Needs), 30% Lifestyle (Wants), and 20% Savings (Emergency/Sinking Funds).',
    'MONEY_BUDGET_SUMMARY',
    '{"columns":[{"name":"period","type":"DATE"},{"name":"category","type":"STRING"},{"name":"budget_limit","type":"NUMERIC"},{"name":"actual_spent","type":"NUMERIC"},{"name":"variance_amount","type":"NUMERIC"},{"name":"variance_percent","type":"NUMERIC"}],"metrics":["budget_limit","actual_spent","variance_amount"],"dimensions":["period","category"]}',
    'SAMPLE_SERIES',
    'cranial://datasets/peggy/budget-2026',
    'DAILY_SNAPSHOT',
    '[
      {"period":"2026-07","category":"Needs (50%)","budget_limit":10000000.0,"actual_spent":9850000.0,"variance_amount":-150000.0,"variance_percent":-1.5},
      {"period":"2026-07","category":"Wants (30%)","budget_limit":6000000.0,"actual_spent":5800000.0,"variance_amount":-200000.0,"variance_percent":-3.3},
      {"period":"2026-07","category":"Savings (20%)","budget_limit":4000000.0,"actual_spent":4000000.0,"variance_amount":0.0,"variance_percent":0.0},
      {"period":"2026-08","category":"Needs (50%)","budget_limit":10000000.0,"actual_spent":10200000.0,"variance_amount":200000.0,"variance_percent":2.0},
      {"period":"2026-08","category":"Wants (30%)","budget_limit":6000000.0,"actual_spent":6400000.0,"variance_amount":400000.0,"variance_percent":6.7},
      {"period":"2026-08","category":"Savings (20%)","budget_limit":4000000.0,"actual_spent":3800000.0,"variance_amount":-200000.0,"variance_percent":-5.0},
      {"period":"2026-09","category":"Needs (50%)","budget_limit":10000000.0,"actual_spent":9900000.0,"variance_amount":-100000.0,"variance_percent":-1.0},
      {"period":"2026-09","category":"Wants (30%)","budget_limit":6000000.0,"actual_spent":5700000.0,"variance_amount":-300000.0,"variance_percent":-5.0},
      {"period":"2026-09","category":"Savings (20%)","budget_limit":4000000.0,"actual_spent":4100000.0,"variance_amount":100000.0,"variance_percent":2.5},
      {"period":"2026-10","category":"Needs (50%)","budget_limit":10000000.0,"actual_spent":10100000.0,"variance_amount":100000.0,"variance_percent":1.0},
      {"period":"2026-10","category":"Wants (30%)","budget_limit":6000000.0,"actual_spent":6150000.0,"variance_amount":150000.0,"variance_percent":2.5},
      {"period":"2026-10","category":"Savings (20%)","budget_limit":4000000.0,"actual_spent":3950000.0,"variance_amount":-50000.0,"variance_percent":-1.25}
    ]',
    365,
    TRUE,
    TIMESTAMP '2026-10-31 00:00:00+00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Dataset B: Peggy Daily Cash Flow Ledger with Known Spending Spike
INSERT INTO ai_analytics_datasets (
    id,
    dataset_code,
    application_id,
    tenant_id,
    name,
    description,
    domain_archetype,
    schema_definition_json,
    connector_type,
    source_uri,
    refresh_policy,
    data_points_json,
    max_query_window_days,
    is_active,
    source_freshness_at,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0008-000000000002',
    'peggy_cashflow_daily_q4',
    NULL,
    NULL,
    'Peggy Daily Cash Flow & Expense Ledger (Q4 2026)',
    'Daily tracking of household inflows and outlays for October 2026, including an intentional anomaly outlier spike on 2026-10-15 (Dining Out celebration).',
    'CASHFLOW_LEDGER',
    '{"columns":[{"name":"date","type":"DATE"},{"name":"inflow","type":"NUMERIC"},{"name":"outflow","type":"NUMERIC"},{"name":"net_flow","type":"NUMERIC"},{"name":"primary_category","type":"STRING"}],"metrics":["inflow","outflow","net_flow"],"dimensions":["date","primary_category"]}',
    'SAMPLE_SERIES',
    'cranial://datasets/peggy/cashflow-q4',
    'DAILY_SNAPSHOT',
    '[
      {"date":"2026-10-01","inflow":25000000.0,"outflow":1500000.0,"net_flow":23500000.0,"primary_category":"Salary & Groceries"},
      {"date":"2026-10-02","inflow":0.0,"outflow":250000.0,"net_flow":-250000.0,"primary_category":"Fuel"},
      {"date":"2026-10-03","inflow":0.0,"outflow":350000.0,"net_flow":-350000.0,"primary_category":"Groceries"},
      {"date":"2026-10-04","inflow":0.0,"outflow":180000.0,"net_flow":-180000.0,"primary_category":"Coffee"},
      {"date":"2026-10-05","inflow":0.0,"outflow":400000.0,"net_flow":-400000.0,"primary_category":"Utilities"},
      {"date":"2026-10-06","inflow":0.0,"outflow":220000.0,"net_flow":-220000.0,"primary_category":"Lunch"},
      {"date":"2026-10-07","inflow":0.0,"outflow":310000.0,"net_flow":-310000.0,"primary_category":"Groceries"},
      {"date":"2026-10-08","inflow":0.0,"outflow":190000.0,"net_flow":-190000.0,"primary_category":"Fuel"},
      {"date":"2026-10-09","inflow":0.0,"outflow":280000.0,"net_flow":-280000.0,"primary_category":"Lunch"},
      {"date":"2026-10-10","inflow":0.0,"outflow":550000.0,"net_flow":-550000.0,"primary_category":"Weekend Outing"},
      {"date":"2026-10-11","inflow":0.0,"outflow":210000.0,"net_flow":-210000.0,"primary_category":"Groceries"},
      {"date":"2026-10-12","inflow":0.0,"outflow":270000.0,"net_flow":-270000.0,"primary_category":"Internet Bill"},
      {"date":"2026-10-13","inflow":0.0,"outflow":230000.0,"net_flow":-230000.0,"primary_category":"Lunch"},
      {"date":"2026-10-14","inflow":0.0,"outflow":300000.0,"net_flow":-300000.0,"primary_category":"Groceries"},
      {"date":"2026-10-15","inflow":0.0,"outflow":4850000.0,"net_flow":-4850000.0,"primary_category":"Dining Out Luxury"},
      {"date":"2026-10-16","inflow":0.0,"outflow":240000.0,"net_flow":-240000.0,"primary_category":"Lunch"},
      {"date":"2026-10-17","inflow":0.0,"outflow":320000.0,"net_flow":-320000.0,"primary_category":"Groceries"},
      {"date":"2026-10-18","inflow":0.0,"outflow":190000.0,"net_flow":-190000.0,"primary_category":"Coffee"},
      {"date":"2026-10-19","inflow":0.0,"outflow":260000.0,"net_flow":-260000.0,"primary_category":"Fuel"},
      {"date":"2026-10-20","inflow":0.0,"outflow":340000.0,"net_flow":-340000.0,"primary_category":"Groceries"}
    ]',
    180,
    TRUE,
    TIMESTAMP '2026-10-31 00:00:00+00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Seed known detected anomaly for 2026-10-15 spending spike
INSERT INTO ai_analytics_anomalies (
    id,
    dataset_id,
    application_id,
    tenant_id,
    metric_name,
    period_timestamp,
    actual_value,
    expected_baseline,
    deviation_score,
    direction,
    severity,
    algorithm,
    status,
    review_notes,
    reviewed_by,
    details_json,
    created_at
) VALUES (
    '00000000-0000-0000-0009-000000000001',
    '00000000-0000-0000-0008-000000000002',
    NULL,
    NULL,
    'outflow',
    TIMESTAMP '2026-10-15 00:00:00+00',
    4850000.0,
    345000.0,
    4.1200,
    'SPIKE',
    'CRITICAL',
    'IQR_OUTLIER',
    'DETECTED',
    NULL,
    NULL,
    '{"category":"Dining Out Luxury","iqr_multiplier":5.8,"z_score":4.12}',
    CURRENT_TIMESTAMP
);

