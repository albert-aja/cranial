import { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type { AuditLogView } from '../api/types';
import { StatusBadge } from '../components/StatusBadge';

export function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLogView[]>([]);
  const [outcome, setOutcome] = useState('');
  const [correlationId, setCorrelationId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminApi.searchAuditLogs({
        outcome: outcome || undefined,
        correlationId: correlationId || undefined,
        size: 30,
      });
      setLogs(res.data);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [outcome]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Audit & Security Operations</h1>
        <p className="text-sm text-slate-400">
          Inspect sanitized authentication, authorization, credential, and tenant-isolation records.
        </p>
      </div>

      <div className="flex flex-wrap gap-3">
        <select
          aria-label="Filter by outcome"
          value={outcome}
          onChange={(e) => setOutcome(e.target.value)}
          className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
        >
          <option value="">All Outcomes</option>
          <option value="SUCCESS">SUCCESS</option>
          <option value="DENIED">DENIED</option>
          <option value="FAILURE">FAILURE</option>
        </select>

        <input
          type="search"
          placeholder="Filter by correlation_id…"
          value={correlationId}
          onChange={(e) => setCorrelationId(e.target.value)}
          className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
        />
        <button
          type="button"
          onClick={load}
          className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-slate-200 hover:bg-slate-700"
        >
          Search
        </button>
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-200">
          {error}
        </div>
      )}

      {loading ? (
        <div className="text-sm text-slate-400">Loading audit trail…</div>
      ) : logs.length === 0 ? (
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400">
          No matching audit records found.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-slate-800 bg-slate-950/60 text-slate-400">
              <tr>
                <th className="px-4 py-3">Timestamp</th>
                <th className="px-4 py-3">Outcome</th>
                <th className="px-4 py-3">Action</th>
                <th className="px-4 py-3">Actor</th>
                <th className="px-4 py-3">Correlation ID</th>
                <th className="px-4 py-3">Sanitized Metadata</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/70">
              {logs.map((entry) => (
                <tr key={entry.id} className="hover:bg-slate-800/30">
                  <td className="px-4 py-3 whitespace-nowrap text-slate-400">
                    {new Date(entry.created_at).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge label={entry.outcome} />
                  </td>
                  <td className="px-4 py-3 font-medium text-slate-200">{entry.action}</td>
                  <td className="px-4 py-3 text-slate-300">
                    {entry.actor_type}:{entry.actor_id || '-'}
                  </td>
                  <td className="px-4 py-3 font-mono text-indigo-300">{entry.correlation_id}</td>
                  <td className="px-4 py-3 font-mono text-slate-400 max-w-xs truncate">
                    {JSON.stringify(entry.metadata)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

