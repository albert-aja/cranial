-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 1 Migration: Platform Foundation, IAM, App Registry, Multi-Tenancy, Audit
-- =============================================================================

-- 1. Operator IAM
CREATE TABLE platform_users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(150) NOT NULL,
    status          VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id              UUID PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    id              UUID PRIMARY KEY,
    code            VARCHAR(80)  NOT NULL UNIQUE,
    description     VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE platform_user_roles (
    platform_user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    role_id          UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (platform_user_id, role_id)
);

-- 2. Application Registry & Settings
CREATE TABLE applications (
    id              UUID PRIMARY KEY,
    code            VARCHAR(80)  NOT NULL,
    environment     VARCHAR(32)  NOT NULL CHECK (environment IN ('DEV', 'STAGING', 'PROD')),
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    allowed_origins JSONB        NOT NULL,
    created_by      UUID         REFERENCES platform_users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_applications_code_env UNIQUE (code, environment)
);

CREATE INDEX idx_applications_status ON applications(status);

CREATE TABLE application_settings (
    application_id           UUID PRIMARY KEY REFERENCES applications(id) ON DELETE CASCADE,
    rate_limit_rpm           INTEGER      NOT NULL DEFAULT 120 CHECK (rate_limit_rpm > 0),
    concurrency_limit        INTEGER      NOT NULL DEFAULT 10 CHECK (concurrency_limit > 0),
    max_input_bytes          INTEGER      NOT NULL DEFAULT 1048576 CHECK (max_input_bytes > 0),
    require_signed_context   BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_hosted_providers   BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_sensitive_external BOOLEAN      NOT NULL DEFAULT FALSE,
    webhook_url              VARCHAR(500),
    webhook_secret_encrypted VARCHAR(1024),
    config_json              JSONB        NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Application Credentials (never stores plaintext secrets)
CREATE TABLE application_credentials (
    id                       UUID PRIMARY KEY,
    application_id           UUID          NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    name                     VARCHAR(120)  NOT NULL,
    environment              VARCHAR(32)   NOT NULL CHECK (environment IN ('DEV', 'STAGING', 'PROD')),
    key_prefix               VARCHAR(48)   NOT NULL UNIQUE,
    secret_hash              VARCHAR(128)  NOT NULL,
    signing_secret_encrypted VARCHAR(1024) NOT NULL,
    status                   VARCHAR(32)   NOT NULL CHECK (status IN ('ACTIVE', 'ROTATING', 'REVOKED', 'EXPIRED')),
    expires_at               TIMESTAMPTZ,
    last_used_at             TIMESTAMPTZ,
    created_by               UUID          REFERENCES platform_users(id),
    revoked_by               UUID          REFERENCES platform_users(id),
    revoked_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_credentials_app_status ON application_credentials(application_id, status);

-- 4. Shared Intelligence Capabilities & Application Entitlements
CREATE TABLE ai_capabilities (
    id              UUID PRIMARY KEY,
    code            VARCHAR(80)  NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(1000) NOT NULL,
    status          VARCHAR(32)  NOT NULL CHECK (status IN ('AVAILABLE', 'PLANNED', 'DISABLED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application_capabilities (
    id                   UUID PRIMARY KEY,
    application_id       UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    capability_id        UUID        NOT NULL REFERENCES ai_capabilities(id) ON DELETE CASCADE,
    enabled              BOOLEAN     NOT NULL DEFAULT FALSE,
    quota_daily_requests INTEGER     CHECK (quota_daily_requests IS NULL OR quota_daily_requests >= 0),
    config_override      JSONB       NOT NULL,
    updated_by           UUID        REFERENCES platform_users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_capability UNIQUE (application_id, capability_id)
);

CREATE INDEX idx_app_capabilities_app ON application_capabilities(application_id, enabled);

-- 5. Multi-Tenancy Foundation
CREATE TABLE tenants (
    id                  UUID PRIMARY KEY,
    application_id      UUID         NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    external_tenant_ref VARCHAR(150) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    status              VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    metadata            JSONB        NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenants_app_ext_ref UNIQUE (application_id, external_tenant_ref)
);

CREATE INDEX idx_tenants_app_status ON tenants(application_id, status);

CREATE TABLE tenant_memberships (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    external_user_ref VARCHAR(150) NOT NULL,
    member_role       VARCHAR(80)  NOT NULL,
    status            VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_member UNIQUE (tenant_id, external_user_ref)
);

-- 6. Request Idempotency Records
CREATE TABLE idempotency_records (
    id              UUID PRIMARY KEY,
    application_id  UUID         NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(160) NOT NULL,
    endpoint        VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INTEGER      NOT NULL,
    response_body   JSONB        NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_app_key_endpoint UNIQUE (application_id, idempotency_key, endpoint)
);

CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);

-- 7. Platform Audit Logs (append-only semantics)
CREATE TABLE ai_audit_logs (
    id              UUID PRIMARY KEY,
    correlation_id  VARCHAR(80)  NOT NULL,
    actor_type      VARCHAR(32)  NOT NULL CHECK (actor_type IN ('PLATFORM_USER', 'APPLICATION', 'ANONYMOUS', 'SYSTEM')),
    actor_id        VARCHAR(120),
    application_id  UUID,
    tenant_id       UUID,
    action          VARCHAR(120) NOT NULL,
    target_type     VARCHAR(80)  NOT NULL,
    target_id       VARCHAR(120),
    outcome         VARCHAR(32)  NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),
    metadata        JSONB        NOT NULL,
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_created_at ON ai_audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_app_created ON ai_audit_logs(application_id, created_at DESC);
CREATE INDEX idx_audit_logs_correlation ON ai_audit_logs(correlation_id);
CREATE INDEX idx_audit_logs_outcome ON ai_audit_logs(outcome, created_at DESC);

-- =============================================================================
-- Seed Data: Roles, Permissions, and Core Shared Capabilities
-- =============================================================================

INSERT INTO roles (id, code, name, description) VALUES
    ('00000000-0000-0000-0001-000000000001', 'SUPER_ADMIN', 'Super Administrator', 'Full platform administration including operator, credential, capability, and security governance.'),
    ('00000000-0000-0000-0001-000000000002', 'PLATFORM_OPERATOR', 'Platform Operator', 'Manages applications, capability entitlements, and tenants without super-admin overrides.'),
    ('00000000-0000-0000-0001-000000000003', 'AUDITOR', 'Platform Auditor', 'Read-only visibility into applications, capabilities, tenants, and sanitized platform audit logs.');

INSERT INTO permissions (id, code, description) VALUES
    ('00000000-0000-0000-0002-000000000001', 'app:read', 'View registered applications, settings, and credential metadata'),
    ('00000000-0000-0000-0002-000000000002', 'app:write', 'Register applications and update lifecycle status, quotas, and settings'),
    ('00000000-0000-0000-0002-000000000003', 'credential:manage', 'Issue, rotate, and revoke application credentials'),
    ('00000000-0000-0000-0002-000000000004', 'capability:manage', 'Configure per-application capability entitlements and quotas'),
    ('00000000-0000-0000-0002-000000000005', 'tenant:read', 'View application tenants and memberships'),
    ('00000000-0000-0000-0002-000000000006', 'tenant:manage', 'Provision and update application tenants and memberships'),
    ('00000000-0000-0000-0002-000000000007', 'audit:read', 'Inspect platform audit and authorization records');

-- SUPER_ADMIN -> all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0001-000000000001', id FROM permissions;

-- PLATFORM_OPERATOR -> all except credential:manage stays included for ops; full operational set
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000001'),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000002'),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000003'),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000004'),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000005'),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000006'),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0002-000000000007');

