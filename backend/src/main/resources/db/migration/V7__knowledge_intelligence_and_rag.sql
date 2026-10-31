-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 6 & Section 20.5 Migration: Knowledge Intelligence, Hybrid RAG,
-- Versioning, Permission-Aware Retrieval, Glossary Linking & Evaluation
-- =============================================================================

-- 1. Register local deterministic semantic embedding model & wire local fallback
INSERT INTO ai_models (
    id, provider_id, model_identifier, display_name, modality,
    context_window_tokens, max_output_tokens, cost_per_million_input_cents,
    cost_per_million_output_cents, is_local, status, metadata_json
) VALUES (
    '00000000-0000-0000-0006-000000000005',
    '00000000-0000-0000-0005-000000000002',
    'local-semantic-embed-v1',
    'Local Semantic Feature Embedder (EN/ID)',
    'EMBEDDING',
    4096, 0, 0, 0, TRUE, 'ACTIVE',
    '{"dimensions": 64, "bilingual_en_id": true, "zero_cloud_cost": true}'
);

INSERT INTO ai_model_capabilities (model_id, capability_code) VALUES
    ('00000000-0000-0000-0006-000000000005', 'knowledge_intelligence'),
    ('00000000-0000-0000-0006-000000000002', 'knowledge_intelligence'),
    ('00000000-0000-0000-0006-000000000004', 'knowledge_intelligence');

UPDATE ai_routing_policies
SET fallback_model_id = '00000000-0000-0000-0006-000000000005'
WHERE id = '00000000-0000-0000-0007-000000000003';

INSERT INTO ai_routing_policies (
    id, name, capability_code, task_code, preferred_model_id, fallback_model_id, allowed_data_sensitivity, is_active
) VALUES (
    '00000000-0000-0000-0007-000000000005',
    'Grounded RAG Answer Synthesis Route',
    'knowledge_intelligence',
    'rag_grounded_answer',
    '00000000-0000-0000-0006-000000000002',
    '00000000-0000-0000-0006-000000000004',
    'RESTRICTED',
    TRUE
);

INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES (
    '00000000-0000-0000-0004-000000000005',
    'rag.hybrid_rerank.enabled',
    'RAG Hybrid Retrieval & Cross-Feature Reranker',
    'Enables BM25 + dense vector fusion and heading/proximity/freshness reranking for knowledge retrieval',
    TRUE,
    100,
    '[]'
);

