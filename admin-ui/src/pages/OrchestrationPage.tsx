import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type {
  TaskDefinitionView,
  PromptView,
  SchemaView,
  ToolView,
  OrchestratorTraceView,
  SchemaValidationResult,
  ToolExecutionResult,
} from '../api/types';
import { StatusBadge } from '../components/StatusBadge';
import { ErrorExplanationBanner } from '../components/ErrorExplanationBanner';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';

type Tab = 'tasks' | 'prompts' | 'schemas' | 'tools' | 'traces';

export default function OrchestrationPage() {
  const [activeTab, setActiveTab] = useState<Tab>('tasks');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Data states
  const [tasks, setTasks] = useState<TaskDefinitionView[]>([]);
  const [prompts, setPrompts] = useState<PromptView[]>([]);
  const [schemas, setSchemas] = useState<SchemaView[]>([]);
  const [tools, setTools] = useState<ToolView[]>([]);
  const [traces, setTraces] = useState<OrchestratorTraceView[]>([]);

  // Prompt Version Diff State
  const [selectedPromptForDiff, setSelectedPromptForDiff] = useState<PromptView | null>(null);

  // Schema Validator Playground State
  const [selectedSchemaId, setSelectedSchemaId] = useState<string>('');
  const [testPayloadJson, setTestPayloadJson] = useState<string>(
    '{\n  "status": "COMPLETED",\n  "summary": "Monthly balance is within normal limits.",\n  "recommended_action": "Set aside $150 to emergency savings."\n}'
  );
  const [valLoading, setValLoading] = useState(false);
  const [valResult, setValResult] = useState<SchemaValidationResult | null>(null);

  // Tool Test Runner State
  const [selectedToolId, setSelectedToolId] = useState<string>('');
  const [toolParamsJson, setToolParamsJson] = useState<string>('{\n  "account_id": "ACC-998877"\n}');
  const [toolLoading, setToolLoading] = useState(false);
  const [toolResult, setToolResult] = useState<ToolExecutionResult | null>(null);

  // Trace Inspector State & Filters
  const [inspectingTrace, setInspectingTrace] = useState<OrchestratorTraceView | null>(null);
  const [traceSearch, setTraceSearch] = useState('');
  const [traceStatusFilter, setTraceStatusFilter] = useState('');

  // New Prompt Modal State
  const [showPromptModal, setShowPromptModal] = useState(false);
  const [promptForm, setPromptForm] = useState({
    prompt_code: '',
    name: '',
    description: '',
    capability_code: 'conversational_intelligence',
    task_code: '',
    version: 1,
    status: 'DRAFT',
    system_template: 'You are an intelligent AI assistant.',
    user_template: 'Please process the following data: {{inputData}}',
    input_variables_json: '["inputData"]',
  });

  // Live JSON Syntax Check for Schema Editor
  const payloadSyntax = useMemo(() => {
    try {
      const parsed = JSON.parse(testPayloadJson);
      const isObj = typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed);
      const keyCount = isObj ? Object.keys(parsed).length : 0;
      return {
        valid: true,
        error: null,
        keyCount,
        chars: testPayloadJson.length,
      };
    } catch (e) {
      return {
        valid: false,
        error: e instanceof Error ? e.message : 'Invalid JSON syntax',
        keyCount: 0,
        chars: testPayloadJson.length,
      };
    }
  }, [testPayloadJson]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [tRes, pRes, sRes, toolRes, trRes] = await Promise.all([
        api.listTasks(),
        api.listPrompts(),
        api.listSchemas(),
        api.listTools(),
        api.listTraces(undefined, 30),
      ]);

      if (tRes.status === 'ok') setTasks(tRes.data);
      if (pRes.status === 'ok') {
        setPrompts(pRes.data);
        if (pRes.data.length > 0 && !selectedPromptForDiff) {
          setSelectedPromptForDiff(pRes.data[0]);
        }
      }
      if (sRes.status === 'ok') {
        setSchemas(sRes.data);
        if (sRes.data.length > 0 && !selectedSchemaId) {
          setSelectedSchemaId(sRes.data[0].id);
        }
      }
      if (toolRes.status === 'ok') {
        setTools(toolRes.data);
        if (toolRes.data.length > 0 && !selectedToolId) {
          setSelectedToolId(toolRes.data[0].id);
        }
      }
      if (trRes.status === 'ok') setTraces(trRes.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load orchestration data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handlePublishPrompt = async (id: string) => {
    try {
      setLoading(true);
      const res = await api.publishPrompt(id);
      if (res.status === 'ok') {
        await loadData();
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to publish prompt');
    } finally {
      setLoading(false);
    }
  };

  const handleValidateSchema = async () => {
    if (!selectedSchemaId) return;
    try {
      setValLoading(true);
      setValResult(null);
      let parsed = {};
      try {
        parsed = JSON.parse(testPayloadJson);
      } catch {
        alert('Payload is not valid JSON');
        setValLoading(false);
        return;
      }
      const res = await api.validateSchema(selectedSchemaId, parsed);
      if (res.status === 'ok') {
        setValResult(res.data);
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Validation request failed');
    } finally {
      setValLoading(false);
    }
  };

  const handleTestTool = async () => {
    if (!selectedToolId) return;
    const tool = tools.find((t) => t.id === selectedToolId);
    if (!tool) return;

    try {
      setToolLoading(true);
      setToolResult(null);
      let params = {};
      try {
        params = JSON.parse(toolParamsJson);
      } catch {
        alert('Parameters are not valid JSON');
        setToolLoading(false);
        return;
      }
      const res = await api.testTool(selectedToolId, {
        tool_code: tool.tool_code,
        parameters: params,
      });
      if (res.status === 'ok') {
        setToolResult(res.data);
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Tool test execution failed');
    } finally {
      setToolLoading(false);
    }
  };

  const handleSavePrompt = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      const res = await api.savePrompt(promptForm);
      if (res.status === 'ok') {
        setShowPromptModal(false);
        await loadData();
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to save prompt');
    } finally {
      setLoading(false);
    }
  };

  // Filtered Traces
  const filteredTraces = useMemo(() => {
    return traces.filter((tr) => {
      const matchesStatus = !traceStatusFilter || tr.status === traceStatusFilter;
      const q = traceSearch.toLowerCase().trim();
      const matchesSearch =
        !q ||
        tr.id.toLowerCase().includes(q) ||
        tr.task_code.toLowerCase().includes(q) ||
        (tr.model_identifier && tr.model_identifier.toLowerCase().includes(q)) ||
        (tr.prompt_code && tr.prompt_code.toLowerCase().includes(q));
      return matchesStatus && matchesSearch;
    });
  }, [traces, traceStatusFilter, traceSearch]);

  const selectedSchema = schemas.find((s) => s.id === selectedSchemaId);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-700/60 pb-5">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-3">
            <span className="p-2 rounded-lg bg-teal-500/10 border border-teal-500/30 text-teal-400">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </span>
            AI Orchestration & Schemas
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Task execution engine, versioned prompt templates, JSON schema guards, safe tool calls, and step traces.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => loadData()}
            className="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-800 text-slate-300 hover:text-white border border-slate-700 flex items-center gap-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
          <button
            type="button"
            onClick={() => setShowPromptModal(true)}
            className="px-3 py-1.5 text-xs font-medium rounded-lg bg-teal-600 hover:bg-teal-500 text-white shadow-sm transition-colors flex items-center gap-1.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-400"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Prompt
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-800 space-x-1 overflow-x-auto">
        {(
          [
            { id: 'tasks', label: 'Tasks', count: tasks.length },
            { id: 'prompts', label: 'Prompts & Diff', count: prompts.length },
            { id: 'schemas', label: 'Schemas & Live Validator', count: schemas.length },
            { id: 'tools', label: 'Safe Tools', count: tools.length },
            { id: 'traces', label: 'Execution Traces', count: traces.length },
          ] as const
        ).map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors border-b-2 flex items-center gap-2 whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-400 ${
              activeTab === tab.id
                ? 'border-teal-500 text-teal-400 bg-slate-800/40'
                : 'border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/20'
            }`}
          >
            {tab.label}
            <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-slate-800 border border-slate-700 text-slate-300">
              {tab.count}
            </span>
          </button>
        ))}
      </div>

      {error && (
        <ErrorExplanationBanner
          errorCode="ORCHESTRATION_LOAD_FAILED"
          errorMessage={error}
          onRetry={loadData}
        />
      )}

      {/* Strict Money Management Boundary Banner for Tools */}
      {activeTab === 'tools' && (
        <div className="p-3 text-xs rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-200 flex items-start gap-2.5">
          <svg className="w-4 h-4 shrink-0 text-amber-400 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <div>
            <span className="font-semibold text-amber-300">Strict Money Management Boundary:</span> This app is purely a money management platform. It does not handle banking activities, wire transfers, payment disbursements, or settlements. Tools are bounded to read-only financial data retrieval and safe calculations.
          </div>
        </div>
      )}

      {/* Tab Content */}
      {loading ? (
        <LoadingState message="Loading orchestration registry..." />
      ) : (
        <>
          {/* TAB 1: TASKS */}
          {activeTab === 'tasks' && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-800/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Task Code</th>
                      <th className="py-3 px-4">Name</th>
                      <th className="py-3 px-4">Capability</th>
                      <th className="py-3 px-4">Default Prompt</th>
                      <th className="py-3 px-4">Output Schema</th>
                      <th className="py-3 px-4">Sensitivity</th>
                      <th className="py-3 px-4">Timeout</th>
                      <th className="py-3 px-4">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/50 font-mono">
                    {tasks.map((t) => (
                      <tr key={t.id} className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-3 px-4 font-semibold text-teal-400">{t.task_code}</td>
                        <td className="py-3 px-4 font-sans text-slate-200 font-medium">{t.name}</td>
                        <td className="py-3 px-4 text-slate-400">{t.capability_code}</td>
                        <td className="py-3 px-4 text-cyan-400">{t.default_prompt_code || '—'}</td>
                        <td className="py-3 px-4 text-purple-400">{t.output_schema_code || '—'}</td>
                        <td className="py-3 px-4">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-sans ${
                              t.allowed_data_sensitivity === 'RESTRICTED'
                                ? 'bg-rose-500/10 text-rose-300 border border-rose-500/20'
                                : 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/20'
                            }`}
                          >
                            {t.allowed_data_sensitivity}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-slate-400">{t.timeout_seconds}s</td>
                        <td className="py-3 px-4">
                          <StatusBadge label={t.is_active ? 'ACTIVE' : 'DISABLED'} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 2: PROMPTS WITH VERSION COMPARISON */}
          {activeTab === 'prompts' && (
            <div className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Prompts Table */}
                <div className="lg:col-span-2 rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs text-slate-300">
                      <thead className="bg-slate-800/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                        <tr>
                          <th className="py-3 px-4">Prompt Code</th>
                          <th className="py-3 px-4">Name</th>
                          <th className="py-3 px-4">Version</th>
                          <th className="py-3 px-4">Status</th>
                          <th className="py-3 px-4 text-right">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {prompts.map((p) => {
                          const isSelected = selectedPromptForDiff?.id === p.id;
                          return (
                            <tr
                              key={p.id}
                              onClick={() => setSelectedPromptForDiff(p)}
                              className={`cursor-pointer transition-colors ${
                                isSelected ? 'bg-teal-900/20 border-l-2 border-teal-500' : 'hover:bg-slate-800/30'
                              }`}
                            >
                              <td className="py-3 px-4 font-mono font-semibold text-teal-400">
                                {p.prompt_code}
                                <div className="text-[10px] text-slate-500">{p.capability_code}</div>
                              </td>
                              <td className="py-3 px-4 text-slate-200">
                                <div className="font-medium">{p.name}</div>
                                {p.description && <p className="text-[11px] text-slate-400 font-normal">{p.description}</p>}
                              </td>
                              <td className="py-3 px-4 font-mono text-cyan-400">v{p.version}</td>
                              <td className="py-3 px-4">
                                <StatusBadge label={p.status} />
                              </td>
                              <td className="py-3 px-4 text-right">
                                {p.status === 'DRAFT' ? (
                                  <button
                                    type="button"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      handlePublishPrompt(p.id);
                                    }}
                                    className="px-2.5 py-1 text-[11px] font-medium rounded bg-teal-600 hover:bg-teal-500 text-white transition-colors"
                                  >
                                    Publish
                                  </button>
                                ) : (
                                  <span className="text-[11px] text-slate-500">Active</span>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Prompt Version Inspector & Diff */}
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <h3 className="text-xs font-bold text-white uppercase tracking-wider">
                      Prompt Inspector & Variables
                    </h3>
                    {selectedPromptForDiff && (
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-teal-500/10 text-teal-300 border border-teal-500/20">
                        v{selectedPromptForDiff.version}
                      </span>
                    )}
                  </div>

                  {selectedPromptForDiff ? (
                    <div className="space-y-3 text-xs">
                      <div>
                        <div className="text-slate-400 text-[11px] font-semibold mb-1">System Template:</div>
                        <pre className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-slate-300 font-mono text-[11px] whitespace-pre-wrap">
                          {selectedPromptForDiff.system_template || '(None configured)'}
                        </pre>
                      </div>

                      <div>
                        <div className="text-slate-400 text-[11px] font-semibold mb-1">User Template:</div>
                        <pre className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-slate-300 font-mono text-[11px] whitespace-pre-wrap">
                          {selectedPromptForDiff.user_template}
                        </pre>
                      </div>

                      <div>
                        <div className="text-slate-400 text-[11px] font-semibold mb-1">Interpolated Variables:</div>
                        <div className="flex flex-wrap gap-1">
                          {selectedPromptForDiff.input_variables.map((v) => (
                            <span
                              key={v}
                              className="px-2 py-0.5 rounded bg-slate-800 text-teal-300 border border-slate-700 font-mono text-[10px]"
                            >
                              {`{{${v}}}`}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <p className="text-xs text-slate-500">Select a prompt template to inspect details.</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: SCHEMAS & LIVE VALIDATOR */}
          {activeTab === 'schemas' && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Schemas List */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-200">Registered JSON Schemas</h3>
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs text-slate-300">
                      <thead className="bg-slate-800/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                        <tr>
                          <th className="py-3 px-4">Code</th>
                          <th className="py-3 px-4">Type</th>
                          <th className="py-3 px-4">Strict</th>
                          <th className="py-3 px-4">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {schemas.map((s) => (
                          <tr
                            key={s.id}
                            onClick={() => setSelectedSchemaId(s.id)}
                            className={`cursor-pointer transition-colors ${
                              selectedSchemaId === s.id ? 'bg-teal-900/20 border-l-2 border-teal-500' : 'hover:bg-slate-800/30'
                            }`}
                          >
                            <td className="py-3 px-4 font-mono font-semibold text-purple-400">
                              {s.schema_code}
                              <p className="text-[11px] text-slate-400 font-sans font-normal">{s.name}</p>
                            </td>
                            <td className="py-3 px-4 font-mono text-[11px] text-slate-300">{s.schema_type}</td>
                            <td className="py-3 px-4 font-mono">{s.is_strict ? 'Strict' : 'Lenient'}</td>
                            <td className="py-3 px-4">
                              <StatusBadge label={s.status} />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Schema Definition Preview */}
                {selectedSchema && (
                  <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-2 text-xs">
                    <div className="flex items-center justify-between text-slate-300 font-semibold">
                      <span>Schema Contract: {selectedSchema.schema_code}</span>
                      <span className="font-mono text-slate-500">v{selectedSchema.version}</span>
                    </div>
                    <pre className="p-2.5 rounded bg-slate-950 border border-slate-800 font-mono text-[11px] text-teal-300 overflow-x-auto max-h-48">
                      {JSON.stringify(selectedSchema.schema_definition, null, 2)}
                    </pre>
                  </div>
                )}
              </div>

              {/* Schema Playground with Live Validation */}
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-slate-200">Interactive Live Schema Validator</h3>
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-mono ${
                        payloadSyntax.valid
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                      }`}
                    >
                      <span className={`h-1.5 w-1.5 rounded-full ${payloadSyntax.valid ? 'bg-emerald-400' : 'bg-rose-400'}`} />
                      {payloadSyntax.valid ? `JSON Valid (${payloadSyntax.keyCount} keys)` : 'Syntax Error'}
                    </span>
                  </div>
                </div>

                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">Select Schema to Validate Against</label>
                    <select
                      value={selectedSchemaId}
                      onChange={(e) => setSelectedSchemaId(e.target.value)}
                      className="w-full text-xs font-mono bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-slate-200 focus:outline-none focus:border-teal-500"
                    >
                      {schemas.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.schema_code} ({s.schema_type})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-medium text-slate-400">Payload JSON (Simulate Model Output)</label>
                      <span className="text-[10px] text-slate-500">{payloadSyntax.chars} chars</span>
                    </div>
                    <textarea
                      rows={7}
                      value={testPayloadJson}
                      onChange={(e) => setTestPayloadJson(e.target.value)}
                      className="w-full text-xs font-mono bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:outline-none focus:border-teal-500"
                    />
                    {!payloadSyntax.valid && (
                      <p className="mt-1 text-[11px] text-rose-400 font-mono">{payloadSyntax.error}</p>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={handleValidateSchema}
                    disabled={valLoading || !payloadSyntax.valid}
                    className="w-full py-2 text-xs font-medium rounded-lg bg-teal-600 hover:bg-teal-500 disabled:opacity-50 text-white transition-colors flex items-center justify-center gap-2"
                  >
                    {valLoading ? 'Validating against schema...' : 'Validate Payload'}
                  </button>

                  {valResult && (
                    <div
                      className={`p-3 rounded-lg border text-xs ${
                        valResult.valid
                          ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                          : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                      }`}
                    >
                      <div className="font-semibold flex items-center gap-1.5">
                        {valResult.valid ? '✓ Schema Validation Passed' : '✗ Schema Validation Failed'}
                      </div>
                      {!valResult.valid && valResult.errors.length > 0 && (
                        <ul className="list-disc list-inside mt-1 space-y-0.5 text-[11px]">
                          {valResult.errors.map((err, i) => (
                            <li key={i}>{err}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: SAFE CONTROLLED TOOLS */}
          {activeTab === 'tools' && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Tools List */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-200">Controlled Application Tools</h3>
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs text-slate-300">
                      <thead className="bg-slate-800/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                        <tr>
                          <th className="py-3 px-4">Tool Code</th>
                          <th className="py-3 px-4">Type</th>
                          <th className="py-3 px-4">Safety</th>
                          <th className="py-3 px-4">Risk</th>
                          <th className="py-3 px-4">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {tools.map((t) => (
                          <tr
                            key={t.id}
                            onClick={() => setSelectedToolId(t.id)}
                            className={`cursor-pointer transition-colors ${
                              selectedToolId === t.id ? 'bg-teal-900/20 border-l-2 border-teal-500' : 'hover:bg-slate-800/30'
                            }`}
                          >
                            <td className="py-3 px-4 font-mono font-semibold text-amber-400">
                              {t.tool_code}
                              <p className="text-[11px] text-slate-400 font-sans font-normal">{t.name}</p>
                            </td>
                            <td className="py-3 px-4 font-mono text-[11px] text-slate-300">{t.execution_type}</td>
                            <td className="py-3 px-4">
                              <span className="px-2 py-0.5 rounded text-[10px] bg-emerald-500/10 text-emerald-300 border border-emerald-500/20 font-sans">
                                {t.is_read_only ? 'Read-Only' : 'Stateful'}
                              </span>
                            </td>
                            <td className="py-3 px-4 font-mono text-[11px] text-slate-300">{t.risk_level}</td>
                            <td className="py-3 px-4">
                              <StatusBadge label={t.status} />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              {/* Tool Test Runner */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-200">Safe Sandboxed Tool Runner</h3>
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">Select Tool</label>
                    <select
                      value={selectedToolId}
                      onChange={(e) => setSelectedToolId(e.target.value)}
                      className="w-full text-xs font-mono bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-slate-200 focus:outline-none focus:border-teal-500"
                    >
                      {tools.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.tool_code} ({t.name})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">Tool Input Parameters (JSON)</label>
                    <textarea
                      rows={4}
                      value={toolParamsJson}
                      onChange={(e) => setToolParamsJson(e.target.value)}
                      className="w-full text-xs font-mono bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:outline-none focus:border-teal-500"
                    />
                  </div>

                  <button
                    type="button"
                    onClick={handleTestTool}
                    disabled={toolLoading}
                    className="w-full py-2 text-xs font-medium rounded-lg bg-teal-600 hover:bg-teal-500 text-white transition-colors flex items-center justify-center gap-2"
                  >
                    {toolLoading ? 'Executing safe tool...' : 'Execute Tool Test'}
                  </button>

                  {toolResult && (
                    <div className="p-3 rounded-lg border border-slate-800 bg-slate-950 font-mono text-xs space-y-1">
                      <div className="flex items-center justify-between text-slate-400">
                        <span>Duration: {toolResult.duration_ms}ms</span>
                        <span className={toolResult.success ? 'text-emerald-400' : 'text-rose-400'}>
                          {toolResult.success ? 'SUCCESS' : 'FAILED'}
                        </span>
                      </div>
                      <pre className="text-teal-300 text-[11px] overflow-x-auto p-2 bg-slate-900 rounded">
                        {JSON.stringify(toolResult.data, null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: TRACES & VISUAL TIMELINE */}
          {activeTab === 'traces' && (
            <div className="space-y-4">
              {/* Universal Trace Filter Bar */}
              <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl border border-slate-800 bg-slate-900/60">
                <div className="flex flex-wrap items-center gap-2 flex-1">
                  <input
                    type="text"
                    value={traceSearch}
                    onChange={(e) => setTraceSearch(e.target.value)}
                    placeholder="Filter by trace ID, task code, model..."
                    className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-white placeholder-slate-500 w-64"
                  />
                  <select
                    value={traceStatusFilter}
                    onChange={(e) => setTraceStatusFilter(e.target.value)}
                    className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-white"
                  >
                    <option value="">All Statuses</option>
                    <option value="SUCCESS">SUCCESS</option>
                    <option value="FAILED">FAILED</option>
                    <option value="TIMEOUT">TIMEOUT</option>
                    <option value="REJECTED">REJECTED</option>
                  </select>
                </div>
                <div className="text-xs text-slate-400">{filteredTraces.length} traces found</div>
              </div>

              {filteredTraces.length === 0 ? (
                <EmptyState
                  title="No orchestration traces matching filters"
                  message="Execute conversational, extraction, or data workflows to see step traces."
                />
              ) : (
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs text-slate-300">
                      <thead className="bg-slate-800/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                        <tr>
                          <th className="py-3 px-4">Trace ID</th>
                          <th className="py-3 px-4">Task</th>
                          <th className="py-3 px-4">Model</th>
                          <th className="py-3 px-4">Prompt</th>
                          <th className="py-3 px-4">Schema</th>
                          <th className="py-3 px-4">Tokens</th>
                          <th className="py-3 px-4">Duration</th>
                          <th className="py-3 px-4">Status</th>
                          <th className="py-3 px-4 text-right">Inspect</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50 font-mono">
                        {filteredTraces.map((tr) => (
                          <tr key={tr.id} className="hover:bg-slate-800/30 transition-colors">
                            <td className="py-3 px-4 font-semibold text-teal-400 text-[11px]">
                              {tr.id.substring(0, 8)}...
                            </td>
                            <td className="py-3 px-4 font-sans text-slate-200">{tr.task_code}</td>
                            <td className="py-3 px-4 text-cyan-400">{tr.model_identifier || '—'}</td>
                            <td className="py-3 px-4 text-slate-400">{tr.prompt_code || '—'}</td>
                            <td className="py-3 px-4 text-purple-400">{tr.schema_code || '—'}</td>
                            <td className="py-3 px-4 text-slate-400">{tr.total_tokens}</td>
                            <td className="py-3 px-4 text-slate-400">{tr.total_duration_ms}ms</td>
                            <td className="py-3 px-4 font-sans">
                              <StatusBadge label={tr.status} />
                            </td>
                            <td className="py-3 px-4 text-right font-sans">
                              <button
                                type="button"
                                onClick={() => setInspectingTrace(tr)}
                                className="px-2.5 py-1 text-[11px] font-medium rounded bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors border border-slate-700"
                              >
                                Timeline ({tr.steps ? tr.steps.length : 0})
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Visual Execution Step Timeline Modal */}
              {inspectingTrace && (
                <div
                  role="dialog"
                  aria-modal="true"
                  aria-label="Trace Step Timeline Inspector"
                  className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4 backdrop-blur-sm"
                  onClick={() => setInspectingTrace(null)}
                >
                  <div
                    className="bg-slate-900 border border-slate-700 rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-2xl max-h-[85vh] flex flex-col"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div>
                        <div className="flex items-center gap-2">
                          <h4 className="text-base font-bold text-white">Execution Step Timeline</h4>
                          <StatusBadge label={inspectingTrace.status} />
                        </div>
                        <p className="text-xs text-slate-400 font-mono mt-0.5">Trace: {inspectingTrace.id}</p>
                      </div>
                      <button
                        type="button"
                        onClick={() => setInspectingTrace(null)}
                        className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800"
                      >
                        ✕
                      </button>
                    </div>

                    {/* Error Banner if Trace Failed */}
                    {inspectingTrace.status === 'FAILED' && (
                      <ErrorExplanationBanner
                        errorCode="ORCHESTRATOR_EXECUTION_FAILED"
                        errorMessage={inspectingTrace.error_message || 'Workflow step failed or rejected'}
                        correlationId={inspectingTrace.id}
                      />
                    )}

                    {/* Timeline Summary Metrics */}
                    <div className="grid grid-cols-3 gap-2 p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs">
                      <div>
                        <span className="text-slate-500">Total Duration:</span>
                        <div className="font-mono font-semibold text-teal-400">{inspectingTrace.total_duration_ms}ms</div>
                      </div>
                      <div>
                        <span className="text-slate-500">Tokens:</span>
                        <div className="font-mono font-semibold text-slate-200">{inspectingTrace.total_tokens}</div>
                      </div>
                      <div>
                        <span className="text-slate-500">Task Code:</span>
                        <div className="font-mono font-semibold text-indigo-300">{inspectingTrace.task_code}</div>
                      </div>
                    </div>

                    {/* Chronological Step Ladder */}
                    <div className="space-y-3 overflow-y-auto flex-1 pr-1">
                      {inspectingTrace.steps && inspectingTrace.steps.length > 0 ? (
                        inspectingTrace.steps.map((st, index) => {
                          const maxDuration = Math.max(...inspectingTrace.steps.map((s) => s.duration_ms || 1), 1);
                          const pct = Math.min(100, Math.max(10, Math.round(((st.duration_ms || 1) / maxDuration) * 100)));

                          return (
                            <div key={st.step || index} className="relative pl-6 pb-2 border-l-2 border-slate-800">
                              {/* Timeline Node Dot */}
                              <div
                                className={`absolute -left-[9px] top-1 h-4 w-4 rounded-full border-2 border-slate-900 flex items-center justify-center text-[9px] font-bold ${
                                  st.status === 'SUCCESS' ? 'bg-emerald-500 text-white' : 'bg-rose-500 text-white'
                                }`}
                              >
                                {st.step}
                              </div>

                              <div className="rounded-xl border border-slate-800 bg-slate-950/80 p-3 space-y-2">
                                <div className="flex items-center justify-between text-xs">
                                  <span className="font-bold text-slate-200">{st.name}</span>
                                  <div className="flex items-center gap-2">
                                    <span className="font-mono text-slate-400">{st.duration_ms}ms</span>
                                    <span
                                      className={`px-1.5 py-0.2 rounded text-[10px] font-mono ${
                                        st.status === 'SUCCESS'
                                          ? 'bg-emerald-500/10 text-emerald-300'
                                          : 'bg-rose-500/10 text-rose-300'
                                      }`}
                                    >
                                      {st.status}
                                    </span>
                                  </div>
                                </div>

                                {/* Latency Visual Bar */}
                                <div className="w-full bg-slate-900 rounded-full h-1.5 overflow-hidden">
                                  <div
                                    className={`h-full rounded-full transition-all ${
                                      st.status === 'SUCCESS' ? 'bg-teal-500' : 'bg-rose-500'
                                    }`}
                                    style={{ width: `${pct}%` }}
                                  />
                                </div>

                                {st.details && (
                                  <pre className="text-slate-300 text-[10px] font-mono overflow-x-auto p-2 bg-slate-900/90 rounded border border-slate-800/60 max-h-32">
                                    {JSON.stringify(st.details, null, 2)}
                                  </pre>
                                )}
                              </div>
                            </div>
                          );
                        })
                      ) : (
                        <div className="text-center p-6 text-xs text-slate-400">
                          No step breakdown recorded for this trace.
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* New Prompt Modal */}
      {showPromptModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Create New Prompt Template"
          className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4 backdrop-blur-sm"
          onClick={() => setShowPromptModal(false)}
        >
          <div
            className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h4 className="text-base font-bold text-white">Create New Prompt Template</h4>
              <button
                type="button"
                onClick={() => setShowPromptModal(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleSavePrompt} className="space-y-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1">Prompt Code</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. portfolio_summary_prompt"
                  value={promptForm.prompt_code}
                  onChange={(e) => setPromptForm({ ...promptForm, prompt_code: e.target.value })}
                  className="w-full font-mono bg-slate-800 border border-slate-700 rounded-lg px-3 py-1.5 text-slate-200"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Name</label>
                <input
                  type="text"
                  required
                  value={promptForm.name}
                  onChange={(e) => setPromptForm({ ...promptForm, name: e.target.value })}
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-1.5 text-slate-200"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">User Template</label>
                <textarea
                  rows={3}
                  required
                  value={promptForm.user_template}
                  onChange={(e) => setPromptForm({ ...promptForm, user_template: e.target.value })}
                  className="w-full font-mono bg-slate-800 border border-slate-700 rounded-lg p-2 text-slate-200"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowPromptModal(false)}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-3 py-1.5 rounded-lg bg-teal-600 hover:bg-teal-500 text-white font-medium"
                >
                  Save Prompt
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
