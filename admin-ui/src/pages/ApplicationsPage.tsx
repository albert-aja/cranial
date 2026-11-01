import React, { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type {
  ApplicationView,
  CapabilityEntitlementView,
  CredentialIssuedResult,
  CredentialView,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { RevealOnceModal } from '../components/RevealOnceModal';
import { StatusBadge } from '../components/StatusBadge';
import { SignedContextSnippetGenerator } from '../components/SignedContextSnippetGenerator';
import { ErrorExplanationBanner } from '../components/ErrorExplanationBanner';

export function ApplicationsPage() {
  const { hasPermission } = useAuth();
  const canWriteApp = hasPermission('app:write');
  const canManageCreds = hasPermission('credential:manage');
  const canManageCaps = hasPermission('capability:manage');

  const [apps, setApps] = useState<ApplicationView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [envFilter, setEnvFilter] = useState('');
  const [search, setSearch] = useState('');

  const [selectedApp, setSelectedApp] = useState<ApplicationView | null>(null);
  const [credentials, setCredentials] = useState<CredentialView[]>([]);
  const [capabilities, setCapabilities] = useState<CapabilityEntitlementView[]>([]);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newCode, setNewCode] = useState('');
  const [newEnv, setNewEnv] = useState<'DEV' | 'STAGING' | 'PROD'>('DEV');
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newRpm, setNewRpm] = useState(120);

  const [newKeyName, setNewKeyName] = useState('Primary Backend Key');
  const [revealedSecret, setRevealedSecret] = useState<CredentialIssuedResult | null>(null);

  const [confirmState, setConfirmState] = useState<{
    open: boolean;
    title: string;
    description: string;
    recoveryNote?: string;
    confirmLabel: string;
    destructive?: boolean;
    action: () => Promise<void>;
  } | null>(null);

  const loadApps = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminApi.listApplications({
        environment: envFilter || undefined,
        search: search || undefined,
      });
      setApps(res.data);
      if (selectedApp) {
        const updated = res.data.find((a) => a.id === selectedApp.id);
        if (updated) setSelectedApp(updated);
      }
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to load applications');
    } finally {
      setLoading(false);
    }
  };

  const loadAppDetails = async (app: ApplicationView) => {
    setSelectedApp(app);
    try {
      const [credsRes, capsRes] = await Promise.all([
        adminApi.listCredentials(app.id),
        adminApi.listApplicationCapabilities(app.id),
      ]);
      setCredentials(credsRes.data);
      setCapabilities(capsRes.data);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to load details');
    }
  };

  useEffect(() => {
    loadApps();
  }, [envFilter]);

  const handleCreateApp = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const created = await adminApi.createApplication({
        code: newCode,
        environment: newEnv,
        name: newName,
        description: newDesc,
        rate_limit_rpm: newRpm,
      });
      setShowCreateModal(false);
      setNewCode('');
      setNewName('');
      setNewDesc('');
      await loadApps();
      await loadAppDetails(created.data);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to register application');
    }
  };

  const handleIssueKey = async () => {
    if (!selectedApp) return;
    try {
      const res = await adminApi.issueCredential(selectedApp.id, newKeyName);
      setRevealedSecret(res.data);
      await loadAppDetails(selectedApp);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to issue credential');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">Applications, Credentials & Entitlements</h1>
          <p className="text-sm text-slate-400">
            Manage registered client backends, rotate/revoke API credentials, and govern capability entitlements.
          </p>
        </div>
        {canWriteApp ? (
          <button
            type="button"
            onClick={() => setShowCreateModal(true)}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            + Register Application
          </button>
        ) : (
          <span className="text-xs text-amber-300">Read-only role (missing app:write)</span>
        )}
      </div>

      {error && (
        <ErrorExplanationBanner
          errorCode="APPLICATION_ACTION_FAILED"
          errorMessage={error}
          onRetry={loadApps}
        />
      )}

      <div className="flex flex-wrap gap-3">
        <select
          aria-label="Filter by environment"
          value={envFilter}
          onChange={(e) => setEnvFilter(e.target.value)}
          className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
        >
          <option value="">All Environments</option>
          <option value="DEV">DEV</option>
          <option value="STAGING">STAGING</option>
          <option value="PROD">PROD</option>
        </select>

        <input
          type="search"
          placeholder="Search code or name…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && loadApps()}
          className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
        />
        <button
          type="button"
          onClick={loadApps}
          className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-slate-200 hover:bg-slate-700"
        >
          Filter
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 space-y-3">
          {loading ? (
            <div className="text-sm text-slate-400">Loading applications…</div>
          ) : apps.length === 0 ? (
            <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400">
              No registered applications found. Register Peggy, SMS, Worker App, or E-commerce to begin.
            </div>
          ) : (
            apps.map((app) => (
              <button
                key={app.id}
                type="button"
                onClick={() => loadAppDetails(app)}
                className={`w-full text-left rounded-xl border p-4 transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 ${
                  selectedApp?.id === app.id
                    ? 'border-indigo-500 bg-slate-900'
                    : 'border-slate-800 bg-slate-900/60 hover:border-slate-700'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-white">{app.name}</span>
                  <div className="flex items-center gap-1.5">
                    <StatusBadge label={app.environment} />
                    <StatusBadge label={app.status} />
                  </div>
                </div>
                <div className="mt-1 text-xs font-mono text-indigo-300">{app.code}</div>
                <div className="mt-2 text-xs text-slate-400">
                  RPM: {app.settings.rate_limit_rpm} · Max Input: {Math.round(app.settings.max_input_bytes / 1024)} KB ·
                  Hosted Providers: {app.settings.allow_hosted_providers ? 'Opt-In' : 'Disabled (Local-First)'}
                </div>
              </button>
            ))
          )}
        </div>

        <div className="lg:col-span-7">
          {!selectedApp ? (
            <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-8 text-center text-sm text-slate-400">
              Select an application to inspect credentials, rate limits, and capability entitlements.
            </div>
          ) : (
            <div className="space-y-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
              <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-lg font-bold text-white">{selectedApp.name}</h2>
                  <p className="text-xs font-mono text-indigo-300">
                    {selectedApp.code} ({selectedApp.environment})
                  </p>
                  {selectedApp.description && (
                    <p className="mt-1 text-xs text-slate-400">{selectedApp.description}</p>
                  )}
                </div>
                {canWriteApp && (
                  <div className="flex gap-2">
                    {selectedApp.status !== 'ACTIVE' && (
                      <button
                        type="button"
                        onClick={() =>
                          setConfirmState({
                            open: true,
                            title: `Activate ${selectedApp.name}?`,
                            description: 'Active credentials will immediately resume gateway access.',
                            confirmLabel: 'Activate',
                            action: async () => {
                              await adminApi.updateApplicationStatus(selectedApp.id, 'ACTIVE');
                              await loadApps();
                            },
                          })
                        }
                        className="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-500"
                      >
                        Activate
                      </button>
                    )}
                    {selectedApp.status === 'ACTIVE' && (
                      <button
                        type="button"
                        onClick={() =>
                          setConfirmState({
                            open: true,
                            title: `Suspend ${selectedApp.name}?`,
                            description: 'All integration API calls for this application will be rejected with 403 APP_SUSPENDED.',
                            recoveryNote: 'You can re-activate the application at any time without re-issuing credentials.',
                            confirmLabel: 'Suspend Application',
                            destructive: true,
                            action: async () => {
                              await adminApi.updateApplicationStatus(selectedApp.id, 'SUSPENDED');
                              await loadApps();
                            },
                          })
                        }
                        className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-1.5 text-xs font-medium text-amber-200 hover:bg-amber-500/20"
                      >
                        Suspend
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* Credentials Section */}
              <div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="text-sm font-semibold text-white">API Credentials</h3>
                  {canManageCreds && (
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={newKeyName}
                        onChange={(e) => setNewKeyName(e.target.value)}
                        placeholder="Credential label"
                        className="rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1 text-xs text-white"
                      />
                      <button
                        type="button"
                        onClick={handleIssueKey}
                        className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-500"
                      >
                        Issue Key
                      </button>
                    </div>
                  )}
                </div>

                {credentials.length === 0 ? (
                  <p className="mt-2 text-xs text-slate-400">No credentials issued yet.</p>
                ) : (
                  <div className="mt-3 space-y-2">
                    {credentials.map((cred) => (
                      <div
                        key={cred.id}
                        className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs"
                      >
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-slate-200">{cred.name}</span>
                            <StatusBadge label={cred.status} />
                          </div>
                          <div className="mt-1 font-mono text-slate-400">{cred.key_prefix}.••••••••</div>
                        </div>
                        {canManageCreds && (cred.status === 'ACTIVE' || cred.status === 'ROTATING') && (
                          <div className="flex gap-2">
                            <button
                              type="button"
                              onClick={() =>
                                setConfirmState({
                                  open: true,
                                  title: `Rotate credential ${cred.key_prefix}?`,
                                  description:
                                    'Issues a new active key (revealed once) and places the current key in ROTATING state with a 60-minute grace window.',
                                  recoveryNote: 'Update client backend secrets within 60 minutes, then revoke old key.',
                                  confirmLabel: 'Rotate Key',
                                  action: async () => {
                                    const res = await adminApi.rotateCredential(selectedApp.id, cred.id, 60);
                                    setRevealedSecret(res.data);
                                    await loadAppDetails(selectedApp);
                                  },
                                })
                              }
                              className="rounded border border-slate-700 px-2.5 py-1 text-slate-200 hover:bg-slate-800"
                            >
                              Rotate (60m grace)
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                setConfirmState({
                                  open: true,
                                  title: `Revoke credential ${cred.key_prefix}?`,
                                  description:
                                    'Immediate and irreversible. Requests using this key will fail with 401 CREDENTIAL_REVOKED.',
                                  recoveryNote: 'Issue a new credential if access is still needed.',
                                  confirmLabel: 'Revoke Immediately',
                                  destructive: true,
                                  action: async () => {
                                    await adminApi.revokeCredential(selectedApp.id, cred.id);
                                    await loadAppDetails(selectedApp);
                                  },
                                })
                              }
                              className="rounded border border-rose-500/40 bg-rose-500/10 px-2.5 py-1 text-rose-300 hover:bg-rose-500/20"
                            >
                              Revoke
                            </button>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Capability Entitlements Section */}
              <div className="border-t border-slate-800 pt-4">
                <h3 className="text-sm font-semibold text-white">Capability Entitlements</h3>
                <p className="text-xs text-slate-400">
                  Applications can invoke only capabilities explicitly entitled below.
                </p>
                <div className="mt-3 space-y-2">
                  {capabilities.map((cap) => (
                    <div
                      key={cap.capability_code}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs"
                    >
                      <div className="max-w-md">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-slate-200">{cap.capability_name}</span>
                          <StatusBadge label={cap.enabled ? 'ACTIVE' : 'DISABLED'} />
                        </div>
                        <div className="mt-0.5 text-slate-400">{cap.description}</div>
                      </div>
                      {canManageCaps && (
                        <button
                          type="button"
                          onClick={() =>
                            setConfirmState({
                              open: true,
                              title: `${cap.enabled ? 'Disable' : 'Enable'} ${cap.capability_name}?`,
                              description: cap.enabled
                                ? `Requests from ${selectedApp.code} to ${cap.capability_code} will be blocked with 403 CAPABILITY_NOT_ENTITLED.`
                                : `Authorizes ${selectedApp.code} to invoke ${cap.capability_code} workflows.`,
                              confirmLabel: cap.enabled ? 'Disable Capability' : 'Enable Capability',
                              destructive: cap.enabled,
                              action: async () => {
                                await adminApi.updateCapabilityEntitlement(
                                  selectedApp.id,
                                  cap.capability_code,
                                  !cap.enabled,
                                  cap.quota_daily_requests ?? 1000
                                );
                                await loadAppDetails(selectedApp);
                              },
                            })
                          }
                          className={`rounded px-3 py-1 font-medium ${
                            cap.enabled
                              ? 'border border-slate-700 text-slate-300 hover:bg-slate-800'
                              : 'bg-indigo-600 text-white hover:bg-indigo-500'
                          }`}
                        >
                          {cap.enabled ? 'Disable' : 'Enable'}
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* Signed-Context Test Snippet Generator */}
              <div className="border-t border-slate-800 pt-4">
                <SignedContextSnippetGenerator app={selectedApp} credentials={credentials} />
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Create Application Modal */}
      {showCreateModal && (
        <div role="dialog" aria-modal="true" className="fixed inset-0 z-40 flex items-center justify-center bg-black/70 p-4">
          <form
            onSubmit={handleCreateApp}
            className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 space-y-4"
          >
            <h2 className="text-lg font-bold text-white">Register Client Application</h2>
            <div>
              <label className="block text-xs text-slate-300">Application Code (slug)</label>
              <input
                required
                placeholder="peggy, sms, worker-app, ecommerce"
                value={newCode}
                onChange={(e) => setNewCode(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>
            <div>
              <label className="block text-xs text-slate-300">Environment</label>
              <select
                value={newEnv}
                onChange={(e) => setNewEnv(e.target.value as 'DEV' | 'STAGING' | 'PROD')}
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              >
                <option value="DEV">DEV</option>
                <option value="STAGING">STAGING</option>
                <option value="PROD">PROD</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-slate-300">Display Name</label>
              <input
                required
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>
            <div>
              <label className="block text-xs text-slate-300">Description</label>
              <textarea
                rows={2}
                value={newDesc}
                onChange={(e) => setNewDesc(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>
            <div>
              <label className="block text-xs text-slate-300">Rate Limit (Requests / Minute)</label>
              <input
                type="number"
                min={1}
                value={newRpm}
                onChange={(e) => setNewRpm(Number(e.target.value))}
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-300"
              >
                Cancel
              </button>
              <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white">
                Register
              </button>
            </div>
          </form>
        </div>
      )}

      <RevealOnceModal issued={revealedSecret} onClose={() => setRevealedSecret(null)} />

      {confirmState && (
        <ConfirmDialog
          open={confirmState.open}
          title={confirmState.title}
          description={confirmState.description}
          recoveryNote={confirmState.recoveryNote}
          confirmLabel={confirmState.confirmLabel}
          destructive={confirmState.destructive}
          onCancel={() => setConfirmState(null)}
          onConfirm={async () => {
            const fn = confirmState.action;
            setConfirmState(null);
            await fn();
          }}
        />
      )}
    </div>
  );
}

