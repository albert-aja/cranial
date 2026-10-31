-- =============================================================================
-- CRANIAL — Universal AI Integration Platform
-- Phase 10 Migration: Application Integrations, Sandboxes, Schemas & Readiness
-- =============================================================================

-- 1. Pre-register Official Client Applications (DEV / Sandbox)
INSERT INTO applications (id, code, environment, name, description, status, allowed_origins) VALUES
    ('00000000-0000-0000-0004-000000000001', 'sms', 'DEV', 'Store Management System (SMS)', 'Store operations, sales analytics, inventory insights, and task delegation.', 'ACTIVE', '["http://localhost:3000", "http://localhost:5173"]'),
    ('00000000-0000-0000-0004-000000000002', 'worker_app', 'DEV', 'Worker App', 'Field task assistant, delivery manifest handling, and warehouse SOP knowledge retrieval.', 'ACTIVE', '["http://localhost:3000", "http://localhost:8081"]'),
    ('00000000-0000-0000-0004-000000000003', 'peggy', 'DEV', 'Peggy Money Management', 'Personal and collaborative money management, budgeting, receipt OCR extraction preview, and financial literacy (strictly non-banking).', 'ACTIVE', '["http://localhost:3000", "http://localhost:4200"]'),
    ('00000000-0000-0000-0004-000000000004', 'ecommerce', 'DEV', 'E-Commerce Platform', 'Customer product discovery, FAQ assistance, and order status tracking.', 'ACTIVE', '["http://localhost:3000", "http://localhost:8000"]');

-- 2. Configure Application Settings & Local-First Boundaries
INSERT INTO application_settings (application_id, rate_limit_rpm, concurrency_limit, max_input_bytes, require_signed_context, allow_hosted_providers, allow_sensitive_external, config_json) VALUES
    ('00000000-0000-0000-0004-000000000001', 120, 10, 10485760, TRUE, FALSE, FALSE, '{"app_type": "retail_pos_backoffice"}'),
    ('00000000-0000-0000-0004-000000000002', 60, 5, 5242880, TRUE, FALSE, FALSE, '{"app_type": "mobile_logistics"}'),
    ('00000000-0000-0000-0004-000000000003', 120, 10, 10485760, TRUE, FALSE, FALSE, '{"app_type": "personal_finance", "banking_activities_allowed": false}'),
    ('00000000-0000-0000-0004-000000000004', 180, 15, 10485760, TRUE, FALSE, FALSE, '{"app_type": "online_storefront"}');

-- 3. Seed Application Capability Entitlements
-- SMS
INSERT INTO application_capabilities (id, application_id, capability_id, enabled, quota_daily_requests, config_override) VALUES
    ('00000000-0000-0000-0005-000000000001', '00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0003-000000000001', TRUE, 10000, '{}'), -- conversational_intelligence
    ('00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0003-000000000003', TRUE, 5000, '{}'),  -- data_intelligence
    ('00000000-0000-0000-0005-000000000003', '00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0003-000000000002', TRUE, 3000, '{}'),  -- document_intelligence
    ('00000000-0000-0000-0005-000000000004', '00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0003-000000000005', TRUE, 5000, '{}');  -- knowledge_intelligence

-- Worker App (Strict Boundary: NO data_intelligence financial reporting entitlement)
INSERT INTO application_capabilities (id, application_id, capability_id, enabled, quota_daily_requests, config_override) VALUES
    ('00000000-0000-0000-0005-000000000005', '00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0003-000000000001', TRUE, 5000, '{}'),  -- conversational_intelligence
    ('00000000-0000-0000-0005-000000000006', '00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0003-000000000002', TRUE, 3000, '{}'),  -- document_intelligence
    ('00000000-0000-0000-0005-000000000007', '00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0003-000000000005', TRUE, 5000, '{}');  -- knowledge_intelligence

