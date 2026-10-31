-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 6 Continuation (Section 20.5 Advanced RAG):
-- 1. Per-Collection pgvector HNSW Tuning Parameters (m, ef_construction, ef_search)
-- 2. Parent-Child Hierarchical Chunk Context Storage
-- 3. Bilingual Query Synonym Dictionary Registry (EN <-> ID)
-- 4. Conditional URI / Webhook Feed Pull Connectors (ETag / If-Modified-Since)
-- 5. RAG Answer User Feedback Loop & Eval Benchmark Promotion
-- =============================================================================

-- 1. Per-collection pgvector HNSW index & search tuning parameters
ALTER TABLE ai_knowledge_collections ADD COLUMN hnsw_m INTEGER NOT NULL DEFAULT 16;
ALTER TABLE ai_knowledge_collections ADD COLUMN hnsw_ef_construction INTEGER NOT NULL DEFAULT 64;
ALTER TABLE ai_knowledge_collections ADD COLUMN hnsw_ef_search INTEGER NOT NULL DEFAULT 40;

ALTER TABLE ai_knowledge_collections ADD CONSTRAINT chk_kc_hnsw_m
    CHECK (hnsw_m >= 4 AND hnsw_m <= 64);
ALTER TABLE ai_knowledge_collections ADD CONSTRAINT chk_kc_hnsw_ef_construction
    CHECK (hnsw_ef_construction >= 16 AND hnsw_ef_construction <= 512);
ALTER TABLE ai_knowledge_collections ADD CONSTRAINT chk_kc_hnsw_ef_search
    CHECK (hnsw_ef_search >= 10 AND hnsw_ef_search <= 500);

-- 2. Parent-child hierarchical chunk context on ai_knowledge_chunks
ALTER TABLE ai_knowledge_chunks ADD COLUMN parent_section_content TEXT;

