-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 3 Migration: AI Orchestrator, Tasks, Prompts, Schemas, Tools & Traces
-- =============================================================================

-- 1. AI Schemas Registry (JSON Schema definitions for inputs, outputs, tools)
CREATE TABLE ai_schemas (
    id                     UUID PRIMARY KEY,
    schema_code            VARCHAR(80)  NOT NULL UNIQUE,
    name                   VARCHAR(150) NOT NULL,
    description            VARCHAR(500),
    schema_type            VARCHAR(32)  NOT NULL CHECK (schema_type IN ('REQUEST', 'RESPONSE_EXTRACTION', 'TOOL_INPUT', 'TOOL_OUTPUT')),
    version                INTEGER      NOT NULL DEFAULT 1 CHECK (version > 0),
    schema_definition_json JSONB        NOT NULL DEFAULT '{}',
    is_strict              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_schemas_type_status ON ai_schemas(schema_type, status);

-- 2. AI Prompts Registry & Versioning
CREATE TABLE ai_prompts (
    id                    UUID PRIMARY KEY,
    prompt_code           VARCHAR(80)  NOT NULL,
    name                  VARCHAR(150) NOT NULL,
    description           VARCHAR(500),
    capability_code       VARCHAR(80)  NOT NULL,
    task_code             VARCHAR(120),
    application_id        UUID         REFERENCES applications(id) ON DELETE CASCADE,
    version               INTEGER      NOT NULL DEFAULT 1 CHECK (version > 0),
    status                VARCHAR(32)  NOT NULL CHECK (status IN ('DRAFT', 'TESTING', 'PUBLISHED', 'RETIRED')),
    system_template       TEXT         NOT NULL,
    user_template         TEXT         NOT NULL,
    input_variables_json  JSONB        NOT NULL DEFAULT '[]',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_prompt_code_version UNIQUE (prompt_code, version)
);

CREATE INDEX idx_prompts_lookup ON ai_prompts(capability_code, status);
CREATE INDEX idx_prompts_app_code ON ai_prompts(application_id, prompt_code);

-- 3. AI Task Definitions
CREATE TABLE ai_task_definitions (
    id                       UUID PRIMARY KEY,
    task_code                VARCHAR(120) NOT NULL UNIQUE,
    name                     VARCHAR(150) NOT NULL,
    description              VARCHAR(500),
    capability_code          VARCHAR(80)  NOT NULL,
    default_prompt_id        UUID         REFERENCES ai_prompts(id),
    output_schema_id         UUID         REFERENCES ai_schemas(id),
    timeout_seconds          INTEGER      NOT NULL DEFAULT 60 CHECK (timeout_seconds > 0 AND timeout_seconds <= 300),
    allowed_data_sensitivity VARCHAR(32)  NOT NULL CHECK (allowed_data_sensitivity IN ('PUBLIC', 'INTERNAL', 'RESTRICTED')),
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tasks_capability ON ai_task_definitions(capability_code, is_active);

-- 4. AI Tool Registry (controlled interfaces to application services)
CREATE TABLE ai_tools (
    id                  UUID PRIMARY KEY,
    tool_code           VARCHAR(80)  NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    description         VARCHAR(500) NOT NULL,
    application_id      UUID         REFERENCES applications(id) ON DELETE CASCADE,
    input_schema_id     UUID         REFERENCES ai_schemas(id),
    output_schema_id    UUID         REFERENCES ai_schemas(id),
    execution_type      VARCHAR(32)  NOT NULL CHECK (execution_type IN ('INTERNAL_MOCK', 'HTTP_WEBHOOK', 'GATEWAY_CALLBACK')),
    endpoint_url        VARCHAR(500),
    is_read_only        BOOLEAN      NOT NULL DEFAULT TRUE,
    required_permission VARCHAR(80),
    risk_level          VARCHAR(32)  NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    timeout_ms          INTEGER      NOT NULL DEFAULT 5000 CHECK (timeout_ms > 0 AND timeout_ms <= 60000),
    status              VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tools_app_status ON ai_tools(application_id, status);
CREATE INDEX idx_tools_read_only ON ai_tools(is_read_only);

-- 5. AI Orchestrator Traces (end-to-end execution telemetry & step logs)
CREATE TABLE ai_orchestrator_traces (
    id                      UUID PRIMARY KEY,
    application_id          UUID         NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID,
    correlation_id          VARCHAR(80)  NOT NULL,
    task_code               VARCHAR(120) NOT NULL,
    capability_code         VARCHAR(80)  NOT NULL,
    model_id                UUID         REFERENCES ai_models(id),
    prompt_id               UUID         REFERENCES ai_prompts(id),
    schema_id               UUID         REFERENCES ai_schemas(id),
    status                  VARCHAR(32)  NOT NULL CHECK (status IN ('SUCCESS', 'SCHEMA_VALIDATION_FAILED', 'TOOL_FAILED', 'ERROR')),
    total_duration_ms       BIGINT       NOT NULL DEFAULT 0,
    prompt_tokens           INTEGER      NOT NULL DEFAULT 0,
    completion_tokens       INTEGER      NOT NULL DEFAULT 0,
    tool_invocations_count  INTEGER      NOT NULL DEFAULT 0,
    steps_json              JSONB        NOT NULL DEFAULT '[]',
    error_message           VARCHAR(500),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_traces_app_created ON ai_orchestrator_traces(application_id, created_at DESC);
CREATE INDEX idx_traces_correlation ON ai_orchestrator_traces(correlation_id);
CREATE INDEX idx_traces_task_status ON ai_orchestrator_traces(task_code, status);

-- =============================================================================
-- Seed Data: Core Task Definitions, Versioned Prompts, Schemas, & Read-Only Tools
-- =============================================================================

-- Seed Schemas
INSERT INTO ai_schemas (id, schema_code, name, description, schema_type, version, schema_definition_json, is_strict, status) VALUES
    ('00000000-0000-0000-0008-000000000001', 'financial_summary_v1', 'Financial Summary Output Schema', 'Structured response for financial education summaries', 'RESPONSE_EXTRACTION', 1, '{"type": "object", "properties": {"summary": {"type": "string"}, "key_takeaways": {"type": "array", "items": {"type": "string"}}, "confidence_score": {"type": "number"}}, "required": ["summary", "key_takeaways"]}', TRUE, 'ACTIVE'),
    ('00000000-0000-0000-0008-000000000002', 'tool_balance_input_v1', 'Get Account Balance Tool Input', 'Input schema for read-only account balance inquiry', 'TOOL_INPUT', 1, '{"type": "object", "properties": {"account_ref": {"type": "string"}}, "required": ["account_ref"]}', TRUE, 'ACTIVE'),
    ('00000000-0000-0000-0008-000000000003', 'tool_balance_output_v1', 'Get Account Balance Tool Output', 'Output schema returning calculated ledger balance', 'TOOL_OUTPUT', 1, '{"type": "object", "properties": {"account_ref": {"type": "string"}, "currency": {"type": "string"}, "current_balance": {"type": "number"}, "as_of": {"type": "string"}}, "required": ["account_ref", "current_balance"]}', TRUE, 'ACTIVE');

-- Seed Prompts
INSERT INTO ai_prompts (id, prompt_code, name, description, capability_code, task_code, application_id, version, status, system_template, user_template, input_variables_json) VALUES
    ('00000000-0000-0000-0009-000000000001', 'financial_summary_prompt', 'Financial Education Summary', 'Generates clear financial management takeaways for users', 'conversational_intelligence', 'financial_summary', NULL, 1, 'PUBLISHED', 'You are CRANIAL Financial Assistant. Provide educational insights. Never initiate banking transactions or transfers.', 'User financial ledger context: {{ledger_context}}. User question: {{user_question}}', '["ledger_context", "user_question"]'),
    ('00000000-0000-0000-0009-000000000002', 'generic_qa_prompt', 'Generic Capability Assistant Prompt', 'General assistance prompt for conversational tasks', 'conversational_intelligence', 'generic_assistance', NULL, 1, 'PUBLISHED', 'You are CRANIAL Universal AI Platform. Provide helpful, accurate responses based only on supplied data.', 'Context: {{context}}. Question: {{query}}', '["context", "query"]');

-- Seed Task Definitions
INSERT INTO ai_task_definitions (id, task_code, name, description, capability_code, default_prompt_id, output_schema_id, timeout_seconds, allowed_data_sensitivity, is_active) VALUES
    ('00000000-0000-0000-000a-000000000001', 'financial_summary', 'Financial Education Summary Task', 'Interprets ledger notes and provides educational summaries for money management apps', 'conversational_intelligence', '00000000-0000-0000-0009-000000000001', '00000000-0000-0000-0008-000000000001', 45, 'RESTRICTED', TRUE),
    ('00000000-0000-0000-000a-000000000002', 'generic_assistance', 'Generic Conversational Task', 'Default assistant task for general inquiry', 'conversational_intelligence', '00000000-0000-0000-0009-000000000002', NULL, 30, 'RESTRICTED', TRUE);

-- Seed Read-Only Tool (Peggy ledger balance inquiry — purely read-only money management, zero payment capability)
INSERT INTO ai_tools (id, tool_code, name, description, application_id, input_schema_id, output_schema_id, execution_type, endpoint_url, is_read_only, required_permission, risk_level, timeout_ms, status) VALUES
    ('00000000-0000-0000-000b-000000000001', 'peggy_get_account_balance', 'Get Account Ledger Balance', 'Inquires read-only ledger balance for an account in Peggy money management app. Never executes banking or payments.', NULL, '00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0008-000000000003', 'INTERNAL_MOCK', 'mock://peggy/balance', TRUE, 'app:read', 'LOW', 3000, 'ACTIVE');

