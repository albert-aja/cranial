-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 2 Migration: Provider Infrastructure, Model Registry, Routing & Usage
-- =============================================================================

-- 1. AI Providers (Ollama, local runtime, optional hosted adapters)
CREATE TABLE ai_providers (
    id                   UUID PRIMARY KEY,
    code                 VARCHAR(64)  NOT NULL UNIQUE,
    name                 VARCHAR(120) NOT NULL,
    provider_type        VARCHAR(32)  NOT NULL CHECK (provider_type IN ('LOCAL', 'HOSTED_FREE', 'HOSTED_PAID')),
    base_url             VARCHAR(500) NOT NULL,
    api_key_encrypted    VARCHAR(1024),
    status               VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'UNREACHABLE')),
    timeout_seconds      INTEGER      NOT NULL DEFAULT 30 CHECK (timeout_seconds > 0 AND timeout_seconds <= 300),
    config_json          JSONB        NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_providers_type_status ON ai_providers(provider_type, status);

-- 2. AI Models Registry
CREATE TABLE ai_models (
    id                            UUID PRIMARY KEY,
    provider_id                   UUID         NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    model_identifier              VARCHAR(120) NOT NULL,
    display_name                  VARCHAR(150) NOT NULL,
    modality                      VARCHAR(32)  NOT NULL CHECK (modality IN ('CHAT', 'EMBEDDING', 'VISION', 'MULTIMODAL')),
    context_window_tokens         INTEGER      NOT NULL DEFAULT 8192 CHECK (context_window_tokens > 0),
    max_output_tokens             INTEGER      NOT NULL DEFAULT 2048 CHECK (max_output_tokens >= 0),
    cost_per_million_input_cents  INTEGER      NOT NULL DEFAULT 0 CHECK (cost_per_million_input_cents >= 0),
    cost_per_million_output_cents INTEGER      NOT NULL DEFAULT 0 CHECK (cost_per_million_output_cents >= 0),
    is_local                      BOOLEAN      NOT NULL DEFAULT TRUE,
    status                        VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    metadata_json                 JSONB        NOT NULL DEFAULT '{}',
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_provider_ident UNIQUE (provider_id, model_identifier)
);

CREATE INDEX idx_models_modality_status ON ai_models(modality, status);
CREATE INDEX idx_models_is_local ON ai_models(is_local);

-- 3. Model Capabilities Mapping
CREATE TABLE ai_model_capabilities (
    model_id        UUID        NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    capability_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (model_id, capability_code)
);