-- Peggy Money Management
INSERT INTO application_capabilities (id, application_id, capability_id, enabled, quota_daily_requests, config_override) VALUES
    ('00000000-0000-0000-0005-000000000008', '00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0003-000000000001', TRUE, 10000, '{}'), -- conversational_intelligence
    ('00000000-0000-0000-0005-000000000009', '00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0003-000000000003', TRUE, 5000, '{}'),  -- data_intelligence
    ('00000000-0000-0000-0005-000000000010', '00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0003-000000000002', TRUE, 5000, '{}'),  -- document_intelligence
    ('00000000-0000-0000-0005-000000000011', '00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0003-000000000005', TRUE, 10000, '{}'); -- knowledge_intelligence

-- E-Commerce Platform
INSERT INTO application_capabilities (id, application_id, capability_id, enabled, quota_daily_requests, config_override) VALUES
    ('00000000-0000-0000-0005-000000000012', '00000000-0000-0000-0004-000000000004', '00000000-0000-0000-0003-000000000001', TRUE, 10000, '{}'), -- conversational_intelligence
    ('00000000-0000-0000-0005-000000000013', '00000000-0000-0000-0004-000000000004', '00000000-0000-0000-0003-000000000005', TRUE, 10000, '{}'), -- knowledge_intelligence
    ('00000000-0000-0000-0005-000000000014', '00000000-0000-0000-0004-000000000004', '00000000-0000-0000-0003-000000000006', TRUE, 5000, '{}');  -- customer_intelligence

-- 4. Seed Application Sandboxes & Test Tenants
INSERT INTO tenants (id, application_id, external_tenant_ref, name, status, metadata) VALUES
    ('00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0004-000000000001', 'store-jakarta-01', 'SMS Jakarta Flagship Store', 'ACTIVE', '{}'),
    ('00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0004-000000000002', 'store-jakarta-01', 'Worker App Jakarta Hub', 'ACTIVE', '{}'),
    ('00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0004-000000000003', 'peggy-family-budget', 'Peggy Personal & Family Budget', 'ACTIVE', '{}'),
    ('00000000-0000-0000-0006-000000000004', '00000000-0000-0000-0004-000000000004', 'indo-market-online', 'IndoMarket Online Store', 'ACTIVE', '{}');

-- Seed Tenant Memberships
INSERT INTO tenant_memberships (id, tenant_id, external_user_ref, member_role, status) VALUES
    ('00000000-0000-0000-0007-000000000001', '00000000-0000-0000-0006-000000000001', 'owner@store.test', 'STORE_OWNER', 'ACTIVE'),
    ('00000000-0000-0000-0007-000000000002', '00000000-0000-0000-0006-000000000002', 'worker1@store.test', 'WORKER', 'ACTIVE'),
    ('00000000-0000-0000-0007-000000000003', '00000000-0000-0000-0006-000000000003', 'user@peggy.test', 'BUDGET_USER', 'ACTIVE'),
    ('00000000-0000-0000-0007-000000000004', '00000000-0000-0000-0006-000000000004', 'buyer@shop.test', 'CUSTOMER', 'ACTIVE');

