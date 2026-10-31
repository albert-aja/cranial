-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 1 Foundation & Architecture: Feature Flags
-- =============================================================================

CREATE TABLE platform_feature_flags (
    id                 UUID PRIMARY KEY,
    flag_key           VARCHAR(120) NOT NULL UNIQUE,
    name               VARCHAR(150) NOT NULL,
    description        VARCHAR(500),
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    rollout_percentage INTEGER      NOT NULL DEFAULT 100 CHECK (rollout_percentage >= 0 AND rollout_percentage <= 100),
    allowed_app_codes  JSONB        NOT NULL DEFAULT '[]',
    updated_by         UUID         REFERENCES platform_users(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feature_flags_enabled ON platform_feature_flags(enabled);

-- Seed core foundation rollout flags
INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES
    ('00000000-0000-0000-0004-000000000001', 'system.maintenance.auto_cleanup', 'Automatic Maintenance Cleanup', 'Background purge of expired idempotency records and rotating credentials.', TRUE, 100, '[]'),
    ('00000000-0000-0000-0004-000000000002', 'stream.sse.enabled', 'SSE Streaming AI Responses', 'Server-Sent Events streaming for conversational intelligence.', FALSE, 0, '[]'),
    ('00000000-0000-0000-0004-000000000003', 'ocr.preprocessing.advanced', 'Advanced Image Preprocessing', 'Deskew, denoise, and auto-contrast enhancement before OCR pass.', TRUE, 100, '[]'),
    ('00000000-0000-0000-0004-000000000004', 'routing.local_first.strict', 'Strict Local-First Routing', 'Enforce local inference and prevent hosted provider fallback.', TRUE, 100, '[]');