-- 2. Knowledge Collections
CREATE TABLE ai_knowledge_collections (
    id                      UUID PRIMARY KEY,
    collection_code         VARCHAR(100) NOT NULL UNIQUE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name                    VARCHAR(160) NOT NULL,
    description             VARCHAR(1000),
    collection_type         VARCHAR(40)  NOT NULL CHECK (collection_type IN ('GENERAL_RAG', 'SOP_MANUAL', 'FAQ_POLICY', 'GLOSSARY_ENCYCLOPEDIA')),
    visibility_scope        VARCHAR(32)  NOT NULL CHECK (visibility_scope IN ('APPLICATION', 'TENANT', 'GLOBAL_BENCHMARK')),
    default_chunk_strategy  VARCHAR(32)  NOT NULL CHECK (default_chunk_strategy IN ('PARAGRAPH', 'MARKDOWN_SECTION', 'FAQ_QA', 'GLOSSARY_ENTRY', 'FIXED_WINDOW')),
    chunk_size_chars        INTEGER      NOT NULL DEFAULT 600 CHECK (chunk_size_chars >= 80 AND chunk_size_chars <= 8000),
    chunk_overlap_chars     INTEGER      NOT NULL DEFAULT 80 CHECK (chunk_overlap_chars >= 0 AND chunk_overlap_chars <= 2000),
    embedding_model_code    VARCHAR(120) NOT NULL DEFAULT 'local-semantic-embed-v1',
    embedding_dimensions    INTEGER      NOT NULL DEFAULT 64 CHECK (embedding_dimensions > 0),
    rerank_by_default       BOOLEAN      NOT NULL DEFAULT TRUE,
    freshness_ttl_days      INTEGER      NOT NULL DEFAULT 90 CHECK (freshness_ttl_days > 0),
    retention_days          INTEGER      CHECK (retention_days IS NULL OR retention_days > 0),
    allowed_roles_json      JSONB        NOT NULL DEFAULT '[]',
    metadata_json           JSONB        NOT NULL DEFAULT '{}',
    status                  VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kc_app_tenant ON ai_knowledge_collections(application_id, tenant_id, status);

-- 3. Knowledge Sources (Versioned, Permission-Tagged, Freshness & Retention Governed)
CREATE TABLE ai_knowledge_sources (
    id                      UUID PRIMARY KEY,
    collection_id           UUID         NOT NULL REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    application_id          UUID REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID REFERENCES tenants(id) ON DELETE CASCADE,
    source_code             VARCHAR(120) NOT NULL,
    title                   VARCHAR(250) NOT NULL,
    source_uri              VARCHAR(500),
    content_type            VARCHAR(32)  NOT NULL CHECK (content_type IN ('PLAIN_TEXT', 'MARKDOWN', 'FAQ', 'GLOSSARY', 'POLICY_SOP')),
    chunk_strategy_override VARCHAR(32),
    raw_content             TEXT,
    content_sha256          VARCHAR(64)  NOT NULL,
    version                 INTEGER      NOT NULL DEFAULT 1,
    indexing_status         VARCHAR(32)  NOT NULL CHECK (indexing_status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'REVOKED', 'DELETED')),
    error_detail            VARCHAR(1000),
    chunk_count             INTEGER      NOT NULL DEFAULT 0,
    token_count             INTEGER      NOT NULL DEFAULT 0,
    permission_scope        VARCHAR(32)  NOT NULL DEFAULT 'AUTHENTICATED' CHECK (permission_scope IN ('PUBLIC', 'AUTHENTICATED', 'TENANT_MEMBERS', 'ROLE_RESTRICTED', 'OWNER_ONLY')),
    allowed_roles_json      JSONB        NOT NULL DEFAULT '[]',
    allowed_user_refs_json  JSONB        NOT NULL DEFAULT '[]',
    metadata_json           JSONB        NOT NULL DEFAULT '{}',
    effective_updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_indexed_at         TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ks_collection_code UNIQUE (collection_id, source_code)
);

CREATE INDEX idx_ks_collection_status ON ai_knowledge_sources(collection_id, indexing_status);
CREATE INDEX idx_ks_app_tenant ON ai_knowledge_sources(application_id, tenant_id);
CREATE INDEX idx_ks_expires ON ai_knowledge_sources(expires_at);

