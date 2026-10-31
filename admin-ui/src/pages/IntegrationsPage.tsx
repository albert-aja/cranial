import { useEffect, useState } from 'react';
import { adminApi, CranialApiError } from '../api/client';
import type { IntegrationSummaryItem, SandboxCredentialInfo } from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';

export function IntegrationsPage() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'apps' | 'sandbox' | 'probe' | 'sdks'>('apps');
  const [summaries, setSummaries] = useState<IntegrationSummaryItem[]>([]);
  const [sandboxes, setSandboxes] = useState<SandboxCredentialInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<CranialApiError | null>(null);

  // Probe testing state
  const [probeApp, setProbeApp] = useState<string>('sms');
  const [probeRunning, setProbeRunning] = useState(false);
  const [probeResult, setProbeResult] = useState<Record<string, unknown> | null>(null);
  const [probeType, setProbeType] = useState<'health' | 'catalog'>('health');

  const loadData = () => {
    setLoading(true);
    setError(null);
    Promise.all([
      adminApi.getIntegrationSummary(),
      adminApi.listSandboxCredentials(),
    ])
      .then(([summaryRes, sandboxRes]) => {
        setSummaries(summaryRes.data);
        setSandboxes(sandboxRes.data);
      })
      .catch((err) => {
        if (err instanceof CranialApiError) setError(err);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  const runProbe = async (type: 'health' | 'catalog') => {
    setProbeRunning(true);
    setProbeResult(null);
    setProbeType(type);
    try {
      const selectedSb = sandboxes.find((s) => s.application_code === probeApp);
      const apiKey = selectedSb ? selectedSb.api_key : undefined;
      let res;
      if (type === 'health') {
        res = await adminApi.probeIntegrationHealth(apiKey);
      } else {
        res = await adminApi.probeIntegrationCatalog(apiKey);
      }
      setProbeResult(res.data);
    } catch (err) {
      if (err instanceof CranialApiError) {
        setProbeResult({ error: err.message, code: err.code, status: err.status });
      } else {
        setProbeResult({ error: String(err) });
      }
    } finally {
      setProbeRunning(false);
    }
  };

  if (loading) {
    return <div className="text-sm text-slate-400">Loading integrations & sandbox configurations…</div>;
  }

  if (error) {
    return (
      <div role="alert" className="rounded-xl border border-rose-500/40 bg-rose-500/10 p-5 text-sm text-rose-200">
        <div className="font-semibold">Failed to load integrations ({error.code})</div>
        <div className="mt-1">{error.message}</div>
        <button
          type="button"
          onClick={loadData}
          className="mt-3 rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-rose-500"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-white tracking-tight">
            {t('integrations.title', 'Application Integrations & Readiness')}
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            {t(
              'integrations.subtitle',
              'Unified integration hub for Store Management System, Worker App, Peggy Money Management, and E-Commerce platform with client SDKs & MCP'
            )}
          </p>
        </div>
        <button
          type="button"
          onClick={loadData}
          className="self-start md:self-auto px-3 py-1.5 rounded-lg bg-slate-800 text-xs font-medium text-slate-200 hover:bg-slate-700 transition"
        >
          {t('action.refreshAll', 'Refresh All')}
        </button>
      </div>

      {/* Strict Policy Alerts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4">
          <div className="flex items-start gap-2.5">
            <span className="text-amber-400 text-base">🛡️</span>
            <div>
              <div className="text-xs font-semibold text-amber-200">Strict Money Management Boundary (Peggy)</div>
              <div className="text-[11px] text-amber-300/90 mt-0.5 leading-relaxed">
                {t(
                  'integrations.nonBankingWarning',
                  'Strict Money Management Policy: Peggy is strictly educational and personal budgeting only (50-30-20, OCR preview). All banking verbs (payment, transfer, disburse, withdraw) are strictly blocked with TOOL_PERMISSION_DENIED.'
                )}
              </div>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-indigo-500/30 bg-indigo-500/10 p-4">
          <div className="flex items-start gap-2.5">
            <span className="text-indigo-400 text-base">🔒</span>
            <div>
              <div className="text-xs font-semibold text-indigo-200">Role & Security Boundaries (Worker App & HMAC)</div>
              <div className="text-[11px] text-indigo-300/90 mt-0.5 leading-relaxed">
                {t(
                  'integrations.workerBoundaryWarning',
                  'Worker Access Boundary: Workers are strictly blocked from accessing store owner financial reports (sms:owner_reports). HMAC signed context headers guarantee identity non-repudiation.'
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-slate-800 flex gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('apps')}
          className={`pb-2.5 px-3 text-xs font-medium border-b-2 transition ${
            activeTab === 'apps'
              ? 'border-indigo-500 text-indigo-300'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('integrations.tab.apps', 'Connected Applications')} ({summaries.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('sandbox')}
          className={`pb-2.5 px-3 text-xs font-medium border-b-2 transition ${
            activeTab === 'sandbox'
              ? 'border-indigo-500 text-indigo-300'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('integrations.tab.sandbox', 'Sandbox & Credentials')}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('probe')}
          className={`pb-2.5 px-3 text-xs font-medium border-b-2 transition ${
            activeTab === 'probe'
              ? 'border-indigo-500 text-indigo-300'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('integrations.tab.probe', 'Live Gateway Probe')}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('sdks')}
          className={`pb-2.5 px-3 text-xs font-medium border-b-2 transition ${
            activeTab === 'sdks'
              ? 'border-indigo-500 text-indigo-300'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('integrations.tab.sdks', 'Client SDKs & MCP')}
        </button>
      </div>

      {/* TAB 1: Connected Applications */}
      {activeTab === 'apps' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {summaries.map((app) => (
            <div
              key={app.code}
              className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 flex flex-col justify-between hover:border-slate-700 transition"
            >
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-lg">
                      {app.code === 'sms' && '🏬'}
                      {app.code === 'worker_app' && '👷'}
                      {app.code === 'peggy' && '🐷'}
                      {app.code === 'ecommerce' && '🛍️'}
                    </span>
                    <div>
                      <div className="text-sm font-bold text-white">{app.name}</div>
                      <div className="text-[11px] font-mono text-slate-400">{app.code}</div>
                    </div>
                  </div>
                  <span className="px-2 py-0.5 text-[10px] rounded-full font-semibold uppercase bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
                    {app.status}
                  </span>
                </div>

                {app.money_management_only && (
                  <div className="mt-3 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-emerald-950/60 border border-emerald-600/40 text-[11px] text-emerald-300">
                    <span>✨</span>
                    <span className="font-medium">Pure Money Management & Education (Strictly Non-Banking)</span>
                  </div>
                )}

                <div className="mt-4 space-y-2 text-xs">
                  <div className="flex justify-between text-slate-400">
                    <span>Environment:</span>
                    <span className="text-slate-200 font-mono">{app.environment}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Default Tenant Ref:</span>
                    <span className="text-slate-200 font-mono">{app.default_tenant_ref}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Registered Tools:</span>
                    <span className="text-slate-200 font-semibold">{app.available_tools_count} read-only tools</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Default Roles:</span>
                    <span className="text-slate-200 font-mono">{app.default_roles.join(', ')}</span>
                  </div>
                </div>

                <div className="mt-4">
                  <div className="text-[11px] font-medium text-slate-400 mb-1.5">Entitled Capabilities:</div>
                  <div className="flex flex-wrap gap-1.5">
                    {app.entitled_capabilities.map((cap) => (
                      <span
                        key={cap}
                        className="px-2 py-0.5 rounded bg-slate-800 text-[10px] font-mono text-indigo-300 border border-slate-700"
                      >
                        {cap}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="mt-5 pt-3 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-400">
                <span>API Key: <span className="font-mono text-slate-300">{app.sandbox_api_key.slice(0, 15)}…</span></span>
                <button
                  type="button"
                  onClick={() => {
                    setProbeApp(app.code);
                    setActiveTab('probe');
                  }}
                  className="text-indigo-400 hover:text-indigo-300 font-medium"
                >
                  Test Probe →
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* TAB 2: Sandbox & Credentials */}
      {activeTab === 'sandbox' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
            <h2 className="text-sm font-bold text-white mb-1">Developer Sandbox Access Credentials</h2>
            <p className="text-xs text-slate-400 mb-4">
              Predictable pre-seeded development credentials for local integration testing with HMAC-SHA256 request signing.
            </p>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 text-[11px]">
                    <th className="py-2.5 px-3">Application</th>
                    <th className="py-2.5 px-3">API Key (Header)</th>
                    <th className="py-2.5 px-3">HMAC Signing Secret</th>
                    <th className="py-2.5 px-3">Default Context Claims</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-200">
                  {sandboxes.map((sb) => (
                    <tr key={sb.application_code} className="hover:bg-slate-800/30">
                      <td className="py-3 px-3">
                        <div className="font-medium text-white">{sb.application_name}</div>
                        <div className="text-[10px] font-mono text-indigo-300">{sb.application_code}</div>
                      </td>
                      <td className="py-3 px-3">
                        <code className="text-[11px] bg-slate-800/80 px-2 py-0.5 rounded text-emerald-300 border border-slate-700 font-mono select-all">
                          {sb.api_key}
                        </code>
                      </td>
                      <td className="py-3 px-3">
                        <code className="text-[11px] bg-slate-800/80 px-2 py-0.5 rounded text-amber-300 border border-slate-700 font-mono select-all">
                          {sb.signing_secret}
                        </code>
                      </td>
                      <td className="py-3 px-3 text-[11px] font-mono text-slate-400">
                        <div>tenant: <span className="text-slate-200">{sb.tenant_ref}</span></div>
                        <div>user: <span className="text-slate-200">{sb.user_ref}</span></div>
                        <div>roles: <span className="text-slate-200">{sb.roles.join(', ')}</span></div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Quick Copy cURL */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
            <h3 className="text-xs font-bold text-slate-200 mb-2">Diagnostic Health Probe cURL</h3>
            <pre className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] font-mono text-slate-300 overflow-x-auto select-all">
{`curl -X GET http://localhost:8080/api/v1/integration/health \\
  -H "X-Cranial-Api-Key: crn_dev_sms_app001" \\
  -H "Accept: application/json"`}
            </pre>
          </div>
        </div>
      )}

      {/* TAB 3: Live Gateway Probe */}
      {activeTab === 'probe' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
            <h2 className="text-sm font-bold text-white mb-1">Live Integration Gateway Diagnostics</h2>
            <p className="text-xs text-slate-400 mb-4">
              Directly ping the live CRANIAL integration gateway using pre-configured sandbox application keys.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
              <div>
                <label className="block text-[11px] font-medium text-slate-400 mb-1">Target Application</label>
                <select
                  value={probeApp}
                  onChange={(e) => setProbeApp(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-indigo-500"
                >
                  {sandboxes.map((sb) => (
                    <option key={sb.application_code} value={sb.application_code}>
                      {sb.application_name} ({sb.application_code})
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={probeRunning}
                  onClick={() => runProbe('health')}
                  className="flex-1 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-medium text-white transition disabled:opacity-50"
                >
                  {probeRunning && probeType === 'health' ? 'Checking…' : 'Check Health Probe'}
                </button>
                <button
                  type="button"
                  disabled={probeRunning}
                  onClick={() => runProbe('catalog')}
                  className="flex-1 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 border border-slate-700 transition disabled:opacity-50"
                >
                  {probeRunning && probeType === 'catalog' ? 'Fetching…' : 'Inspect Catalog'}
                </button>
              </div>
            </div>

            {probeResult && (
              <div className="mt-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-semibold text-slate-300">Gateway Response Payload:</span>
                  <span className="text-[10px] text-emerald-400 font-mono">HTTP 200 OK</span>
                </div>
                <pre className="p-4 rounded-lg bg-slate-950 border border-slate-800 text-xs font-mono text-emerald-400 overflow-x-auto max-h-96">
                  {JSON.stringify(probeResult, null, 2)}
                </pre>
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 4: Client SDKs & MCP */}
      {activeTab === 'sdks' && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Java SDK */}
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
              <div className="flex items-center justify-between mb-2">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <span>☕</span> Java Client SDK
                </div>
                <span className="text-[10px] font-mono text-indigo-300 bg-indigo-950/60 border border-indigo-700/50 px-2 py-0.5 rounded">
                  v0.1.0
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-3">
                Official Java 21+ SDK with automatic HMAC-SHA256 signature generation and thread-safe HTTP client.
              </p>
              <pre className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] font-mono text-slate-300 overflow-x-auto select-all mb-3">
{`CranialConfig config = CranialConfig.builder()
    .endpoint("http://localhost:8080")
    .apiKey("crn_dev_sms_app001")
    .signingSecret("dev_sec_sms_59281a8b")
    .build();

CranialClient client = new CranialClient(config);
var health = client.probeHealth();`}
              </pre>
              <div className="text-[11px] text-slate-400 font-mono">Location: <code>sdk/java/</code></div>
            </div>

            {/* TypeScript SDK */}
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
              <div className="flex items-center justify-between mb-2">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <span>🟦</span> TypeScript SDK
                </div>
                <span className="text-[10px] font-mono text-indigo-300 bg-indigo-950/60 border border-indigo-700/50 px-2 py-0.5 rounded">
                  v0.1.0
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-3">
                Typed ESM/CJS client for Node.js and modern JavaScript environments with crypto-native signing.
              </p>
              <pre className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] font-mono text-slate-300 overflow-x-auto select-all mb-3">
{`import { CranialClient } from '@cranial/client';

const client = new CranialClient({
  baseUrl: 'http://localhost:8080',
  apiKey: 'crn_dev_pgy_app001',
  signingSecret: 'dev_sec_pgy_3819fa2c'
});

const health = await client.health();`}
              </pre>
              <div className="text-[11px] text-slate-400 font-mono">Location: <code>sdk/typescript/</code></div>
            </div>
          </div>

          {/* Model Context Protocol (MCP) Server */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm font-bold text-white flex items-center gap-2">
                <span>🤖</span> Model Context Protocol (MCP) Stdio Server
              </div>
              <span className="text-[10px] font-mono text-emerald-300 bg-emerald-950/60 border border-emerald-700/50 px-2 py-0.5 rounded">
                JSON-RPC 2.0
              </span>
            </div>
            <p className="text-xs text-slate-400 mb-3">
              Standardized MCP server enabling external AI tools (Claude Desktop, Antigravity, Cursor) to discover CRANIAL tools and execute tasks securely over stdio.
            </p>
            <pre className="p-3 rounded-lg bg-slate-950 border border-slate-800 text-[11px] font-mono text-slate-300 overflow-x-auto select-all mb-3">
{`# Run Cranial MCP Server
node sdk/mcp/cranial-mcp-server.js

# Or configure in claude_desktop_config.json:
{
  "mcpServers": {
    "cranial": {
      "command": "node",
      "args": ["c:/D/Project/cranial/sdk/mcp/cranial-mcp-server.js"],
      "env": {
        "CRANIAL_ENDPOINT": "http://localhost:8080",
        "CRANIAL_API_KEY": "crn_dev_sms_app001",
        "CRANIAL_SIGNING_SECRET": "dev_sec_sms_59281a8b"
      }
    }
  }
}`}
            </pre>
            <div className="text-[11px] text-slate-400 font-mono">Location: <code>sdk/mcp/cranial-mcp-server.js</code></div>
          </div>
        </div>
      )}
    </div>
  );
}
export default IntegrationsPage;