-- 5. Seed Schemas for Application Tools (using prefix 0008-000000000020 - 0027 to avoid collision with V6)
INSERT INTO ai_schemas (id, schema_code, name, description, schema_type, version, schema_definition_json, is_strict, status) VALUES
    -- SMS Sales Output
    ('00000000-0000-0000-0008-000000000020', 'sms_daily_sales_output_v1', 'SMS Daily Sales Output Schema', 'Structured response for store daily sales summary', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"date":{"type":"string"},"currency":{"type":"string"},"gross_sales":{"type":"number"},"net_sales":{"type":"number"},"total_transactions":{"type":"number"},"top_selling_category":{"type":"string"}},"required":["date","currency","gross_sales","total_transactions"]}', TRUE, 'ACTIVE'),

    -- SMS Low Stock Output
    ('00000000-0000-0000-0008-000000000021', 'sms_low_stock_output_v1', 'SMS Low Stock Output Schema', 'Structured response for inventory items below reorder threshold', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"warehouse_ref":{"type":"string"},"items":{"type":"array","items":{"type":"object"}},"total_critical_items":{"type":"number"}},"required":["warehouse_ref","items","total_critical_items"]}', TRUE, 'ACTIVE'),

    -- Worker Tasks Output
    ('00000000-0000-0000-0008-000000000022', 'worker_tasks_output_v1', 'Worker Assigned Tasks Output Schema', 'Structured list of assigned operational logistics tasks', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"worker_ref":{"type":"string"},"assigned_date":{"type":"string"},"tasks":{"type":"array","items":{"type":"object"}}},"required":["worker_ref","tasks"]}', TRUE, 'ACTIVE'),

    -- Worker Delivery Manifest Output
    ('00000000-0000-0000-0008-000000000023', 'worker_manifest_output_v1', 'Worker Delivery Manifest Output Schema', 'Structured delivery manifest details', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"delivery_id":{"type":"string"},"recipient_name":{"type":"string"},"package_count":{"type":"number"},"weight_kg":{"type":"number"}},"required":["delivery_id","recipient_name","package_count"]}', TRUE, 'ACTIVE'),

    -- Peggy Budget Breakdown Output (Read-only money management, strictly non-banking)
    ('00000000-0000-0000-0008-000000000024', 'peggy_budget_breakdown_output_v1', 'Peggy Budget Breakdown Output Schema', 'Structured budget allocation comparison (Needs 50%, Wants 30%, Savings 20%)', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"app_context":{"type":"string"},"currency":{"type":"string"},"monthly_net_income":{"type":"number"},"budget_rule":{"type":"string"},"categories":{"type":"array","items":{"type":"object"}}},"required":["currency","monthly_net_income","categories"]}', TRUE, 'ACTIVE'),

    -- Peggy Monthly Spending Output (Read-only money management, strictly non-banking)
    ('00000000-0000-0000-0008-000000000025', 'peggy_monthly_spending_output_v1', 'Peggy Monthly Spending Output Schema', 'Structured categorized spending breakdown', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"period":{"type":"string"},"currency":{"type":"string"},"total_expenses":{"type":"number"},"breakdown":{"type":"array","items":{"type":"object"}}},"required":["period","currency","total_expenses","breakdown"]}', TRUE, 'ACTIVE'),

    -- E-Commerce Product Info Output
    ('00000000-0000-0000-0008-000000000026', 'ecom_product_info_output_v1', 'E-Commerce Product Info Output Schema', 'Structured product specifications and stock status', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"sku":{"type":"string"},"name":{"type":"string"},"currency":{"type":"string"},"price":{"type":"number"},"stock_status":{"type":"string"}},"required":["sku","name","price","stock_status"]}', TRUE, 'ACTIVE'),

    -- E-Commerce Order Status Output
    ('00000000-0000-0000-0008-000000000027', 'ecom_order_status_output_v1', 'E-Commerce Order Status Output Schema', 'Structured customer order fulfillment status and tracking', 'TOOL_OUTPUT', 1,
     '{"type":"object","properties":{"order_id":{"type":"string"},"status":{"type":"string"},"courier":{"type":"string"},"tracking_number":{"type":"string"}},"required":["order_id","status"]}', TRUE, 'ACTIVE');