-- 4. Knowledge Chunks & Embeddings (with Page/Section Citations & Content Hash for Incremental Re-Indexing)
CREATE TABLE ai_knowledge_chunks (
    id                      UUID PRIMARY KEY,
    collection_id           UUID         NOT NULL REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    source_id               UUID         NOT NULL REFERENCES ai_knowledge_sources(id) ON DELETE CASCADE,
    application_id          UUID,
    tenant_id               UUID,
    source_version          INTEGER      NOT NULL DEFAULT 1,
    chunk_index             INTEGER      NOT NULL,
    chunk_sha256            VARCHAR(64)  NOT NULL,
    content                 TEXT         NOT NULL,
    normalized_terms        TEXT         NOT NULL,
    token_count             INTEGER      NOT NULL DEFAULT 0,
    page_number             INTEGER,
    section_title           VARCHAR(250),
    char_start              INTEGER      NOT NULL DEFAULT 0,
    char_end                INTEGER      NOT NULL DEFAULT 0,
    embedding_model_code    VARCHAR(120) NOT NULL,
    embedding_dimensions    INTEGER      NOT NULL,
    embedding_json          JSONB        NOT NULL,
    metadata_json           JSONB        NOT NULL DEFAULT '{}',
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kch_collection_source ON ai_knowledge_chunks(collection_id, source_id);
CREATE INDEX idx_kch_app_tenant ON ai_knowledge_chunks(application_id, tenant_id);
CREATE INDEX idx_kch_expires ON ai_knowledge_chunks(expires_at);

-- 5. Source Versioning & Re-Index Audit History
CREATE TABLE ai_knowledge_source_events (
    id                      UUID PRIMARY KEY,
    collection_id           UUID         NOT NULL REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    source_id               UUID         NOT NULL,
    source_code             VARCHAR(120) NOT NULL,
    event_type              VARCHAR(40)  NOT NULL CHECK (event_type IN ('INGESTED', 'SKIPPED_UNCHANGED', 'INCREMENTAL_REINDEXED', 'FORCE_REINDEXED', 'REVOKED_PROPAGATED', 'DELETED_PROPAGATED', 'RETENTION_PURGED', 'INDEX_FAILED')),
    source_version          INTEGER      NOT NULL,
    previous_sha256         VARCHAR(64),
    new_sha256              VARCHAR(64),
    chunks_added            INTEGER      NOT NULL DEFAULT 0,
    chunks_reused           INTEGER      NOT NULL DEFAULT 0,
    chunks_removed          INTEGER      NOT NULL DEFAULT 0,
    duration_ms             BIGINT       NOT NULL DEFAULT 0,
    actor                   VARCHAR(150),
    details_json            JSONB        NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kse_source_created ON ai_knowledge_source_events(source_id, created_at DESC);

-- 6. Glossary & Encyclopedia Terms (Educational & Money-Management Term Linking)
CREATE TABLE ai_knowledge_glossary_terms (
    id                      UUID PRIMARY KEY,
    collection_id           UUID         NOT NULL REFERENCES ai_knowledge_collections(id) ON DELETE CASCADE,
    source_id               UUID REFERENCES ai_knowledge_sources(id) ON DELETE CASCADE,
    chunk_id                UUID REFERENCES ai_knowledge_chunks(id) ON DELETE SET NULL,
    application_id          UUID,
    tenant_id               UUID,
    term                    VARCHAR(160) NOT NULL,
    normalized_term         VARCHAR(160) NOT NULL,
    aliases_json            JSONB        NOT NULL DEFAULT '[]',
    locale                  VARCHAR(16)  NOT NULL DEFAULT 'en',
    category                VARCHAR(80),
    short_definition        VARCHAR(1000) NOT NULL,
    related_terms_json      JSONB        NOT NULL DEFAULT '[]',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kgt_collection_norm ON ai_knowledge_glossary_terms(collection_id, normalized_term);

-- 7. Retrieval Evaluation Dataset & Permission Regression Cases
CREATE TABLE ai_knowledge_eval_cases (
    id                               UUID PRIMARY KEY,
    case_code                        VARCHAR(80)  NOT NULL UNIQUE,
    name                             VARCHAR(160) NOT NULL,
    collection_code                  VARCHAR(100) NOT NULL,
    query_text                       VARCHAR(500) NOT NULL,
    retrieval_mode                   VARCHAR(24)  NOT NULL DEFAULT 'HYBRID',
    rerank                           BOOLEAN      NOT NULL DEFAULT TRUE,
    simulated_roles_json             JSONB        NOT NULL DEFAULT '["MEMBER"]',
    metadata_filters_json            JSONB        NOT NULL DEFAULT '{}',
    expected_source_code             VARCHAR(120),
    expected_section_contains        VARCHAR(160),
    expect_permission_blocked_source VARCHAR(120),
    top_k                            INTEGER      NOT NULL DEFAULT 3,
    is_active                        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Seed Data: Reference Collections, Bilingual Peggy Encyclopedia, Ops SOPs & Eval Suite
-- =============================================================================

INSERT INTO ai_knowledge_collections (
    id, collection_code, application_id, tenant_id, name, description,
    collection_type, visibility_scope, default_chunk_strategy, chunk_size_chars,
    chunk_overlap_chars, embedding_model_code, embedding_dimensions,
    rerank_by_default, freshness_ttl_days, retention_days, metadata_json, status
) VALUES
    ('00000000-0000-0000-000e-000000000001', 'peggy_financial_encyclopedia_ref', NULL, NULL,
     'Peggy Financial Literacy Encyclopedia (EN/ID)',
     'Educational money-management glossary and budgeting guides (read-only knowledge; never executes banking or payments)',
     'GLOSSARY_ENCYCLOPEDIA', 'GLOBAL_BENCHMARK', 'GLOSSARY_ENTRY', 550, 60,
     'local-semantic-embed-v1', 64, TRUE, 180, NULL,
     '{"domain":"money_management_education","languages":["en","id"]}', 'ACTIVE'),

    ('00000000-0000-0000-000e-000000000002', 'ops_sop_handbook_ref', NULL, NULL,
     'SMS & Worker Operational SOP Handbook',
     'Warehouse receiving, delivery proof SOPs, and owner-restricted margin policies for permission-aware RAG verification',
     'SOP_MANUAL', 'GLOBAL_BENCHMARK', 'MARKDOWN_SECTION', 600, 80,
     'local-semantic-embed-v1', 64, TRUE, 90, NULL,
     '{"domain":"operations_sop","languages":["en","id"]}', 'ACTIVE');

INSERT INTO ai_knowledge_sources (
    id, collection_id, source_code, title, source_uri, content_type,
    raw_content, content_sha256, version, indexing_status, permission_scope,
    allowed_roles_json, metadata_json
) VALUES
    ('00000000-0000-0000-000f-000000000001', '00000000-0000-0000-000e-000000000001',
     'peggy_glossary_en', 'Personal Budgeting & Financial Terms (English)',
     'cranial://peggy/encyclopedia/en-core', 'GLOSSARY',
     '### Emergency Fund (Aliases: Safety Net, Rainy Day Fund)
An emergency fund is a dedicated personal savings reserve covering 3 to 6 months of essential living expenses so unexpected shocks do not derail a household budget.

### Compound Interest (Aliases: Compounding Growth)
Compound interest means earning returns on both original principal and previously accumulated returns over long horizons.

### Cash Flow (Aliases: Monthly Net Cashflow)
Cash flow tracks total money coming into personal pockets minus categorized expenses over a specific period.',
     'seed_sha_peggy_en_v1', 1, 'PENDING', 'PUBLIC', '[]',
     '{"locale":"en","category":"budgeting","topic":"personal_finance"}'),

    ('00000000-0000-0000-000f-000000000002', '00000000-0000-0000-000e-000000000001',
     'peggy_glossary_id', 'Ensiklopedia Literasi Keuangan Pribadi (Indonesia)',
     'cranial://peggy/encyclopedia/id-core', 'GLOSSARY',
     '### Dana Darurat (Aliases: Simpanan Darurat, Cadangan Kas)
Dana darurat adalah tabungan khusus sebesar 3 hingga 6 kali pengeluaran bulanan wajib untuk menghadapi kondisi mendesak tanpa berutang.

### Inflasi (Aliases: Kenaikan Harga Umum)
Inflasi adalah kenaikan harga barang dan jasa secara umum dari waktu ke waktu yang menurunkan daya beli uang tunai.

### Anggaran 50-30-20 (Aliases: Metode Budgeting 50/30/20)
Metode pengelolaan uang yang membagi pendapatan bersih ke 50% kebutuhan pokok, 30% keinginan terukur, dan 20% tabungan atau dana darurat.',
     'seed_sha_peggy_id_v1', 1, 'PENDING', 'PUBLIC', '[]',
     '{"locale":"id","category":"budgeting","topic":"literasi_keuangan"}'),

    ('00000000-0000-0000-000f-000000000003', '00000000-0000-0000-000e-000000000002',
     'worker_sop_receiving', 'SOP Penerimaan & Pemeriksaan Barang Gudang',
     'cranial://ops/sop/warehouse-receiving', 'MARKDOWN',
     '[Page 1]
# Penerimaan Barang Masuk
Petugas gudang wajib mencocokkan nomor Surat Jalan fisik dengan pesanan pembelian sebelum membongkar muatan truk.

[Page 2]
## Penanganan Barang Rusak
Jika ditemukan kemasan penyok atau segel terbuka, foto barang pada area karantina dan catat selisih kuantitas pada formulir pemeriksaan.',
     'seed_sha_worker_sop_v1', 1, 'PENDING', 'ROLE_RESTRICTED', '["WORKER","SUPERVISOR","OWNER"]',
     '{"locale":"id","department":"warehouse","role_tier":"worker"}'),

    ('00000000-0000-0000-000f-000000000004', '00000000-0000-0000-000e-000000000002',
     'worker_sop_coldchain', 'Cold-Chain Delivery Temperature SOP',
     'cranial://ops/sop/cold-chain-delivery', 'MARKDOWN',
     '[Page 1]
# Chilled & Frozen Delivery Protocol
Drivers must verify insulated box temperature stays between 2C and 8C for chilled items and below -18C for frozen goods prior to departure.

[Page 2]
## Proof of Delivery Handover
Capture recipient name, timestamp, and carton count on the delivery note upon store arrival.',
     'seed_sha_coldchain_v1', 1, 'PENDING', 'ROLE_RESTRICTED', '["WORKER","SUPERVISOR","OWNER"]',
     '{"locale":"en","department":"delivery","role_tier":"worker"}'),

    ('00000000-0000-0000-000f-000000000005', '00000000-0000-0000-000e-000000000002',
     'owner_margin_payroll_policy', 'Confidential Store Profit Margin & Executive Payroll Policy',
     'cranial://ops/policy/owner-margins', 'POLICY_SOP',
     '[Page 1]
# Executive Gross Margin & Rebate Structure
Store net supplier rebate tiers target 18.5% gross margin with quarterly executive bonus distributions reviewed solely by store owners.',
     'seed_sha_owner_only_v1', 1, 'PENDING', 'OWNER_ONLY', '["OWNER"]',
     '{"locale":"en","department":"finance_exec","sensitivity":"owner_only"}');

INSERT INTO ai_knowledge_eval_cases (
    id, case_code, name, collection_code, query_text, retrieval_mode, rerank,
    simulated_roles_json, metadata_filters_json, expected_source_code,
    expected_section_contains, expect_permission_blocked_source, top_k, is_active
) VALUES
    ('00000000-0000-0000-0010-000000000001', 'eval_peggy_emergency_en',
     'Peggy Emergency Fund Retrieval (English)', 'peggy_financial_encyclopedia_ref',
     'How many months of expenses should an emergency fund cover?',
     'HYBRID', TRUE, '["MEMBER"]', '{"locale":"en"}',
     'peggy_glossary_en', 'Emergency Fund', NULL, 3, TRUE),

    ('00000000-0000-0000-0010-000000000002', 'eval_peggy_dana_darurat_id',
     'Peggy Dana Darurat & 50-30-20 Retrieval (Indonesian)', 'peggy_financial_encyclopedia_ref',
     'Berapa besaran dana darurat dan pembagian metode anggaran 50-30-20?',
     'HYBRID', TRUE, '["MEMBER"]', '{"locale":"id"}',
     'peggy_glossary_id', 'Dana Darurat', NULL, 3, TRUE),

    ('00000000-0000-0000-0010-000000000003', 'eval_worker_damaged_goods_id',
     'Worker SOP Damaged Goods Quarantine (Indonesian + Page Citation)', 'ops_sop_handbook_ref',
     'Apa prosedur jika ditemukan barang rusak atau kemasan penyok saat penerimaan gudang?',
     'HYBRID', TRUE, '["WORKER"]', '{}',
     'worker_sop_receiving', 'Barang Rusak', 'owner_margin_payroll_policy', 3, TRUE),

    ('00000000-0000-0000-0010-000000000004', 'eval_worker_coldchain_temp_en',
     'Worker Cold-Chain Temperature SOP (English)', 'ops_sop_handbook_ref',
     'What temperature range is required for chilled delivery boxes?',
     'HYBRID', TRUE, '["WORKER"]', '{}',
     'worker_sop_coldchain', 'Chilled', 'owner_margin_payroll_policy', 3, TRUE),

    ('00000000-0000-0000-0010-000000000005', 'eval_worker_blocked_from_owner_policy',
     'Permission Guard: Worker Blocked From Owner Margin Policy', 'ops_sop_handbook_ref',
     'What is the executive gross margin supplier rebate percentage and payroll bonus?',
     'HYBRID', TRUE, '["WORKER"]', '{}',
     NULL, NULL, 'owner_margin_payroll_policy', 3, TRUE),

    ('00000000-0000-0000-0010-000000000006', 'eval_owner_authorized_margin_policy',
     'Permission Guard: Owner Authorized to Retrieve Margin Policy', 'ops_sop_handbook_ref',
     'What is the executive gross margin supplier rebate percentage?',
     'HYBRID', TRUE, '["OWNER"]', '{}',
     'owner_margin_payroll_policy', 'Gross Margin', NULL, 3, TRUE);
