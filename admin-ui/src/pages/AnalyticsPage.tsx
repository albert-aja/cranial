import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  AnalyticsBenchmarkResult,
  AnalyticsDatasetRequest,
  AnalyticsDatasetView,
  AnalyticsQueryRequest,
  AnalyticsReportResult,
  AnomalyItemView,
  AnomalyReviewRequest,
  DeterministicProvenance,
} from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';

export function AnalyticsPage() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'explorer' | 'budget' | 'anomalies' | 'connectors' | 'benchmarks'>('explorer');
  const [datasets, setDatasets] = useState<AnalyticsDatasetView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Explorer State
  const [selectedDataset, setSelectedDataset] = useState<string>('peggy_budget_monthly_2026');
  const [nlQuery, setNlQuery] = useState<string>('');
  const [comparisonMode, setComparisonMode] = useState<string>('BUDGET_VS_ACTUAL');
  const [forceFresh, setForceFresh] = useState<boolean>(false);
  const [reportResult, setReportResult] = useState<AnalyticsReportResult | null>(null);
  const [provenanceModal, setProvenanceModal] = useState<DeterministicProvenance | null>(null);

  // Anomalies Queue State
  const [anomalies, setAnomalies] = useState<AnomalyItemView[]>([]);
  const [anomalyFilterStatus, setAnomalyFilterStatus] = useState<string>('ALL');
  const [reviewModalAnomaly, setReviewModalAnomaly] = useState<AnomalyItemView | null>(null);
  const [reviewStatusChoice, setReviewStatusChoice] = useState<'VERIFIED_ANOMALY' | 'FALSE_POSITIVE' | 'RESOLVED' | 'IGNORED'>('VERIFIED_ANOMALY');
  const [reviewNotesInput, setReviewNotesInput] = useState<string>('');
  const [reviewSubmitting, setReviewSubmitting] = useState<boolean>(false);

  // Connectors State
  const [isNewConnectorOpen, setIsNewConnectorOpen] = useState(false);
  const [newConnectorForm, setNewConnectorForm] = useState<AnalyticsDatasetRequest>({
    dataset_code: '',
    name: '',
    description: '',
    domain_archetype: 'MONEY_BUDGET_SUMMARY',
    connector_type: 'SAMPLE_SERIES',
    refresh_policy: 'DAILY_SNAPSHOT',
    max_query_window_days: 180,
    is_active: true,
  });

  // Benchmarks & Cache State
  const [cacheStats, setCacheStats] = useState<{ total_entries: number; total_hits: number; oldest_entry_at: string | null; executed_at: string } | null>(null);
  const [benchmarkResult, setBenchmarkResult] = useState<AnalyticsBenchmarkResult | null>(null);
  const [benchmarking, setBenchmarking] = useState(false);

  useEffect(() => {
    loadDatasets();
    loadAnomalies();
    loadCacheStats();
  }, []);

  async function loadDatasets() {
    try {
      setLoading(true);
      const res = await api.listAnalyticsDatasets();
      if (res.data) {
        setDatasets(res.data);
        if (res.data.length > 0 && !selectedDataset) {
          setSelectedDataset(res.data[0].dataset_code);
        }
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load analytics datasets');
    } finally {
      setLoading(false);
    }
  }

  async function loadAnomalies() {
    try {
      const res = await api.listAnalyticsAnomalies({
        status: anomalyFilterStatus !== 'ALL' ? anomalyFilterStatus : undefined,
      });
      if (res.data) {
        setAnomalies(res.data);
      }
    } catch (err: unknown) {
      console.error('Failed to load anomalies:', err);
    }
  }

  async function loadCacheStats() {
    try {
      const res = await api.getAnalyticsCacheStats();
      if (res.data) {
        setCacheStats(res.data);
      }
    } catch (err: unknown) {
      console.error('Failed to load cache stats:', err);
    }
  }

  async function handleRunQuery(overrideQuery?: string) {
    const q = overrideQuery !== undefined ? overrideQuery : nlQuery;
    try {
      setLoading(true);
      setError(null);
      const req: AnalyticsQueryRequest = {
        dataset_code: selectedDataset,
        natural_language_query: q.trim() || undefined,
        comparison_type: comparisonMode as any,
        force_fresh: forceFresh,
        include_anomalies: true,
        include_forecast: true,
      };
      const res = await api.runAnalyticsQuery(req);
      if (res.data) {
        setReportResult(res.data);
      }
      loadCacheStats();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Analytical calculation failed');
    } finally {
      setLoading(false);
    }
  }

  async function handleReviewSubmit() {
    if (!reviewModalAnomaly) return;
    try {
      setReviewSubmitting(true);
      const payload: AnomalyReviewRequest = {
        status: reviewStatusChoice,
        review_notes: reviewNotesInput.trim() || undefined,
      };
      await api.reviewAnalyticsAnomaly(reviewModalAnomaly.id, payload);
      setReviewModalAnomaly(null);
      setReviewNotesInput('');
      await loadAnomalies();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to submit review');
    } finally {
      setReviewSubmitting(false);
    }
  }

  async function handlePurgeCache() {
    try {
      setLoading(true);
      await api.purgeAnalyticsCache(selectedDataset);
      await loadCacheStats();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to purge cache');
    } finally {
      setLoading(false);
    }
  }

  async function handleRunBenchmarks() {
    try {
      setBenchmarking(true);
      setError(null);
      const res = await api.runAnalyticsBenchmarks();
      if (res.data) {
        setBenchmarkResult(res.data);
      }
      await loadCacheStats();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Benchmark execution failed');
    } finally {
      setBenchmarking(false);
    }
  }

  async function handleSaveNewDataset(e: React.FormEvent) {
    e.preventDefault();
    try {
      setLoading(true);
      await api.saveAnalyticsDataset(newConnectorForm);
      setIsNewConnectorOpen(false);
      setNewConnectorForm({
        dataset_code: '',
        name: '',
        description: '',
        domain_archetype: 'MONEY_BUDGET_SUMMARY',
        connector_type: 'SAMPLE_SERIES',
        refresh_policy: 'DAILY_SNAPSHOT',
        max_query_window_days: 180,
        is_active: true,
      });
      await loadDatasets();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to register dataset connector');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <span>{t('analytics.title', 'Data Intelligence & Analytics')}</span>
            <span className="rounded-full bg-emerald-500/20 px-2.5 py-0.5 text-xs font-semibold text-emerald-400 border border-emerald-500/30">
              Deterministic Math Engine
            </span>
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {t('analytics.subtitle', 'Authoritative application reporting connectors, deterministic calculation provenance, 50-30-20 budget trends, IQR/Z-score anomaly detection, and safe query caching')}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              loadDatasets();
              loadAnomalies();
              loadCacheStats();
            }}
            className="rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 hover:text-white transition"
          >
            {t('action.refreshAll', 'Refresh All')}
          </button>
        </div>
      </div>

      {/* Global Error Banner */}
      {error && (
        <div className="rounded-lg border border-red-500/30 bg-red-950/40 p-4 text-sm text-red-200 flex items-start justify-between">
          <span>{error}</span>
          <button type="button" onClick={() => setError(null)} className="text-red-400 hover:text-red-100 font-bold ml-4">
            ×
          </button>
        </div>
      )}

      {/* Navigation Sub-Tabs */}
      <div className="border-b border-slate-800">
        <nav className="flex space-x-4">
          {[
            { id: 'explorer', label: t('analytics.tab.explorer', 'Analytics Explorer & NL Query') },
            { id: 'budget', label: t('analytics.tab.budget', 'Budget vs Actual & Trends') },
            { id: 'anomalies', label: `${t('analytics.tab.anomalies', 'Anomaly Review Queue')} (${anomalies.filter((a) => a.status === 'DETECTED').length})` },
            { id: 'connectors', label: t('analytics.tab.connectors', 'Dataset Connectors') },
            { id: 'benchmarks', label: t('analytics.tab.benchmarks', 'Cache & Benchmarks') },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`pb-3 text-sm font-medium border-b-2 transition ${
                activeTab === tab.id
                  ? 'border-indigo-500 text-indigo-400'
                  : 'border-transparent text-slate-400 hover:text-slate-200 hover:border-slate-700'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* TAB 1: ANALYTICS EXPLORER & NL QUERY */}
      {activeTab === 'explorer' && (
        <div className="space-y-6">
          {/* Query Bar & Controls */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="md:col-span-2">
                <label className="block text-xs font-medium text-slate-400 mb-1">
                  {t('analytics.query.dataset', 'Dataset Source')}
                </label>
                <select
                  value={selectedDataset}
                  onChange={(e) => setSelectedDataset(e.target.value)}
                  className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  {datasets.map((d) => (
                    <option key={d.dataset_code} value={d.dataset_code}>
                      {d.name} ({d.dataset_code})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">
                  {t('analytics.query.comparison', 'Comparison Mode')}
                </label>
                <select
                  value={comparisonMode}
                  onChange={(e) => setComparisonMode(e.target.value)}
                  className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="BUDGET_VS_ACTUAL">Budget vs Actual</option>
                  <option value="PERIOD_OVER_PERIOD">Period over Period (MoM/DoD)</option>
                  <option value="NONE">Standard Aggregation (None)</option>
                </select>
              </div>

              <div className="flex items-center pt-6">
                <label className="flex items-center gap-2 cursor-pointer text-xs text-slate-300">
                  <input
                    type="checkbox"
                    checked={forceFresh}
                    onChange={(e) => setForceFresh(e.target.checked)}
                    className="rounded border-slate-700 bg-slate-950 text-indigo-600 focus:ring-indigo-500"
                  />
                  <span>{t('analytics.query.forceFresh', 'Bypass Cache (Force Fresh)')}</span>
                </label>
              </div>
            </div>

            {/* Natural Language Query Input */}
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">
                Natural Language Financial Query (Dual-Language EN / ID)
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={nlQuery}
                  onChange={(e) => setNlQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleRunQuery();
                  }}
                  placeholder={t(
                    'analytics.query.placeholder',
                    'Ask financial question (e.g. "Analyze Peggy household budget variance and savings rate" or "Jelaskan ringkasan arus kas dan anomali")'
                  )}
                  className="flex-1 rounded-lg bg-slate-950 border border-slate-800 px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <button
                  type="button"
                  disabled={loading}
                  onClick={() => handleRunQuery()}
                  className="rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 transition disabled:opacity-50"
                >
                  {loading ? 'Calculating…' : t('analytics.query.run', 'Run Analysis')}
                </button>
              </div>

              {/* Sample Query Pills */}
              <div className="mt-2.5 flex flex-wrap gap-2 text-xs">
                <span className="text-slate-500 self-center">Try:</span>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedDataset('peggy_budget_monthly_2026');
                    setNlQuery('Analyze Peggy household budget variance and 50-30-20 savings rate');
                    handleRunQuery('Analyze Peggy household budget variance and 50-30-20 savings rate');
                  }}
                  className="rounded-full bg-slate-800 px-3 py-1 text-slate-300 hover:bg-slate-700 transition"
                >
                  📊 Peggy 50-30-20 Budget Variance (EN)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedDataset('peggy_cashflow_daily_q4');
                    setNlQuery('Jelaskan ringkasan arus kas harian dan deteksi anomali pengeluaran');
                    handleRunQuery('Jelaskan ringkasan arus kas harian dan deteksi anomali pengeluaran');
                  }}
                  className="rounded-full bg-slate-800 px-3 py-1 text-slate-300 hover:bg-slate-700 transition"
                >
                  🔎 Arus Kas & Anomali Pengeluaran (ID)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedDataset('peggy_budget_monthly_2026');
                    setNlQuery('How is our money doing?');
                    handleRunQuery('How is our money doing?');
                  }}
                  className="rounded-full bg-amber-950/60 border border-amber-800/40 px-3 py-1 text-amber-300 hover:bg-amber-900/60 transition"
                >
                  ⚡ Ambiguous Query (Triggers Clarification)
                </button>
              </div>
            </div>
          </div>

          {/* Clarification Prompt Banner (if vague query was asked) */}
          {reportResult?.clarification?.required && (
            <div className="rounded-xl border border-amber-500/40 bg-amber-950/20 p-5 space-y-3">
              <div className="flex items-center gap-2">
                <span className="text-amber-400 text-lg">⚠️</span>
                <h3 className="text-sm font-semibold text-amber-200">
                  {t('analytics.clarification.title', 'Clarification Needed for Ambiguous Query')}
                </h3>
              </div>
              <p className="text-xs text-amber-300/90">{reportResult.clarification.question}</p>
              <div className="flex flex-wrap gap-2 pt-1">
                {reportResult.clarification.suggested_options.map((opt, i) => (
                  <button
                    key={i}
                    type="button"
                    onClick={() => {
                      setNlQuery(opt);
                      handleRunQuery(opt);
                    }}
                    className="rounded-lg bg-amber-900/40 border border-amber-600/40 px-3 py-1.5 text-xs font-medium text-amber-200 hover:bg-amber-800/60 transition"
                  >
                    ↳ {opt}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Results Section */}
          {reportResult && (
            <div className="space-y-6">
              {/* Natural Language Summary Card with Provenance Tag */}
              {reportResult.natural_language_summary && (
                <div className="rounded-xl border border-indigo-900/40 bg-indigo-950/20 p-5 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400 flex items-center gap-2">
                      <span>Deterministic Insight Summary</span>
                      {reportResult.cached && (
                        <span className="rounded bg-indigo-500/20 px-2 py-0.5 text-[10px] text-indigo-300 border border-indigo-500/30">
                          ⚡ CACHE HIT
                        </span>
                      )}
                    </span>
                    <button
                      type="button"
                      onClick={() => setProvenanceModal(reportResult.provenance)}
                      className="text-xs text-indigo-400 hover:text-indigo-200 underline"
                    >
                      Inspect Provenance Formula
                    </button>
                  </div>
                  <p className="text-sm text-slate-200 leading-relaxed">{reportResult.natural_language_summary}</p>
                </div>
              )}

              {/* KPI Cards Grid */}
              {reportResult.kpis && reportResult.kpis.length > 0 && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3">Key Metrics</h3>
                  <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    {reportResult.kpis.map((kpi, idx) => (
                      <div key={idx} className="rounded-xl border border-slate-800 bg-slate-900/80 p-4 space-y-2 shadow-sm">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-medium text-slate-400">{kpi.label}</span>
                          {kpi.trend_direction && (
                            <span
                              className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                                kpi.trend_direction === 'UP' || kpi.trend_direction === 'ON_TRACK'
                                  ? 'bg-emerald-500/20 text-emerald-300'
                                  : kpi.trend_direction === 'DOWN' || kpi.trend_direction === 'OFF_TRACK'
                                  ? 'bg-rose-500/20 text-rose-300'
                                  : 'bg-slate-700 text-slate-300'
                              }`}
                            >
                              {kpi.trend_direction}
                            </span>
                          )}
                        </div>
                        <div className="text-xl font-bold text-white tracking-tight">{kpi.formatted_value}</div>
                        {kpi.provenance_formula && (
                          <div className="text-[10px] font-mono text-slate-500 truncate" title={kpi.provenance_formula}>
                            fx: {kpi.provenance_formula}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Category Breakdown (e.g., 50-30-20 or Expense Categories) */}
              {reportResult.breakdown && reportResult.breakdown.length > 0 && (
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
                  <h3 className="text-sm font-semibold text-white flex items-center justify-between">
                    <span>{t('analytics.chart.breakdown', 'Category Allocation & Budget Limits')}</span>
                    <span className="text-xs font-normal text-slate-400">Deterministic Mathematical Variance</span>
                  </h3>
                  <div className="space-y-3">
                    {reportResult.breakdown.map((item, idx) => {
                      const budget = item.budget_amount ?? 1;
                      const actual = item.actual_amount;
                      const ratio = Math.min(100, Math.round((actual / budget) * 100));
                      const isOver = item.status === 'OVER_BUDGET';
                      return (
                        <div key={idx} className="space-y-1">
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-medium text-slate-200">{item.category}</span>
                            <div className="flex items-center gap-3">
                              <span className="text-slate-400">
                                Rp {actual.toLocaleString('id-ID')} / {item.budget_amount ? `Rp ${item.budget_amount.toLocaleString('id-ID')}` : 'N/A'}
                              </span>
                              <span
                                className={`font-semibold ${
                                  isOver ? 'text-rose-400' : 'text-emerald-400'
                                }`}
                              >
                                {item.variance_percent !== undefined && item.variance_percent !== null
                                  ? `${item.variance_percent > 0 ? '+' : ''}${item.variance_percent.toFixed(1)}%`
                                  : ''}
                              </span>
                            </div>
                          </div>
                          {/* Progress bar */}
                          <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden">
                            <div
                              className={`h-full rounded-full transition-all duration-500 ${
                                isOver ? 'bg-rose-500' : 'bg-emerald-500'
                              }`}
                              style={{ width: `${ratio}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Time Series Timeline Chart (SVG visualization) */}
              {reportResult.time_series && reportResult.time_series.length > 0 && (
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
                  <h3 className="text-sm font-semibold text-white">
                    {t('analytics.chart.timeSeries', 'Cashflow Trend & Inflow/Outflow Timeline')}
                  </h3>
                  <div className="h-44 w-full flex items-end gap-1.5 pt-4">
                    {reportResult.time_series.map((pt, i) => {
                      const maxVal = Math.max(...reportResult.time_series.map((p) => p.primary_value || 1));
                      const heightPercent = Math.max(8, Math.round((pt.primary_value / maxVal) * 100));
                      return (
                        <div key={i} className="flex-1 flex flex-col items-center gap-1 group relative">
                          {/* Tooltip on hover */}
                          <div className="opacity-0 group-hover:opacity-100 transition absolute bottom-full mb-1 z-10 bg-slate-950 border border-slate-700 rounded px-2 py-1 text-[10px] text-slate-200 pointer-events-none whitespace-nowrap shadow-lg">
                            <div>{pt.period_label}</div>
                            <div className="font-semibold text-indigo-300">Rp {pt.primary_value.toLocaleString('id-ID')}</div>
                            {pt.is_anomaly && <div className="text-rose-400 font-bold">⚠️ OUTLIER SPIKE</div>}
                          </div>
                          {/* Bar */}
                          <div
                            className={`w-full rounded-t transition-all ${
                              pt.is_anomaly
                                ? 'bg-rose-500 hover:bg-rose-400 shadow-rose-500/50 shadow-md'
                                : 'bg-indigo-600/80 hover:bg-indigo-500'
                            }`}
                            style={{ height: `${heightPercent}%` }}
                          />
                          <span className="text-[9px] text-slate-500 rotate-45 md:rotate-0 truncate max-w-full">
                            {pt.period_label.slice(-5)}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Tabular Records Preview */}
              {reportResult.tabular_rows && reportResult.tabular_rows.length > 0 && (
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden">
                  <div className="p-4 border-b border-slate-800">
                    <h3 className="text-sm font-semibold text-white">Data Records Preview</h3>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs text-slate-300">
                      <thead className="bg-slate-950/60 text-slate-400 font-medium border-b border-slate-800">
                        <tr>
                          {Object.keys(reportResult.tabular_rows[0]).map((col) => (
                            <th key={col} className="px-4 py-2.5 capitalize">
                              {col.replace(/_/g, ' ')}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/60">
                        {reportResult.tabular_rows.slice(0, 10).map((row, idx) => (
                          <tr key={idx} className="hover:bg-slate-800/40">
                            {Object.values(row).map((val: any, colIdx) => (
                              <td key={colIdx} className="px-4 py-2 text-slate-200">
                                {typeof val === 'number' ? val.toLocaleString('id-ID') : String(val)}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: BUDGET VS ACTUAL & 50-30-20 TRENDS */}
      {activeTab === 'budget' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-4">
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <span>Peggy Household 50-30-20 Budget Framework (2026)</span>
              <span className="rounded bg-indigo-500/20 px-2 py-0.5 text-xs text-indigo-400 border border-indigo-500/30">
                Rule-Based Money Management
              </span>
            </h2>
            <p className="text-sm text-slate-400">
              Deterministic allocation tracking for household financial resilience. Never moves money or accesses bank rails; purely models personal budget variance.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-5 pt-3">
              <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-5 space-y-2">
                <div className="text-xs font-semibold text-emerald-400 uppercase tracking-wide">50% Needs (Essentials)</div>
                <div className="text-2xl font-bold text-white">Rp 10.000.000</div>
                <div className="text-xs text-slate-400">Actual spent: Rp 9.850.000 (98.5%)</div>
                <div className="text-xs text-emerald-400 font-semibold">✓ Under budget by Rp 150.000</div>
                <div className="text-[10px] text-slate-500">Rent, Groceries, Electricity, Fuel, Health</div>
              </div>

              <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-5 space-y-2">
                <div className="text-xs font-semibold text-indigo-400 uppercase tracking-wide">30% Wants (Lifestyle)</div>
                <div className="text-2xl font-bold text-white">Rp 6.000.000</div>
                <div className="text-xs text-slate-400">Actual spent: Rp 6.200.000 (103.3%)</div>
                <div className="text-xs text-rose-400 font-semibold">⚠️ Over budget by Rp 200.000</div>
                <div className="text-[10px] text-slate-500">Dining Out, Weekend Outings, Subscriptions</div>
              </div>

              <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-5 space-y-2">
                <div className="text-xs font-semibold text-cyan-400 uppercase tracking-wide">20% Savings (Emergency)</div>
                <div className="text-2xl font-bold text-white">Rp 4.000.000</div>
                <div className="text-xs text-slate-400">Actual saved: Rp 3.950.000 (98.8%)</div>
                <div className="text-xs text-emerald-400 font-semibold">✓ Savings Rate: 19.75% of income</div>
                <div className="text-[10px] text-slate-500">Emergency fund, Sinking funds, Investment</div>
              </div>
            </div>

            <div className="pt-4 flex justify-end">
              <button
                type="button"
                onClick={() => {
                  setSelectedDataset('peggy_budget_monthly_2026');
                  setActiveTab('explorer');
                  handleRunQuery('Analyze Peggy household budget variance and savings rate');
                }}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 transition"
              >
                Inspect Live Query in Explorer →
              </button>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: ANOMALY REVIEW QUEUE */}
      {activeTab === 'anomalies' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs font-medium text-slate-400">Filter Status:</span>
              <select
                value={anomalyFilterStatus}
                onChange={(e) => {
                  setAnomalyFilterStatus(e.target.value);
                  setTimeout(() => loadAnomalies(), 50);
                }}
                className="rounded-lg bg-slate-950 border border-slate-800 px-3 py-1.5 text-xs text-slate-200"
              >
                <option value="ALL">All Statuses</option>
                <option value="DETECTED">DETECTED (Pending Review)</option>
                <option value="VERIFIED_ANOMALY">VERIFIED_ANOMALY</option>
                <option value="FALSE_POSITIVE">FALSE_POSITIVE</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="IGNORED">IGNORED</option>
              </select>
            </div>
            <button
              type="button"
              onClick={loadAnomalies}
              className="text-xs text-indigo-400 hover:text-indigo-300"
            >
              Refresh Queue
            </button>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950/60 text-slate-400 font-medium border-b border-slate-800">
                  <tr>
                    <th className="px-4 py-3">Metric & Period</th>
                    <th className="px-4 py-3">Actual Value</th>
                    <th className="px-4 py-3">Expected Baseline</th>
                    <th className="px-4 py-3">Direction & Score</th>
                    <th className="px-4 py-3">Severity</th>
                    <th className="px-4 py-3">Algorithm</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {anomalies.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="px-4 py-8 text-center text-slate-500">
                        No anomalies matching filter.
                      </td>
                    </tr>
                  ) : (
                    anomalies.map((anom) => (
                      <tr key={anom.id} className="hover:bg-slate-800/40">
                        <td className="px-4 py-3">
                          <div className="font-semibold text-white">{anom.metric_name}</div>
                          <div className="text-[10px] text-slate-500">{new Date(anom.period_timestamp).toLocaleDateString()}</div>
                        </td>
                        <td className="px-4 py-3 font-semibold text-rose-300">
                          Rp {anom.actual_value.toLocaleString('id-ID')}
                        </td>
                        <td className="px-4 py-3 text-slate-400">
                          Rp {anom.expected_baseline.toLocaleString('id-ID')}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                              anom.direction === 'SPIKE' ? 'bg-rose-500/20 text-rose-300' : 'bg-cyan-500/20 text-cyan-300'
                            }`}
                          >
                            {anom.direction} ({anom.deviation_score.toFixed(2)}x)
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                              anom.severity === 'CRITICAL'
                                ? 'bg-red-500/20 text-red-300 border border-red-500/30'
                                : anom.severity === 'HIGH'
                                ? 'bg-amber-500/20 text-amber-300'
                                : 'bg-slate-800 text-slate-300'
                            }`}
                          >
                            {anom.severity}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-slate-400 font-mono text-[11px]">{anom.algorithm}</td>
                        <td className="px-4 py-3">
                          <span
                            className={`rounded px-2 py-0.5 text-[10px] font-medium ${
                              anom.status === 'DETECTED'
                                ? 'bg-amber-500/20 text-amber-300'
                                : anom.status === 'VERIFIED_ANOMALY'
                                ? 'bg-rose-500/20 text-rose-300'
                                : anom.status === 'FALSE_POSITIVE'
                                ? 'bg-slate-700 text-slate-300'
                                : 'bg-emerald-500/20 text-emerald-300'
                            }`}
                          >
                            {anom.status}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            type="button"
                            onClick={() => {
                              setReviewModalAnomaly(anom);
                              setReviewStatusChoice(anom.status === 'DETECTED' ? 'VERIFIED_ANOMALY' : (anom.status as any));
                              setReviewNotesInput(anom.review_notes || '');
                            }}
                            className="rounded bg-indigo-600/20 border border-indigo-500/30 px-2.5 py-1 text-[11px] font-medium text-indigo-300 hover:bg-indigo-600/40 transition"
                          >
                            Review
                          </button>
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

      {/* TAB 4: DATASET CONNECTORS */}
      {activeTab === 'connectors' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-white">Registered Application Data Connectors</h2>
            <button
              type="button"
              onClick={() => setIsNewConnectorOpen(true)}
              className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-500 transition"
            >
              + Register New Dataset
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {datasets.map((ds) => (
              <div key={ds.id} className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-3">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-white">{ds.name}</h3>
                    <div className="text-xs font-mono text-indigo-400">{ds.dataset_code}</div>
                  </div>
                  <span className="rounded bg-emerald-500/20 text-emerald-400 px-2 py-0.5 text-[10px] font-semibold border border-emerald-500/30">
                    {ds.domain_archetype}
                  </span>
                </div>

                <p className="text-xs text-slate-400 leading-relaxed">{ds.description}</p>

                <div className="grid grid-cols-2 gap-2 text-[11px] pt-2 border-t border-slate-800">
                  <div>
                    <span className="text-slate-500">Connector:</span>{' '}
                    <span className="text-slate-300">{ds.connector_type}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Refresh:</span>{' '}
                    <span className="text-slate-300">{ds.refresh_policy}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Max Query Window:</span>{' '}
                    <span className="text-slate-300">{ds.max_query_window_days} days</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Data Points:</span>{' '}
                    <span className="text-slate-300">{ds.data_points_count} records</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 5: CACHE & DETERMINISTIC BENCHMARKS */}
      {activeTab === 'benchmarks' && (
        <div className="space-y-6">
          {/* Cache Stats Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white">SHA-256 Analytical Query Cache</h3>
                <p className="text-xs text-slate-400">
                  Deterministic caching of computational result payloads to protect reporting backends.
                </p>
              </div>
              <button
                type="button"
                onClick={handlePurgeCache}
                className="rounded-lg bg-rose-600/20 border border-rose-500/30 px-3 py-1.5 text-xs font-medium text-rose-300 hover:bg-rose-600/40 transition"
              >
                {t('analytics.cache.purge', 'Purge Query Cache')}
              </button>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-2">
              <div className="rounded-lg bg-slate-950 p-3">
                <div className="text-xs text-slate-500">Total Entries</div>
                <div className="text-xl font-bold text-white">{cacheStats?.total_entries ?? 0}</div>
              </div>
              <div className="rounded-lg bg-slate-950 p-3">
                <div className="text-xs text-slate-500">Total Hits</div>
                <div className="text-xl font-bold text-indigo-400">{cacheStats?.total_hits ?? 0}</div>
              </div>
              <div className="rounded-lg bg-slate-950 p-3">
                <div className="text-xs text-slate-500">Cache Strategy</div>
                <div className="text-sm font-semibold text-slate-300">SHA-256 Keyed</div>
              </div>
              <div className="rounded-lg bg-slate-950 p-3">
                <div className="text-xs text-slate-500">Status</div>
                <div className="text-sm font-semibold text-emerald-400">ACTIVE</div>
              </div>
            </div>
          </div>

          {/* Benchmark Runner Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white">Deterministic Mathematics Benchmark Suite</h3>
                <p className="text-xs text-slate-400">
                  Verifies formula accuracy, period-over-period variance calculation, IQR anomaly bounds, and cache hit latency.
                </p>
              </div>
              <button
                type="button"
                disabled={benchmarking}
                onClick={handleRunBenchmarks}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 transition disabled:opacity-50"
              >
                {benchmarking ? 'Running Benchmarks…' : t('analytics.benchmarks.run', 'Run Deterministic Benchmark Suite')}
              </button>
            </div>

            {benchmarkResult && (
              <div className="space-y-4 pt-3 border-t border-slate-800">
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="rounded-lg bg-slate-950 p-3">
                    <div className="text-xs text-slate-500">Accuracy Score</div>
                    <div className="text-xl font-bold text-emerald-400">
                      {(benchmarkResult.accuracy_score * 100).toFixed(0)}%
                    </div>
                  </div>
                  <div className="rounded-lg bg-slate-950 p-3">
                    <div className="text-xs text-slate-500">Passed Queries</div>
                    <div className="text-xl font-bold text-white">
                      {benchmarkResult.passed_queries} / {benchmarkResult.total_queries_tested}
                    </div>
                  </div>
                  <div className="rounded-lg bg-slate-950 p-3">
                    <div className="text-xs text-slate-500">Average Latency</div>
                    <div className="text-xl font-bold text-indigo-300">
                      {benchmarkResult.average_latency_ms.toFixed(1)} ms
                    </div>
                  </div>
                  <div className="rounded-lg bg-slate-950 p-3">
                    <div className="text-xs text-slate-500">Cache Hit Rate</div>
                    <div className="text-xl font-bold text-cyan-300">
                      {(benchmarkResult.cache_hit_rate * 100).toFixed(0)}%
                    </div>
                  </div>
                </div>

                {/* Individual Test Cases */}
                <div className="rounded-lg border border-slate-800 overflow-hidden">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-950 text-slate-400 font-medium">
                      <tr>
                        <th className="px-4 py-2">Test Case</th>
                        <th className="px-4 py-2">Dataset</th>
                        <th className="px-4 py-2">Status</th>
                        <th className="px-4 py-2 text-right">Latency</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800">
                      {benchmarkResult.test_cases.map((tc, i) => (
                        <tr key={i} className="hover:bg-slate-800/30">
                          <td className="px-4 py-2 font-medium text-white">{tc.test_case_name}</td>
                          <td className="px-4 py-2 font-mono text-slate-400">{tc.dataset_code}</td>
                          <td className="px-4 py-2">
                            <span
                              className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                                tc.passed ? 'bg-emerald-500/20 text-emerald-300' : 'bg-rose-500/20 text-rose-300'
                              }`}
                            >
                              {tc.passed ? 'PASS' : 'FAIL'}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-right font-mono text-slate-400">{tc.latency_ms} ms</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Review Anomaly Modal */}
      {reviewModalAnomaly && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 space-y-4">
            <h3 className="text-base font-bold text-white">Review Spending Outlier</h3>
            <p className="text-xs text-slate-400">
              Metric <span className="text-white font-semibold">{reviewModalAnomaly.metric_name}</span> detected on{' '}
              <span className="text-white font-semibold">
                {new Date(reviewModalAnomaly.period_timestamp).toLocaleDateString()}
              </span>{' '}
              with actual value <span className="text-rose-400 font-bold">Rp {reviewModalAnomaly.actual_value.toLocaleString('id-ID')}</span> (expected Rp {reviewModalAnomaly.expected_baseline.toLocaleString('id-ID')}).
            </p>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Decision Status</label>
              <select
                value={reviewStatusChoice}
                onChange={(e) => setReviewStatusChoice(e.target.value as any)}
                className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-slate-200"
              >
                <option value="VERIFIED_ANOMALY">VERIFIED_ANOMALY (Real Spending Issue / Spike)</option>
                <option value="FALSE_POSITIVE">FALSE_POSITIVE (Authorized Unusual Spending)</option>
                <option value="RESOLVED">RESOLVED (Acknowledged & Settled)</option>
                <option value="IGNORED">IGNORED (Disregard)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Reviewer Notes</label>
              <textarea
                rows={3}
                value={reviewNotesInput}
                onChange={(e) => setReviewNotesInput(e.target.value)}
                placeholder="e.g. Verified authorized family birthday dinner celebration at restaurant"
                className="w-full rounded-lg bg-slate-950 border border-slate-800 p-3 text-xs text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setReviewModalAnomaly(null)}
                className="rounded-lg bg-slate-800 px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={reviewSubmitting}
                onClick={handleReviewSubmit}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
              >
                {reviewSubmitting ? 'Saving…' : 'Submit Review'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Provenance Formula Modal */}
      {provenanceModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-lg rounded-xl border border-slate-800 bg-slate-900 p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Deterministic Calculation Provenance</h3>
              <button
                type="button"
                onClick={() => setProvenanceModal(null)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-slate-400">
              Audit trail proving that this calculation was strictly computed by deterministic Java math algorithms and NOT hallucinated by an LLM.
            </p>

            <div className="space-y-2 text-xs">
              <div className="rounded-lg bg-slate-950 p-3 space-y-1">
                <span className="text-slate-500">Provenance Method:</span>
                <div className="font-mono text-emerald-400">{provenanceModal.method}</div>
              </div>
              <div className="rounded-lg bg-slate-950 p-3 space-y-1">
                <span className="text-slate-500">Dataset Code & Rows:</span>
                <div className="font-mono text-slate-200">
                  {provenanceModal.dataset_code} ({provenanceModal.row_count} rows processed)
                </div>
              </div>
              <div className="rounded-lg bg-slate-950 p-3 space-y-1">
                <span className="text-slate-500">Calculation Steps:</span>
                <ul className="list-disc list-inside space-y-0.5 text-slate-300 pt-1">
                  {provenanceModal.calculation_steps.map((step, i) => (
                    <li key={i}>{step}</li>
                  ))}
                </ul>
              </div>
              <div className="rounded-lg bg-slate-950 p-3 space-y-1">
                <span className="text-slate-500">SHA-256 Computation Hash:</span>
                <div className="font-mono text-[10px] text-slate-400 truncate">{provenanceModal.computation_hash}</div>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={() => setProvenanceModal(null)}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* New Dataset Connector Modal */}
      {isNewConnectorOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <form onSubmit={handleSaveNewDataset} className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 space-y-4">
            <h3 className="text-base font-bold text-white">Register Dataset Connector</h3>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Dataset Code</label>
              <input
                type="text"
                required
                value={newConnectorForm.dataset_code}
                onChange={(e) => setNewConnectorForm({ ...newConnectorForm, dataset_code: e.target.value })}
                placeholder="e.g. peggy_investments_2026"
                className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-xs text-slate-200"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Display Name</label>
              <input
                type="text"
                required
                value={newConnectorForm.name}
                onChange={(e) => setNewConnectorForm({ ...newConnectorForm, name: e.target.value })}
                placeholder="e.g. Peggy Portfolio & Sinking Funds"
                className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-xs text-slate-200"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Domain Archetype</label>
              <select
                value={newConnectorForm.domain_archetype}
                onChange={(e) => setNewConnectorForm({ ...newConnectorForm, domain_archetype: e.target.value as any })}
                className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-xs text-slate-200"
              >
                <option value="MONEY_BUDGET_SUMMARY">MONEY_BUDGET_SUMMARY</option>
                <option value="CASHFLOW_LEDGER">CASHFLOW_LEDGER</option>
                <option value="CATEGORY_EXPENSES">CATEGORY_EXPENSES</option>
                <option value="INVENTORY_OPS">INVENTORY_OPS</option>
                <option value="KPI_METRICS">KPI_METRICS</option>
                <option value="CUSTOM_TABULAR">CUSTOM_TABULAR</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Description</label>
              <textarea
                rows={2}
                value={newConnectorForm.description || ''}
                onChange={(e) => setNewConnectorForm({ ...newConnectorForm, description: e.target.value })}
                placeholder="Detailed explanation of dataset metrics"
                className="w-full rounded-lg bg-slate-950 border border-slate-800 p-2 text-xs text-slate-200"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsNewConnectorOpen(false)}
                className="rounded-lg bg-slate-800 px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500"
              >
                Register
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

export default AnalyticsPage;