-- 4. AI Routing Policies (local-first; no-paid-fallback enforced)
CREATE TABLE ai_routing_policies (
    id                         UUID PRIMARY KEY,
    name                       VARCHAR(150) NOT NULL,
    capability_code            VARCHAR(80)  NOT NULL,
    task_code                  VARCHAR(120),
    preferred_model_id         UUID         NOT NULL REFERENCES ai_models(id),
    fallback_model_id          UUID         REFERENCES ai_models(id),
    allowed_data_sensitivity   VARCHAR(32)  NOT NULL CHECK (allowed_data_sensitivity IN ('PUBLIC', 'INTERNAL', 'RESTRICTED')),
    is_active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_routing_policies_lookup ON ai_routing_policies(capability_code, is_active);

-- 5. AI Usage & Resource Records
CREATE TABLE ai_usage_records (
    id                     UUID PRIMARY KEY,
    application_id         UUID         NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id              UUID,
    correlation_id         VARCHAR(80)  NOT NULL,
    provider_id            UUID         NOT NULL REFERENCES ai_providers(id),
    model_id               UUID         NOT NULL REFERENCES ai_models(id),
    capability_code        VARCHAR(80)  NOT NULL,
    task_code              VARCHAR(120),
    prompt_tokens          INTEGER      NOT NULL DEFAULT 0,
    completion_tokens      INTEGER      NOT NULL DEFAULT 0,
    total_tokens           INTEGER      NOT NULL DEFAULT 0,
    duration_ms            BIGINT       NOT NULL DEFAULT 0,
    is_local               BOOLEAN      NOT NULL DEFAULT TRUE,
    estimated_cost_cents   INTEGER      NOT NULL DEFAULT 0,
    status                 VARCHAR(32)  NOT NULL CHECK (status IN ('SUCCESS', 'ERROR', 'FALLBACK_BLOCKED')),
    error_message          VARCHAR(500),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usage_records_app_created ON ai_usage_records(application_id, created_at DESC);
CREATE INDEX idx_usage_records_model ON ai_usage_records(model_id, created_at DESC);
CREATE INDEX idx_usage_records_correlation ON ai_usage_records(correlation_id);

-- =============================================================================
-- Seed Data: Local Providers, Open Models & Baseline Local-First Routing
-- =============================================================================

INSERT INTO ai_providers (id, code, name, provider_type, base_url, status, timeout_seconds, config_json) VALUES
    ('00000000-0000-0000-0005-000000000001', 'ollama', 'Ollama Local Runtime', 'LOCAL', 'http://localhost:11434', 'ACTIVE', 60, '{"api_version": "v1"}'),
    ('00000000-0000-0000-0005-000000000002', 'mock_local', 'Mock Local Engine', 'LOCAL', 'internal://mock', 'ACTIVE', 10, '{"offline_test_ready": true}');

INSERT INTO ai_models (id, provider_id, model_identifier, display_name, modality, context_window_tokens, max_output_tokens, cost_per_million_input_cents, cost_per_million_output_cents, is_local, status, metadata_json) VALUES
    ('00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000001', 'llama3.2:3b', 'Llama 3.2 3B (Local)', 'CHAT', 8192, 2048, 0, 0, TRUE, 'ACTIVE', '{"parameter_size": "3B", "quantization": "q4_K_M"}'),
    ('00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0005-000000000001', 'qwen2.5:7b', 'Qwen 2.5 7B (Local)', 'CHAT', 32768, 4096, 0, 0, TRUE, 'ACTIVE', '{"parameter_size": "7B", "quantization": "q4_K_M"}'),
    ('00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0005-000000000001', 'nomic-embed-text', 'Nomic Embed Text (Local)', 'EMBEDDING', 2048, 0, 0, 0, TRUE, 'ACTIVE', '{"dimensions": 768}'),
    ('00000000-0000-0000-0006-000000000004', '00000000-0000-0000-0005-000000000002', 'mock-chat-v1', 'Mock Chat Engine (Testing)', 'CHAT', 4096, 1024, 0, 0, TRUE, 'ACTIVE', '{"testing_only": true}');

-- Capabilities mapping for Llama 3.2, Qwen 2.5, Mock Chat, and Nomic Embed
INSERT INTO ai_model_capabilities (model_id, capability_code) VALUES
    ('00000000-0000-0000-0006-000000000001', 'conversational_intelligence'),
    ('00000000-0000-0000-0006-000000000001', 'data_intelligence'),
    ('00000000-0000-0000-0006-000000000002', 'conversational_intelligence'),
    ('00000000-0000-0000-0006-000000000002', 'document_intelligence'),
    ('00000000-0000-0000-0006-000000000002', 'customer_intelligence'),
    ('00000000-0000-0000-0006-000000000003', 'knowledge_intelligence'),
    ('00000000-0000-0000-0006-000000000004', 'conversational_intelligence'),
    ('00000000-0000-0000-0006-000000000004', 'document_intelligence');

-- Local-first default routing policies (Zero paid fallbacks)
INSERT INTO ai_routing_policies (id, name, capability_code, task_code, preferred_model_id, fallback_model_id, allowed_data_sensitivity, is_active) VALUES
    ('00000000-0000-0000-0007-000000000001', 'Default Conversational Route', 'conversational_intelligence', NULL, '00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0006-000000000004', 'RESTRICTED', TRUE),
    ('00000000-0000-0000-0007-000000000002', 'Default Document Extraction Route', 'document_intelligence', NULL, '00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0006-000000000004', 'RESTRICTED', TRUE),
    ('00000000-0000-0000-0007-000000000003', 'Default Embedding Route', 'knowledge_intelligence', NULL, '00000000-0000-0000-0006-000000000003', NULL, 'RESTRICTED', TRUE);
