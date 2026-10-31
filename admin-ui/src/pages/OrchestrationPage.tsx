import React, { useEffect, useState } from 'react';
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

  // Schema Validator Playground State
  const [selectedSchemaId, setSelectedSchemaId] = useState<string>('');
  const [testPayloadJson, setTestPayloadJson] = useState<string>('{\n  "status": "COMPLETED",\n  "summary": "Monthly balance is within normal limits.",\n  "recommended_action": "Set aside $150 to emergency savings."\n}');
  const [valLoading, setValLoading] = useState(false);
  const [valResult, setValResult] = useState<SchemaValidationResult | null>(null);

  // Tool Test Runner State
  const [selectedToolId, setSelectedToolId] = useState<string>('');
  const [toolParamsJson, setToolParamsJson] = useState<string>('{\n  "account_id": "ACC-998877"\n}');
  const [toolLoading, setToolLoading] = useState(false);
  const [toolResult, setToolResult] = useState<ToolExecutionResult | null>(null);

  // Trace Inspector State
  const [inspectingTrace, setInspectingTrace] = useState<OrchestratorTraceView | null>(null);

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
      if (pRes.status === 'ok') setPrompts(pRes.data);
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
      } catch (e) {
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
      } catch (e) {
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
            onClick={() => loadData()}
            className="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-800 text-slate-300 hover:text-white border border-slate-700 flex items-center gap-1.5 transition-colors"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
          <button
            onClick={() => setShowPromptModal(true)}
            className="px-3 py-1.5 text-xs font-medium rounded-lg bg-teal-600 hover:bg-teal-500 text-white shadow-sm transition-colors flex items-center gap-1.5"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Prompt
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-800 space-x-1">
        {(
          [
            { id: 'tasks', label: 'Tasks', count: tasks.length },
            { id: 'prompts', label: 'Prompts', count: prompts.length },
            { id: 'schemas', label: 'Schemas & Validator', count: schemas.length },
            { id: 'tools', label: 'Safe Tools', count: tools.length },
            { id: 'traces', label: 'Execution Traces', count: traces.length },
          ] as const
        ).map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors border-b-2 flex items-center gap-2 ${
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
        <div className="p-3 text-xs rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 flex items-center gap-2">
          <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {error}
        </div>
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
        <div className="flex items-center justify-center p-12 text-slate-400 text-sm">
          <div className="animate-spin rounded-full h-5 w-5 border-2 border-teal-500 border-t-transparent mr-2" />
          Loading orchestration registry...
        </div>
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
                          <span className={`px-2 py-0.5 rounded text-[10px] font-sans ${
                            t.allowed_data_sensitivity === 'RESTRICTED'
                              ? 'bg-rose-500/10 text-rose-300 border border-rose-500/20'
                              : 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/20'
                          }`}>
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

          {/* TAB 2: PROMPTS */}
          {activeTab === 'prompts' && (
            <div className="space-y-4">
              <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-800/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                      <tr>
                        <th className="py-3 px-4">Prompt Code</th>
                        <th className="py-3 px-4">Name</th>
                        <th className="py-3 px-4">Version</th>
                        <th className="py-3 px-4">Capability</th>
                        <th className="py-3 px-4">Variables</th>
                        <th className="py-3 px-4">Status</th>
                        <th className="py-3 px-4 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/50">
                      {prompts.map((p) => (
                        <tr key={p.id} className="hover:bg-slate-800/30 transition-colors">
                          <td className="py-3 px-4 font-mono font-semibold text-teal-400">{p.prompt_code}</td>
                          <td className="py-3 px-4 text-slate-200 font-medium">
                            {p.name}
                            {p.description && <p className="text-[11px] text-slate-400 font-normal">{p.description}</p>}
                          </td>
                          <td className="py-3 px-4 font-mono text-cyan-400">v{p.version}</td>
                          <td className="py-3 px-4 text-slate-400 font-mono text-[11px]">{p.capability_code}</td>
                          <td className="py-3 px-4">
                            <div className="flex flex-wrap gap-1">
                              {p.input_variables.map((v) => (
                                <span key={v} className="px-1.5 py-0.5 rounded bg-slate-800 text-[10px] font-mono text-slate-300 border border-slate-700">
                                  {`{{${v}}}`}
                                </span>
                              ))}
                            </div>
                          </td>
                          <td className="py-3 px-4">
                            <StatusBadge label={p.status} />
                          </td>
                          <td className="py-3 px-4 text-right">
                            {p.status === 'DRAFT' && (
                              <button
                                onClick={() => handlePublishPrompt(p.id)}
                                className="px-2.5 py-1 text-[11px] font-medium rounded bg-teal-600 hover:bg-teal-500 text-white transition-colors"
                              >
                                Publish
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: SCHEMAS & VALIDATOR */}
          {activeTab === 'schemas' && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Schemas List */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-200">Registered Schemas</h3>
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
              </div>

              {/* Schema Playground */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-200">Interactive Schema Validator Playground</h3>
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
                    <label className="block text-xs font-medium text-slate-400 mb-1">Payload JSON (Simulate Model Output)</label>
                    <textarea
                      rows={6}
                      value={testPayloadJson}
                      onChange={(e) => setTestPayloadJson(e.target.value)}
                      className="w-full text-xs font-mono bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-slate-200 focus:outline-none focus:border-teal-500"
                    />
                  </div>

                  <button
                    onClick={handleValidateSchema}
                    disabled={valLoading}
                    className="w-full py-2 text-xs font-medium rounded-lg bg-teal-600 hover:bg-teal-500 text-white transition-colors flex items-center justify-center gap-2"
                  >
                    {valLoading ? 'Validating against schema...' : 'Validate Payload'}
                  </button>

                  {valResult && (
                    <div className={`p-3 rounded-lg border text-xs ${
                      valResult.valid
                        ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                        : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                    }`}>
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

          {/* TAB 4: TOOLS */}
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

          {/* TAB 5: TRACES */}
          {activeTab === 'traces' && (
            <div className="space-y-4">
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
                      {traces.map((tr) => (
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
                              onClick={() => setInspectingTrace(tr)}
                              className="px-2.5 py-1 text-[11px] font-medium rounded bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors"
                            >
                              Steps ({tr.steps ? tr.steps.length : 0})
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Trace Step Drawer / Modal */}
              {inspectingTrace && (
                <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
                  <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-2xl w-full p-5 space-y-4 shadow-xl">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div>
                        <h4 className="text-sm font-bold text-white">Execution Steps Inspector</h4>
                        <p className="text-xs text-slate-400 font-mono">Trace ID: {inspectingTrace.id}</p>
                      </div>
                      <button
                        onClick={() => setInspectingTrace(null)}
                        className="text-slate-400 hover:text-white"
                      >
                        ✕
                      </button>
                    </div>

                    <div className="space-y-2 max-h-96 overflow-y-auto">
                      {inspectingTrace.steps && inspectingTrace.steps.length > 0 ? (
                        inspectingTrace.steps.map((st) => (
                          <div
                            key={st.step}
                            className="p-3 rounded-lg border border-slate-800 bg-slate-950 font-mono text-xs space-y-1.5"
                          >
                            <div className="flex items-center justify-between">
                              <span className="font-semibold text-teal-400">
                                Step {st.step}: {st.name}
                              </span>
                              <span className={`px-2 py-0.5 rounded text-[10px] ${
                                st.status === 'SUCCESS'
                                  ? 'bg-emerald-500/10 text-emerald-300'
                                  : 'bg-rose-500/10 text-rose-300'
                              }`}>
                                {st.status} ({st.duration_ms}ms)
                              </span>
                            </div>
                            <pre className="text-slate-300 text-[11px] overflow-x-auto p-2 bg-slate-900 rounded">
                              {JSON.stringify(st.details, null, 2)}
                            </pre>
                          </div>
                        ))
                      ) : (
                        <div className="text-center p-4 text-xs text-slate-400 font-sans">
                          No step data recorded for this trace.
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
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-5 space-y-4 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h4 className="text-sm font-bold text-white">Create New Prompt Template</h4>
              <button onClick={() => setShowPromptModal(false)} className="text-slate-400 hover:text-white">✕</button>
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
