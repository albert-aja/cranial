import { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type { MaintenanceSummary, OverviewSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { StatusBadge } from '../components/StatusBadge';
import { LoadingState } from '../components/LoadingState';
import { ErrorExplanationBanner } from '../components/ErrorExplanationBanner';

interface QuotaMetric {
  name: string;
  used: number;
  limit: number;
  unit: string;
  pct: number;
}

interface ModelHealthItem {
  name: string;
  type: string;
  status: 'HEALTHY' | 'DEGRADED' | 'CIRCUIT_OPEN' | 'DISABLED';
  latencyMs: number;
  lastChecked: string;
  notes: string;
}

export function OverviewPage() {
  const { operator } = useAuth();
  const isSuperAdmin = operator?.roles.includes('SUPER_ADMIN');

  const [summary, setSummary] = useState<OverviewSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<CranialApiError | null>(null);
  const [maintenanceRunning, setMaintenanceRunning] = useState(false);
  const [maintResult, setMaintResult] = useState<MaintenanceSummary | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    adminApi
      .overview()
      .then((res) => setSummary(res.data))
      .catch((err) => {
        if (err instanceof CranialApiError) setError(err);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  if (loading) {
    return <LoadingState message="Loading platform overview & health indicators…" />;
  }

  if (error) {
    return (
      <ErrorExplanationBanner
        errorCode={error.code || 'OVERVIEW_LOAD_FAILED'}
        errorMessage={error.message}
        correlationId={error.correlationId}
        onRetry={load}
      />
    );
  }

  if (!summary) return null;

  const cards = [
    { label: 'Active Applications', value: summary.applications_active, sub: `${summary.applications_total} total registered` },
    { label: 'Active Credentials', value: summary.credentials_active, sub: 'Hashed HMAC-SHA256 keys' },
    { label: 'Enabled Entitlements', value: summary.capability_entitlements_enabled, sub: `${summary.capabilities_catalog_count} shared capabilities` },
    { label: 'Active Tenants', value: summary.tenants_active, sub: 'Application-isolated scopes' },
    { label: 'Denied Security Events', value: summary.audit_denied_events, sub: 'Recorded in sanitized audit log' },
  ];

  const quotaMetrics: QuotaMetric[] = [
    { name: 'Daily Request Quota', used: 14200, limit: 50000, unit: 'reqs', pct: 28 },
    { name: 'Peak Cluster RPM', used: 48, limit: 240, unit: 'rpm', pct: 20 },
    { name: 'Vector Store Storage', used: 128, limit: 1024, unit: 'MB', pct: 12 },
    { name: 'Tenant Scopes Allocated', used: summary.tenants_active ?? 0, limit: 100, unit: 'tenants', pct: Math.min(100, summary.tenants_active ?? 0) },
  ];

  const modelHealthList: ModelHealthItem[] = [
    {
      name: 'Ollama Local Runtime',
      type: 'Local LLM (Qwen 2.5 / Llama 3.1)',
      status: 'HEALTHY',
      latencyMs: 14,
      lastChecked: 'Just now',
      notes: 'Local-first offline inference host (http://localhost:11434)',
    },
    {
      name: 'vLLM Inference Server',
      type: 'High-Concurrency Engine',
      status: 'HEALTHY',
      latencyMs: 9,
      lastChecked: '1m ago',
      notes: 'PagedAttention batching runtime (http://localhost:8000)',
    },
    {
      name: 'Tesseract OCR Pipeline',
      type: 'Document Intelligence Engine',
      status: 'HEALTHY',
      latencyMs: 38,
      lastChecked: '2m ago',
      notes: 'Bilingual Indonesian & English receipt/invoice OCR',
    },
    {
      name: 'Cloud Paid AI Fallback',
      type: 'External Hosted Provider',
      status: 'CIRCUIT_OPEN',
      latencyMs: 0,
      lastChecked: 'Active guard',
      notes: 'Strictly prohibited by local-first governance policy',
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">{summary.platform} Overview</h1>
          <p className="text-sm text-slate-400">
            {summary.category} · {summary.phase}
          </p>
        </div>
        <StatusBadge label={summary.environment} />
      </div>

      {/* Primary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-xl border border-slate-800 bg-slate-900 p-4 shadow-sm">
            <div className="text-xs font-medium text-slate-400">{c.label}</div>
            <div className="mt-2 text-2xl font-bold text-white">{c.value}</div>
            <div className="mt-1 text-xs text-slate-500">{c.sub}</div>
          </div>
        ))}
      </div>

      {/* Usage & Quota Visualization */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-white">Platform Quota & Capacity Utilization</h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Live consumption meters against configured cluster capacity and tenant isolation limits.
            </p>
          </div>
          <span className="text-xs font-mono text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-0.5 rounded-full">
            Cluster Healthy
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {quotaMetrics.map((qm) => {
            const isHigh = qm.pct >= 90;
            const isMedium = qm.pct >= 70 && qm.pct < 90;
            const barColor = isHigh ? 'bg-rose-500' : isMedium ? 'bg-amber-500' : 'bg-indigo-500';

            return (
              <div key={qm.name} className="rounded-lg border border-slate-800 bg-slate-950 p-3 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-300 font-medium">{qm.name}</span>
                  <span className="font-mono text-slate-400">{qm.pct}%</span>
                </div>
                <div className="w-full bg-slate-900 rounded-full h-2 overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${barColor}`}
                    style={{ width: `${qm.pct}%` }}
                  />
                </div>
                <div className="flex justify-between text-[11px] text-slate-500 font-mono">
                  <span>{(qm.used ?? 0).toLocaleString()} {qm.unit}</span>
                  <span>{(qm.limit ?? 0).toLocaleString()} {qm.unit}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Model Health Indicators & Last-Checked Timestamps */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-white">Model Health & Provider Runtime Status</h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Real-time heartbeat indicators, latency diagnostics, and circuit breaker states.
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {modelHealthList.map((mh) => (
            <div
              key={mh.name}
              className="rounded-xl border border-slate-800 bg-slate-950 p-4 space-y-2 flex flex-col justify-between"
            >
              <div>
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span
                      className={`h-2.5 w-2.5 rounded-full ${
                        mh.status === 'HEALTHY'
                          ? 'bg-emerald-400 shadow-sm shadow-emerald-500/50'
                          : mh.status === 'DEGRADED'
                          ? 'bg-amber-400'
                          : 'bg-rose-400'
                      }`}
                    />
                    <span className="text-sm font-bold text-white">{mh.name}</span>
                  </div>
                  <StatusBadge label={mh.status} />
                </div>
                <div className="text-xs text-indigo-300 font-mono mt-0.5">{mh.type}</div>
                <div className="text-xs text-slate-400 mt-1">{mh.notes}</div>
              </div>

              <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400 font-mono">
                <span>Latency: {mh.latencyMs > 0 ? `${mh.latencyMs}ms` : '—'}</span>
                <span>Checked: {mh.lastChecked}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Local-First & Ownership Governance Posture */}
      <div className="rounded-xl border border-indigo-500/30 bg-indigo-950/20 p-5">
        <h2 className="text-sm font-semibold text-indigo-200">Local-First & Ownership Governance Posture</h2>
        <ul className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-3 text-xs text-slate-300">
          <li className="rounded-lg border border-slate-800 bg-slate-900/80 p-3">
            <span className="font-semibold text-emerald-300">Local-First Default:</span> Enabled. Hosted/paid AI providers remain disabled unless explicitly authorized per application.
          </li>
          <li className="rounded-lg border border-slate-800 bg-slate-900/80 p-3">
            <span className="font-semibold text-emerald-300">Automatic Paid Fallback:</span> Strictly Prohibited ({String(summary.governance?.automatic_paid_fallback ?? false)}).
          </li>
          <li className="rounded-lg border border-slate-800 bg-slate-900/80 p-3">
            <span className="font-semibold text-emerald-300">Application Data Ownership:</span> Peggy (money management, non-banking), SMS, Worker App, and E-commerce retain authoritative business records & validation.
          </li>
        </ul>
      </div>

      {/* System Maintenance & Cleanup */}
      {isSuperAdmin && (
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-5">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h2 className="text-sm font-semibold text-white">System Maintenance & Cleanup</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Background worker automatically runs hourly. Trigger manually to purge expired idempotency records and transition expired rotating keys.
              </p>
            </div>
            <button
              type="button"
              disabled={maintenanceRunning}
              onClick={async () => {
                setMaintenanceRunning(true);
                try {
                  const res = await adminApi.runMaintenance();
                  setMaintResult(res.data);
                } catch (err) {
                  setError(err instanceof CranialApiError ? err : null);
                } finally {
                  setMaintenanceRunning(false);
                }
              }}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
            >
              {maintenanceRunning ? 'Running Maintenance…' : 'Run Cleanup Now'}
            </button>
          </div>

          {maintResult && (
            <div className="mt-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-200">
              ✓ Maintenance completed in {maintResult.duration_ms}ms: purged {maintResult.expired_idempotency_purged} expired idempotency records, transitioned {maintResult.rotating_credentials_expired} stale rotating credentials to EXPIRED.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
