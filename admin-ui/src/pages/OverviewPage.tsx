import { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type { MaintenanceSummary, OverviewSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { StatusBadge } from '../components/StatusBadge';

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
    return <div className="text-sm text-slate-400">Loading platform overview…</div>;
  }

  if (error) {
    return (
      <div role="alert" className="rounded-xl border border-rose-500/40 bg-rose-500/10 p-5 text-sm text-rose-200">
        <div className="font-semibold">Failed to load overview ({error.code})</div>
        <div className="mt-1">{error.message}</div>
        <div className="mt-1 text-xs text-rose-300/80">Correlation ID: {error.correlationId}</div>
        <button
          type="button"
          onClick={load}
          className="mt-3 rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-rose-500"
        >
          Retry
        </button>
      </div>
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

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">{summary.platform} Overview</h1>
          <p className="text-sm text-slate-400">
            {summary.category} · {summary.phase}
          </p>
        </div>
        <StatusBadge label={summary.environment} />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="text-xs font-medium text-slate-400">{c.label}</div>
            <div className="mt-2 text-2xl font-bold text-white">{c.value}</div>
            <div className="mt-1 text-xs text-slate-500">{c.sub}</div>
          </div>
        ))}
      </div>

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

