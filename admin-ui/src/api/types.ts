export interface ErrorDetail {
  code: string;
  message: string;
  field?: string | null;
  retryable: boolean;
}

export interface PageMeta {
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface ApiResponse<T> {
  request_id: string;
  correlation_id: string;
  status: 'ok' | 'error';
  data: T;
  pagination?: PageMeta;
  errors: ErrorDetail[];
  timestamp: string;
}

export interface AdminOperator {
  user_id: string;
  email: string;
  display_name: string;
  roles: string[];
  permissions: string[];
}

export interface LoginResponse {
  access_token: string;
  expires_at: string;
  operator: AdminOperator;
}

export interface OverviewSummary {
  platform: string;
  category: string;
  tagline: string;
  environment: string;
  phase: string;
  applications_total: number;
  applications_active: number;
  applications_suspended: number;
  credentials_active: number;
  capabilities_catalog_count: number;
  capability_entitlements_enabled: number;
  tenants_active: number;
  audit_denied_events: number;
  governance: {
    local_first_default: boolean;
    automatic_paid_fallback: boolean;
    hosted_providers_default_enabled: boolean;
  };
  generated_at: string;
}

export interface ApplicationSettingsSummary {
  rate_limit_rpm: number;
  concurrency_limit: number;
  max_input_bytes: number;
  require_signed_context: boolean;
  allow_hosted_providers: boolean;
  allow_sensitive_external: boolean;
  webhook_url?: string | null;
  webhook_secret_configured: boolean;
}

export interface ApplicationView {
  id: string;
  code: string;
  environment: 'DEV' | 'STAGING' | 'PROD';
  name: string;
  description?: string | null;
  status: 'ACTIVE' | 'SUSPENDED' | 'RETIRED';
  allowed_origins: string[];
  settings: ApplicationSettingsSummary;
  created_at: string;
  updated_at: string;
}

export interface CredentialView {
  id: string;
  application_id: string;
  name: string;
  environment: string;
  key_prefix: string;
  status: 'ACTIVE' | 'ROTATING' | 'REVOKED' | 'EXPIRED';
  expires_at?: string | null;
  last_used_at?: string | null;
  revoked_at?: string | null;
  created_at: string;
}

export interface CredentialIssuedResult {
  credential: CredentialView;
  raw_api_key: string;
  raw_signing_secret: string;
  warning: string;
}

export interface CapabilityEntitlementView {
  capability_id: string;
  capability_code: string;
  capability_name: string;
  description: string;
  platform_status: 'AVAILABLE' | 'PLANNED' | 'DISABLED';
  enabled: boolean;
  quota_daily_requests?: number | null;
  updated_at?: string | null;
}

export interface TenantView {
  id: string;
  application_id: string;
  external_tenant_ref: string;
  name: string;
  status: 'ACTIVE' | 'SUSPENDED';
  metadata: Record<string, unknown>;
  created_at: string;
  updated_at: string;
}

export interface TenantMembership {
  id: string;
  tenant_id: string;
  external_user_ref: string;
  member_role: string;
  status: 'ACTIVE' | 'SUSPENDED';
  created_at: string;
  updated_at: string;
}

export interface AuditLogView {
  id: string;
  correlation_id: string;
  actor_type: string;
  actor_id?: string | null;
  application_id?: string | null;
  tenant_id?: string | null;
  action: string;
  target_type: string;
  target_id?: string | null;
  outcome: 'SUCCESS' | 'DENIED' | 'FAILURE';
  metadata: Record<string, unknown>;
  ip_address?: string | null;
  created_at: string;
}

export interface FeatureFlagView {
  id: string;
  flag_key: string;
  name: string;
  description?: string | null;
  enabled: boolean;
  rollout_percentage: number;
  allowed_app_codes: string[];
  updated_at?: string | null;
}

export interface MaintenanceSummary {
  expired_idempotency_purged: number;
  rotating_credentials_expired: number;
  duration_ms: number;
  timestamp: string;
}

export interface ProviderView {
  id: string;
  code: string;
  name: string;
  provider_type: 'LOCAL' | 'HOSTED_FREE' | 'HOSTED_PAID';
  base_url: string;
  status: 'ACTIVE' | 'DISABLED' | 'UNREACHABLE';
  timeout_seconds: number;
  has_api_key: boolean;
  config_json?: string;
  created_at: string;
  updated_at: string;
}

export interface ProviderHealth {
  provider_code: string;
  reachable: boolean;
  latency_ms: number;
  message: string;
  details?: Record<string, unknown>;
}

export interface GpuUtilization {
  name: string;
  utilization_gpu_percent?: number | null;
  utilization_memory_percent?: number | null;
  memory_used_bytes?: number | null;
  memory_total_bytes?: number | null;
  temperature_celsius?: number | null;
  source: string;
}

export interface HardwareUtilizationSnapshot {
  cpu_process_load_percent: number;
  cpu_system_load_percent: number;
  available_processors: number;
  jvm_used_memory_bytes: number;
  jvm_max_memory_bytes: number;
  system_used_memory_bytes: number;
  system_total_memory_bytes: number;
  system_memory_used_percent: number;
  gpus: GpuUtilization[];
  timestamp: string;
}

export interface ModelView {
  id: string;
  provider_id: string;
  provider_code: string;
  provider_name: string;
  model_identifier: string;
  display_name: string;
  modality: 'CHAT' | 'EMBEDDING' | 'VISION' | 'MULTIMODAL';
  context_window_tokens: number;
  max_output_tokens: number;
  cost_per_million_input_cents: number;
  cost_per_million_output_cents: number;
  is_local: boolean;
  status: 'ACTIVE' | 'DISABLED';
  capabilities: string[];
  metadata_json?: string;
  created_at: string;
  updated_at: string;
}

export interface RoutingPolicyView {
  id: string;
  name: string;
  capability_code: string;
  task_code?: string | null;
  preferred_model_id: string;
  preferred_model_identifier: string;
  preferred_model_local: boolean;
  fallback_model_id?: string | null;
  fallback_model_identifier?: string | null;
  fallback_model_local?: boolean;
  allowed_data_sensitivity: 'PUBLIC' | 'INTERNAL' | 'RESTRICTED';
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface SimulationRequest {
  application_code: string;
  capability_code: string;
  task_code?: string;
  data_sensitivity?: string;
}

export interface SimulationResult {
  allowed: boolean;
  application_code: string;
  capability_code: string;
  task_code?: string | null;
  data_sensitivity: string;
  policy_name: string;
  preferred_model_id?: string | null;
  preferred_model_identifier?: string | null;
  preferred_model_local: boolean;
  fallback_model_id?: string | null;
  fallback_model_identifier?: string | null;
  fallback_model_local: boolean;
  paid_fallback_allowed: boolean;
  decision_reason: string;
  blocked_reason?: string | null;
}

export interface UsageSummaryView {
  total_requests: number;
  local_requests: number;
  hosted_requests: number;
  total_prompt_tokens: number;
  total_completion_tokens: number;
  total_tokens: number;
  total_cost_cents: number;
  fallback_blocked_count: number;
  p50_duration_ms?: number;
  p95_duration_ms?: number;
  p99_duration_ms?: number;
}

export interface ModelWarmupResult {
  model_id: string;
  model_identifier: string;
  success: boolean;
  duration_ms: number;
  message: string;
}

export interface ChatCompletionResponse {
  id: string;
  model: string;
  provider: string;
  message: {
    role: string;
    content: string;
  };
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  duration_ms: number;
  is_local: boolean;
  fallback_used: boolean;
  metadata?: Record<string, unknown>;
}

export interface UsageRecordView {
  id: string;
  application_id: string;
  correlation_id: string;
  provider_id: string;
  model_id: string;
  capability_code: string;
  task_code?: string | null;
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  duration_ms: number;
  is_local: boolean;
  estimated_cost_cents: number;
  status: 'SUCCESS' | 'ERROR' | 'FALLBACK_BLOCKED';
  error_message?: string | null;
  created_at: string;
}

// -------------------------------------------------------------
// Phase 3 Orchestration Types
// -------------------------------------------------------------

export interface TaskDefinitionView {
  id: string;
  task_code: string;
  name: string;
  description?: string | null;
  capability_code: string;
  default_prompt_id?: string | null;
  default_prompt_code?: string | null;
  output_schema_id?: string | null;
  output_schema_code?: string | null;
  timeout_seconds: number;
  allowed_data_sensitivity: 'PUBLIC' | 'INTERNAL' | 'RESTRICTED';
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface PromptView {
  id: string;
  prompt_code: string;
  name: string;
  description?: string | null;
  capability_code: string;
  task_code?: string | null;
  application_id?: string | null;
  version: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED';
  system_template: string;
  user_template: string;
  input_variables: string[];
  created_at: string;
  updated_at: string;
}

export interface SchemaView {
  id: string;
  schema_code: string;
  name: string;
  description?: string | null;
  schema_type: 'REQUEST' | 'RESPONSE_EXTRACTION' | 'TOOL_INPUT' | 'TOOL_OUTPUT';
  version: number;
  schema_definition_json: string;
  is_strict: boolean;
  status: 'ACTIVE' | 'DEPRECATED';
  created_at: string;
  updated_at: string;
}

export interface SchemaValidationResult {
  valid: boolean;
  errors: string[];
}

export interface ToolView {
  id: string;
  tool_code: string;
  name: string;
  description?: string | null;
  application_id?: string | null;
  input_schema_id?: string | null;
  input_schema_code?: string | null;
  output_schema_id?: string | null;
  output_schema_code?: string | null;
  execution_type: 'INTERNAL_MOCK' | 'WEBHOOK';
  endpoint_url?: string | null;
  is_read_only: boolean;
  required_permission: string;
  risk_level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  timeout_ms: number;
  status: 'ACTIVE' | 'DISABLED';
  created_at: string;
  updated_at: string;
}

export interface ToolExecutionResult {
  tool_code: string;
  success: boolean;
  data: Record<string, any>;
  error_message?: string | null;
  duration_ms: number;
}

export interface OrchestrationStep {
  step: number;
  name: string;
  type: string;
  duration_ms: number;
  status: 'SUCCESS' | 'FAILURE' | 'SKIPPED';
  details: Record<string, any>;
}

export interface OrchestratorTraceView {
  id: string;
  application_id?: string | null;
  tenant_id?: string | null;
  correlation_id: string;
  task_code: string;
  capability_code: string;
  model_id?: string | null;
  model_identifier?: string | null;
  prompt_id?: string | null;
  prompt_code?: string | null;
  schema_id?: string | null;
  schema_code?: string | null;
  status: 'SUCCESS' | 'FAILED' | 'SCHEMA_VALIDATION_FAILED' | 'TIMED_OUT';
  total_duration_ms: number;
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  tool_calls_count: number;
  steps: OrchestrationStep[];
  error_message?: string | null;
  created_at: string;
}

// -------------------------------------------------------------
// Section 20.4 Document Intelligence & OCR Types
// -------------------------------------------------------------

export interface FieldProvenance {
  page_number: number;
  line_number: number;
  char_start: number;
  char_end: number;
  region_hint: string;
  raw_snippet: string;
  source_engine: string;
}

export interface ExtractedFieldValue {
  value: unknown;
  status: 'EXTRACTED' | 'INFERRED' | 'MISSING' | 'UNCERTAIN';
  confidence: number;
  provenance?: FieldProvenance | null;
}

export interface ExtractedLineItem {
  index: number;
  description: string;
  quantity?: number | null;
  unit_price?: number | null;
  amount?: number | null;
  currency?: string | null;
  page_number: number;
  line_number: number;
  raw_line: string;
  confidence: number;
  status: string;
}

export interface ExtractedTable {
  table_index: number;
  page_number: number;
  headers: string[];
  rows: string[][];
  confidence: number;
}

export interface DocumentPageView {
  id: string;
  page_number: number;
  status: 'SUCCESS' | 'FAILED' | 'SKIPPED';
  engine_used: string;
  ocr_confidence?: number | null;
  extracted_text: string;
  char_count: number;
  word_count: number;
  preprocess_applied: string[];
  diagnostics: Record<string, unknown>;
  duration_ms: number;
}

export interface DocumentJobView {
  id: string;
  document_id: string;
  application_id?: string | null;
  correlation_id: string;
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELLED';
  page_range?: string | null;
  total_pages: number;
  processed_pages: number;
  failed_pages: number;
  last_completed_page: number;
  attempt_count: number;
  max_attempts: number;
  error_message?: string | null;
  started_at?: string | null;
  completed_at?: string | null;
  created_at: string;
}

export interface DocumentExtractionView {
  id: string;
  document_id: string;
  job_id?: string | null;
  schema_code?: string | null;
  document_type: string;
  overall_confidence: number;
  schema_valid: boolean;
  validation_errors: string[];
  extracted_fields: Record<string, unknown>;
  field_details: Record<string, ExtractedFieldValue>;
  tables: ExtractedTable[];
  line_items: ExtractedLineItem[];
  warnings: string[];
  review_status: 'NOT_REQUIRED' | 'PENDING_REVIEW' | 'APPROVED' | 'CORRECTED' | 'REJECTED';
  review_reasons: string[];
  reviewed_fields?: Record<string, unknown> | null;
  reviewer_actor?: string | null;
  reviewer_notes?: string | null;
  reviewed_at?: string | null;
  promoted_to_ground_truth: boolean;
  duration_ms: number;
  created_at: string;
}

export interface DocumentView {
  id: string;
  application_id?: string | null;
  tenant_id?: string | null;
  file_name: string;
  mime_type: string;
  detected_mime_type: string;
  file_size_bytes: number;
  sha256_hash: string;
  page_count: number;
  document_type: string;
  classification_confidence?: number | null;
  ocr_language: string;
  is_duplicate: boolean;
  duplicate_of_document_id?: string | null;
  duplicate_type?: string | null;
  status: string;
  retention_until?: string | null;
  created_at: string;
  latest_job?: DocumentJobView | null;
  extraction?: DocumentExtractionView | null;
  pages: DocumentPageView[];
}

export interface ExtractionConfigView {
  id: string;
  config_code: string;
  application_id?: string | null;
  document_type: string;
  schema_id: string;
  schema_code: string;
  default_ocr_language: string;
  confidence_threshold: number;
  auto_review_low_confidence: boolean;
  default_retention_hours: number;
  is_active: boolean;
  updated_at: string;
}

export interface DocumentComparisonResult {
  left_document_id: string;
  right_document_id: string;
  left_file_name: string;
  right_file_name: string;
  identical_bytes: boolean;
  text_similarity_score: number;
  page_count_delta: number;
  field_changes: {
    field_name: string;
    change_type: 'ADDED' | 'REMOVED' | 'MODIFIED' | 'UNCHANGED';
    left_value?: unknown;
    right_value?: unknown;
  }[];
  line_item_changes: string[];
  text_diff_summary: string[];
}

export interface DocumentBatchEstimateResult {
  document_count: number;
  estimated_pages: number;
  total_bytes: number;
  estimated_duration_ms: number;
  estimated_memory_mb: number;
  estimated_tokens: number;
  estimated_hosted_cost_cents: number;
  local_first_execution: boolean;
  recommended_execution_mode: string;
  guidance_note: string;
}

export interface OcrBenchmarkResult {
  total_cases: number;
  classification_matches: number;
  schema_valid_cases: number;
  classification_accuracy: number;
  field_accuracy: number;
  schema_valid_rate: number;
  total_duration_ms: number;
  executed_at: string;
  cases: {
    case_code: string;
    name: string;
    source_type: string;
    expected_type: string;
    predicted_type: string;
    type_matched: boolean;
    schema_valid: boolean;
    matched_fields: number;
    total_expected_fields: number;
    field_accuracy: number;
    extracted_line_items: number;
    duration_ms: number;
    extracted_fields: Record<string, unknown>;
  }[];
}

// -------------------------------------------------------------
// Section 20.5 Knowledge Intelligence & RAG Types
// -------------------------------------------------------------

export interface KnowledgeCollectionView {
  id: string;
  collection_code: string;
  application_id?: string | null;
  tenant_id?: string | null;
  name: string;
  description?: string | null;
  collection_type: 'GENERAL_RAG' | 'SOP_MANUAL' | 'FAQ_POLICY' | 'GLOSSARY_ENCYCLOPEDIA';
  visibility_scope: 'APPLICATION' | 'TENANT' | 'GLOBAL_BENCHMARK';
  default_chunk_strategy: 'PARAGRAPH' | 'MARKDOWN_SECTION' | 'FAQ_QA' | 'GLOSSARY_ENTRY' | 'FIXED_WINDOW';
  chunk_size_chars: number;
  chunk_overlap_chars: number;
  embedding_model_code: string;
  embedding_dimensions: number;
  hnsw_m: number;
  hnsw_ef_construction: number;
  hnsw_ef_search: number;
  vector_index_runtime_mode: string;
  rerank_by_default: boolean;
  freshness_ttl_days: number;
  retention_days?: number | null;
  allowed_roles: string[];
  metadata: Record<string, unknown>;
  status: string;
  source_count: number;
  indexed_source_count: number;
  stale_source_count: number;
  chunk_count: number;
  glossary_term_count: number;
  created_at: string;
  updated_at: string;
}

export interface KnowledgeCollectionRequest {
  collection_code: string;
  name: string;
  description?: string;
  application_id?: string | null;
  tenant_id?: string | null;
  collection_type?: 'GENERAL_RAG' | 'SOP_MANUAL' | 'FAQ_POLICY' | 'GLOSSARY_ENCYCLOPEDIA';
  visibility_scope?: 'APPLICATION' | 'TENANT' | 'GLOBAL_BENCHMARK';
  default_chunk_strategy?: 'PARAGRAPH' | 'MARKDOWN_SECTION' | 'FAQ_QA' | 'GLOSSARY_ENTRY' | 'FIXED_WINDOW';
  chunk_size_chars?: number;
  chunk_overlap_chars?: number;
  embedding_model_code?: string;
  hnsw_m?: number;
  hnsw_ef_construction?: number;
  hnsw_ef_search?: number;
  rerank_by_default?: boolean;
  freshness_ttl_days?: number;
  retention_days?: number | null;
  allowed_roles?: string[];
  metadata?: Record<string, unknown>;
}

export interface HnswTuningUpdateRequest {
  hnsw_m?: number;
  hnsw_ef_construction?: number;
  hnsw_ef_search?: number;
}

export interface KnowledgeSourceView {
  id: string;
  collection_id: string;
  collection_code?: string | null;
  application_id?: string | null;
  tenant_id?: string | null;
  source_code: string;
  title: string;
  source_uri?: string | null;
  content_type: 'PLAIN_TEXT' | 'MARKDOWN' | 'FAQ' | 'GLOSSARY' | 'POLICY_SOP';
  chunk_strategy_override?: string | null;
  content_sha256: string;
  version: number;
  indexing_status: 'PENDING' | 'INDEXING' | 'INDEXED' | 'FAILED' | 'REVOKED' | 'DELETED';
  error_detail?: string | null;
  chunk_count: number;
  token_count: number;
  permission_scope: 'PUBLIC' | 'AUTHENTICATED' | 'TENANT_MEMBERS' | 'ROLE_RESTRICTED' | 'OWNER_ONLY';
  allowed_roles: string[];
  allowed_user_refs: string[];
  metadata: Record<string, unknown>;
  stale: boolean;
  age_days: number;
  last_index_outcome?: string | null;
  last_chunks_added: number;
  last_chunks_reused: number;
  last_chunks_removed: number;
  effective_updated_at: string;
  last_indexed_at?: string | null;
  expires_at?: string | null;
  created_at: string;
  updated_at: string;
}

export interface KnowledgeSourceEventView {
  id: string;
  collection_id: string;
  source_id: string;
  source_code: string;
  event_type: string;
  source_version: number;
  previous_sha256?: string | null;
  new_sha256?: string | null;
  chunks_added: number;
  chunks_reused: number;
  chunks_removed: number;
  duration_ms: number;
  actor: string;
  details: Record<string, unknown>;
  created_at: string;
}

export interface SourceCitation {
  citation_id: string;
  chunk_id: string;
  source_id: string;
  source_code: string;
  source_title: string;
  source_uri?: string | null;
  source_version: number;
  page_number?: number | null;
  section_title?: string | null;
  char_start: number;
  char_end: number;
  stale: boolean;
  age_days: number;
  stale_warning?: string | null;
}

export interface RetrievedChunkHit {
  rank: number;
  pre_rerank_rank: number;
  final_score: number;
  vector_score: number;
  keyword_score: number;
  hybrid_score: number;
  rerank_boost: number;
  content: string;
  expanded_context?: string | null;
  matched_terms: string[];
  metadata: Record<string, unknown>;
  citation: SourceCitation;
}

export interface RetrievalDebugTrace {
  normalized_query_terms: string[];
  retrieval_mode: string;
  rerank_applied: boolean;
  embedding_model_used: string;
  embedding_duration_ms: number;
  total_chunks_in_collection: number;
  after_isolation_chunks: number;
  permission_filtered_chunks: number;
  metadata_filtered_chunks: number;
  below_threshold_chunks: number;
  returned_hits: number;
  vector_weight: number;
  keyword_weight: number;
  total_duration_ms: number;
  candidate_traces: {
    chunk_id: string;
    source_code: string;
    section_title?: string | null;
    page_number?: number | null;
    permission_scope: string;
    authorized: boolean;
    metadata_matched: boolean;
    vector_score: number;
    keyword_score: number;
    hybrid_score: number;
    rerank_boost: number;
    final_score: number;
    matched_terms: string[];
    filter_reason: string;
  }[];
}

export interface KnowledgeRetrievalResult {
  collection_id: string;
  collection_code: string;
  query: string;
  retrieval_mode: string;
  reranked: boolean;
  hits: RetrievedChunkHit[];
  stale_warnings: string[];
  security_warnings: string[];
  duration_ms: number;
  debug_trace?: RetrievalDebugTrace | null;
}

export interface LinkedTermOccurrence {
  term_id: string;
  term: string;
  matched_text: string;
  start_offset: number;
  end_offset: number;
  locale: string;
  category: string;
  short_definition: string;
  source_id?: string | null;
  chunk_id?: string | null;
}

export interface KnowledgeAnswerResult {
  question: string;
  answer: string;
  abstained: boolean;
  abstention_reason?: string | null;
  model_used: string;
  citations: SourceCitation[];
  glossary_links: LinkedTermOccurrence[];
  stale_warnings: string[];
  security_warnings: string[];
  duration_ms: number;
  debug_trace?: RetrievalDebugTrace | null;
}

export interface GlossaryTermView {
  id: string;
  collection_id: string;
  source_id?: string | null;
  chunk_id?: string | null;
  term: string;
  normalized_term: string;
  aliases: string[];
  locale: string;
  category: string;
  short_definition: string;
  related_terms: string[];
  created_at: string;
}

export interface GlossaryLinkResult {
  text: string;
  matched_count: number;
  occurrences: LinkedTermOccurrence[];
}

export interface KnowledgeExportBundle {
  bundle_version: string;
  exported_at: string;
  collection: Record<string, unknown>;
  sources: Record<string, unknown>[];
  glossary_terms: Record<string, unknown>[];
  manifest_sha256: string;
}

export interface KnowledgeEvalResult {
  total_cases: number;
  retrieval_cases_count: number;
  hit_count: number;
  hit_rate_at_k: number;
  mrr: number;
  mean_precision_at_k: number;
  citation_accuracy: number;
  permission_check_count: number;
  permission_pass_count: number;
  permission_pass_rate: number;
  total_duration_ms: number;
  executed_at: string;
  cases: {
    case_code: string;
    name: string;
    collection_code: string;
    query_text: string;
    retrieval_mode: string;
    reranked: boolean;
    simulated_roles: string[];
    expected_source_code?: string | null;
    expect_permission_blocked_source?: string | null;
    hit_passed: boolean;
    matched_rank: number;
    reciprocal_rank: number;
    precision_at_k: number;
    citation_accurate: boolean;
    permission_passed: boolean;
    returned_sources: string[];
    duration_ms: number;
  }[];
}

export interface PromotableDocumentView {
  id: string;
  application_id?: string | null;
  tenant_id?: string | null;
  file_name: string;
  mime_type: string;
  document_type: string;
  ocr_language: string;
  page_count: number;
  extracted_page_count: number;
  status: string;
  created_at: string;
}

export interface DocumentKnowledgePromoteRequest {
  source_code?: string;
  title?: string;
  content_type?: string;
  chunk_strategy_override?: string;
  permission_scope?: string;
  allowed_roles?: string[];
  allowed_user_refs?: string[];
  page_numbers?: number[];
  metadata?: Record<string, unknown>;
  force_reindex?: boolean;
}

export interface ChunkPreviewRequest {
  content: string;
  content_type?: string;
  collection_id?: string | null;
  left_strategy?: string;
  left_chunk_size_chars?: number;
  left_chunk_overlap_chars?: number;
  right_strategy?: string;
  right_chunk_size_chars?: number;
  right_chunk_overlap_chars?: number;
}

export interface ChunkBoundarySlice {
  chunk_index: number;
  content: string;
  parent_section_preview?: string | null;
  token_count: number;
  page_number?: number | null;
  section_title?: string | null;
  char_start: number;
  char_end: number;
}

export interface StrategyPreviewPane {
  strategy_used: string;
  chunk_size_chars: number;
  chunk_overlap_chars: number;
  total_chunks: number;
  total_tokens: number;
  avg_tokens_per_chunk: number;
  max_tokens_in_chunk: number;
  glossary_terms_detected: string[];
  chunks: ChunkBoundarySlice[];
}

export interface ChunkPreviewComparisonResult {
  left: StrategyPreviewPane;
  right: StrategyPreviewPane;
  security_warnings: string[];
}

export interface KnowledgeSynonymRequest {
  collection_id?: string | null;
  application_id?: string | null;
  canonical_term: string;
  synonyms: string[];
  locale_pair?: string;
  domain_tag?: string;
}

export interface KnowledgeSynonymView {
  id: string;
  collection_id?: string | null;
  application_id?: string | null;
  canonical_term: string;
  synonyms: string[];
  locale_pair: string;
  domain_tag: string;
  is_active: boolean;
  created_at: string;
}

export interface KnowledgeConnectorRequest {
  connector_code: string;
  collection_id: string;
  name: string;
  connector_type?: string;
  feed_uri: string;
  target_source_code: string;
  content_type?: string;
  permission_scope?: string;
  allowed_roles?: string[];
  poll_interval_minutes?: number;
}

export interface KnowledgeConnectorView {
  id: string;
  connector_code: string;
  collection_id: string;
  collection_code?: string | null;
  application_id?: string | null;
  tenant_id?: string | null;
  name: string;
  connector_type: string;
  feed_uri: string;
  target_source_code: string;
  content_type: string;
  permission_scope: string;
  allowed_roles: string[];
  poll_interval_minutes: number;
  last_etag?: string | null;
  last_modified_header?: string | null;
  last_sync_status: string;
  last_sync_message?: string | null;
  last_synced_at?: string | null;
  is_active: boolean;
  created_at: string;
}

export interface KnowledgeConnectorSyncResult {
  connector_id: string;
  connector_code: string;
  sync_status: string;
  http_status_code: number;
  reindexed: boolean;
  etag?: string | null;
  last_modified_header?: string | null;
  target_source_code: string;
  chunks_added: number;
  chunks_reused: number;
  message: string;
  synced_at: string;
}

export interface KnowledgeFeedbackRequest {
  collection_id?: string | null;
  collection_code?: string | null;
  question_text: string;
  answer_text?: string | null;
  rating: 'HELPFUL' | 'UNHELPFUL' | 'CITATION_MISMATCH' | 'MISSING_INFO';
  cited_source_codes?: string[];
  expected_source_code?: string | null;
  comment?: string | null;
  application_id?: string | null;
}

export interface KnowledgeFeedbackView {
  id: string;
  collection_id: string;
  collection_code?: string | null;
  application_id?: string | null;
  tenant_id?: string | null;
  external_user_ref?: string | null;
  question_text: string;
  answer_text?: string | null;
  rating: 'HELPFUL' | 'UNHELPFUL' | 'CITATION_MISMATCH' | 'MISSING_INFO';
  cited_source_codes: string[];
  expected_source_code?: string | null;
  comment?: string | null;
  promoted_eval_case_id?: string | null;
  created_at: string;
}

export interface FeedbackPromoteEvalRequest {
  case_code?: string;
  name?: string;
  expected_source_code?: string;
  expected_section_contains?: string;
  simulated_roles?: string[];
  activate_immediately?: boolean;
}

export interface AnalyticsDatasetView {
  id: string;
  dataset_code: string;
  application_id?: string | null;
  tenant_id?: string | null;
  name: string;
  description?: string | null;
  domain_archetype: 'MONEY_BUDGET_SUMMARY' | 'CASHFLOW_LEDGER' | 'CATEGORY_EXPENSES' | 'INVENTORY_OPS' | 'KPI_METRICS' | 'CUSTOM_TABULAR';
  schema_definition?: Record<string, unknown> | null;
  connector_type: 'INTERNAL_TABLE' | 'REPORT_TOOL' | 'SAMPLE_SERIES';
  source_uri?: string | null;
  refresh_policy: 'READ_THROUGH' | 'DAILY_SNAPSHOT' | 'HOURLY_PUSH' | 'MANUAL';
  data_points_count: number;
  max_query_window_days: number;
  is_active: boolean;
  source_freshness_at?: string | null;
  created_at: string;
  updated_at: string;
}

export interface AnalyticsDatasetRequest {
  dataset_code: string;
  application_id?: string | null;
  tenant_id?: string | null;
  name: string;
  description?: string | null;
  domain_archetype: 'MONEY_BUDGET_SUMMARY' | 'CASHFLOW_LEDGER' | 'CATEGORY_EXPENSES' | 'INVENTORY_OPS' | 'KPI_METRICS' | 'CUSTOM_TABULAR';
  schema_definition?: Record<string, unknown> | null;
  connector_type?: 'INTERNAL_TABLE' | 'REPORT_TOOL' | 'SAMPLE_SERIES';
  source_uri?: string | null;
  refresh_policy?: 'READ_THROUGH' | 'DAILY_SNAPSHOT' | 'HOURLY_PUSH' | 'MANUAL';
  max_query_window_days?: number;
  is_active?: boolean;
}

export interface DeterministicProvenance {
  method: string;
  deterministic: boolean;
  dataset_code: string;
  row_count: number;
  calculation_steps: string[];
  aggregation_columns: string[];
  computation_hash: string;
  generated_at: string;
}

export interface ClarificationPrompt {
  required: boolean;
  question: string;
  suggested_options: string[];
  reason: string;
}

export interface KpiCardView {
  name: string;
  label: string;
  formatted_value: string;
  numeric_value?: number | null;
  unit: string;
  variance_percent?: number | null;
  trend_direction: 'UP' | 'DOWN' | 'FLAT' | 'ON_TRACK' | 'OFF_TRACK';
  provenance_formula?: string | null;
}

export interface TimeSeriesPointView {
  period_label: string;
  timestamp: string;
  primary_value: number;
  secondary_value?: number | null;
  baseline_value?: number | null;
  is_anomaly?: boolean;
}

export interface BreakdownItemView {
  category: string;
  actual_amount: number;
  budget_amount?: number | null;
  variance_amount?: number | null;
  variance_percent?: number | null;
  share_percent: number;
  status: 'UNDER_BUDGET' | 'ON_BUDGET' | 'OVER_BUDGET' | 'INFO';
}

export interface AnomalyItemView {
  id: string;
  dataset_code: string;
  metric_name: string;
  period_timestamp: string;
  actual_value: number;
  expected_baseline: number;
  deviation_score: number;
  direction: 'SPIKE' | 'DROP';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  algorithm: string;
  status: 'DETECTED' | 'VERIFIED_ANOMALY' | 'FALSE_POSITIVE' | 'RESOLVED' | 'IGNORED';
  review_notes?: string | null;
  reviewed_by?: string | null;
  reviewed_at?: string | null;
}

export interface ForecastPointView {
  period_label: string;
  forecast_value: number;
  lower_bound: number;
  upper_bound: number;
  confidence_level: number;
  data_sufficient: boolean;
}

export interface AnalyticsQueryRequest {
  dataset_code: string;
  natural_language_query?: string | null;
  start_date?: string | null;
  end_date?: string | null;
  timezone?: string | null;
  group_by?: string | null;
  comparison_type?: 'NONE' | 'PERIOD_OVER_PERIOD' | 'BUDGET_VS_ACTUAL' | 'YEAR_OVER_YEAR';
  include_anomalies?: boolean;
  include_forecast?: boolean;
  forecast_periods?: number;
  force_fresh?: boolean;
}

export interface AnalyticsReportResult {
  report_id: string;
  dataset_code: string;
  title: string;
  time_range: string;
  timezone: string;
  source_freshness_at?: string | null;
  data_completeness_ratio: number;
  cached: boolean;
  kpis: KpiCardView[];
  time_series: TimeSeriesPointView[];
  breakdown: BreakdownItemView[];
  tabular_rows: Record<string, unknown>[];
  anomalies: AnomalyItemView[];
  forecast: ForecastPointView[];
  provenance: DeterministicProvenance;
  natural_language_summary?: string | null;
  clarification?: ClarificationPrompt | null;
}

export interface AnomalyReviewRequest {
  status: 'VERIFIED_ANOMALY' | 'FALSE_POSITIVE' | 'RESOLVED' | 'IGNORED';
  review_notes?: string | null;
}

export interface AnalyticsBenchmarkTestCase {
  test_case_name: string;
  dataset_code: string;
  passed: boolean;
  latency_ms: number;
  error_message?: string | null;
}

export interface AnalyticsBenchmarkResult {
  benchmark_run_id: string;
  total_queries_tested: number;
  passed_queries: number;
  failed_queries: number;
  accuracy_score: number;
  average_latency_ms: number;
  cache_hit_rate: number;
  test_cases: AnalyticsBenchmarkTestCase[];
  executed_at: string;
}

// Phase 8: Decision Intelligence & Optimization (20.7)
export interface DecisionPlanView {
  id: string;
  plan_code: string;
  application_id?: string | null;
  tenant_id?: string | null;
  name: string;
  domain_archetype: 'DEBT_PAYOFF_STRATEGY' | 'BUDGET_REBALANCING' | 'SAVINGS_GOAL_ALLOCATION' | 'DISPATCH_ROUTE_SCHEDULING' | 'CUSTOM_OPTIMIZATION';
  description?: string | null;
  parameters: Record<string, unknown>;
  constraints: unknown;
  freshness_ttl_hours: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface DecisionPlanRequest {
  plan_code: string;
  name: string;
  domain_archetype: 'DEBT_PAYOFF_STRATEGY' | 'BUDGET_REBALANCING' | 'SAVINGS_GOAL_ALLOCATION' | 'DISPATCH_ROUTE_SCHEDULING' | 'CUSTOM_OPTIMIZATION';
  description?: string | null;
  parameters?: Record<string, unknown>;
  constraints?: unknown;
  freshness_ttl_hours?: number;
  application_id?: string | null;
  tenant_id?: string | null;
}

export interface OptimizationConstraintView {
  name: string;
  type: 'HARD' | 'SOFT';
  status: 'SATISFIED' | 'VIOLATED';
  current_value?: number | null;
  threshold?: number | null;
  description?: string | null;
}

export interface TradeOffOptionView {
  strategy: string;
  quantitative_metric: string;
  score_or_amount: number;
  pros: string[];
  cons: string[];
  recommended: boolean;
}

export interface ScenarioComparisonView {
  baseline: Record<string, unknown>;
  recommended: Record<string, unknown>;
  delta_metrics: Record<string, unknown>;
  summary: string;
}

export interface DecisionRecommendationRequest {
  plan_code: string;
  natural_language_goal?: string | null;
  runtime_parameters?: Record<string, unknown> | null;
  simulation_mode?: boolean;
  allow_soft_constraint_violations?: boolean;
  locale?: string;
}

export interface DecisionRecommendationView {
  id: string;
  plan_id?: string | null;
  plan_code: string;
  application_id?: string | null;
  tenant_id?: string | null;
  recommendation_code: string;
  title: string;
  summary: string;
  confidence_score: number;
  abstained: boolean;
  abstention_reason?: string | null;
  simulation_mode: boolean;
  status: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED' | 'EXECUTED_SIMULATION';
  action_type: string;
  evidence_rationale: Record<string, unknown>;
  trade_offs: TradeOffOptionView[];
  scenario_diff: ScenarioComparisonView;
  constraints_validated: OptimizationConstraintView[];
  reviewed_by?: string | null;
  reviewed_at?: string | null;
  review_notes?: string | null;
  source_freshness_at?: string | null;
  created_at: string;
}

export interface RecommendationApprovalRequest {
  decision: 'APPROVED' | 'REJECTED' | 'EXECUTED_SIMULATION' | 'SUPERSEDED';
  review_notes?: string | null;
}

export interface RecommendationFeedbackRequest {
  rating: 'HELPFUL' | 'UNHELPFUL' | 'OVERRIDDEN' | 'REJECTED';
  applied?: boolean;
  measured_outcome?: Record<string, unknown>;
  comments?: string | null;
}

export interface DecisionFeedbackView {
  id: string;
  recommendation_id: string;
  application_id?: string | null;
  rating: 'HELPFUL' | 'UNHELPFUL' | 'OVERRIDDEN' | 'REJECTED';
  applied: boolean;
  measured_outcome: Record<string, unknown>;
  comments?: string | null;
  external_user_ref?: string | null;
  created_at: string;
}

export interface DecisionBenchmarkTestCase {
  scenario_name: string;
  archetype: string;
  passed: boolean;
  constraints_satisfied: boolean;
  latency_ms: number;
  error_message?: string | null;
}

export interface DecisionBenchmarkResult {
  benchmark_run_id: string;
  total_scenarios_tested: number;
  passed_scenarios: number;
  failed_scenarios: number;
  constraint_satisfaction_rate: number;
  abstention_accuracy_rate: number;
  average_solver_latency_ms: number;
  test_cases: DecisionBenchmarkTestCase[];
  executed_at: string;
}

// Phase 9 Customer Intelligence Types
export type CustomerSentimentType = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'MIXED';
export type CustomerUrgencyType = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
export type CustomerPriorityType = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type TicketCategoryType =
  | 'MONEY_MANAGEMENT_INQUIRY'
  | 'BUDGET_AND_POCKETS'
  | 'EXPENSE_TRACKING_ISSUE'
  | 'ACCOUNT_AND_SECURITY'
  | 'FEATURE_REQUEST'
  | 'BUG_REPORT'
  | 'GENERAL_INQUIRY'
  | 'PROMISE_POLICY_RESTRICTION';
export type RoutingQueueType =
  | 'TIER1_SUPPORT'
  | 'FINANCIAL_GUIDANCE'
  | 'TECH_SUPPORT'
  | 'SECURITY_ESCALATION'
  | 'HUMAN_DISPUTE_DESK';
export type EscalationReasonType =
  | 'HIGH_FRUSTRATION'
  | 'DISPUTE_REQUIRES_HUMAN'
  | 'PROMISE_POLICY_RESTRICTION'
  | 'USER_REQUESTED_AGENT'
  | 'COMPLEX_UNRESOLVED';
export type TicketStatusType = 'NEW' | 'TRIAGED' | 'ESCALATED' | 'RESOLVED' | 'CLOSED';
export type EscalationStatusType = 'PENDING_HANDOFF' | 'IN_REVIEW' | 'RESOLVED';

export interface SentimentAspectView {
  aspect: string;
  sentiment: CustomerSentimentType;
  evidence_quote: string;
}

export interface PiiEntityItemView {
  type: string;
  masked: string;
  start_offset: number;
  end_offset: number;
}

export interface SentimentAnalysisRequestPayload {
  text: string;
  locale?: string;
  redact_pii?: boolean;
}

export interface SentimentAnalysisResultView {
  raw_text: string;
  redacted_text: string;
  sentiment: CustomerSentimentType;
  score: number;
  confidence: number;
  urgency: CustomerUrgencyType;
  pii_detected: boolean;
  pii_entities: PiiEntityItemView[];
  aspects: SentimentAspectView[];
}

export interface EscalationHandoffPackageView {
  escalation_id: string;
  ticket_id: string;
  ticket_number: string;
  reason: EscalationReasonType;
  executive_summary: string;
  customer_sentiment_summary: string;
  attempts_made?: string | null;
  recommended_actions: string[];
  status: EscalationStatusType;
  created_at: string;
}

export interface TicketTriageRequestPayload {
  customer_name?: string;
  external_user_ref?: string;
  subject: string;
  message: string;
  locale?: string;
  source_channel?: string;
}

export interface TicketTriageResultView {
  ticket_id: string;
  ticket_number: string;
  category: TicketCategoryType;
  priority: CustomerPriorityType;
  urgency: CustomerUrgencyType;
  sentiment: CustomerSentimentType;
  sentiment_score: number;
  routing_queue: RoutingQueueType;
  redacted_message: string;
  pii_detected: boolean;
  pii_entities: PiiEntityItemView[];
  promise_guard_flagged: boolean;
  promise_guard_reasons: string[];
  suggested_canned_response: string;
  confidence_score: number;
  escalation_recommended: boolean;
  escalation_reason?: EscalationReasonType | null;
  escalation_package?: EscalationHandoffPackageView | null;
}

export interface CustomerTicketItemView {
  id: string;
  ticket_number: string;
  application_id?: string | null;
  tenant_id?: string | null;
  external_user_ref?: string | null;
  customer_name?: string | null;
  subject: string;
  redacted_message: string;
  pii_detected: boolean;
  pii_entities: PiiEntityItemView[];
  locale: string;
  category: TicketCategoryType;
  sentiment: CustomerSentimentType;
  sentiment_score: number;
  urgency: CustomerUrgencyType;
  priority: CustomerPriorityType;
  routing_queue: RoutingQueueType;
  status: TicketStatusType;
  promise_guard_flagged: boolean;
  promise_guard_reasons: string[];
  suggested_canned_response: string;
  confidence_score: number;
  created_at: string;
  updated_at: string;
}

export interface CustomerEscalationItemView {
  id: string;
  ticket_id: string;
  ticket_number: string;
  customer_name?: string | null;
  subject: string;
  application_id?: string | null;
  tenant_id?: string | null;
  escalation_reason: EscalationReasonType;
  executive_summary: string;
  customer_sentiment_summary: string;
  attempts_made?: string | null;
  recommended_actions: string[];
  status: EscalationStatusType;
  assigned_agent?: string | null;
  resolution_notes?: string | null;
  resolved_at?: string | null;
  created_at: string;
  updated_at: string;
}

export interface ReviewSummarizeRequestPayload {
  reviews: string[];
  locale?: string;
  domain_context?: string;
}

export interface ReviewSummaryResultView {
  total_analyzed: number;
  overall_sentiment: CustomerSentimentType;
  average_sentiment_score: number;
  pros: string[];
  cons: string[];
  top_feature_requests: string[];
  evidence_quotes: string[];
  executive_summary: string;
}

export interface ProductDiscoveryRequestPayload {
  customer_goal: string;
  current_persona?: string;
  locale?: string;
}

export interface RecommendedFeatureItemView {
  feature_code: string;
  feature_name: string;
  category: string;
  match_score: number;
  rationale: string;
  trade_off_notes: string;
  setup_guidance: string[];
}

export interface ProductDiscoveryResultView {
  customer_goal: string;
  recommended_features: RecommendedFeatureItemView[];
  money_management_boundary_disclaimer: string;
}

export interface CustomerBenchmarkResultView {
  total_evaluated: number;
  passed_cases: number;
  failed_cases: number;
  sentiment_accuracy_percent: number;
  classification_f1_score: number;
  promise_guard_safety_rate_percent: number;
  escalation_precision_percent: number;
  suite_breakdowns: Record<string, number>;
  evaluation_details: Record<string, unknown>[];
  evaluated_at: string;
}

// Phase 10 Application Integrations
export interface IntegrationSummaryItem {
  code: string;
  name: string;
  environment: string;
  status: string;
  sandbox_api_key: string;
  sandbox_signing_secret: string;
  default_tenant_ref: string;
  default_user_ref: string;
  default_roles: string[];
  default_scopes: string[];
  entitled_capabilities: string[];
  available_tools_count: number;
  money_management_only: boolean;
}

export interface SandboxCredentialInfo {
  application_code: string;
  application_name: string;
  environment: string;
  api_key: string;
  signing_secret: string;
  tenant_ref: string;
  user_ref: string;
  roles: string[];
  scopes: string[];
}









export interface TicketTriageRequestPayload {
  customer_name?: string;
  external_user_ref?: string;
  subject: string;
  message: string;
  locale?: string;
  source_channel?: string;
}

export interface TicketTriageResultView {
  ticket_id: string;
  ticket_number: string;
  category: TicketCategoryType;
  priority: CustomerPriorityType;
  urgency: CustomerUrgencyType;
  sentiment: CustomerSentimentType;
  sentiment_score: number;
  routing_queue: RoutingQueueType;
  redacted_message: string;
  pii_detected: boolean;
  pii_entities: PiiEntityItemView[];
  promise_guard_flagged: boolean;
  promise_guard_reasons: string[];
  suggested_canned_response: string;
  confidence_score: number;
  escalation_recommended: boolean;
  escalation_reason?: EscalationReasonType | null;
  escalation_package?: EscalationHandoffPackageView | null;
}

export interface CustomerTicketItemView {
  id: string;
  ticket_number: string;
  application_id?: string | null;
  tenant_id?: string | null;
  external_user_ref?: string | null;
  customer_name?: string | null;
  subject: string;
  redacted_message: string;
  pii_detected: boolean;
  pii_entities: PiiEntityItemView[];
  locale: string;
  category: TicketCategoryType;
  sentiment: CustomerSentimentType;
  sentiment_score: number;
  urgency: CustomerUrgencyType;
  priority: CustomerPriorityType;
  routing_queue: RoutingQueueType;
  status: TicketStatusType;
  promise_guard_flagged: boolean;
  promise_guard_reasons: string[];
  suggested_canned_response: string;
  confidence_score: number;
  created_at: string;
  updated_at: string;
}

export interface CustomerEscalationItemView {
  id: string;
  ticket_id: string;
  ticket_number: string;
  customer_name?: string | null;
  subject: string;
  application_id?: string | null;
  tenant_id?: string | null;
  escalation_reason: EscalationReasonType;
  executive_summary: string;
  customer_sentiment_summary: string;
  attempts_made?: string | null;
  recommended_actions: string[];
  status: EscalationStatusType;
  assigned_agent?: string | null;
  resolution_notes?: string | null;
  resolved_at?: string | null;
  created_at: string;
  updated_at: string;
}

export interface ReviewSummarizeRequestPayload {
  reviews: string[];
  locale?: string;
  domain_context?: string;
}

export interface ReviewSummaryResultView {
  total_analyzed: number;
  overall_sentiment: CustomerSentimentType;
  average_sentiment_score: number;
  pros: string[];
  cons: string[];
  top_feature_requests: string[];
  evidence_quotes: string[];
  executive_summary: string;
}

export interface ProductDiscoveryRequestPayload {
  customer_goal: string;
  current_persona?: string;
  locale?: string;
}

export interface RecommendedFeatureItemView {
  feature_code: string;
  feature_name: string;
  category: string;
  match_score: number;
  rationale: string;
  trade_off_notes: string;
  setup_guidance: string[];
}

export interface ProductDiscoveryResultView {
  customer_goal: string;
  recommended_features: RecommendedFeatureItemView[];
  money_management_boundary_disclaimer: string;
}

export interface CustomerBenchmarkResultView {
  total_evaluated: number;
  passed_cases: number;
  failed_cases: number;
  sentiment_accuracy_percent: number;
  classification_f1_score: number;
  promise_guard_safety_rate_percent: number;
  escalation_precision_percent: number;
  suite_breakdowns: Record<string, number>;
  evaluation_details: Record<string, unknown>[];
  evaluated_at: string;
}







