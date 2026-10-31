-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- V6: Document Intelligence & OCR (Section 20.4 & Phase 5)
-- =============================================================================

-- 1. Documents Registry (Intake, Duplicate Detection, Classification & Retention)
CREATE TABLE ai_documents (
    id                      UUID PRIMARY KEY,
    application_id          UUID             REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID             REFERENCES tenants(id) ON DELETE SET NULL,
    external_user_ref       VARCHAR(120),
    file_name               VARCHAR(255)     NOT NULL,
    mime_type               VARCHAR(120)     NOT NULL,
    detected_mime_type      VARCHAR(120)     NOT NULL,
    file_size_bytes         BIGINT           NOT NULL CHECK (file_size_bytes >= 0),
    sha256_hash             VARCHAR(64)      NOT NULL,
    normalized_text_hash    VARCHAR(64),
    storage_key             VARCHAR(255),
    page_count              INTEGER          NOT NULL DEFAULT 1 CHECK (page_count >= 0),
    document_type           VARCHAR(64)      NOT NULL DEFAULT 'UNKNOWN',
    classification_confidence DOUBLE PRECISION,
    ocr_language            VARCHAR(32)      NOT NULL DEFAULT 'eng+ind',
    is_duplicate            BOOLEAN          NOT NULL DEFAULT FALSE,
    duplicate_of_document_id UUID            REFERENCES ai_documents(id) ON DELETE SET NULL,
    duplicate_type          VARCHAR(32),
    status                  VARCHAR(32)      NOT NULL CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED', 'PROCESSING', 'PROCESSED', 'PARTIAL_SUCCESS', 'FAILED', 'EXPIRED', 'DELETED')),
    retention_until         TIMESTAMPTZ,
    deleted_at              TIMESTAMPTZ,
    metadata_json           JSONB            NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_app_created ON ai_documents(application_id, created_at DESC);
CREATE INDEX idx_documents_app_sha256 ON ai_documents(application_id, sha256_hash);
CREATE INDEX idx_documents_app_text_hash ON ai_documents(application_id, normalized_text_hash);
CREATE INDEX idx_documents_retention ON ai_documents(status, retention_until);

-- 2. Document Pages (Page-Level OCR Text, Confidence, Preprocessing & Diagnostics)
CREATE TABLE ai_document_pages (
    id                      UUID PRIMARY KEY,
    document_id             UUID             NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    page_number             INTEGER          NOT NULL CHECK (page_number >= 1),
    status                  VARCHAR(32)      NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'SKIPPED')),
    engine_used             VARCHAR(64)      NOT NULL,
    ocr_confidence          DOUBLE PRECISION,
    extracted_text          TEXT,
    char_count              INTEGER          NOT NULL DEFAULT 0,
    word_count              INTEGER          NOT NULL DEFAULT 0,
    preprocess_applied_json JSONB            NOT NULL DEFAULT '[]',
    diagnostics_json        JSONB            NOT NULL DEFAULT '{}',
    duration_ms             BIGINT           NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_page_num UNIQUE (document_id, page_number)
);

CREATE INDEX idx_doc_pages_doc_id ON ai_document_pages(document_id, page_number);

