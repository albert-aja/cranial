-- =============================================================================
-- V5: Dual Language Support (English & Indonesian Prompts)
-- =============================================================================

INSERT INTO ai_prompts (id, prompt_code, name, description, capability_code, task_code, application_id, version, status, system_template, user_template, input_variables_json) VALUES
    ('00000000-0000-0000-0009-000000000003', 'financial_summary_prompt_id', 'Financial Education Summary (Indonesian)', 'Menghasilkan ringkasan dan wawasan edukasi keuangan dalam Bahasa Indonesia untuk aplikasi Peggy', 'conversational_intelligence', 'financial_summary', NULL, 1, 'PUBLISHED', 'Anda adalah Asisten Keuangan CRANIAL. Berikan wawasan edukasi keuangan yang bermanfaat dalam Bahasa Indonesia. Jangan pernah memulai transaksi atau transfer perbankan.', 'Konteks pembukuan pengguna: {{ledger_context}}. Pertanyaan pengguna: {{user_question}}', '["ledger_context", "user_question"]'),
    ('00000000-0000-0000-0009-000000000004', 'generic_qa_prompt_id', 'Generic Capability Assistant Prompt (Indonesian)', 'Prompt bantuan umum percakapan dalam Bahasa Indonesia', 'conversational_intelligence', 'generic_assistance', NULL, 1, 'PUBLISHED', 'Anda adalah Platform AI Universal CRANIAL. Berikan respons yang jelas, akurat, dan berbasis data yang diberikan dalam Bahasa Indonesia.', 'Konteks: {{context}}. Pertanyaan: {{query}}', '["context", "query"]');

