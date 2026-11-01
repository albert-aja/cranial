-- ============================================================================
-- CRANIAL Section 20.9: Indonesian Slang & Colloquial Spelling Normalization
-- Normalizes informal customer support chats (e.g. batalin, balikin duit,
-- lemot bgt, gak bisa login, tf ke rek, lemooottt) into canonical Bahasa
-- Indonesia while strictly enforcing non-banking money management guardrails.
-- ============================================================================

-- 1. Extend Customer Support Tickets with Slang Normalization Metadata
ALTER TABLE ai_customer_tickets
    ADD COLUMN IF NOT EXISTS normalized_message TEXT;

ALTER TABLE ai_customer_tickets
    ADD COLUMN IF NOT EXISTS slang_normalized BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ai_customer_tickets
    ADD COLUMN IF NOT EXISTS slang_replacements_json TEXT;

UPDATE ai_customer_tickets
SET normalized_message = redacted_message
WHERE normalized_message IS NULL;

-- 2. Extend Customer Reviews with Normalized Text Metadata
ALTER TABLE ai_customer_reviews
    ADD COLUMN IF NOT EXISTS normalized_review TEXT;

ALTER TABLE ai_customer_reviews
    ADD COLUMN IF NOT EXISTS slang_normalized BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE ai_customer_reviews
SET normalized_review = redacted_review
WHERE normalized_review IS NULL;

-- 3. Operator-Governed Colloquial & Slang Lexicon Registry
CREATE TABLE IF NOT EXISTS ai_customer_slang_lexicon (
    id                          UUID PRIMARY KEY,
    slang_term                  VARCHAR(128) NOT NULL UNIQUE,
    canonical_form              VARCHAR(255) NOT NULL,
    category                    VARCHAR(64) NOT NULL CHECK (category IN (
                                    'ACTION_COLLOQUIALISM',
                                    'BANKING_REFUND_SLANG',
                                    'PERFORMANCE_SLANG',
                                    'AUTH_ACCESS_SLANG',
                                    'CHAT_ABBREVIATION',
                                    'NEGATION_SHORTHAND',
                                    'FINANCIAL_SLANG',
                                    'ESCALATION_SLANG',
                                    'OCR_SUPPORT_SLANG'
                                )),
    locale                      VARCHAR(16) NOT NULL DEFAULT 'id',
    is_banking_boundary_hint    BOOLEAN NOT NULL DEFAULT FALSE,
    explanation                 VARCHAR(500),
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_slang_category ON ai_customer_slang_lexicon(category);
CREATE INDEX IF NOT EXISTS idx_customer_slang_active ON ai_customer_slang_lexicon(is_active);

-- 4. Seed Governed Domain Colloquialisms for Customer Support Chats
INSERT INTO ai_customer_slang_lexicon (
    id, slang_term, canonical_form, category, locale, is_banking_boundary_hint, explanation, is_active
) VALUES
('00000000-0000-0000-0014-000000000001', 'balikin duit', 'kembalikan uang', 'BANKING_REFUND_SLANG', 'id', TRUE,
 'Colloquial demand for cash refund; triggers non-banking money management Promise Guard verification', TRUE),
('00000000-0000-0000-0014-000000000002', 'batalin', 'batalkan', 'ACTION_COLLOQUIALISM', 'id', FALSE,
 'Informal -in suffix verb for cancelling a subscription, record, or ledger entry', TRUE),
('00000000-0000-0000-0014-000000000003', 'lemot bgt', 'lambat sekali', 'PERFORMANCE_SLANG', 'id', FALSE,
 'Colloquial complaint about severe app sluggishness or sync latency', TRUE),
('00000000-0000-0000-0014-000000000004', 'gak bisa login', 'tidak bisa login', 'AUTH_ACCESS_SLANG', 'id', FALSE,
 'Colloquial authentication issue phrasing mapped to account security support queue', TRUE),
('00000000-0000-0000-0014-000000000005', 'tf ke rek', 'transfer saldo ke rekening', 'BANKING_REFUND_SLANG', 'id', TRUE,
 'Abbreviated bank transfer demand; intercepted by non-banking money management boundary', TRUE),
('00000000-0000-0000-0014-000000000006', 'cairin saldo', 'tarik tunai otomatis', 'BANKING_REFUND_SLANG', 'id', TRUE,
 'Colloquial cash disbursement request; blocked because platform only tracks budgets and ledgers', TRUE),
('00000000-0000-0000-0014-000000000007', 'struk ga kebaca', 'struk rusak error', 'OCR_SUPPORT_SLANG', 'id', FALSE,
 'Informal receipt OCR failure report routed to expense tracking / technical support', TRUE),
('00000000-0000-0000-0014-000000000008', 'sambungin ke cs', 'bicara dengan agen manusia', 'ESCALATION_SLANG', 'id', FALSE,
 'Informal request to speak directly with a human customer support operator', TRUE);

-- 5. Seed Colloquial Indonesian Evaluation Cases into Benchmark Repository
INSERT INTO ai_customer_eval_cases (
    id, suite_code, locale, input_text, expected_category, expected_sentiment, expected_escalation, expected_promise_violation, notes
) VALUES
('00000000-0000-0000-0010-000000000011', 'PROMISE_GUARD_SAFETY', 'id',
 'Parah bgt min, batalin transaksi trs janji balikin duit gw 100% dan tf ke rek BCA gw skrg jg!',
 'PROMISE_POLICY_RESTRICTION', 'NEGATIVE', TRUE, TRUE,
 '[ID-SLANG] Colloquial refund & bank transfer demand caught after slang normalization'),

('00000000-0000-0000-0010-000000000012', 'SENTIMENT_BENCHMARK', 'id',
 'Kesel bgt aplikasinya lemooottt bgt parah pas buka catatan pengeluaran bulanan!',
 'EXPENSE_TRACKING_ISSUE', 'NEGATIVE', TRUE, FALSE,
 '[ID-SLANG] Elongated & abbreviated performance frustration (lemooottt bgt -> lambat sekali)'),

('00000000-0000-0000-0010-000000000013', 'TICKET_CLASSIFICATION', 'id',
 'Min tolongin dong gw gak bisa login dr pagi padahal pw udh bener.',
 'ACCOUNT_AND_SECURITY', 'NEUTRAL', FALSE, FALSE,
 '[ID-SLANG] Colloquial login trouble (gak bisa login, pw udh bener -> tidak bisa login, password sudah benar)'),

('00000000-0000-0000-0010-000000000014', 'ESCALATION_TRIGGER', 'id',
 'Batalin aja catetannya error mulu, sambungin ke cs skrg jg jgn dijawab bot!',
 'BUG_REPORT', 'NEGATIVE', TRUE, FALSE,
 '[ID-SLANG] Colloquial human agent handoff demand with bug complaint');

