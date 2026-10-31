import { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type { FeatureFlagView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { StatusBadge } from '../components/StatusBadge';

export function FeatureFlagsPage() {
  const { operator } = useAuth();
  const isSuperAdmin = operator?.roles.includes('SUPER_ADMIN');

  const [flags, setFlags] = useState<FeatureFlagView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editingFlag, setEditingFlag] = useState<FeatureFlagView | null>(null);
  const [editPercentage, setEditPercentage] = useState<number>(100);
  const [editAllowedApps, setEditAllowedApps] = useState<string>('');

  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean;
    title: string;
    description: string;
    action: () => Promise<void>;
  } | null>(null);

  const loadFlags = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminApi.listFeatureFlags();
      setFlags(res.data);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to load feature flags');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFlags();
  }, []);

  const handleToggle = (flag: FeatureFlagView) => {
    const nextState = !flag.enabled;
    setConfirmDialog({
      open: true,
      title: `${nextState ? 'Enable' : 'Disable'} feature flag '${flag.flag_key}'?`,
      description: nextState
        ? `Enables rollout for ${flag.name}.`
        : `Immediately disables ${flag.name} platform-wide.`,
      action: async () => {
        await adminApi.updateFeatureFlag(flag.flag_key, {
          enabled: nextState,
          rollout_percentage: flag.rollout_percentage,
          allowed_app_codes: flag.allowed_app_codes,
        });
        await loadFlags();
      },
    });
  };

  const handleSaveEdit = async () => {
    if (!editingFlag) return;
    try {
      const apps = editAllowedApps
        .split(',')
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean);

      await adminApi.updateFeatureFlag(editingFlag.flag_key, {
        enabled: editingFlag.enabled,
        rollout_percentage: editPercentage,
        allowed_app_codes: apps,
      });
      setEditingFlag(null);
      await loadFlags();
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to update feature flag');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">Feature Flags & Controlled Rollouts</h1>
          <p className="text-sm text-slate-400">
            Dynamically toggle shared platform capabilities, manage canary rollout percentages, and target specific applications.
          </p>
        </div>
        {!isSuperAdmin && (
          <span className="text-xs text-amber-300">Read-only mode (SUPER_ADMIN role required to edit)</span>
        )}
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-200">
          {error}
        </div>
      )}

      {loading ? (
        <div className="text-sm text-slate-400">Loading feature flags…</div>
      ) : flags.length === 0 ? (
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400">
          No feature flags registered in the system.
        </div>
      ) : (
        <div className="space-y-4">
          {flags.map((flag) => (
            <div
              key={flag.id}
              className="rounded-xl border border-slate-800 bg-slate-900 p-5 space-y-3"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-white">{flag.name}</span>
                    <StatusBadge label={flag.enabled ? 'ACTIVE' : 'DISABLED'} />
                  </div>
                  <div className="mt-1 font-mono text-xs text-indigo-300">{flag.flag_key}</div>
                  {flag.description && (
                    <div className="mt-1 text-xs text-slate-400">{flag.description}</div>
                  )}
                </div>

                {isSuperAdmin && (
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setEditingFlag(flag);
                        setEditPercentage(flag.rollout_percentage);
                        setEditAllowedApps(flag.allowed_app_codes.join(', '));
                      }}
                      className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs text-slate-200 hover:bg-slate-700"
                    >
                      Configure Rollout
                    </button>
                    <button
                      type="button"
                      onClick={() => handleToggle(flag)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-medium text-white transition ${
                        flag.enabled
                          ? 'bg-rose-600 hover:bg-rose-500'
                          : 'bg-emerald-600 hover:bg-emerald-500'
                      }`}
                    >
                      {flag.enabled ? 'Disable' : 'Enable'}
                    </button>
                  </div>
                )}
              </div>

              <div className="flex flex-wrap gap-4 border-t border-slate-800/80 pt-3 text-xs text-slate-400">
                <div>
                  <span className="text-slate-500">Rollout Percentage:</span>{' '}
                  <span className="font-semibold text-slate-200">{flag.rollout_percentage}%</span>
                </div>
                <div>
                  <span className="text-slate-500">Target Applications:</span>{' '}
                  <span className="font-semibold text-slate-200">
                    {flag.allowed_app_codes.length > 0 ? flag.allowed_app_codes.join(', ') : 'All Applications'}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Edit Modal */}
      {editingFlag && (
        <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4">
          <div className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 space-y-4">
            <h2 className="text-lg font-bold text-white">Configure Rollout: {editingFlag.name}</h2>
            <div className="font-mono text-xs text-indigo-300">{editingFlag.flag_key}</div>

            <div>
              <label className="block text-xs font-medium text-slate-300">
                Rollout Percentage (0 - 100%): {editPercentage}%
              </label>
              <input
                type="range"
                min={0}
                max={100}
                value={editPercentage}
                onChange={(e) => setEditPercentage(Number(e.target.value))}
                className="mt-2 w-full accent-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300">
                Target Application Codes (comma-separated, e.g. peggy, sms)
              </label>
              <input
                type="text"
                placeholder="Leave blank for general rollout"
                value={editAllowedApps}
                onChange={(e) => setEditAllowedApps(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setEditingFlag(null)}
                className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSaveEdit}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDialog && (
        <ConfirmDialog
          open={confirmDialog.open}
          title={confirmDialog.title}
          description={confirmDialog.description}
          confirmLabel="Confirm"
          onCancel={() => setConfirmDialog(null)}
          onConfirm={async () => {
            const action = confirmDialog.action;
            setConfirmDialog(null);
            await action();
          }}
        />
      )}
    </div>
  );
}

