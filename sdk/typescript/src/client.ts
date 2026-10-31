import type {
  CranialConfig,
  CranialResponse,
  HealthResponse,
  EchoResponse,
  TaskExecutionResult,
  KnowledgeHit,
  KnowledgeAskResult,
  AnalyticsQueryResult,
} from './types.js';
import { SignedContextSigner } from './signer.js';

export class CranialClient {
  private config: CranialConfig;

  constructor(config: CranialConfig) {
    if (!config.baseUrl) {
      throw new Error('baseUrl is required');
    }
    if (!config.apiKey) {
      throw new Error('apiKey is required');
    }
    this.config = {
      ...config,
      baseUrl: config.baseUrl.replace(/\/+$/, ''),
      timeoutMs: config.timeoutMs || 30000,
    };
  }

  public static create(config: CranialConfig): CranialClient {
    return new CranialClient(config);
  }

  public async checkHealth(): Promise<HealthResponse> {
    return this.request<HealthResponse>('GET', '/api/v1/integration/health');
  }

  public async echo(signer?: SignedContextSigner, payload?: unknown): Promise<EchoResponse> {
    return this.request<EchoResponse>('POST', '/api/v1/integration/echo', payload, signer);
  }

  public async getCatalog(signer?: SignedContextSigner): Promise<Record<string, unknown>> {
    return this.request<Record<string, unknown>>('GET', '/api/v1/integration/catalog', undefined, signer);
  }

  public async executeTask(
    taskCode: string,
    inputs: Record<string, unknown>,
    signer?: SignedContextSigner
  ): Promise<TaskExecutionResult> {
    return this.request<TaskExecutionResult>(
      'POST',
      '/api/v1/orchestrator/execute',
      {
        task_code: taskCode,
        inputs: inputs || {},
        allow_tools: true,
      },
      signer
    );
  }

  public async chat(
    chatRequest: Record<string, unknown>,
    signer?: SignedContextSigner
  ): Promise<Record<string, unknown>> {
    return this.request<Record<string, unknown>>('POST', '/api/v1/models/chat', chatRequest, signer);
  }

  public async retrieveKnowledge(
    collectionCode: string,
    query: string,
    limit = 5,
    signer?: SignedContextSigner
  ): Promise<KnowledgeHit[]> {
    return this.request<KnowledgeHit[]>(
      'POST',
      '/api/v1/knowledge/retrieve',
      {
        collection_code: collectionCode,
        query,
        limit,
      },
      signer
    );
  }

  public async askKnowledge(
    collectionCode: string,
    question: string,
    signer?: SignedContextSigner
  ): Promise<KnowledgeAskResult> {
    return this.request<KnowledgeAskResult>(
      'POST',
      '/api/v1/knowledge/ask',
      {
        collection_code: collectionCode,
        question,
      },
      signer
    );
  }

  public async queryAnalytics(
    queryType: string,
    datasetCode: string,
    filters: Record<string, unknown> = {},
    signer?: SignedContextSigner
  ): Promise<AnalyticsQueryResult> {
    return this.request<AnalyticsQueryResult>(
      'POST',
      '/api/v1/analytics/query',
      {
        query_type: queryType,
        dataset_code: datasetCode,
        filters,
      },
      signer
    );
  }

  public async submitDocumentText(
    textContent: string,
    documentType: string,
    signer?: SignedContextSigner
  ): Promise<Record<string, unknown>> {
    return this.request<Record<string, unknown>>(
      'POST',
      '/api/v1/documents',
      {
        document_type: documentType,
        raw_text: textContent,
      },
      signer
    );
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    signer?: SignedContextSigner
  ): Promise<T> {
    const url = `${this.config.baseUrl}${path}`;
    const headers: Record<string, string> = {
      'X-Cranial-Api-Key': this.config.apiKey,
      Accept: 'application/json',
    };

    let bodyBytes: Uint8Array | undefined;
    let bodyString: string | undefined;

    if (body !== undefined && body !== null) {
      bodyString = typeof body === 'string' ? body : JSON.stringify(body);
      bodyBytes = new TextEncoder().encode(bodyString);
      headers['Content-Type'] = 'application/json';
    }

    if (signer && this.config.signingSecret) {
      const signedHeaders = await signer.generateHeaders(
        method,
        path,
        bodyBytes,
        this.config.signingSecret
      );
      headers['X-Cranial-Context'] = signedHeaders.contextHeader;
      headers['X-Cranial-Timestamp'] = signedHeaders.timestampHeader;
      headers['X-Cranial-Signature'] = signedHeaders.signatureHeader;
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeoutMs);

    try {
      const response = await fetch(url, {
        method,
        headers,
        body: bodyString,
        signal: controller.signal,
      });

      const responseText = await response.text();
      let parsed: CranialResponse<T>;

      try {
        parsed = JSON.parse(responseText);
      } catch {
        throw new Error(`CRANIAL API returned non-JSON response (status ${response.status}): ${responseText}`);
      }

      if (!response.ok) {
        const firstError = parsed.errors?.[0];
        const errCode = firstError?.code || `HTTP_${response.status}`;
        const errMsg = firstError?.message || response.statusText;
        throw new Error(`CRANIAL error (${errCode}): ${errMsg}`);
      }

      return (parsed.data !== undefined ? parsed.data : parsed) as T;
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

