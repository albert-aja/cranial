import React, { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type { ApplicationView, TenantMembership, TenantView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { StatusBadge } from '../components/StatusBadge';

export function TenantsPage() {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('tenant:manage');

  const [apps, setApps] = useState<ApplicationView[]>([]);
  const [selectedAppId, setSelectedAppId] = useState<string>('');
  const [tenants, setTenants] = useState<TenantView[]>([]);
  const [selectedTenant, setSelectedTenant] = useState<TenantView | null>(null);
  const [memberships, setMemberships] = useState<TenantMembership[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [extRef, setExtRef] = useState('');
  const [tenantName, setTenantName] = useState('');
  const [memberUserRef, setMemberUserRef] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');

  const loadInitial = async () => {
    try {
      const [appsRes, tenantsRes] = await Promise.all([
        adminApi.listApplications({ size: 100 }),
        adminApi.listTenants({ applicationId: selectedAppId || undefined }),
      ]);
      setApps(appsRes.data);
      setTenants(tenantsRes.data);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to load tenants');
    }
  };

  useEffect(() => {
    loadInitial();
  }, [selectedAppId]);

  const selectTenant = async (t: TenantView) => {
    setSelectedTenant(t);
    const res = await adminApi.listMemberships(t.id);
    setMemberships(res.data);
  };

  const handleProvision = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAppId) return;
    try {
      await adminApi.provisionTenant({
        application_id: selectedAppId,
        external_tenant_ref: extRef,
        name: tenantName,
      });
      setExtRef('');
      setTenantName('');
      await loadInitial();
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to provision tenant');
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTenant) return;
    try {
      await adminApi.upsertMembership(selectedTenant.id, {
        external_user_ref: memberUserRef,
        member_role: memberRole,
        status: 'ACTIVE',
      });
      setMemberUserRef('');
      await selectTenant(selectedTenant);
    } catch (err) {
      setError(err instanceof CranialApiError ? `${err.code}: ${err.message}` : 'Failed to add member');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Tenants & Scope Isolation</h1>
        <p className="text-sm text-slate-400">
          Map external store, workspace, or shared-pocket scopes to owning applications for pre-execution isolation.
        </p>
      </div>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-200">
          {error}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <select
          aria-label="Filter by owning application"
          value={selectedAppId}
          onChange={(e) => setSelectedAppId(e.target.value)}
          className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200"
        >
          <option value="">All Applications</option>
          {apps.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name} ({a.code} · {a.environment})
            </option>
          ))}
        </select>
      </div>

      {canManage && selectedAppId && (
        <form onSubmit={handleProvision} className="flex flex-wrap gap-3 rounded-xl border border-slate-800 bg-slate-900 p-4">
          <input
            required
            placeholder="external_tenant_ref (e.g. store-01)"
            value={extRef}
            onChange={(e) => setExtRef(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white"
          />
          <input
            required
            placeholder="Tenant display name"
            value={tenantName}
            onChange={(e) => setTenantName(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-sm text-white"
          />
          <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white">
            Provision Tenant
          </button>
        </form>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-2">
          {tenants.length === 0 ? (
            <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400">
              No tenants provisioned yet. Select an application above to provision a tenant scope.
            </div>
          ) : (
            tenants.map((t) => (
              <div
                key={t.id}
                onClick={() => selectTenant(t)}
                className={`cursor-pointer rounded-xl border p-4 ${
                  selectedTenant?.id === t.id ? 'border-indigo-500 bg-slate-900' : 'border-slate-800 bg-slate-900/60'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-white">{t.name}</span>
                  <StatusBadge label={t.status} />
                </div>
                <div className="mt-1 font-mono text-xs text-indigo-300">{t.external_tenant_ref}</div>
              </div>
            ))
          )}
        </div>

        <div>
          {selectedTenant && (
            <div className="rounded-xl border border-slate-800 bg-slate-900 p-5 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-white">Memberships: {selectedTenant.name}</h2>
                {canManage && (
                  <button
                    type="button"
                    onClick={async () => {
                      const next = selectedTenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
                      const updated = await adminApi.updateTenantStatus(selectedTenant.id, next);
                      setSelectedTenant(updated.data);
                      await loadInitial();
                    }}
                    className="rounded border border-slate-700 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-800"
                  >
                    {selectedTenant.status === 'ACTIVE' ? 'Suspend Tenant' : 'Activate Tenant'}
                  </button>
                )}
              </div>

              {canManage && (
                <form onSubmit={handleAddMember} className="flex flex-wrap gap-2">
                  <input
                    required
                    placeholder="external_user_ref"
                    value={memberUserRef}
                    onChange={(e) => setMemberUserRef(e.target.value)}
                    className="rounded border border-slate-700 bg-slate-950 px-2.5 py-1 text-xs text-white"
                  />
                  <input
                    required
                    placeholder="Role"
                    value={memberRole}
                    onChange={(e) => setMemberRole(e.target.value)}
                    className="w-28 rounded border border-slate-700 bg-slate-950 px-2.5 py-1 text-xs text-white"
                  />
                  <button type="submit" className="rounded bg-indigo-600 px-3 py-1 text-xs font-medium text-white">
                    Upsert Member
                  </button>
                </form>
              )}

              {memberships.length === 0 ? (
                <p className="text-xs text-slate-400">No explicit user memberships mapped.</p>
              ) : (
                <ul className="space-y-1.5 text-xs">
                  {memberships.map((m) => (
                    <li
                      key={m.id}
                      className="flex items-center justify-between rounded border border-slate-800 bg-slate-950 px-3 py-2"
                    >
                      <span className="font-mono text-slate-200">{m.external_user_ref}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-slate-400">{m.member_role}</span>
                        <StatusBadge label={m.status} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

