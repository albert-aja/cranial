import type {
  ApiResponse,
  ApplicationView,
  AuditLogView,
  CapabilityEntitlementView,
  CredentialIssuedResult,
  CredentialView,
  LoginResponse,
  OverviewSummary,
  TenantMembership,
  TenantView,
} from './types';

export class CranialApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId: string;
  readonly retryable: boolean;

  constructor(status: number, code: string, message: string, correlationId: string, retryable = false) {
    super(message);
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.retryable = retryable;
  }
}

const SESSION_TOKEN_KEY = 'cranial_admin_jwt';

export function getStoredToken(): string | null {
  return sessionStorage.getItem(SESSION_TOKEN_KEY);
}

export function setStoredToken(token: string | null): void {
  if (token) {
    sessionStorage.setItem(SESSION_TOKEN_KEY, token);
  } else {
    sessionStorage.removeItem(SESSION_TOKEN_KEY);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
  const token = getStoredToken();
  const correlationId = `ui-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

  const headers: Record<string, string> = {
    'X-Correlation-Id': correlationId,
    ...(init.headers as Record<string, string> | undefined),
  };

  if (init.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(path, { ...init, headers });
  const corrHeader = res.headers.get('X-Correlation-Id') || correlationId;

  let parsed: ApiResponse<T> | null = null;
  try {
    parsed = (await res.json()) as ApiResponse<T>;
  } catch {
    throw new CranialApiError(res.status, 'NETWORK_OR_PARSE_ERROR', `HTTP ${res.status} response could not be parsed`, corrHeader);
  }

  if (!res.ok || parsed.status === 'error') {
    const first = parsed.errors?.[0];
    throw new CranialApiError(
      res.status,
      first?.code || `HTTP_${res.status}`,
      first?.message || 'Platform request failed',
      parsed.correlation_id || corrHeader,
      Boolean(first?.retryable)
    );
  }

  return parsed;
}

export const adminApi = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/api/admin/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  me: () => request<LoginResponse['operator']>('/api/admin/v1/auth/me'),

  overview: () => request<OverviewSummary>('/api/admin/v1/overview'),

  listApplications: (params: { environment?: string; status?: string; search?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params.environment) qs.set('environment', params.environment);
    if (params.status) qs.set('status', params.status);
    if (params.search) qs.set('search', params.search);
    qs.set('page', String(params.page || 1));
    qs.set('size', String(params.size || 20));
    return request<ApplicationView[]>(`/api/admin/v1/applications?${qs.toString()}`);
  },

  createApplication: (payload: {
    code: string;
    environment: string;
    name: string;
    description?: string;
    rate_limit_rpm?: number;
    max_input_bytes?: number;
    require_signed_context?: boolean;
  }) =>
    request<ApplicationView>('/api/admin/v1/applications', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateApplicationStatus: (appId: string, status: string) =>
    request<ApplicationView>(`/api/admin/v1/applications/${appId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  updateApplicationSettings: (appId: string, payload: Record<string, unknown>) =>
    request<ApplicationView>(`/api/admin/v1/applications/${appId}/settings`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  listCredentials: (appId: string) =>
    request<CredentialView[]>(`/api/admin/v1/applications/${appId}/credentials`),

  issueCredential: (appId: string, name: string) =>
    request<CredentialIssuedResult>(`/api/admin/v1/applications/${appId}/credentials`, {
      method: 'POST',
      body: JSON.stringify({ name }),
    }),

  rotateCredential: (appId: string, credentialId: string, gracePeriodMinutes: number) =>
    request<CredentialIssuedResult>(`/api/admin/v1/applications/${appId}/credentials/${credentialId}/rotate`, {
      method: 'POST',
      body: JSON.stringify({ grace_period_minutes: gracePeriodMinutes }),
    }),

  revokeCredential: (appId: string, credentialId: string) =>
    request<CredentialView>(`/api/admin/v1/applications/${appId}/credentials/${credentialId}/revoke`, {
      method: 'POST',
    }),

  listApplicationCapabilities: (appId: string) =>
    request<CapabilityEntitlementView[]>(`/api/admin/v1/applications/${appId}/capabilities`),

  updateCapabilityEntitlement: (
    appId: string,
    capabilityCode: string,
    enabled: boolean,
    quotaDailyRequests?: number | null
  ) =>
    request<CapabilityEntitlementView>(`/api/admin/v1/applications/${appId}/capabilities/${capabilityCode}`, {
      method: 'PUT',
      body: JSON.stringify({ enabled, quota_daily_requests: quotaDailyRequests ?? null }),
    }),

  listTenants: (params: { applicationId?: string; status?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params.applicationId) qs.set('applicationId', params.applicationId);
    if (params.status) qs.set('status', params.status);
    qs.set('page', String(params.page || 1));
    qs.set('size', String(params.size || 20));
    return request<TenantView[]>(`/api/admin/v1/tenants?${qs.toString()}`);
  },

  provisionTenant: (payload: { application_id: string; external_tenant_ref: string; name: string }) =>
    request<TenantView>('/api/admin/v1/tenants', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateTenantStatus: (tenantId: string, status: 'ACTIVE' | 'SUSPENDED') =>
    request<TenantView>(`/api/admin/v1/tenants/${tenantId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  listMemberships: (tenantId: string) =>
    request<TenantMembership[]>(`/api/admin/v1/tenants/${tenantId}/memberships`),

  upsertMembership: (tenantId: string, payload: { external_user_ref: string; member_role: string; status: string }) =>
    request<TenantMembership>(`/api/admin/v1/tenants/${tenantId}/memberships`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  searchAuditLogs: (params: {
    applicationId?: string;
    outcome?: string;
    action?: string;
    correlationId?: string;
    page?: number;
    size?: number;
  }) => {
    const qs = new URLSearchParams();
    if (params.applicationId) qs.set('applicationId', params.applicationId);
    if (params.outcome) qs.set('outcome', params.outcome);
    if (params.action) qs.set('action', params.action);
    if (params.correlationId) qs.set('correlationId', params.correlationId);
    qs.set('page', String(params.page || 1));
    qs.set('size', String(params.size || 25));
    return request<AuditLogView[]>(`/api/admin/v1/audit-logs?${qs.toString()}`);
  },

  listFeatureFlags: () =>
    request<import('./types').FeatureFlagView[]>('/api/admin/v1/feature-flags'),

  updateFeatureFlag: (
    flagKey: string,
    payload: { enabled: boolean; rollout_percentage: number; allowed_app_codes: string[] }
  ) =>
    request<import('./types').FeatureFlagView>(`/api/admin/v1/feature-flags/${flagKey}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  runMaintenance: () =>
    request<import('./types').MaintenanceSummary>('/api/admin/v1/maintenance/run', {
      method: 'POST',
    }),

  listProviders: () =>
    request<import('./types').ProviderView[]>('/api/admin/v1/providers'),

  checkProviderHealth: (id: string) =>
    request<import('./types').ProviderHealth>(`/api/admin/v1/providers/${id}/health`),

  resetCircuitBreaker: (id: string) =>
    request<{ message: string }>(`/api/admin/v1/providers/${id}/circuit/reset`, {
      method: 'POST',
    }),

  saveProvider: (payload: {
    id?: string;
    code: string;
    name: string;
    providerType: string;
    baseUrl: string;
    status?: string;
    timeoutSeconds?: number;
    configJson?: string;
  }) =>
    request<import('./types').ProviderView>('/api/admin/v1/providers', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listModels: () =>
    request<import('./types').ModelView[]>('/api/admin/v1/models'),

  saveModel: (payload: {
    id?: string;
    providerId: string;
    modelIdentifier: string;
    displayName: string;
    modality: string;
    contextWindowTokens?: number;
    maxOutputTokens?: number;
    costPerMillionInputCents?: number;
    costPerMillionOutputCents?: number;
    isLocal?: boolean;
    status?: string;
    capabilities?: string[];
    metadataJson?: string;
  }) =>
    request<import('./types').ModelView>('/api/admin/v1/models', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  warmupModel: (id: string) =>
    request<import('./types').ModelWarmupResult>(`/api/admin/v1/models/${id}/warmup`, {
      method: 'POST',
    }),

  getHardwareMetrics: () =>
    request<import('./types').HardwareUtilizationSnapshot>('/api/admin/v1/models/hardware-metrics'),

  playgroundChat: (payload: {
    application_id?: string;
    model_override?: string;
    capability?: string;
    task?: string;
    data_sensitivity?: string;
    temperature?: number;
    max_tokens?: number;
    messages: Array<{ role: string; content: string }>;
  }) =>
    request<import('./types').ChatCompletionResponse>('/api/admin/v1/models/playground', {
      method: 'POST',
      body: JSON.stringify({
        applicationId: payload.application_id,
        modelOverride: payload.model_override,
        capability: payload.capability,
        task: payload.task,
        dataSensitivity: payload.data_sensitivity,
        temperature: payload.temperature,
        maxTokens: payload.max_tokens,
        messages: payload.messages,
      }),
    }),

  playgroundStream: async (
    payload: {
      application_id?: string;
      model_override?: string;
      capability?: string;
      task?: string;
      data_sensitivity?: string;
      temperature?: number;
      max_tokens?: number;
      messages: Array<{ role: string; content: string }>;
    },
    callbacks: {
      onChunk: (chunk: string) => void;
      onDone: (data: import('./types').ChatCompletionResponse) => void;
      onError: (err: Error) => void;
    }
  ) => {
    const token = getStoredToken();
    const correlationId = `ui-stream-${Date.now().toString(36)}`;
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    try {
      const res = await fetch('/api/admin/v1/models/playground/stream', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          applicationId: payload.application_id,
          modelOverride: payload.model_override,
          capability: payload.capability,
          task: payload.task,
          dataSensitivity: payload.data_sensitivity,
          temperature: payload.temperature,
          maxTokens: payload.max_tokens,
          messages: payload.messages,
        }),
      });

      if (!res.ok) {
        let errMessage = `HTTP ${res.status}`;
        try {
          const errJson = await res.json();
          if (errJson.errors && errJson.errors[0]?.message) {
            errMessage = errJson.errors[0].message;
          }
        } catch {
          // fallback to status
        }
        throw new Error(errMessage);
      }

      const reader = res.body?.getReader();
      if (!reader) {
        throw new Error('Readable stream not supported by browser/server');
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('data:')) {
            const dataStr = trimmed.slice(5).trim();
            if (!dataStr) continue;
            try {
              const parsed = JSON.parse(dataStr);
              if (parsed.chunk !== undefined) {
                callbacks.onChunk(parsed.chunk);
              } else if (parsed.content !== undefined || parsed.message !== undefined) {
                callbacks.onDone(parsed);
              }
            } catch {
              callbacks.onChunk(dataStr);
            }
          }
        }
      }
    } catch (err) {
      callbacks.onError(err instanceof Error ? err : new Error(String(err)));
    }
  },

  listRoutingPolicies: () =>
    request<import('./types').RoutingPolicyView[]>('/api/admin/v1/routing-policies'),

  saveRoutingPolicy: (payload: {
    id?: string;
    name: string;
    capabilityCode: string;
    taskCode?: string | null;
    preferredModelId: string;
    fallbackModelId?: string | null;
    allowedDataSensitivity?: string;
    isActive?: boolean;
  }) =>
    request<import('./types').RoutingPolicyView>('/api/admin/v1/routing-policies', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  simulateRouting: (payload: import('./types').SimulationRequest) =>
    request<import('./types').SimulationResult>('/api/admin/v1/routing-policies/simulate', {
      method: 'POST',
      body: JSON.stringify({
        applicationCode: payload.application_code,
        capabilityCode: payload.capability_code,
        taskCode: payload.task_code,
        dataSensitivity: payload.data_sensitivity,
      }),
    }),

  getUsageSummary: (applicationId?: string) => {
    const qs = applicationId ? `?application_id=${applicationId}` : '';
    return request<import('./types').UsageSummaryView>(`/api/admin/v1/usage/summary${qs}`);
  },

  listUsageRecords: (applicationId?: string, limit = 50) => {
    const qs = new URLSearchParams();
    if (applicationId) qs.set('application_id', applicationId);
    qs.set('limit', String(limit));
    return request<import('./types').UsageRecordView[]>(`/api/admin/v1/usage/records?${qs.toString()}`);
  },

  // -------------------------------------------------------------
  // Phase 3 Orchestration APIs
  // -------------------------------------------------------------

  listTasks: () =>
    request<import('./types').TaskDefinitionView[]>('/api/admin/v1/tasks'),

  getTask: (id: string) =>
    request<import('./types').TaskDefinitionView>(`/api/admin/v1/tasks/${id}`),

  saveTask: (payload: {
    id?: string;
    task_code: string;
    name: string;
    description?: string;
    capability_code: string;
    default_prompt_id?: string | null;
    output_schema_id?: string | null;
    timeout_seconds?: number;
    allowed_data_sensitivity?: string;
    is_active?: boolean;
  }) =>
    request<import('./types').TaskDefinitionView>('/api/admin/v1/tasks', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listPrompts: () =>
    request<import('./types').PromptView[]>('/api/admin/v1/prompts'),

  getPrompt: (id: string) =>
    request<import('./types').PromptView>(`/api/admin/v1/prompts/${id}`),

  listPromptVersions: (promptCode: string) =>
    request<import('./types').PromptView[]>(`/api/admin/v1/prompts/versions/${promptCode}`),

  savePrompt: (payload: {
    id?: string;
    prompt_code: string;
    name: string;
    description?: string;
    capability_code: string;
    task_code?: string;
    application_id?: string;
    version?: number;
    status?: string;
    system_template?: string;
    user_template: string;
    input_variables_json?: string;
  }) =>
    request<import('./types').PromptView>('/api/admin/v1/prompts', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  publishPrompt: (id: string) =>
    request<import('./types').PromptView>(`/api/admin/v1/prompts/${id}/publish`, {
      method: 'POST',
    }),

  listSchemas: () =>
    request<import('./types').SchemaView[]>('/api/admin/v1/schemas'),

  getSchema: (id: string) =>
    request<import('./types').SchemaView>(`/api/admin/v1/schemas/${id}`),

  saveSchema: (payload: {
    id?: string;
    schema_code: string;
    name: string;
    description?: string;
    schema_type: string;
    version?: number;
    schema_definition_json: string;
    is_strict?: boolean;
    status?: string;
  }) =>
    request<import('./types').SchemaView>('/api/admin/v1/schemas', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  validateSchema: (id: string, data: any) =>
    request<import('./types').SchemaValidationResult>(`/api/admin/v1/schemas/${id}/validate`, {
      method: 'POST',
      body: JSON.stringify({ data }),
    }),

  listTools: () =>
    request<import('./types').ToolView[]>('/api/admin/v1/tools'),

  getTool: (id: string) =>
    request<import('./types').ToolView>(`/api/admin/v1/tools/${id}`),

  saveTool: (payload: {
    id?: string;
    tool_code: string;
    name: string;
    description?: string;
    application_id?: string;
    input_schema_id?: string | null;
    output_schema_id?: string | null;
    execution_type?: string;
    endpoint_url?: string;
    is_read_only?: boolean;
    required_permission?: string;
    risk_level?: string;
    timeout_ms?: number;
    status?: string;
  }) =>
    request<import('./types').ToolView>('/api/admin/v1/tools', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  testTool: (id: string, payload: { tool_code: string; parameters: Record<string, any> }) =>
    request<import('./types').ToolExecutionResult>(`/api/admin/v1/tools/${id}/test`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listTraces: (applicationId?: string, limit = 50) => {
    const qs = new URLSearchParams();
    if (applicationId) qs.set('applicationId', applicationId);
    qs.set('limit', String(limit));
    return request<import('./types').OrchestratorTraceView[]>(`/api/admin/v1/traces?${qs.toString()}`);
  },

  getTrace: (id: string) =>
    request<import('./types').OrchestratorTraceView>(`/api/admin/v1/traces/${id}`),

  // Document Intelligence & OCR (20.4)
  runDocumentPlayground: (payload: {
    file_name: string;
    mime_type?: string;
    content_base64?: string;
    raw_text?: string;
    document_type_hint?: string;
    ocr_language?: string;
    page_range?: string;
    preprocess?: {
      rotate_degrees?: number;
      deskew?: boolean;
      denoise?: boolean;
      contrast_enhance?: boolean;
    };
    extraction_schema_code?: string;
    retention_hours?: number;
  }) =>
    request<import('./types').DocumentView>('/api/admin/v1/documents/playground', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listDocuments: (params?: { applicationId?: string; status?: string; limit?: number }) => {
    const qs = new URLSearchParams();
    if (params?.applicationId) qs.set('applicationId', params.applicationId);
    if (params?.status) qs.set('status', params.status);
    qs.set('limit', String(params?.limit || 30));
    return request<import('./types').DocumentView[]>(`/api/admin/v1/documents?${qs.toString()}`);
  },

  getDocument: (id: string, revealContent = false) =>
    request<import('./types').DocumentView>(`/api/admin/v1/documents/${id}?revealContent=${revealContent}`),

  deleteDocument: (id: string) =>
    request<{ document_id: string; deleted: boolean }>(`/api/admin/v1/documents/${id}`, {
      method: 'DELETE',
    }),

  listDocumentJobs: (status?: string) => {
    const qs = new URLSearchParams();
    if (status) qs.set('status', status);
    return request<import('./types').DocumentJobView[]>(`/api/admin/v1/documents/jobs?${qs.toString()}`);
  },

  resumeDocumentJob: (jobId: string) =>
    request<import('./types').DocumentJobView>(`/api/admin/v1/documents/jobs/${jobId}/resume`, {
      method: 'POST',
    }),

  listDocumentReviewQueue: (status = 'PENDING_REVIEW') =>
    request<import('./types').DocumentExtractionView[]>(`/api/admin/v1/documents/review-queue?status=${encodeURIComponent(status)}`),

  decideDocumentReview: (
    extractionId: string,
    payload: {
      decision: 'APPROVED' | 'CORRECTED' | 'REJECTED';
      corrected_fields?: Record<string, unknown>;
      reviewer_notes?: string;
      promote_to_ground_truth?: boolean;
    }
  ) =>
    request<import('./types').DocumentExtractionView>(`/api/admin/v1/documents/review-queue/${extractionId}/decide`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listDocumentConfigs: () =>
    request<import('./types').ExtractionConfigView[]>('/api/admin/v1/documents/configs'),

  saveDocumentConfig: (payload: {
    config_code: string;
    application_id?: string | null;
    document_type: string;
    schema_code: string;
    default_ocr_language?: string;
    confidence_threshold?: number;
    auto_review_low_confidence?: boolean;
    default_retention_hours?: number;
  }) =>
    request<import('./types').ExtractionConfigView>('/api/admin/v1/documents/configs', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  compareDocuments: (left_document_id: string, right_document_id: string) =>
    request<import('./types').DocumentComparisonResult>('/api/admin/v1/documents/compare', {
      method: 'POST',
      body: JSON.stringify({ left_document_id, right_document_id }),
    }),

  estimateDocumentBatch: (payload: {
    document_count: number;
    total_bytes: number;
    estimated_pages: number;
    ocr_enabled?: boolean;
    preprocess_enabled?: boolean;
    llm_assist_enabled?: boolean;
  }) =>
    request<import('./types').DocumentBatchEstimateResult>('/api/admin/v1/documents/estimate', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  runOcrBenchmarks: () =>
    request<import('./types').OcrBenchmarkResult>('/api/admin/v1/documents/benchmarks/run', {
      method: 'POST',
    }),

  // -------------------------------------------------------------
  // Knowledge Intelligence & RAG (20.5)
  // -------------------------------------------------------------

  listKnowledgeCollections: () =>
    request<import('./types').KnowledgeCollectionView[]>('/api/admin/v1/knowledge/collections'),

  createKnowledgeCollection: (payload: import('./types').KnowledgeCollectionRequest) =>
    request<import('./types').KnowledgeCollectionView>('/api/admin/v1/knowledge/collections', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listKnowledgeSources: (collectionId: string) =>
    request<import('./types').KnowledgeSourceView[]>(`/api/admin/v1/knowledge/collections/${collectionId}/sources`),

  ingestKnowledgeSource: (
    collectionId: string,
    payload: {
      source_code: string;
      title: string;
      source_uri?: string;
      content_type?: string;
      chunk_strategy_override?: string | null;
      content: string;
      permission_scope?: string;
      allowed_roles?: string[];
      metadata?: Record<string, unknown>;
      retention_days?: number | null;
      force_reindex?: boolean;
      simulate_failure?: boolean;
    }
  ) =>
    request<import('./types').KnowledgeSourceView>(`/api/admin/v1/knowledge/collections/${collectionId}/sources`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  reindexKnowledgeSource: (sourceId: string) =>
    request<import('./types').KnowledgeSourceView>(`/api/admin/v1/knowledge/sources/${sourceId}/reindex`, {
      method: 'POST',
    }),

  revokeKnowledgeSource: (sourceId: string) =>
    request<import('./types').KnowledgeSourceView>(`/api/admin/v1/knowledge/sources/${sourceId}/revoke`, {
      method: 'POST',
    }),

  deleteKnowledgeSource: (sourceId: string) =>
    request<{ source_id: string; status: string; propagated: boolean }>(`/api/admin/v1/knowledge/sources/${sourceId}`, {
      method: 'DELETE',
    }),

  getKnowledgeSourceHistory: (sourceId: string) =>
    request<import('./types').KnowledgeSourceEventView[]>(`/api/admin/v1/knowledge/sources/${sourceId}/history`),

  listStaleKnowledgeSources: () =>
    request<import('./types').KnowledgeSourceView[]>('/api/admin/v1/knowledge/stale-sources'),

  debugKnowledgeRetrieve: (payload: {
    collection_id?: string;
    collection_code?: string;
    query: string;
    retrieval_mode?: string;
    top_k?: number;
    rerank?: boolean;
    include_stale?: boolean;
    expand_parent_context?: boolean;
    metadata_filters?: Record<string, unknown>;
    simulated_roles?: string[];
    debug?: boolean;
  }) =>
    request<import('./types').KnowledgeRetrievalResult>('/api/admin/v1/knowledge/retrieve', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  debugKnowledgeAsk: (payload: {
    collection_id?: string;
    collection_code?: string;
    question: string;
    locale?: string;
    retrieval_mode?: string;
    top_k?: number;
    rerank?: boolean;
    expand_parent_context?: boolean;
    metadata_filters?: Record<string, unknown>;
    simulated_roles?: string[];
    debug?: boolean;
  }) =>
    request<import('./types').KnowledgeAnswerResult>('/api/admin/v1/knowledge/ask', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listGlossaryTerms: (collectionId: string, locale?: string) => {
    const qs = locale ? `?locale=${encodeURIComponent(locale)}` : '';
    return request<import('./types').GlossaryTermView[]>(`/api/admin/v1/knowledge/collections/${collectionId}/glossary${qs}`);
  },

  createGlossaryTerm: (payload: {
    collection_id: string;
    term: string;
    aliases?: string[];
    locale?: string;
    category?: string;
    short_definition: string;
    related_terms?: string[];
  }) =>
    request<import('./types').GlossaryTermView>('/api/admin/v1/knowledge/glossary/terms', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  linkGlossaryPreview: (payload: { collection_id?: string; collection_code?: string; text: string; locale?: string }) =>
    request<import('./types').GlossaryLinkResult>('/api/admin/v1/knowledge/glossary/link', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  exportKnowledgeCollection: (collectionId: string) =>
    request<import('./types').KnowledgeExportBundle>(`/api/admin/v1/knowledge/collections/${collectionId}/export`),

  importKnowledgeCollection: (bundle: import('./types').KnowledgeExportBundle, targetCode?: string) => {
    const qs = targetCode ? `?target_collection_code=${encodeURIComponent(targetCode)}` : '';
    return request<import('./types').KnowledgeCollectionView>(`/api/admin/v1/knowledge/collections/import${qs}`, {
      method: 'POST',
      body: JSON.stringify(bundle),
    });
  },

  runKnowledgeBenchmarks: () =>
    request<import('./types').KnowledgeEvalResult>('/api/admin/v1/knowledge/benchmarks/run', {
      method: 'POST',
    }),

  updateHnswTuning: (collectionId: string, payload: import('./types').HnswTuningUpdateRequest) =>
    request<import('./types').KnowledgeCollectionView>(`/api/admin/v1/knowledge/collections/${collectionId}/hnsw-tuning`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    }),

  listPromotableDocuments: () =>
    request<import('./types').PromotableDocumentView[]>('/api/admin/v1/knowledge/promotable-documents'),

  promoteDocumentToKnowledge: (collectionId: string, documentId: string, payload?: import('./types').DocumentKnowledgePromoteRequest) =>
    request<import('./types').KnowledgeSourceView>(`/api/admin/v1/knowledge/collections/${collectionId}/promote-document/${documentId}`, {
      method: 'POST',
      body: JSON.stringify(payload ?? {}),
    }),

  previewChunkBoundaries: (payload: import('./types').ChunkPreviewRequest) =>
    request<import('./types').ChunkPreviewComparisonResult>('/api/admin/v1/knowledge/chunk-preview', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listSynonyms: (collectionId?: string) => {
    const qs = collectionId ? `?collection_id=${encodeURIComponent(collectionId)}` : '';
    return request<import('./types').KnowledgeSynonymView[]>(`/api/admin/v1/knowledge/synonyms${qs}`);
  },

  createSynonym: (payload: import('./types').KnowledgeSynonymRequest) =>
    request<import('./types').KnowledgeSynonymView>('/api/admin/v1/knowledge/synonyms', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  deleteSynonym: (id: string) =>
    request<{ deleted: boolean; id: string }>(`/api/admin/v1/knowledge/synonyms/${id}`, {
      method: 'DELETE',
    }),

  listConnectors: (collectionId?: string) => {
    const qs = collectionId ? `?collection_id=${encodeURIComponent(collectionId)}` : '';
    return request<import('./types').KnowledgeConnectorView[]>(`/api/admin/v1/knowledge/connectors${qs}`);
  },

  createConnector: (payload: import('./types').KnowledgeConnectorRequest) =>
    request<import('./types').KnowledgeConnectorView>('/api/admin/v1/knowledge/connectors', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  syncConnector: (id: string, force = false, simulatedRevision?: string) => {
    const params = new URLSearchParams();
    if (force) params.set('force', 'true');
    if (simulatedRevision) params.set('simulated_revision', simulatedRevision);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return request<import('./types').KnowledgeConnectorSyncResult>(`/api/admin/v1/knowledge/connectors/${id}/sync${qs}`, {
      method: 'POST',
    });
  },

  listKnowledgeFeedback: (collectionId?: string, rating?: string) => {
    const params = new URLSearchParams();
    if (collectionId) params.set('collection_id', collectionId);
    if (rating && rating !== 'ALL') params.set('rating', rating);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return request<import('./types').KnowledgeFeedbackView[]>(`/api/admin/v1/knowledge/feedback${qs}`);
  },

  submitKnowledgeFeedback: (payload: import('./types').KnowledgeFeedbackRequest) =>
    request<import('./types').KnowledgeFeedbackView>('/api/admin/v1/knowledge/feedback', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  promoteFeedbackToEval: (id: string, payload?: import('./types').FeedbackPromoteEvalRequest) =>
    request<unknown>(`/api/admin/v1/knowledge/feedback/${id}/promote-eval`, {
      method: 'POST',
      body: JSON.stringify(payload ?? {}),
    }),

  purgeExpiredKnowledge: () =>
    request<{ purged_sources: number; purged_chunks: number; purged_glossary_terms: number; executed_at: string }>(
      '/api/admin/v1/knowledge/retention/purge',
      { method: 'POST' }
    ),

  // Phase 7 Analytics
  listAnalyticsDatasets: () =>
    request<import('./types').AnalyticsDatasetView[]>('/api/admin/v1/analytics/datasets'),

  saveAnalyticsDataset: (payload: import('./types').AnalyticsDatasetRequest) =>
    request<import('./types').AnalyticsDatasetView>('/api/admin/v1/analytics/datasets', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  runAnalyticsQuery: (payload: import('./types').AnalyticsQueryRequest) =>
    request<import('./types').AnalyticsReportResult>('/api/admin/v1/analytics/query', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listAnalyticsAnomalies: (params?: { dataset_code?: string; status?: string; min_severity?: string }) => {
    const qs = new URLSearchParams();
    if (params?.dataset_code) qs.set('dataset_code', params.dataset_code);
    if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
    if (params?.min_severity && params.min_severity !== 'ALL') qs.set('min_severity', params.min_severity);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').AnomalyItemView[]>(`/api/admin/v1/analytics/anomalies${queryString}`);
  },

  reviewAnalyticsAnomaly: (id: string, payload: import('./types').AnomalyReviewRequest) =>
    request<import('./types').AnomalyItemView>(`/api/admin/v1/analytics/anomalies/${id}/review`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getAnalyticsCacheStats: () =>
    request<{ total_entries: number; total_hits: number; oldest_entry_at: string | null; executed_at: string }>(
      '/api/admin/v1/analytics/cache/stats'
    ),

  purgeAnalyticsCache: (datasetCode?: string) => {
    const qs = datasetCode ? `?dataset_code=${encodeURIComponent(datasetCode)}` : '';
    return request<{ purged_entries: number; dataset_code: string | null; executed_at: string }>(
      `/api/admin/v1/analytics/cache/purge${qs}`,
      { method: 'POST' }
    );
  },

  runAnalyticsBenchmarks: () =>
    request<import('./types').AnalyticsBenchmarkResult>('/api/admin/v1/analytics/benchmarks/run', {
      method: 'POST',
    }),

  // Phase 8 Decision Intelligence
  listDecisionPlans: (params?: { application_id?: string; tenant_id?: string }) => {
    const qs = new URLSearchParams();
    if (params?.application_id) qs.set('application_id', params.application_id);
    if (params?.tenant_id) qs.set('tenant_id', params.tenant_id);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').DecisionPlanView[]>(`/api/admin/v1/decision/plans${queryString}`);
  },

  createDecisionPlan: (payload: import('./types').DecisionPlanRequest) =>
    request<import('./types').DecisionPlanView>('/api/admin/v1/decision/plans', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listDecisionRecommendations: (params?: { plan_code?: string; status?: string; application_id?: string }) => {
    const qs = new URLSearchParams();
    if (params?.plan_code) qs.set('plan_code', params.plan_code);
    if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
    if (params?.application_id) qs.set('application_id', params.application_id);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').DecisionRecommendationView[]>(`/api/admin/v1/decision/recommendations${queryString}`);
  },

  generateDecisionRecommendation: (payload: import('./types').DecisionRecommendationRequest) =>
    request<import('./types').DecisionRecommendationView>('/api/admin/v1/decision/recommendations', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  reviewDecisionRecommendation: (id: string, payload: import('./types').RecommendationApprovalRequest) =>
    request<import('./types').DecisionRecommendationView>(`/api/admin/v1/decision/recommendations/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listDecisionFeedbacks: (recommendationId: string) =>
    request<import('./types').DecisionFeedbackView[]>(`/api/admin/v1/decision/recommendations/${recommendationId}/feedback`),

  submitDecisionFeedback: (recommendationId: string, payload: import('./types').RecommendationFeedbackRequest) =>
    request<import('./types').DecisionFeedbackView>(`/api/admin/v1/decision/recommendations/${recommendationId}/feedback`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  runDecisionBenchmarks: () =>
    request<import('./types').DecisionBenchmarkResult>('/api/admin/v1/decision/benchmarks/run', {
      method: 'POST',
    }),

  // Phase 9 Customer Intelligence
  analyzeCustomerSentiment: (payload: import('./types').SentimentAnalysisRequestPayload) =>
    request<import('./types').SentimentAnalysisResultView>('/api/v1/customer/sentiment/analyze', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  triageCustomerTicket: (payload: import('./types').TicketTriageRequestPayload) =>
    request<import('./types').TicketTriageResultView>('/api/v1/customer/tickets/triage', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listCustomerTickets: (params?: { status?: string; priority?: string; category?: string; application_id?: string }) => {
    const qs = new URLSearchParams();
    if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
    if (params?.priority && params.priority !== 'ALL') qs.set('priority', params.priority);
    if (params?.category && params.category !== 'ALL') qs.set('category', params.category);
    if (params?.application_id) qs.set('application_id', params.application_id);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').CustomerTicketItemView[]>(`/api/admin/v1/customer/tickets${queryString}`);
  },

  getCustomerTicket: (id: string) =>
    request<import('./types').CustomerTicketItemView>(`/api/admin/v1/customer/tickets/${id}`),

  listCustomerEscalations: (params?: { status?: string; application_id?: string }) => {
    const qs = new URLSearchParams();
    if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
    if (params?.application_id) qs.set('application_id', params.application_id);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').CustomerEscalationItemView[]>(`/api/admin/v1/customer/escalations${queryString}`);
  },

  resolveCustomerEscalation: (id: string, resolutionNotes?: string) =>
    request<import('./types').CustomerEscalationItemView>(`/api/admin/v1/customer/escalations/${id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ resolution_notes: resolutionNotes || 'Resolved by operator' }),
    }),

  summarizeCustomerReviews: (payload: import('./types').ReviewSummarizeRequestPayload) =>
    request<import('./types').ReviewSummaryResultView>('/api/v1/customer/reviews/summarize', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  discoverCustomerProducts: (payload: import('./types').ProductDiscoveryRequestPayload) =>
    request<import('./types').ProductDiscoveryResultView>('/api/v1/customer/products/discover', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  runCustomerBenchmarks: () =>
    request<import('./types').CustomerBenchmarkResultView>('/api/admin/v1/customer/benchmarks/run', {
      method: 'POST',
    }),

  // Phase 10 Application Integrations
  getIntegrationSummary: () =>
    request<import('./types').IntegrationSummaryItem[]>('/api/admin/v1/integrations/summary'),

  listSandboxCredentials: () =>
    request<import('./types').SandboxCredentialInfo[]>('/api/admin/v1/integrations/sandbox'),

  probeIntegrationHealth: (apiKey?: string) =>
    request<Record<string, unknown>>('/api/v1/integration/health', {
      headers: apiKey ? { 'X-Cranial-Api-Key': apiKey } : {},
    }),

  probeIntegrationCatalog: (apiKey?: string) =>
    request<Record<string, unknown>>('/api/v1/integration/catalog', {
      headers: apiKey ? { 'X-Cranial-Api-Key': apiKey } : {},
    }),

  probeIntegrationEcho: (apiKey: string, signature: string, contextJson: string, timestamp: number, body?: unknown) =>
    request<Record<string, unknown>>('/api/v1/integration/echo', {
      method: 'POST',
      headers: {
        'X-Cranial-Api-Key': apiKey,
        'X-Cranial-Context': contextJson,
        'X-Cranial-Timestamp': String(timestamp),
        'X-Cranial-Signature': signature,
      },
      body: JSON.stringify(body || { test: 'handshake' }),
    }),

  // Phase 9 Customer Intelligence
  analyzeCustomerSentiment: (payload: import('./types').SentimentAnalysisRequestPayload) =>
    request<import('./types').SentimentAnalysisResultView>('/api/v1/customer/sentiment/analyze', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  triageCustomerTicket: (payload: import('./types').TicketTriageRequestPayload) =>
    request<import('./types').TicketTriageResultView>('/api/v1/customer/tickets/triage', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listCustomerTickets: (params?: { status?: string; priority?: string; category?: string; application_id?: string }) => {
    const qs = new URLSearchParams();
    if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
    if (params?.priority && params.priority !== 'ALL') qs.set('priority', params.priority);
    if (params?.category && params.category !== 'ALL') qs.set('category', params.category);
    if (params?.application_id) qs.set('application_id', params.application_id);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').CustomerTicketItemView[]>(`/api/admin/v1/customer/tickets${queryString}`);
  },

  getCustomerTicket: (id: string) =>
    request<import('./types').CustomerTicketItemView>(`/api/admin/v1/customer/tickets/${id}`),

  listCustomerEscalations: (params?: { status?: string; application_id?: string }) => {
    const qs = new URLSearchParams();
    if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
    if (params?.application_id) qs.set('application_id', params.application_id);
    const queryString = qs.toString() ? `?${qs.toString()}` : '';
    return request<import('./types').CustomerEscalationItemView[]>(`/api/admin/v1/customer/escalations${queryString}`);
  },

  resolveCustomerEscalation: (id: string, resolutionNotes?: string) =>
    request<import('./types').CustomerEscalationItemView>(`/api/admin/v1/customer/escalations/${id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ resolution_notes: resolutionNotes || 'Resolved by operator' }),
    }),

  summarizeCustomerReviews: (payload: import('./types').ReviewSummarizeRequestPayload) =>
    request<import('./types').ReviewSummaryResultView>('/api/v1/customer/reviews/summarize', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  discoverCustomerProducts: (payload: import('./types').ProductDiscoveryRequestPayload) =>
    request<import('./types').ProductDiscoveryResultView>('/api/v1/customer/products/discover', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  runCustomerBenchmarks: () =>
    request<import('./types').CustomerBenchmarkResultView>('/api/admin/v1/customer/benchmarks/run', {
      method: 'POST',
    }),
};

export const api = adminApi;