-- AUDITOR -> read-only permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0002-000000000001'),
    ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0002-000000000005'),
    ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0002-000000000007');

-- Core CRANIAL Shared Intelligence Capabilities
INSERT INTO ai_capabilities (id, code, name, description, status) VALUES
    ('00000000-0000-0000-0003-000000000001', 'conversational_intelligence', 'Conversational Intelligence', 'Multi-turn natural-language assistant over authorized application tools and sessions.', 'AVAILABLE'),
    ('00000000-0000-0000-0003-000000000002', 'document_intelligence', 'Document Intelligence', 'Secure OCR, document parsing, structured field extraction, and schema validation.', 'AVAILABLE'),
    ('00000000-0000-0000-0003-000000000003', 'data_intelligence', 'Data Intelligence', 'Explanations, trend summaries, and anomaly workflows over deterministic application reports.', 'AVAILABLE'),
    ('00000000-0000-0000-0003-000000000004', 'decision_intelligence', 'Decision Intelligence', 'Constraint-based optimization and recommendation workflows with human approval.', 'AVAILABLE'),
    ('00000000-0000-0000-0003-000000000005', 'knowledge_intelligence', 'Knowledge Intelligence', 'Permission-filtered retrieval-augmented generation (RAG) with source citations.', 'AVAILABLE'),
    ('00000000-0000-0000-0003-000000000006', 'customer_intelligence', 'Customer Intelligence', 'Support classification, sentiment analysis, product discovery, and human escalation.', 'AVAILABLE');

