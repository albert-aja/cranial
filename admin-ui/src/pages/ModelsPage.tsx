import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { useLanguage } from '../i18n/LanguageContext';
import type {
  ProviderView,
  ProviderHealth,
  ModelView,
  RoutingPolicyView,
  SimulationResult,
  UsageSummaryView,
  UsageRecordView,
  ApplicationView,
  ModelWarmupResult,
  ChatCompletionResponse,
  HardwareUtilizationSnapshot,
} from '../api/types';
import { StatusBadge } from '../components/StatusBadge';

type Tab = 'providers' | 'models' | 'routing' | 'simulator' | 'hardware' | 'playground' | 'usage';

export default function ModelsPage() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<Tab>('providers');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Data states
  const [providers, setProviders] = useState<ProviderView[]>([]);
  const [models, setModels] = useState<ModelView[]>([]);
  const [policies, setPolicies] = useState<RoutingPolicyView[]>([]);
  const [summary, setSummary] = useState<UsageSummaryView | null>(null);
  const [recentUsage, setRecentUsage] = useState<UsageRecordView[]>([]);
  const [applications, setApplications] = useState<ApplicationView[]>([]);

  // Hardware metrics state
  const [hardware, setHardware] = useState<HardwareUtilizationSnapshot | null>(null);
  const [hwLoading, setHwLoading] = useState(false);

  // Health check states
  const [healthResults, setHealthResults] = useState<Record<string, ProviderHealth>>({});
  const [checkingHealthId, setCheckingHealthId] = useState<string | null>(null);
  const [resettingCircuitId, setResettingCircuitId] = useState<string | null>(null);
  const [circuitNotice, setCircuitNotice] = useState<string | null>(null);

  // Warmup states
  const [warmupStates, setWarmupStates] = useState<
    Record<string, { loading: boolean; result?: ModelWarmupResult; error?: string }>
  >({});

  // Simulator state
  const [simAppCode, setSimAppCode] = useState('');
  const [simCapCode, setSimCapCode] = useState('conversational_intelligence');
  const [simTaskCode, setSimTaskCode] = useState('');
  const [simSensitivity, setSimSensitivity] = useState<'PUBLIC' | 'INTERNAL' | 'RESTRICTED'>('RESTRICTED');
  const [simLoading, setSimLoading] = useState(false);
  const [simResult, setSimResult] = useState<SimulationResult | null>(null);

  // Playground state
  const [pgAppId, setPgAppId] = useState('');
  const [pgModelOverride, setPgModelOverride] = useState('');
  const [pgCapability, setPgCapability] = useState('conversational_intelligence');
  const [pgSensitivity, setPgSensitivity] = useState<'PUBLIC' | 'INTERNAL' | 'RESTRICTED'>('RESTRICTED');
  const [pgStreaming, setPgStreaming] = useState(true);
  const [pgSystemPrompt, setPgSystemPrompt] = useState(
    'You are CRANIAL universal intelligence platform assistant. Be accurate, concise, and helpful.'
  );
  const [pgUserPrompt, setPgUserPrompt] = useState(
    'Explain how CRANIAL local-first AI orchestration ensures complete privacy and zero data leakage.'
  );
  const [pgLoading, setPgLoading] = useState(false);
  const [pgStreamedText, setPgStreamedText] = useState('');
  const [pgResult, setPgResult] = useState<ChatCompletionResponse | null>(null);
  const [pgError, setPgError] = useState<string | null>(null);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [pRes, mRes, polRes, sumRes, uRes, appRes] = await Promise.all([
        api.listProviders(),
        api.listModels(),
        api.listRoutingPolicies(),
        api.getUsageSummary(),
        api.listUsageRecords(undefined, 25),
        api.listApplications({}),
      ]);

      if (pRes.status === 'ok') setProviders(pRes.data);
      if (mRes.status === 'ok') {
        setModels(mRes.data);
        if (mRes.data.length > 0 && !pgModelOverride) {
          const firstLocal = mRes.data.find((m) => m.is_local);
          setPgModelOverride(firstLocal ? firstLocal.model_identifier : mRes.data[0].model_identifier);
        }
      }
      if (polRes.status === 'ok') setPolicies(polRes.data);
      if (sumRes.status === 'ok') setSummary(sumRes.data);
      if (uRes.status === 'ok') setRecentUsage(uRes.data);
      if (appRes.status === 'ok') {
        setApplications(appRes.data);
        if (appRes.data.length > 0) {
          if (!simAppCode) setSimAppCode(appRes.data[0].code);
          if (!pgAppId) setPgAppId(appRes.data[0].id);
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load model infrastructure data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const loadHardwareMetrics = async () => {
    try {
      setHwLoading(true);
      const res = await api.getHardwareMetrics();
      if (res.status === 'ok') {
        setHardware(res.data);
      }
    } catch (err) {
      console.error('Failed to load hardware metrics:', err);
    } finally {
      setHwLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'hardware') {
      void loadHardwareMetrics();
      const interval = setInterval(() => {
        void loadHardwareMetrics();
      }, 3000);
      return () => clearInterval(interval);
    }
  }, [activeTab]);

  const handleCheckHealth = async (providerId: string) => {
    try {
      setCheckingHealthId(providerId);
      const res = await api.checkProviderHealth(providerId);
      if (res.status === 'ok') {
        setHealthResults((prev) => ({ ...prev, [providerId]: res.data }));
      }
    } catch (err) {
      setHealthResults((prev) => ({
        ...prev,
        [providerId]: {
          provider_code: 'error',
          reachable: false,
          latency_ms: 0,
          message: err instanceof Error ? err.message : 'Health check failed',
        },
      }));
    } finally {
      setCheckingHealthId(null);
    }
  };

  const handleResetCircuit = async (providerId: string) => {
    try {
      setResettingCircuitId(providerId);
      setCircuitNotice(null);
      const res = await api.resetCircuitBreaker(providerId);
      setCircuitNotice(res.data.message || 'Circuit breaker reset successfully');
      await handleCheckHealth(providerId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to reset circuit breaker');
    } finally {
      setResettingCircuitId(null);
    }
  };

  const handleWarmup = async (modelId: string) => {
    try {
      setWarmupStates((prev) => ({ ...prev, [modelId]: { loading: true } }));
      const res = await api.warmupModel(modelId);
      setWarmupStates((prev) => ({
        ...prev,
        [modelId]: { loading: false, result: res.data },
      }));
    } catch (err) {
      setWarmupStates((prev) => ({
        ...prev,
        [modelId]: { loading: false, error: err instanceof Error ? err.message : 'Warmup failed' },
      }));
    }
  };

  const handleSimulate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!simAppCode.trim()) return;

    try {
      setSimLoading(true);
      const res = await api.simulateRouting({
        application_code: simAppCode.trim(),
        capability_code: simCapCode.trim(),
        task_code: simTaskCode.trim() || undefined,
        data_sensitivity: simSensitivity,
      });
      if (res.status === 'ok') {
        setSimResult(res.data);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Simulation failed');
    } finally {
      setSimLoading(false);
    }
  };

  const handleRunPlayground = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pgUserPrompt.trim()) return;

    setPgLoading(true);
    setPgError(null);
    setPgStreamedText('');
    setPgResult(null);

    const messages = [];
    if (pgSystemPrompt.trim()) {
      messages.push({ role: 'system', content: pgSystemPrompt.trim() });
    }
    messages.push({ role: 'user', content: pgUserPrompt.trim() });

    const payload = {
      application_id: pgAppId || undefined,
      model_override: pgModelOverride || undefined,
      capability: pgCapability,
      data_sensitivity: pgSensitivity,
      messages,
    };

    if (pgStreaming) {
      try {
        await api.playgroundStream(payload, {
          onChunk: (chunk: string) => {
            setPgStreamedText((prev) => prev + chunk);
          },
          onDone: (data: ChatCompletionResponse) => {
            setPgResult(data);
            setPgLoading(false);
            loadData();
          },
          onError: (err: Error) => {
            setPgError(err.message);
            setPgLoading(false);
          },
        });
      } catch (err) {
        setPgError(err instanceof Error ? err.message : 'Streaming request failed');
        setPgLoading(false);
      }
    } else {
      try {
        const res = await api.playgroundChat(payload);
        if (res.status === 'ok') {
          setPgResult(res.data);
          setPgStreamedText(res.data.message?.content || '');
          loadData();
        }
      } catch (err) {
        setPgError(err instanceof Error ? err.message : 'Execution failed');
      } finally {
        setPgLoading(false);
      }
    }
  };

  const localRatio =
    summary && summary.total_requests > 0
      ? Math.round((summary.local_requests / summary.total_requests) * 100)
      : 100;

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">{t('models.title', 'AI Models & Providers')}</h1>
          <p className="text-sm text-slate-500 mt-1">
            {t('models.subtitle', 'Local-first runtimes, open weight models, zero-paid-fallback routing, and telemetry')}
          </p>
        </div>
        <button
          onClick={loadData}
          disabled={loading}
          className="inline-flex items-center px-3.5 py-2 text-sm font-medium rounded-lg text-slate-700 bg-white border border-slate-300 hover:bg-slate-50 shadow-sm transition-colors"
        >
          {loading ? t('action.pinging', 'Refreshing...') : t('action.refreshAll', 'Refresh All')}
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-sm flex justify-between items-center">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="font-bold ml-2">
            &times;
          </button>
        </div>
      )}

      {circuitNotice && (
        <div className="p-4 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-700 text-sm flex justify-between items-center">
          <span>{circuitNotice}</span>
          <button onClick={() => setCircuitNotice(null)} className="font-bold ml-2">
            &times;
          </button>
        </div>
      )}

      {/* Metrics Banner */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('models.requests', 'AI Requests')}</div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-slate-900">{summary?.total_requests ?? 0}</span>
            <span className="text-xs font-medium text-emerald-600">
              {summary?.local_requests ?? 0} {t('models.local', 'local')}
            </span>
          </div>
          <div className="mt-1 text-xs text-slate-400">
            {summary?.hosted_requests ?? 0} {t('models.hosted', 'hosted/cloud')}
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('models.localRatio', 'Local Execution Ratio')}</div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-slate-900">{localRatio}%</span>
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
              {t('models.localFirst', 'Local First')}
            </span>
          </div>
          <div className="mt-1 text-xs text-slate-400">{t('models.zeroCloudLeakage', 'Zero cloud leakage by default')}</div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('models.totalTokens', 'Total Tokens')}</div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-slate-900">
              {summary ? summary.total_tokens.toLocaleString() : '0'}
            </span>
          </div>
          <div className="mt-1 text-xs text-slate-400">
            {summary ? summary.total_prompt_tokens.toLocaleString() : 0} in /{' '}
            {summary ? summary.total_completion_tokens.toLocaleString() : 0} out
          </div>
        </div>

        <div
          className={`p-5 rounded-xl border shadow-sm ${
            summary && summary.fallback_blocked_count > 0 ? 'bg-amber-50 border-amber-200' : 'bg-white border-slate-200'
          }`}
        >
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('models.blockedFallbacks', 'Blocked Paid Fallbacks')}</div>
          <div className="mt-2 flex items-baseline gap-2">
            <span
              className={`text-2xl font-bold ${
                summary && summary.fallback_blocked_count > 0 ? 'text-amber-700' : 'text-slate-900'
              }`}
            >
              {summary?.fallback_blocked_count ?? 0}
            </span>
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700">
              {t('models.policyEnforced', 'Policy Enforced')}
            </span>
          </div>
          <div className="mt-1 text-xs text-slate-500">{t('models.noSilentCharges', 'No silent external charges')}</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-slate-200">
        <nav className="flex space-x-8" aria-label="Tabs">
          {[
            { id: 'providers', label: `${t('models.tab.providers', 'Providers')} (${providers.length})` },
            { id: 'models', label: `${t('models.tab.models', 'Models')} (${models.length})` },
            { id: 'routing', label: `${t('models.tab.routing', 'Routing Policies')} (${policies.length})` },
            { id: 'simulator', label: t('models.tab.simulator', 'Policy Simulator') },
            { id: 'hardware', label: t('models.tab.hardware', 'Hardware & GPU') },
            { id: 'playground', label: t('models.tab.playground', 'Model Playground') },
            { id: 'usage', label: `${t('models.tab.usage', 'Recent Usage')} (${recentUsage.length})` },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as Tab)}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
                activeTab === tab.id
                  ? 'border-indigo-600 text-indigo-600'
                  : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab 1: Providers */}
      {activeTab === 'providers' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 sm:p-6 border-b border-slate-200 flex justify-between items-center">
            <div>
              <h2 className="text-base font-semibold text-slate-900">AI Execution Runtimes</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Local inference engines (Ollama) and test harnesses powering CRANIAL
              </p>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-6 py-3 font-semibold">Name / Code</th>
                  <th className="px-6 py-3 font-semibold">Type</th>
                  <th className="px-6 py-3 font-semibold">Endpoint</th>
                  <th className="px-6 py-3 font-semibold">Timeout</th>
                  <th className="px-6 py-3 font-semibold">Status</th>
                  <th className="px-6 py-3 font-semibold">Health Status</th>
                  <th className="px-6 py-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {providers.map((p) => {
                  const health = healthResults[p.id];
                  const isChecking = checkingHealthId === p.id;
                  const isResetting = resettingCircuitId === p.id;
                  return (
                    <tr key={p.id} className="hover:bg-slate-50/50">
                      <td className="px-6 py-4">
                        <div className="font-semibold text-slate-900">{p.name}</div>
                        <div className="text-xs text-slate-500 font-mono">{p.code}</div>
                      </td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                            p.provider_type === 'LOCAL'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-indigo-100 text-indigo-800'
                          }`}
                        >
                          {p.provider_type}
                        </span>
                      </td>
                      <td className="px-6 py-4 font-mono text-xs text-slate-600">{p.base_url}</td>
                      <td className="px-6 py-4 text-xs text-slate-600">{p.timeout_seconds}s</td>
                      <td className="px-6 py-4">
                        <StatusBadge label={p.status} />
                      </td>
                      <td className="px-6 py-4">
                        {health ? (
                          <div className="text-xs">
                            <span
                              className={`inline-flex items-center px-2 py-0.5 rounded font-medium ${
                                health.reachable
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : 'bg-rose-100 text-rose-800'
                              }`}
                            >
                              {health.reachable ? `Operational (${health.latency_ms}ms)` : 'Unreachable'}
                            </span>
                            <div className="text-slate-500 text-[11px] mt-0.5 max-w-xs break-words">
                              {health.message}
                            </div>
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400">Untested</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="inline-flex gap-2">
                          <button
                            onClick={() => handleCheckHealth(p.id)}
                            disabled={isChecking}
                            className="px-2.5 py-1 text-xs font-medium rounded-md border border-slate-300 hover:bg-slate-100 text-slate-700 transition-colors"
                          >
                            {isChecking ? 'Pinging...' : 'Check Health'}
                          </button>
                          <button
                            onClick={() => handleResetCircuit(p.id)}
                            disabled={isResetting}
                            className="px-2.5 py-1 text-xs font-medium rounded-md border border-amber-300 bg-amber-50 hover:bg-amber-100 text-amber-800 transition-colors"
                            title="Reset circuit breaker to CLOSED"
                          >
                            {isResetting ? 'Resetting...' : 'Reset Circuit'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: Models */}
      {activeTab === 'models' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 sm:p-6 border-b border-slate-200">
            <h2 className="text-base font-semibold text-slate-900">Registered Models</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Available LLMs, embeddings, and multi-modal models mapped to capabilities
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-6 py-3 font-semibold">Model Identifier</th>
                  <th className="px-6 py-3 font-semibold">Modality</th>
                  <th className="px-6 py-3 font-semibold">Context Window</th>
                  <th className="px-6 py-3 font-semibold">Capabilities</th>
                  <th className="px-6 py-3 font-semibold">Local/Hosted</th>
                  <th className="px-6 py-3 font-semibold">Cost / 1M Tokens</th>
                  <th className="px-6 py-3 font-semibold">Status</th>
                  <th className="px-6 py-3 font-semibold text-right">Warmup</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {models.map((m) => {
                  const warmup = warmupStates[m.id];
                  return (
                    <tr key={m.id} className="hover:bg-slate-50/50">
                      <td className="px-6 py-4">
                        <div className="font-semibold text-slate-900">{m.display_name}</div>
                        <div className="text-xs font-mono text-slate-500">{m.model_identifier}</div>
                        <div className="text-[11px] text-slate-400">via {m.provider_name}</div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-800">
                          {m.modality}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-xs text-slate-600">
                        <div>{m.context_window_tokens.toLocaleString()} tokens</div>
                        {m.max_output_tokens > 0 && (
                          <div className="text-[11px] text-slate-400">
                            max out: {m.max_output_tokens.toLocaleString()}
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1 max-w-xs">
                          {m.capabilities.map((cap) => (
                            <span
                              key={cap}
                              className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono bg-indigo-50 text-indigo-700 border border-indigo-100"
                            >
                              {cap}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        {m.is_local ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 text-emerald-800">
                            Local First
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800">
                            Hosted / Cloud
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-xs font-mono text-slate-600">
                        {m.is_local ? (
                          <span className="text-emerald-600 font-semibold">$0.00 (Self-hosted)</span>
                        ) : (
                          `$${((m.cost_per_million_input_cents || 0) / 100).toFixed(2)} in / $${(
                            (m.cost_per_million_output_cents || 0) / 100
                          ).toFixed(2)} out`
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge label={m.status} />
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex flex-col items-end gap-1">
                          <button
                            onClick={() => handleWarmup(m.id)}
                            disabled={warmup?.loading}
                            className="px-2.5 py-1 text-xs font-medium rounded-md border border-indigo-300 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 transition-colors"
                          >
                            {warmup?.loading ? 'Warming...' : 'Probe Warmup'}
                          </button>
                          {warmup?.result && (
                            <span className="text-[10px] text-emerald-600 font-mono">
                              ✓ Warm ({warmup.result.duration_ms}ms)
                            </span>
                          )}
                          {warmup?.error && (
                            <span className="text-[10px] text-rose-500 max-w-[120px] truncate" title={warmup.error}>
                              ✗ {warmup.error}
                            </span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 3: Routing Policies */}
      {activeTab === 'routing' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="p-4 sm:p-6 border-b border-slate-200">
            <h2 className="text-base font-semibold text-slate-900">AI Routing Policies</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Strict rules determining primary and fallback model dispatching per capability and task
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-6 py-3 font-semibold">Policy Name</th>
                  <th className="px-6 py-3 font-semibold">Capability / Task</th>
                  <th className="px-6 py-3 font-semibold">Preferred Model</th>
                  <th className="px-6 py-3 font-semibold">Fallback Model</th>
                  <th className="px-6 py-3 font-semibold">Sensitivity</th>
                  <th className="px-6 py-3 font-semibold">Active</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {policies.map((pol) => (
                  <tr key={pol.id} className="hover:bg-slate-50/50">
                    <td className="px-6 py-4 font-semibold text-slate-900">{pol.name}</td>
                    <td className="px-6 py-4">
                      <div className="font-mono text-xs text-indigo-700">{pol.capability_code}</div>
                      {pol.task_code && (
                        <div className="text-[11px] font-mono text-slate-500">task: {pol.task_code}</div>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-medium text-slate-800">{pol.preferred_model_identifier}</div>
                      <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-medium bg-emerald-100 text-emerald-800">
                        {pol.preferred_model_local ? 'Local' : 'Cloud'}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      {pol.fallback_model_identifier ? (
                        <div>
                          <div className="font-medium text-slate-800">{pol.fallback_model_identifier}</div>
                          <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] font-medium bg-slate-100 text-slate-700">
                            {pol.fallback_model_local ? 'Local' : 'Cloud (Blocked unless permitted)'}
                          </span>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">None (Fail Closed)</span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-slate-100 text-slate-700">
                        {pol.allowed_data_sensitivity}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge label={pol.is_active ? 'ACTIVE' : 'DISABLED'} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 4: Policy Simulator */}
      {activeTab === 'simulator' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm">
            <h2 className="text-base font-semibold text-slate-900 mb-1">Simulate Routing Decision</h2>
            <p className="text-xs text-slate-500 mb-6">
              Evaluate how CRANIAL local-first governance resolves models and enforces zero-paid-fallback rules
            </p>

            <form onSubmit={handleSimulate} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Application</label>
                <select
                  value={simAppCode}
                  onChange={(e) => setSimAppCode(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  required
                >
                  {applications.map((a) => (
                    <option key={a.id} value={a.code}>
                      {a.name} ({a.code}) — {a.settings.allow_hosted_providers ? 'Hosted Allowed' : 'Local Only'}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Capability</label>
                <select
                  value={simCapCode}
                  onChange={(e) => setSimCapCode(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  required
                >
                  <option value="conversational_intelligence">conversational_intelligence</option>
                  <option value="document_intelligence">document_intelligence</option>
                  <option value="knowledge_intelligence">knowledge_intelligence</option>
                  <option value="data_intelligence">data_intelligence</option>
                  <option value="customer_intelligence">customer_intelligence</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Task Code (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. greeting, summarize_report"
                  value={simTaskCode}
                  onChange={(e) => setSimTaskCode(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Data Sensitivity</label>
                <div className="grid grid-cols-3 gap-2">
                  {(['PUBLIC', 'INTERNAL', 'RESTRICTED'] as const).map((lvl) => (
                    <button
                      key={lvl}
                      type="button"
                      onClick={() => setSimSensitivity(lvl)}
                      className={`px-3 py-2 text-xs font-medium rounded-lg border transition-all ${
                        simSensitivity === lvl
                          ? 'bg-indigo-50 border-indigo-500 text-indigo-700 font-bold'
                          : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      {lvl}
                    </button>
                  ))}
                </div>
              </div>

              <button
                type="submit"
                disabled={simLoading}
                className="w-full mt-4 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-sm rounded-lg shadow-sm transition-colors"
              >
                {simLoading ? 'Simulating...' : 'Run Governance Simulation'}
              </button>
            </form>
          </div>

          <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm">
            <h2 className="text-base font-semibold text-slate-900 mb-1">Simulation Verdict</h2>
            <p className="text-xs text-slate-500 mb-6">Resulting model selection, privacy checks, and fallback policy</p>

            {simResult ? (
              <div className="space-y-4">
                <div
                  className={`p-4 rounded-xl border ${
                    simResult.allowed
                      ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                      : 'bg-rose-50 border-rose-200 text-rose-900'
                  }`}
                >
                  <div className="flex items-center gap-2 font-bold text-sm">
                    <span>{simResult.allowed ? '✓ Route Approved' : '✗ Route Blocked by Policy'}</span>
                  </div>
                  <p className="text-xs mt-1">
                    {simResult.allowed ? simResult.decision_reason : simResult.blocked_reason}
                  </p>
                </div>

                <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 space-y-3 text-xs">
                  <div className="flex justify-between border-b border-slate-200 pb-2">
                    <span className="text-slate-500">Matching Policy:</span>
                    <span className="font-semibold text-slate-900">{simResult.policy_name}</span>
                  </div>
                  <div className="flex justify-between border-b border-slate-200 pb-2">
                    <span className="text-slate-500">Preferred Model:</span>
                    <span className="font-semibold text-slate-900 font-mono">
                      {simResult.preferred_model_identifier || 'None'} (
                      {simResult.preferred_model_local ? 'Local' : 'Cloud'})
                    </span>
                  </div>
                  <div className="flex justify-between border-b border-slate-200 pb-2">
                    <span className="text-slate-500">Configured Fallback:</span>
                    <span className="font-semibold text-slate-900 font-mono">
                      {simResult.fallback_model_identifier || 'None'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Fallback Permission:</span>
                    <span
                      className={`font-semibold px-2 py-0.5 rounded text-[11px] ${
                        simResult.paid_fallback_allowed
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}
                    >
                      {simResult.paid_fallback_allowed
                        ? 'Permitted'
                        : 'Blocked (Zero-Paid-Fallback Active)'}
                    </span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="h-64 flex flex-col items-center justify-center text-slate-400 text-xs border border-dashed border-slate-200 rounded-xl">
                <span>Select parameters and click "Run Governance Simulation"</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 5: Hardware & GPU Metrics */}
      {activeTab === 'hardware' && (
        <div className="space-y-6">
          <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-slate-900">Local Compute Hardware & GPU Telemetry</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Real-time CPU cores, system RAM, JVM memory, and local GPU VRAM / temperature metrics (auto-refreshes every 3s)
              </p>
            </div>
            <button
              onClick={() => void loadHardwareMetrics()}
              disabled={hwLoading}
              className="px-3 py-1.5 text-xs font-medium border border-slate-300 rounded-lg text-slate-700 hover:bg-slate-50 transition"
            >
              {hwLoading ? 'Refreshing...' : 'Refresh Now'}
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">System CPU Load</div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold text-slate-900">
                  {hardware && hardware.cpu_system_load_percent >= 0
                    ? `${hardware.cpu_system_load_percent}%`
                    : 'N/A'}
                </span>
                <span className="text-xs text-slate-500">
                  ({hardware?.available_processors ?? 0} cores)
                </span>
              </div>
              <div className="mt-2 w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-indigo-600 h-1.5 rounded-full transition-all duration-300"
                  style={{ width: `${Math.min(100, Math.max(0, hardware?.cpu_system_load_percent ?? 0))}%` }}
                />
              </div>
            </div>

            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Process CPU (JVM)</div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold text-slate-900">
                  {hardware && hardware.cpu_process_load_percent >= 0
                    ? `${hardware.cpu_process_load_percent}%`
                    : 'N/A'}
                </span>
              </div>
              <div className="mt-2 w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-emerald-600 h-1.5 rounded-full transition-all duration-300"
                  style={{ width: `${Math.min(100, Math.max(0, hardware?.cpu_process_load_percent ?? 0))}%` }}
                />
              </div>
            </div>

            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">System RAM</div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold text-slate-900">
                  {hardware && hardware.system_memory_used_percent >= 0
                    ? `${hardware.system_memory_used_percent}%`
                    : 'N/A'}
                </span>
              </div>
              <div className="mt-1 text-xs text-slate-500">
                {hardware && hardware.system_used_memory_bytes >= 0
                  ? `${Math.round(hardware.system_used_memory_bytes / (1024 * 1024 * 1024))} GB / ${Math.round(
                      hardware.system_total_memory_bytes / (1024 * 1024 * 1024)
                    )} GB`
                  : 'N/A'}
              </div>
              <div className="mt-2 w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-sky-600 h-1.5 rounded-full transition-all duration-300"
                  style={{ width: `${Math.min(100, Math.max(0, hardware?.system_memory_used_percent ?? 0))}%` }}
                />
              </div>
            </div>

            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">JVM Heap Memory</div>
              <div className="mt-2 flex items-baseline gap-2">
                <span className="text-2xl font-bold text-slate-900">
                  {hardware
                    ? `${Math.round(hardware.jvm_used_memory_bytes / (1024 * 1024))} MB`
                    : 'N/A'}
                </span>
              </div>
              <div className="mt-1 text-xs text-slate-500">
                Max Heap: {hardware ? `${Math.round(hardware.jvm_max_memory_bytes / (1024 * 1024))} MB` : 'N/A'}
              </div>
              <div className="mt-2 w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-amber-500 h-1.5 rounded-full transition-all duration-300"
                  style={{
                    width: `${
                      hardware
                        ? Math.min(100, Math.round((hardware.jvm_used_memory_bytes / hardware.jvm_max_memory_bytes) * 100))
                        : 0
                    }%`,
                  }}
                />
              </div>
            </div>
          </div>

          {/* Local GPU Card */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
            <h3 className="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
              <span>Local GPU Accelerators (NVIDIA / VRAM)</span>
              {hardware?.gpus && hardware.gpus.length > 0 ? (
                <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800">
                  {hardware.gpus.length} Detected
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-600">
                  None Detected (CPU Mode)
                </span>
              )}
            </h3>

            {hardware?.gpus && hardware.gpus.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {hardware.gpus.map((gpu, idx) => (
                  <div key={idx} className="p-4 bg-slate-50 rounded-xl border border-slate-200 space-y-3">
                    <div className="flex justify-between items-start">
                      <div>
                        <div className="font-semibold text-slate-800 text-sm">{gpu.name}</div>
                        <div className="text-[11px] text-slate-500">Source: {gpu.source}</div>
                      </div>
                      {gpu.temperature_celsius != null && (
                        <span className="px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800">
                          {gpu.temperature_celsius}°C
                        </span>
                      )}
                    </div>

                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-slate-600">
                        <span>GPU Core Utilization:</span>
                        <span className="font-semibold">
                          {gpu.utilization_gpu_percent != null ? `${gpu.utilization_gpu_percent}%` : 'N/A'}
                        </span>
                      </div>
                      <div className="w-full bg-slate-200 rounded-full h-1.5 overflow-hidden">
                        <div
                          className="bg-emerald-600 h-1.5 rounded-full"
                          style={{ width: `${Math.min(100, Math.max(0, gpu.utilization_gpu_percent ?? 0))}%` }}
                        />
                      </div>
                    </div>

                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-slate-600">
                        <span>VRAM Memory:</span>
                        <span className="font-semibold">
                          {gpu.memory_used_bytes != null && gpu.memory_total_bytes != null
                            ? `${Math.round(gpu.memory_used_bytes / (1024 * 1024))} MB / ${Math.round(
                                gpu.memory_total_bytes / (1024 * 1024)
                              )} MB`
                            : gpu.utilization_memory_percent != null
                            ? `${gpu.utilization_memory_percent}%`
                            : 'N/A'}
                        </span>
                      </div>
                      <div className="w-full bg-slate-200 rounded-full h-1.5 overflow-hidden">
                        <div
                          className="bg-indigo-600 h-1.5 rounded-full"
                          style={{
                            width: `${
                              gpu.memory_used_bytes != null && gpu.memory_total_bytes != null && gpu.memory_total_bytes > 0
                                ? Math.min(100, Math.round((gpu.memory_used_bytes / gpu.memory_total_bytes) * 100))
                                : gpu.utilization_memory_percent ?? 0
                            }%`,
                          }}
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 text-xs text-slate-600">
                No discrete NVIDIA GPU reported via <code>nvidia-smi</code>. CRANIAL local inference operates with CPU threading or Ollama internal process offloading.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 6: Model Playground */}
      {activeTab === 'playground' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Playground Config Form */}
          <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
            <div>
              <h2 className="text-base font-semibold text-slate-900">{t('playground.title', 'Interactive Model Playground')}</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                {t('playground.subtitle', 'Test prompt execution, verify token pruning, and observe real-time SSE streaming')}
              </p>
            </div>

            <form onSubmit={handleRunPlayground} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">{t('playground.appContext', 'Application Context')}</label>
                  <select
                    value={pgAppId}
                    onChange={(e) => setPgAppId(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  >
                    {applications.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name} ({a.code})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">{t('playground.targetModel', 'Target Model')}</label>
                  <select
                    value={pgModelOverride}
                    onChange={(e) => setPgModelOverride(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  >
                    {models.map((m) => (
                      <option key={m.id} value={m.model_identifier}>
                        {m.display_name} ({m.model_identifier}) {m.is_local ? '• Local' : '• Cloud'}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">{t('playground.capability', 'Capability')}</label>
                <select
                  value={pgCapability}
                  onChange={(e) => setPgCapability(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                >
                  <option value="conversational_intelligence">conversational_intelligence</option>
                  <option value="document_intelligence">document_intelligence</option>
                  <option value="knowledge_intelligence">knowledge_intelligence</option>
                  <option value="data_intelligence">data_intelligence</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">{t('playground.dataSensitivity', 'Data Sensitivity')}</label>
                <div className="grid grid-cols-3 gap-2">
                  {(['PUBLIC', 'INTERNAL', 'RESTRICTED'] as const).map((lvl) => (
                    <button
                      key={lvl}
                      type="button"
                      onClick={() => setPgSensitivity(lvl)}
                      className={`px-3 py-2 text-xs font-medium rounded-lg border transition-all ${
                        pgSensitivity === lvl
                          ? 'bg-indigo-50 border-indigo-500 text-indigo-700 font-bold'
                          : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      {lvl}
                    </button>
                  ))}
                </div>
                {pgSensitivity === 'RESTRICTED' && (
                  <p className="mt-1.5 text-[11px] text-emerald-700 font-medium">
                    {t('playground.protectedNotice', '🛡️ Protected: RESTRICTED queries are pinned strictly to local-first zero-leakage models.')}
                  </p>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">{t('playground.systemPrompt', 'System Instructions')}</label>
                <textarea
                  rows={2}
                  value={pgSystemPrompt}
                  onChange={(e) => setPgSystemPrompt(e.target.value)}
                  className="w-full px-3 py-2 text-xs font-mono border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-semibold text-slate-700">{t('playground.userPrompt', 'User Prompt')}</label>
                  <div className="flex gap-1.5">
                    <button
                      type="button"
                      onClick={() => {
                        setPgSystemPrompt('You are CRANIAL universal intelligence platform assistant. Be accurate, concise, and helpful.');
                        setPgUserPrompt('Explain how CRANIAL local-first AI orchestration ensures complete privacy and zero data leakage for financial applications.');
                      }}
                      className="text-[10px] px-2 py-0.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded border border-slate-200 transition"
                    >
                      {t('playground.presetEn', '🇬🇧 English Preset')}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setPgSystemPrompt('Anda adalah asisten platform intelijen universal CRANIAL. Berikan panduan yang akurat, ringkas, dan bermanfaat dalam Bahasa Indonesia.');
                        setPgUserPrompt('Jelaskan bagaimana orkestrasi AI lokal-utama CRANIAL menjamin privasi penuh dan tanpa kebocoran data untuk aplikasi manajemen keuangan.');
                      }}
                      className="text-[10px] px-2 py-0.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded border border-indigo-200 transition"
                    >
                      {t('playground.presetId', '🇮🇩 Indonesian Preset')}
                    </button>
                  </div>
                </div>
                <textarea
                  rows={4}
                  value={pgUserPrompt}
                  onChange={(e) => setPgUserPrompt(e.target.value)}
                  placeholder="Enter message..."
                  required
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-between pt-2">
                <label className="flex items-center gap-2 text-xs font-medium text-slate-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={pgStreaming}
                    onChange={(e) => setPgStreaming(e.target.checked)}
                    className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 h-4 w-4"
                  />
                  <span>{t('playground.streamResponse', 'Stream Response (SSE chunk-by-chunk)')}</span>
                </label>

                <button
                  type="submit"
                  disabled={pgLoading}
                  className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-sm rounded-lg shadow-sm transition-colors flex items-center gap-2"
                >
                  {pgLoading ? (
                    <>
                      <svg
                        className="animate-spin h-4 w-4 text-white"
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                      >
                        <circle
                          className="opacity-25"
                          cx="12"
                          cy="12"
                          r="10"
                          stroke="currentColor"
                          strokeWidth="4"
                        ></circle>
                        <path
                          className="opacity-75"
                          fill="currentColor"
                          d="M4 12a8 8 0 018-8v8H4z"
                        ></path>
                      </svg>
                      <span>{t('action.executing', 'Executing...')}</span>
                    </>
                  ) : (
                    <span>{t('action.executePrompt', 'Execute Prompt')}</span>
                  )}
                </button>
              </div>
            </form>
          </div>

          {/* Playground Output Window */}
          <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <div>
              <div className="flex justify-between items-center mb-3">
                <h2 className="text-base font-semibold text-slate-900">Execution Output</h2>
                {pgLoading && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800 animate-pulse">
                    Streaming Active...
                  </span>
                )}
                {pgResult && !pgLoading && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 text-emerald-800">
                    Completed ({pgResult.duration_ms}ms)
                  </span>
                )}
              </div>

              {pgError && (
                <div className="mb-4 p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-lg">
                  {pgError}
                </div>
              )}

              {/* Streaming / Output display */}
              <div className="min-h-[260px] max-h-[420px] overflow-y-auto p-4 bg-slate-900 text-slate-100 rounded-xl font-mono text-xs whitespace-pre-wrap leading-relaxed shadow-inner">
                {pgStreamedText || (
                  <span className="text-slate-500 italic">
                    Output will stream here in real-time when prompt is executed.
                  </span>
                )}
              </div>
            </div>

            {/* Telemetry pill */}
            {pgResult && (
              <div className="mt-4 pt-4 border-t border-slate-200 grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                <div className="bg-slate-50 p-2 rounded-lg">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Model</div>
                  <div className="font-semibold text-slate-800 truncate">{pgResult.model}</div>
                </div>
                <div className="bg-slate-50 p-2 rounded-lg">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Duration</div>
                  <div className="font-semibold text-slate-800">{pgResult.duration_ms}ms</div>
                </div>
                <div className="bg-slate-50 p-2 rounded-lg">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Tokens</div>
                  <div className="font-semibold text-slate-800">
                    {pgResult.prompt_tokens} in / {pgResult.completion_tokens} out
                  </div>
                </div>
                <div className="bg-slate-50 p-2 rounded-lg">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Hardware</div>
                  <div className="font-semibold text-emerald-700">
                    {pgResult.is_local ? 'Local First ($0.00)' : 'Hosted Cloud'}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 6: Recent Usage */}
      {activeTab === 'usage' && (
        <div className="space-y-4">
          {/* Latency Percentiles Card */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('usage.p50', 'p50 Median Latency')}</div>
              <div className="mt-1 text-2xl font-bold text-slate-900">
                {summary?.p50_duration_ms ?? 0}
                <span className="text-sm font-normal text-slate-400 ml-1">ms</span>
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">50th percentile response time</div>
            </div>

            <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('usage.p95', 'p95 Tail Latency')}</div>
              <div className="mt-1 text-2xl font-bold text-indigo-600">
                {summary?.p95_duration_ms ?? 0}
                <span className="text-sm font-normal text-slate-400 ml-1">ms</span>
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">95th percentile worst-case</div>
            </div>

            <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('usage.p99', 'p99 Outlier Latency')}</div>
              <div className="mt-1 text-2xl font-bold text-amber-600">
                {summary?.p99_duration_ms ?? 0}
                <span className="text-sm font-normal text-slate-400 ml-1">ms</span>
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">99th percentile cold/queue time</div>
            </div>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-4 sm:p-6 border-b border-slate-200">
              <h2 className="text-base font-semibold text-slate-900">Recent AI Execution Records</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Live audit trail of model invocations, latency, tokens, and blocked fallback attempts
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                <thead className="bg-slate-50 text-slate-600">
                  <tr>
                    <th className="px-6 py-3 font-semibold">Timestamp</th>
                    <th className="px-6 py-3 font-semibold">Capability</th>
                    <th className="px-6 py-3 font-semibold">Type</th>
                    <th className="px-6 py-3 font-semibold">Tokens</th>
                    <th className="px-6 py-3 font-semibold">Duration</th>
                    <th className="px-6 py-3 font-semibold">Status</th>
                    <th className="px-6 py-3 font-semibold">Notes</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200">
                  {recentUsage.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-6 py-8 text-center text-slate-400 text-xs">
                        No usage records logged yet. Run chat or embeddings to generate activity.
                      </td>
                    </tr>
                  ) : (
                    recentUsage.map((u) => (
                      <tr key={u.id} className="hover:bg-slate-50/50">
                        <td className="px-6 py-4 text-xs font-mono text-slate-500">
                          {new Date(u.created_at).toLocaleTimeString()}
                        </td>
                        <td className="px-6 py-4">
                          <div className="font-mono text-xs text-indigo-700">{u.capability_code}</div>
                          {u.task_code && <div className="text-[11px] text-slate-400">{u.task_code}</div>}
                        </td>
                        <td className="px-6 py-4">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                              u.is_local ? 'bg-emerald-100 text-emerald-800' : 'bg-indigo-100 text-indigo-800'
                            }`}
                          >
                            {u.is_local ? 'Local' : 'Hosted'}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-xs text-slate-700">
                          {u.total_tokens.toLocaleString()} tokens
                          <div className="text-[11px] text-slate-400">
                            {u.prompt_tokens} in / {u.completion_tokens} out
                          </div>
                        </td>
                        <td className="px-6 py-4 text-xs text-slate-600">{u.duration_ms}ms</td>
                        <td className="px-6 py-4">
                          <StatusBadge label={u.status} />
                        </td>
                        <td className="px-6 py-4 text-xs text-slate-500 max-w-xs truncate">
                          {u.error_message ||
                            (u.estimated_cost_cents > 0
                              ? `$${(u.estimated_cost_cents / 100).toFixed(4)}`
                              : 'Zero Cost')}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