-- 3. Bilingual Domain Synonym Dictionary (EN <-> ID)
CREATE TABLE ai_knowledge_synonyms (
    id                      UUID PRIMARY KEY,
    collection_id           UUID REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    canonical_term          VARCHAR(120) NOT NULL,
    synonyms_json           JSONB        NOT NULL DEFAULT '[]',
    locale_pair             VARCHAR(24)  NOT NULL DEFAULT 'en_id',
    domain_tag              VARCHAR(64)  NOT NULL DEFAULT 'general',
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ksyn_collection_active ON ai_knowledge_synonyms(collection_id, is_active);

-- 4. Conditional Feed & URI Pull Connectors (ETag / If-Modified-Since)
CREATE TABLE ai_knowledge_connectors (
    id                      UUID PRIMARY KEY,
    connector_code          VARCHAR(100) NOT NULL UNIQUE,
    collection_id           UUID         NOT NULL REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name                    VARCHAR(160) NOT NULL,
    connector_type          VARCHAR(32)  NOT NULL CHECK (connector_type IN ('INTERNAL_SAMPLE', 'HTTP_FEED')),
    feed_uri                VARCHAR(500) NOT NULL,
    target_source_code      VARCHAR(120) NOT NULL,
    content_type            VARCHAR(32)  NOT NULL DEFAULT 'MARKDOWN' CHECK (content_type IN ('PLAIN_TEXT', 'MARKDOWN', 'FAQ', 'GLOSSARY', 'POLICY_SOP')),
    permission_scope        VARCHAR(32)  NOT NULL DEFAULT 'AUTHENTICATED' CHECK (permission_scope IN ('PUBLIC', 'AUTHENTICATED', 'TENANT_MEMBERS', 'ROLE_RESTRICTED', 'OWNER_ONLY')),
    allowed_roles_json      JSONB        NOT NULL DEFAULT '[]',
    poll_interval_minutes   INTEGER      NOT NULL DEFAULT 60 CHECK (poll_interval_minutes >= 5),
    last_etag               VARCHAR(200),
    last_modified_header    VARCHAR(120),
    last_sync_status        VARCHAR(32)  NOT NULL DEFAULT 'NEVER_SYNCED' CHECK (last_sync_status IN ('NEVER_SYNCED', 'INDEXED', 'NOT_MODIFIED', 'SKIPPED_UNCHANGED', 'FAILED')),
    last_sync_message       VARCHAR(1000),
    last_synced_at          TIMESTAMPTZ,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kconn_collection ON ai_knowledge_connectors(collection_id, is_active);

-- 5. RAG User / Operator Feedback & Eval Benchmark Promotion Queue
CREATE TABLE ai_knowledge_feedback (
    id                      UUID PRIMARY KEY,
    collection_id           UUID         NOT NULL REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID REFERENCES tenants(id) ON DELETE CASCADE,
    external_user_ref       VARCHAR(150),
    question_text           VARCHAR(1000) NOT NULL,
    answer_text             TEXT,
    rating                  VARCHAR(32)   NOT NULL CHECK (rating IN ('HELPFUL', 'UNHELPFUL', 'CITATION_MISMATCH', 'MISSING_INFO')),
    cited_source_codes_json JSONB         NOT NULL DEFAULT '[]',
    expected_source_code    VARCHAR(120),
    comment                 VARCHAR(1000),
    promoted_eval_case_id   UUID REFERENCES ai_knowledge_eval_cases(id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kfb_collection_rating ON ai_knowledge_feedback(collection_id, rating, created_at DESC);

-- =============================================================================
-- Seed Data: Bilingual EN <-> ID Domain Synonyms & Sample Conditional FAQ Feed
-- =============================================================================

INSERT INTO ai_knowledge_synonyms (
    id, collection_id, application_id, canonical_term, synonyms_json, locale_pair, domain_tag, is_active
) VALUES
    ('00000000-0000-0000-0011-000000000001', NULL, NULL,
     'cicilan', '["installment","angsuran","monthly installment"]', 'en_id', 'money_management', TRUE),
    ('00000000-0000-0000-0011-000000000002', NULL, NULL,
     'dana pendidikan', '["education fund","tabungan sekolah","education sinking fund"]', 'en_id', 'money_management', TRUE),
    ('00000000-0000-0000-0011-000000000003', NULL, NULL,
     'kantong bersama', '["shared pocket","joint budget envelope","anggaran keluarga"]', 'en_id', 'money_management', TRUE),
    ('00000000-0000-0000-0011-000000000004', NULL, NULL,
     'arus kas bersih', '["net cash flow","monthly net cashflow","surplus anggaran"]', 'en_id', 'money_management', TRUE),
    ('00000000-0000-0000-0011-000000000005', NULL, NULL,
     'surat jalan', '["delivery note","packing slip","proof of delivery"]', 'en_id', 'operations_sop', TRUE),
    ('00000000-0000-0000-0011-000000000006', NULL, NULL,
     'selisih stok', '["stock variance","inventory discrepancy","selisih kuantitas"]', 'en_id', 'operations_sop', TRUE);

INSERT INTO ai_knowledge_connectors (
    id, connector_code, collection_id, application_id, tenant_id, name,
    connector_type, feed_uri, target_source_code, content_type, permission_scope,
    allowed_roles_json, poll_interval_minutes, last_sync_status, is_active
) VALUES (
    '00000000-0000-0000-0012-000000000001',
    'peggy_budget_faq_feed',
    '00000000-0000-0000-000e-000000000001',
    NULL,
    NULL,
    'Peggy Sinking Fund & Pocket Budgeting FAQ Feed (EN/ID)',
    'INTERNAL_SAMPLE',
    'cranial://sample-feed/peggy-budget-faq-v1',
    'peggy_faq_feed_live',
    'FAQ',
    'PUBLIC',
    '[]',
    60,
    'NEVER_SYNCED',
    TRUE
);