-- 3. Configurable Extraction Schemas per Application & Document Type
CREATE TABLE ai_document_extraction_configs (
    id                          UUID PRIMARY KEY,
    config_code                 VARCHAR(100)     NOT NULL UNIQUE,
    application_id              UUID             REFERENCES applications(id) ON DELETE CASCADE,
    document_type               VARCHAR(64)      NOT NULL,
    schema_id                   UUID             NOT NULL REFERENCES ai_schemas(id),
    default_ocr_language        VARCHAR(32)      NOT NULL DEFAULT 'eng+ind',
    confidence_threshold        DOUBLE PRECISION NOT NULL DEFAULT 0.75,
    auto_review_low_confidence  BOOLEAN          NOT NULL DEFAULT TRUE,
    default_retention_hours     INTEGER          NOT NULL DEFAULT 168 CHECK (default_retention_hours > 0),
    preprocess_defaults_json    JSONB            NOT NULL DEFAULT '{"deskew":true,"denoise":true,"contrast_enhance":true}',
    is_active                   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_configs_app_type ON ai_document_extraction_configs(application_id, document_type, is_active);

-- 4. Structured Extractions, Provenance, Tables/Line Items & Human Review Queue
CREATE TABLE ai_document_extractions (
    id                          UUID PRIMARY KEY,
    document_id                 UUID             NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    job_id                      UUID,
    schema_id                   UUID             REFERENCES ai_schemas(id),
    schema_code                 VARCHAR(80),
    document_type               VARCHAR(64)      NOT NULL,
    overall_confidence          DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    schema_valid                BOOLEAN          NOT NULL DEFAULT FALSE,
    validation_errors_json      JSONB            NOT NULL DEFAULT '[]',
    extracted_fields_json       JSONB            NOT NULL DEFAULT '{}',
    field_details_json          JSONB            NOT NULL DEFAULT '{}',
    tables_json                 JSONB            NOT NULL DEFAULT '[]',
    line_items_json             JSONB            NOT NULL DEFAULT '[]',
    warnings_json               JSONB            NOT NULL DEFAULT '[]',
    review_status               VARCHAR(32)      NOT NULL CHECK (review_status IN ('NOT_REQUIRED', 'PENDING_REVIEW', 'APPROVED', 'CORRECTED', 'REJECTED')),
    review_reasons_json         JSONB            NOT NULL DEFAULT '[]',
    reviewed_fields_json        JSONB,
    reviewer_actor              VARCHAR(120),
    reviewer_notes              VARCHAR(1000),
    reviewed_at                 TIMESTAMPTZ,
    promoted_to_ground_truth    BOOLEAN          NOT NULL DEFAULT FALSE,
    duration_ms                 BIGINT           NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_extractions_doc ON ai_document_extractions(document_id, created_at DESC);
CREATE INDEX idx_doc_extractions_review ON ai_document_extractions(review_status, created_at DESC);

-- 5. Durable & Resumable Document Processing Jobs
CREATE TABLE ai_document_jobs (
    id                      UUID PRIMARY KEY,
    document_id             UUID         NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    application_id          UUID         REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id               UUID,
    correlation_id          VARCHAR(80)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED')),
    page_range              VARCHAR(64),
    total_pages             INTEGER      NOT NULL DEFAULT 0,
    processed_pages         INTEGER      NOT NULL DEFAULT 0,
    failed_pages            INTEGER      NOT NULL DEFAULT 0,
    last_completed_page     INTEGER      NOT NULL DEFAULT 0,
    attempt_count           INTEGER      NOT NULL DEFAULT 0,
    max_attempts            INTEGER      NOT NULL DEFAULT 3,
    options_json            JSONB        NOT NULL DEFAULT '{}',
    error_message           VARCHAR(500),
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_jobs_app_status ON ai_document_jobs(application_id, status, created_at DESC);
CREATE INDEX idx_doc_jobs_doc ON ai_document_jobs(document_id);

-- 6. Scoped Expiring Temporary Upload & Download URL Tokens
CREATE TABLE ai_document_transfer_tokens (
    id                  UUID PRIMARY KEY,
    token_nonce         VARCHAR(64)  NOT NULL UNIQUE,
    operation           VARCHAR(16)  NOT NULL CHECK (operation IN ('UPLOAD', 'DOWNLOAD')),
    application_id      UUID         NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id           UUID,
    document_id         UUID         REFERENCES ai_documents(id) ON DELETE CASCADE,
    file_name           VARCHAR(255),
    allowed_mime_type   VARCHAR(120),
    max_bytes           BIGINT       NOT NULL DEFAULT 10485760,
    used_count          INTEGER      NOT NULL DEFAULT 0,
    max_uses            INTEGER      NOT NULL DEFAULT 1,
    expires_at          TIMESTAMPTZ  NOT NULL,
    consumed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transfer_tokens_expiry ON ai_document_transfer_tokens(expires_at);

-- 7. Fixed OCR & Extraction Regression Dataset (Synthetic, Zero Real PII)
CREATE TABLE ai_document_eval_cases (
    id                      UUID PRIMARY KEY,
    case_code               VARCHAR(80)  NOT NULL UNIQUE,
    name                    VARCHAR(150) NOT NULL,
    document_type           VARCHAR(64)  NOT NULL,
    ocr_language            VARCHAR(32)  NOT NULL DEFAULT 'eng+ind',
    sample_text             TEXT         NOT NULL,
    expected_fields_json    JSONB        NOT NULL DEFAULT '{}',
    expected_line_item_count INTEGER     NOT NULL DEFAULT 0,
    source_type             VARCHAR(32)  NOT NULL CHECK (source_type IN ('SEEDED_BENCHMARK', 'PROMOTED_GROUND_TRUTH')),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Seed Data: Document Extraction Schemas, Configs & Regression Benchmarks
-- =============================================================================

INSERT INTO ai_schemas (id, schema_code, name, description, schema_type, version, schema_definition_json, is_strict, status) VALUES
    ('00000000-0000-0000-0008-000000000010', 'receipt_extraction_v1', 'Receipt Expense Extraction Schema', 'Structured expense receipt extraction for money management apps (Peggy) — read-only preview, never payment execution', 'RESPONSE_EXTRACTION', 1, '{"type": "object", "properties": {"merchant_name": {"type": "string"}, "transaction_date": {"type": "string"}, "currency": {"type": "string"}, "subtotal_amount": {"type": "number"}, "tax_amount": {"type": "number"}, "total_amount": {"type": "number"}, "receipt_number": {"type": "string"}}, "required": ["merchant_name", "total_amount"]}', FALSE, 'ACTIVE'),
    ('00000000-0000-0000-0008-000000000011', 'invoice_extraction_v1', 'Supplier & Purchase Invoice Schema', 'Structured invoice extraction for SMS inventory and e-commerce procurement', 'RESPONSE_EXTRACTION', 1, '{"type": "object", "properties": {"vendor_name": {"type": "string"}, "invoice_number": {"type": "string"}, "invoice_date": {"type": "string"}, "due_date": {"type": "string"}, "currency": {"type": "string"}, "subtotal_amount": {"type": "number"}, "tax_amount": {"type": "number"}, "total_amount": {"type": "number"}}, "required": ["vendor_name", "invoice_number", "total_amount"]}', FALSE, 'ACTIVE'),
    ('00000000-0000-0000-0008-000000000012', 'delivery_note_extraction_v1', 'Delivery Note / Surat Jalan Schema', 'Proof-of-delivery and shipment manifest extraction for Worker App', 'RESPONSE_EXTRACTION', 1, '{"type": "object", "properties": {"document_number": {"type": "string"}, "sender_name": {"type": "string"}, "recipient_name": {"type": "string"}, "delivery_date": {"type": "string"}, "total_quantity": {"type": "number"}}, "required": ["document_number", "recipient_name"]}', FALSE, 'ACTIVE'),
    ('00000000-0000-0000-0008-000000000013', 'form_extraction_v1', 'Structured Operational Form Schema', 'Key-value form field extraction for operational intake', 'RESPONSE_EXTRACTION', 1, '{"type": "object", "properties": {"form_title": {"type": "string"}, "applicant_name": {"type": "string"}, "reference_number": {"type": "string"}, "submission_date": {"type": "string"}}, "required": ["form_title", "reference_number"]}', FALSE, 'ACTIVE');

INSERT INTO ai_document_extraction_configs (id, config_code, application_id, document_type, schema_id, default_ocr_language, confidence_threshold, auto_review_low_confidence, default_retention_hours, preprocess_defaults_json, is_active) VALUES
    ('00000000-0000-0000-000c-000000000001', 'default_receipt_config', NULL, 'RECEIPT', '00000000-0000-0000-0008-000000000010', 'eng+ind', 0.75, TRUE, 168, '{"deskew":true,"denoise":true,"contrast_enhance":true}', TRUE),
    ('00000000-0000-0000-000c-000000000002', 'default_invoice_config', NULL, 'INVOICE', '00000000-0000-0000-0008-000000000011', 'eng+ind', 0.75, TRUE, 336, '{"deskew":true,"denoise":true,"contrast_enhance":true}', TRUE),
    ('00000000-0000-0000-000c-000000000003', 'default_delivery_note_config', NULL, 'DELIVERY_NOTE', '00000000-0000-0000-0008-000000000012', 'eng+ind', 0.75, TRUE, 168, '{"deskew":true,"denoise":true,"contrast_enhance":true}', TRUE),
    ('00000000-0000-0000-000c-000000000004', 'default_form_config', NULL, 'FORM', '00000000-0000-0000-0008-000000000013', 'eng+ind', 0.70, TRUE, 168, '{"deskew":true,"denoise":true,"contrast_enhance":true}', TRUE);

INSERT INTO ai_document_eval_cases (id, case_code, name, document_type, ocr_language, sample_text, expected_fields_json, expected_line_item_count, source_type, is_active) VALUES
    ('00000000-0000-0000-000d-000000000001', 'bench_receipt_id_minimarket', 'Peggy Expense Receipt (Indonesian)', 'RECEIPT', 'ind',
     'TOKO NUSANTARA MART
No Struk: STR-2026-0891
Tanggal: 2026-10-28
1. Beras Ramos 5kg | 1 | 72000 | 72000
2. Minyak Goreng 2L | 2 | 34000 | 68000
Subtotal: Rp 140.000
PPN: Rp 15.400
Total: Rp 155.400
Tunai Diterima: Rp 160.000',
     '{"merchant_name":"TOKO NUSANTARA MART","receipt_number":"STR-2026-0891","transaction_date":"2026-10-28","currency":"IDR","subtotal_amount":140000,"tax_amount":15400,"total_amount":155400}',
     2, 'SEEDED_BENCHMARK', TRUE),

    ('00000000-0000-0000-000d-000000000002', 'bench_receipt_en_cafe', 'Peggy Expense Receipt (English)', 'RECEIPT', 'eng',
     'HARBOR COFFEE ROASTERS
Receipt #: RCP-44210
Date: 2026-10-29
1. Oat Latte | 2 | 5.50 | 11.00
2. Almond Croissant | 1 | 4.25 | 4.25
Subtotal: USD 15.25
Tax: USD 1.22
Total: USD 16.47',
     '{"merchant_name":"HARBOR COFFEE ROASTERS","receipt_number":"RCP-44210","transaction_date":"2026-10-29","currency":"USD","subtotal_amount":15.25,"tax_amount":1.22,"total_amount":16.47}',
     2, 'SEEDED_BENCHMARK', TRUE),

    ('00000000-0000-0000-000d-000000000003', 'bench_invoice_id_supplier', 'SMS Supplier Purchase Invoice (Indonesian)', 'INVOICE', 'eng+ind',
     'FAKTUR PEMBELIAN / INVOICE
Vendor: PT SUMBER LOGISTIK PRIMA
Invoice Number: INV-2026-7731
Invoice Date: 2026-10-25
Due Date: 2026-11-24
1. Kardus Kemasan XL | 100 | 4500 | 450000
2. Lakban Bening Roll | 50 | 9000 | 450000
Subtotal: Rp 900.000
Tax: Rp 99.000
Total: Rp 999.000',
     '{"vendor_name":"PT SUMBER LOGISTIK PRIMA","invoice_number":"INV-2026-7731","invoice_date":"2026-10-25","due_date":"2026-11-24","currency":"IDR","subtotal_amount":900000,"tax_amount":99000,"total_amount":999000}',
     2, 'SEEDED_BENCHMARK', TRUE),

    ('00000000-0000-0000-000d-000000000004', 'bench_delivery_worker_pod', 'Worker Proof-of-Delivery / Surat Jalan', 'DELIVERY_NOTE', 'eng+ind',
     'SURAT JALAN / DELIVERY NOTE
Document Number: SJ-2026-3319
Sender: Gudang Pusat Cikarang
Recipient: Toko Cabang Bandung Utara
Delivery Date: 2026-10-30
1. Paket Elektronik A1 | 12 | 0 | 0
2. Paket Aksesoris B4 | 8 | 0 | 0
Total Quantity: 20',
     '{"document_number":"SJ-2026-3319","sender_name":"Gudang Pusat Cikarang","recipient_name":"Toko Cabang Bandung Utara","delivery_date":"2026-10-30","total_quantity":20}',
     2, 'SEEDED_BENCHMARK', TRUE),

    ('00000000-0000-0000-000d-000000000005', 'bench_form_vendor_intake', 'Operational Inspection Intake Form', 'FORM', 'eng',
     'APPLICATION FORM: Equipment Safety Inspection Form
Reference Number: FRM-2026-508
Applicant Name: Budi Santoso
Submission Date: 2026-10-31',
     '{"form_title":"Equipment Safety Inspection Form","reference_number":"FRM-2026-508","applicant_name":"Budi Santoso","submission_date":"2026-10-31"}',
     0, 'SEEDED_BENCHMARK', TRUE);