-- 6. Seed Application-Controlled Safe Read-Only Tools
INSERT INTO ai_tools (id, tool_code, name, description, application_id, input_schema_id, output_schema_id, execution_type, endpoint_url, is_read_only, required_permission, risk_level, timeout_ms, status) VALUES
    -- SMS Tools
    ('00000000-0000-0000-000b-000000000010', 'sms_get_daily_sales', 'Get Store Daily Sales', 'Inquires daily sales revenue, transactions count, and category breakdown for store owners.', '00000000-0000-0000-0004-000000000001', NULL, '00000000-0000-0000-0008-000000000020', 'INTERNAL_MOCK', 'mock://sms/sales', TRUE, 'sms:owner_reports', 'LOW', 4000, 'ACTIVE'),
    ('00000000-0000-0000-000b-000000000011', 'sms_get_low_stock_items', 'Get Low Stock Inventory', 'Retrieves items that have fallen below minimum reorder thresholds.', '00000000-0000-0000-0004-000000000001', NULL, '00000000-0000-0000-0008-000000000021', 'INTERNAL_MOCK', 'mock://sms/inventory', TRUE, 'sms:inventory_read', 'LOW', 4000, 'ACTIVE'),

    -- Worker Tools
    ('00000000-0000-0000-000b-000000000012', 'worker_get_assigned_tasks', 'Get Assigned Logistics Tasks', 'Inquires pending pickup, delivery, and packing tasks assigned to a specific worker.', '00000000-0000-0000-0004-000000000002', NULL, '00000000-0000-0000-0008-000000000022', 'INTERNAL_MOCK', 'mock://worker/tasks', TRUE, 'worker:tasks_read', 'LOW', 3000, 'ACTIVE'),
    ('00000000-0000-0000-000b-000000000013', 'worker_get_delivery_manifest', 'Get Delivery Manifest', 'Retrieves delivery note manifest and destination instructions.', '00000000-0000-0000-0004-000000000002', NULL, '00000000-0000-0000-0008-000000000023', 'INTERNAL_MOCK', 'mock://worker/manifest', TRUE, 'worker:delivery_read', 'LOW', 3000, 'ACTIVE'),

    -- Peggy Money Management Tools (STRICTLY READ-ONLY MONEY MANAGEMENT, NEVER BANKING OR FUND TRANSFERS)
    ('00000000-0000-0000-000b-000000000014', 'peggy_get_budget_breakdown', 'Get Budget Rule Breakdown', 'Inquires 50-30-20 budget allocation status and remaining spending allowances in Peggy. Purely money management, zero banking or payment capability.', '00000000-0000-0000-0004-000000000003', NULL, '00000000-0000-0000-0008-000000000024', 'INTERNAL_MOCK', 'mock://peggy/budget', TRUE, 'peggy:budget_read', 'LOW', 3000, 'ACTIVE'),
    ('00000000-0000-0000-000b-000000000015', 'peggy_get_monthly_spending', 'Get Categorized Monthly Spending', 'Inquires categorized expense summaries for personal spending pattern intelligence. Purely money management, zero banking or payment capability.', '00000000-0000-0000-0004-000000000003', NULL, '00000000-0000-0000-0008-000000000025', 'INTERNAL_MOCK', 'mock://peggy/spending', TRUE, 'peggy:spending_read', 'LOW', 3000, 'ACTIVE'),

    -- E-Commerce Tools
    ('00000000-0000-0000-000b-000000000016', 'ecom_get_product_info', 'Get Product Catalog Information', 'Inquires retail price, stock level, and specifications for online store products.', '00000000-0000-0000-0004-000000000004', NULL, '00000000-0000-0000-0008-000000000026', 'INTERNAL_MOCK', 'mock://ecom/product', TRUE, 'ecom:catalog_read', 'LOW', 3000, 'ACTIVE'),
    ('00000000-0000-0000-000b-000000000017', 'ecom_get_order_status', 'Get Customer Order Status', 'Inquires shipping progress and tracking numbers for customer orders.', '00000000-0000-0000-0004-000000000004', NULL, '00000000-0000-0000-0008-000000000027', 'INTERNAL_MOCK', 'mock://ecom/order', TRUE, 'ecom:order_read', 'LOW', 3000, 'ACTIVE');

