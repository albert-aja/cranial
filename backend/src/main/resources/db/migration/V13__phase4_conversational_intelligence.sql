-- V13__phase4_conversational_intelligence.sql
-- Conversational Intelligence Domain: multi-turn sessions, context management, feedback, and retention policies.

CREATE TABLE IF NOT EXISTS ai_conversations (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    locale VARCHAR(32) NOT NULL DEFAULT 'en',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    context_summary TEXT,
    total_turn_count INT NOT NULL DEFAULT 0,
    total_tokens_used INT NOT NULL DEFAULT 0,
    metadata TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_conversations_app_tenant ON ai_conversations (application_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user ON ai_conversations (user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_status ON ai_conversations (status);
CREATE INDEX IF NOT EXISTS idx_conversations_created ON ai_conversations (created_at DESC);

CREATE TABLE IF NOT EXISTS ai_conversation_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    raw_content TEXT,
    token_count INT NOT NULL DEFAULT 0,
    structured_blocks TEXT NOT NULL,
    source_references TEXT NOT NULL,
    tools_invoked TEXT NOT NULL,
    model_used VARCHAR(128),
    latency_ms BIGINT,
    is_safety_violation BOOLEAN NOT NULL DEFAULT FALSE,
    safety_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conv_messages_conv ON ai_conversation_messages (conversation_id, created_at ASC);

CREATE TABLE IF NOT EXISTS ai_conversation_feedbacks (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    message_id UUID REFERENCES ai_conversation_messages(id) ON DELETE CASCADE,
    rating INT,
    is_helpful BOOLEAN,
    is_correct BOOLEAN,
    feedback_category VARCHAR(64),
    comment TEXT,
    operator_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conv_feedback_conv ON ai_conversation_feedbacks (conversation_id);

CREATE TABLE IF NOT EXISTS ai_conversation_retention_policies (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL UNIQUE REFERENCES applications(id) ON DELETE CASCADE,
    retention_days INT NOT NULL DEFAULT 90,
    auto_prune_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    export_on_prune BOOLEAN NOT NULL DEFAULT FALSE,
    default_locale VARCHAR(32) NOT NULL DEFAULT 'en',
    enforce_safety_guard BOOLEAN NOT NULL DEFAULT TRUE,
    max_context_turns INT NOT NULL DEFAULT 20,
    max_context_tokens INT NOT NULL DEFAULT 4096,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed Retention Policies for 4 Registered Applications
INSERT INTO ai_conversation_retention_policies (
    id, application_id, retention_days, auto_prune_enabled, export_on_prune, default_locale, enforce_safety_guard, max_context_turns, max_context_tokens
) VALUES
    ('00000000-0000-0000-000d-000000000001', '00000000-0000-0000-0004-000000000001', 90, TRUE, FALSE, 'id', TRUE, 20, 4096), -- SMS Owner
    ('00000000-0000-0000-000d-000000000002', '00000000-0000-0000-0004-000000000002', 60, TRUE, FALSE, 'id', TRUE, 15, 3072), -- Worker App
    ('00000000-0000-0000-000d-000000000003', '00000000-0000-0000-0004-000000000003', 180, TRUE, TRUE, 'id', TRUE, 30, 8192), -- Peggy Money Management
    ('00000000-0000-0000-000d-000000000004', '00000000-0000-0000-0004-000000000004', 90, TRUE, FALSE, 'en', TRUE, 20, 4096); -- E-Commerce

-- Seed Demo Conversations with rich structured blocks and safety guard compliance
INSERT INTO ai_conversations (
    id, application_id, tenant_id, user_id, title, locale, status, context_summary, total_turn_count, total_tokens_used, metadata
) VALUES
    ('00000000-0000-0000-000e-000000000001', '00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0006-000000000003', 'usr_peggy_101', 'Analisis Anggaran Bulanan & Alokasi 50/30/20', 'id', 'ACTIVE', 'Pengguna berkonsultasi mengenai pengeluaran bulanan gaji Rp 8.000.000 dengan metode 50/30/20.', 2, 420, '{"client_app": "peggy", "channel": "mobile"}'),
    ('00000000-0000-0000-000e-000000000002', '00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0006-000000000001', 'usr_sms_202', 'Ringkasan Stok & Penjualan Toko Retail', 'id', 'ACTIVE', 'Pemilik toko memeriksa ringkasan penjualan mingguan dan produk dengan perputaran stok lambat.', 2, 510, '{"client_app": "sms_owner", "channel": "web"}'),
    ('00000000-0000-0000-000e-000000000003', '00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0006-000000000003', 'usr_peggy_103', 'Percobaan Transfer Dana (Ditolak Kebijakan Non-Banking)', 'id', 'ACTIVE', 'Pengguna meminta CRANIAL mentransfer uang ke rekening BCA. Sistem menolak dan memberikan edukasi pencatatan keuangan.', 2, 280, '{"client_app": "peggy", "safety_flag": true}');

-- Seed Messages for Conversation 1 (Peggy Budget)
INSERT INTO ai_conversation_messages (
    id, conversation_id, role, content, raw_content, token_count, structured_blocks, source_references, tools_invoked, model_used, latency_ms, is_safety_violation, safety_reason
) VALUES
    ('00000000-0000-0000-000f-000000000001', '00000000-0000-0000-000e-000000000001', 'USER', 'Halo Peggy, gaji saya 8 juta sebulan. Pengeluaran makan gocap sehari, kosan 2 juta sebulan. Tolong bantu buat pembagian anggarannya.', 'Halo Peggy, gaji saya 8jt sebulan. Pengeluaran makan gocap sehari, kosan 2jt sebulan. Tolong bantu buat pembagian anggarannya.', 45, '[]', '[]', '[]', NULL, NULL, FALSE, NULL),
    ('00000000-0000-0000-000f-000000000002', '00000000-0000-0000-000e-000000000001', 'ASSISTANT', 'Tentu! Berdasarkan penghasilan Rp 8.000.000 per bulan dan prinsip anggaran 50/30/20, berikut adalah alokasi pengeluaran yang direkomendasikan:', 'Tentu! Berdasarkan penghasilan Rp 8.000.000 per bulan...', 375, '[{"type": "TEXT", "content": "Berdasarkan prinsip alokasi 50/30/20 untuk penghasilan bulanan Rp 8.000.000:"}, {"type": "TABLE", "title": "Rencana Alokasi Anggaran Bulanan", "headers": ["Kategori", "Persentase", "Plafon Anggaran", "Realisasi Saat Ini"], "rows": [["Kebutuhan Pokok (Kosan + Makan)", "50%", "Rp 4.000.000", "Rp 3.500.000 (Aman)"], ["Keinginan / Gaya Hidup", "30%", "Rp 2.400.000", "Rp 1.200.000 (Tersisa Rp 1.200.000)"], ["Tabungan & Dana Darurat", "20%", "Rp 1.600.000", "Rp 1.600.000 (Target)"]]}, {"type": "ACTION_CARD", "title": "Rekomendasi Pencatatan", "description": "Makan harian Rp 50.000 (gocap) selama 30 hari = Rp 1.500.000. Ditambah kosan Rp 2.000.000, total kebutuhan primer Anda Rp 3.500.000 (43,75% dari penghasilan). Anda memiliki sisa surplus Rp 500.000 di pos kebutuhan pokok.", "actionLabel": "Catat Anggaran Baru"}]', '[{"sourceId": "kb_budget_principles_id", "title": "Prinsip Edukasi Anggaran 50/30/20", "uri": "/knowledge/budget-rule-50-30-20", "snippet": "Metode 50/30/20 mengalokasikan 50% untuk kebutuhan primer, 30% keinginan, dan 20% tabungan.", "relevanceScore": 0.94}]', '["financial_analytics_budget_calculator"]', 'qwen2.5-coder:7b', 420, FALSE, NULL);

-- Seed Messages for Conversation 3 (Banking Safety Guard Interception)
INSERT INTO ai_conversation_messages (
    id, conversation_id, role, content, raw_content, token_count, structured_blocks, source_references, tools_invoked, model_used, latency_ms, is_safety_violation, safety_reason
) VALUES
    ('00000000-0000-0000-000f-000000000005', '00000000-0000-0000-000e-000000000003', 'USER', 'Tolong transfer uang Rp 500.000 ke rekening BCA 1234567890 sekarang untuk bayar listrik.', 'Tolong transfer uang Rp 500.000 ke rekening BCA 1234567890 sekarang untuk bayar listrik.', 25, '[]', '[]', '[]', NULL, NULL, FALSE, NULL),
    ('00000000-0000-0000-000f-000000000006', '00000000-0000-0000-000e-000000000003', 'ASSISTANT', 'Permintaan eksekusi transaksi perbankan ditolak. CRANIAL dan Peggy adalah platform manajemen keuangan dan edukasi pencatatan anggaran, bukan aplikasi perbankan atau penyedia jasa pembayaran. Kami tidak dapat melakukan transfer dana, penarikan saldo, atau pembayaran tagihan.', 'Permintaan transfer dana ditolak...', 255, '[{"type": "FINANCIAL_DISCLAIMER", "title": "Batas Kebijakan Platform Non-Perbankan", "content": "CRANIAL murni merupakan platform manajemen keuangan pribadi dan analitik pencatatan (non-banking platform). Sistem tidak memiliki integrasi gerbang pembayaran atau otorisasi transfer dana ke bank."}, {"type": "ACTION_CARD", "title": "Pencatatan Pengeluaran Listrik", "description": "Anda dapat mencatat pembayaran listrik Rp 500.000 ini ke dalam pos buku kas Kebutuhan Pokok (Utilities) di Peggy setelah Anda membayarnya melalui saluran perbankan resmi Anda.", "actionLabel": "Buka Buku Kas Peggy"}]', '[]', '[]', 'rule-based-safety-guard', 15, TRUE, 'NON_BANKING_APPLICATION_POLICY');
