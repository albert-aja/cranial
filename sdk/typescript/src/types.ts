export interface CranialConfig {
  baseUrl: string;
  apiKey: string;
  signingSecret?: string;
  timeoutMs?: number;
}

export interface SignedContextClaims {
  tenantRef?: string;
  userRef?: string;
  roles?: string[];
  scopes?: string[];
}

export interface SignedHeaders {
  contextHeader: string;
  timestampHeader: string;
  signatureHeader: string;
}

export interface CranialError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface CranialResponse<T> {
  success: boolean;
  data: T;
  errors?: CranialError[];
  metadata?: Record<string, unknown>;
}

export interface HealthResponse {
  status: string;
  platform: string;
  version: string;
  environment: string;
  server_time_utc: string;
  server_epoch_seconds: number;
  max_allowed_skew_seconds: number;
  application_id: string;
  application_code: string;
  key_prefix: string;
  context_signed: boolean;
  entitled_capabilities: string[];
}

export interface EchoResponse {
  verified: boolean;
  application_id: string;
  application_code: string;
  environment: string;
  context_signed: boolean;
  tenant_id?: string;
  external_tenant_ref?: string;
  external_user_ref?: string;
  roles: string[];
  scopes: string[];
  correlation_id: string;
  request_path: string;
  http_method: string;
  client_timestamp_epoch: number;
  timestamp_skew_seconds: number;
  body_sha256: string;
  timestamp_utc: string;
}

export interface TaskExecutionResult {
  trace_id: string;
  task_code: string;
  status: 'SUCCESS' | 'SCHEMA_VALIDATION_FAILED' | 'TOOL_FAILED' | 'ERROR';
  duration_ms: number;
  data: Record<string, unknown>;
  steps?: Array<{
    step_index: number;
    step_name: string;
    step_type: string;
    duration_ms: number;
    status: string;
    metadata?: Record<string, unknown>;
  }>;
}

export interface KnowledgeHit {
  chunk_id: string;
  source_code: string;
  source_title: string;
  source_uri?: string;
  page_number?: number;
  section_title?: string;
  content: string;
  score: number;
}

export interface KnowledgeAskResult {
  answer: string;
  citations: Array<{
    citation_id: string;
    source_code: string;
    source_title: string;
    page_number?: number;
    section_title?: string;
  }>;
  abstained: boolean;
  duration_ms: number;
}

export interface AnalyticsQueryResult {
  query_type: string;
  dataset_code: string;
  provenance: {
    calculator: string;
    calculated_at: string;
    source_freshness: string;
    deterministic: boolean;
  };
  kpi_cards?: Array<{
    metric_name: string;
    value: number;
    unit?: string;
    label: string;
  }>;
  summary_text?: string;
}