-- 7. Seed Task Prompts and Task Definitions
INSERT INTO ai_prompts (id, prompt_code, name, description, capability_code, task_code, application_id, version, status, system_template, user_template, input_variables_json) VALUES
    ('00000000-0000-0000-0009-000000000010', 'sms_business_summary_prompt', 'SMS Business Assistant Prompt', 'Generates store performance insights and inventory summaries for SMS owners', 'conversational_intelligence', 'sms_business_summary', '00000000-0000-0000-0004-000000000001', 1, 'PUBLISHED',
     'You are CRANIAL Assistant for Store Management System (SMS). Analyze store performance using verified reporting data. Never compute unapproved financial values.',
     'Store: {{store_name}}. Owner inquiry: {{inquiry}}. Context data: {{context_data}}', '["store_name", "inquiry"]'),

    ('00000000-0000-0000-0009-000000000011', 'worker_task_assistance_prompt', 'Worker Task Assistant Prompt', 'Guides workers on assigned deliveries and operational SOPs without exposing restricted owner metrics', 'conversational_intelligence', 'worker_task_assistance', '00000000-0000-0000-0004-000000000002', 1, 'PUBLISHED',
     'You are CRANIAL Assistant for Worker App. Help workers navigate assigned logistics tasks and SOPs. You strictly do not have access to financial margins or payroll.',
     'Worker ID: {{worker_id}}. Task query: {{task_query}}', '["worker_id", "task_query"]'),

    ('00000000-0000-0000-0009-000000000012', 'peggy_financial_advisor_prompt', 'Peggy Money Management Advisor Prompt', 'Educational financial coaching on budgeting and spending patterns. Strictly money management, never banking or payment execution.', 'conversational_intelligence', 'peggy_financial_advisor', '00000000-0000-0000-0004-000000000003', 1, 'PUBLISHED',
     'You are CRANIAL Financial Literacy Assistant for Peggy. Explain budgeting rules (50-30-20), spending patterns, and savings goals. IMPORTANT: Peggy is purely a money management app, NOT a banking app. You cannot initiate payments, bank transfers, or funds disbursement.',
     'User Question: {{user_question}}. Budget Context: {{budget_context}}', '["user_question"]'),

    ('00000000-0000-0000-0009-000000000013', 'ecom_customer_support_prompt', 'E-Commerce Customer Support Prompt', 'Answers customer questions about products, shipping status, and store policies', 'conversational_intelligence', 'ecom_customer_support', '00000000-0000-0000-0004-000000000004', 1, 'PUBLISHED',
     'You are CRANIAL Customer Support Assistant for E-Commerce. Provide polite, factual answers using authorized catalog and order tools. Never promise unauthorized refunds.',
     'Customer Question: {{question}}. Order Context: {{order_context}}', '["question"]');

INSERT INTO ai_task_definitions (id, task_code, name, description, capability_code, default_prompt_id, output_schema_id, timeout_seconds, allowed_data_sensitivity, is_active) VALUES
    ('00000000-0000-0000-000a-000000000010', 'sms_business_summary', 'SMS Store Owner Assistant Task', 'Analyzes store performance and inventory for SMS owners', 'conversational_intelligence', '00000000-0000-0000-0009-000000000010', NULL, 45, 'RESTRICTED', TRUE),
    ('00000000-0000-0000-000a-000000000011', 'worker_task_assistance', 'Worker Logistics & SOP Task', 'Assists workers with assigned deliveries and SOP instructions', 'conversational_intelligence', '00000000-0000-0000-0009-000000000011', NULL, 30, 'INTERNAL', TRUE),
    ('00000000-0000-0000-000a-000000000012', 'peggy_financial_advisor', 'Peggy Money Management Advice Task', 'Educational money management coaching (non-banking)', 'conversational_intelligence', '00000000-0000-0000-0009-000000000012', NULL, 45, 'RESTRICTED', TRUE),
    ('00000000-0000-0000-000a-000000000013', 'ecom_customer_support', 'E-Commerce Customer Support Task', 'Assists customers with product information and orders', 'conversational_intelligence', '00000000-0000-0000-0009-000000000013', NULL, 30, 'PUBLIC', TRUE);

-- 8. Seed Feature Flags for Phase 10 Integrations
INSERT INTO platform_feature_flags (id, flag_key, name, description, enabled, rollout_percentage, allowed_app_codes) VALUES
    ('00000000-0000-0000-0002-000000000010', 'integration.sandbox_credentials.enabled', 'Sandbox Test Credentials Enabled', 'Expose sandbox connection parameters and test credentials in non-production environments', TRUE, 100, '[]'),
    ('00000000-0000-0000-0002-000000000011', 'integration.mcp_server.enabled', 'MCP Server Integration Enabled', 'Enable Model Context Protocol (MCP) server integration for client tool access', TRUE, 100, '[]');

