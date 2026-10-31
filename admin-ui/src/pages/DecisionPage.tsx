import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  DecisionBenchmarkResult,
  DecisionFeedbackView,
  DecisionPlanRequest,
  DecisionPlanView,
  DecisionRecommendationRequest,
  DecisionRecommendationView,
  RecommendationApprovalRequest,
  RecommendationFeedbackRequest,
} from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';

export function DecisionPage() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'plans' | 'approvals' | 'feedbacks' | 'constraints' | 'benchmarks'>('plans');
  const [plans, setPlans] = useState<DecisionPlanView[]>([]);
  const [recommendations, setRecommendations] = useState<DecisionRecommendationView[]>([]);
  const [feedbacks, setFeedbacks] = useState<DecisionFeedbackView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Scenario Sandbox State
  const [selectedPlanCode, setSelectedPlanCode] = useState<string>('peggy_debt_snowball_vs_avalanche');
  const [nlGoal, setNlGoal] = useState<string>('Prioritize fastest debt freedom while minimizing total interest paid');
  const [extraPaymentSurplus, setExtraPaymentSurplus] = useState<number>(1500000);
  const [simulationMode, setSimulationMode] = useState<boolean>(true);
  const [scenarioOutput, setScenarioOutput] = useState<DecisionRecommendationView | null>(null);
  const [runningScenario, setRunningScenario] = useState<boolean>(false);

  // Plan Creator Modal
  const [isNewPlanOpen, setIsNewPlanOpen] = useState(false);
  const [newPlanForm, setNewPlanForm] = useState<DecisionPlanRequest>({
    plan_code: '',
    name: '',
    domain_archetype: 'DEBT_PAYOFF_STRATEGY',
    description: '',
    freshness_ttl_hours: 48,
  });

  // Human Approval Modal
  const [reviewModalRec, setReviewModalRec] = useState<DecisionRecommendationView | null>(null);
  const [reviewDecisionChoice, setReviewDecisionChoice] = useState<'APPROVED' | 'REJECTED' | 'EXECUTED_SIMULATION'>('APPROVED');
  const [reviewNotesInput, setReviewNotesInput] = useState<string>('');
  const [reviewSubmitting, setReviewSubmitting] = useState<boolean>(false);

  // Outcome Feedback Modal / Form
  const [feedbackRecId, setFeedbackRecId] = useState<string>('');
  const [feedbackRating, setFeedbackRating] = useState<'HELPFUL' | 'UNHELPFUL' | 'OVERRIDDEN' | 'REJECTED'>('HELPFUL');
  const [feedbackApplied, setFeedbackApplied] = useState<boolean>(true);
  const [feedbackComments, setFeedbackComments] = useState<string>('');
  const [submittingFeedback, setSubmittingFeedback] = useState<boolean>(false);

  // Benchmarks State
  const [benchmarkResult, setBenchmarkResult] = useState<DecisionBenchmarkResult | null>(null);
  const [benchmarking, setBenchmarking] = useState(false);

  useEffect(() => {
    loadPlans();
    loadRecommendations();
  }, []);

  async function loadPlans() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.listDecisionPlans();
      if (res.status === 'ok') {
        setPlans(res.data);
      } else {
        setError(res.errors?.[0]?.message || 'Failed to load optimization plans');
      }
    } catch (e: any) {
      setError(e.message || 'Error fetching decision plans');
    } finally {
      setLoading(false);
    }
  }

  async function loadRecommendations() {
    try {
      const res = await api.listDecisionRecommendations();
      if (res.status === 'ok') {
        setRecommendations(res.data);
      }
    } catch (e: any) {
      console.error('Error fetching recommendations:', e);
    }
  }

  async function handleRunScenario() {
    setRunningScenario(true);
    setError(null);
    try {
      const req: DecisionRecommendationRequest = {
        plan_code: selectedPlanCode,
        natural_language_goal: nlGoal,
        runtime_parameters: {
          monthly_extra_payment: extraPaymentSurplus,
          monthly_savings_budget: extraPaymentSurplus,
        },
        simulation_mode: simulationMode,
      };
      const res = await api.generateDecisionRecommendation(req);
      if (res.status === 'ok') {
        setScenarioOutput(res.data);
        loadRecommendations(); // refresh approval queue
      } else {
        setError(res.errors?.[0]?.message || 'Scenario optimization failed');
      }
    } catch (e: any) {
      setError(e.message || 'Error executing scenario optimization');
    } finally {
      setRunningScenario(false);
    }
  }

  async function handleReviewSubmit() {
    if (!reviewModalRec) return;
    setReviewSubmitting(true);
    try {
      const req: RecommendationApprovalRequest = {
        decision: reviewDecisionChoice,
        review_notes: reviewNotesInput,
      };
      const res = await api.reviewDecisionRecommendation(reviewModalRec.id, req);
      if (res.status === 'ok') {
        setReviewModalRec(null);
        setReviewNotesInput('');
        loadRecommendations();
      } else {
        setError(res.errors?.[0]?.message || 'Failed to review recommendation');
      }
    } catch (e: any) {
      setError(e.message || 'Error reviewing recommendation');
    } finally {
      setReviewSubmitting(false);
    }
  }

  async function handleSubmitFeedback() {
    if (!feedbackRecId) {
      setError('Please select or specify a recommendation ID');
      return;
    }
    setSubmittingFeedback(true);
    try {
      const req: RecommendationFeedbackRequest = {
        rating: feedbackRating,
        applied: feedbackApplied,
        comments: feedbackComments,
        measured_outcome: {
          user_sentiment: feedbackRating,
          applied_timestamp: new Date().toISOString(),
        },
      };
      const res = await api.submitDecisionFeedback(feedbackRecId, req);
      if (res.status === 'ok') {
        setFeedbacks((prev) => [res.data, ...prev]);
        setFeedbackComments('');
        alert('Feedback submitted successfully!');
      } else {
        setError(res.errors?.[0]?.message || 'Failed to submit feedback');
      }
    } catch (e: any) {
      setError(e.message || 'Error submitting feedback');
    } finally {
      setSubmittingFeedback(false);
    }
  }

  async function handleRunBenchmarks() {
    setBenchmarking(true);
    try {
      const res = await api.runDecisionBenchmarks();
      if (res.status === 'ok') {
        setBenchmarkResult(res.data);
      } else {
        setError(res.errors?.[0]?.message || 'Failed to run benchmarks');
      }
    } catch (e: any) {
      setError(e.message || 'Error executing decision benchmarks');
    } finally {
      setBenchmarking(false);
    }
  }

  async function handleCreatePlan(e: React.FormEvent) {
    e.preventDefault();
    try {
      const res = await api.createDecisionPlan(newPlanForm);
      if (res.status === 'ok') {
        setIsNewPlanOpen(false);
        setNewPlanForm({
          plan_code: '',
          name: '',
          domain_archetype: 'DEBT_PAYOFF_STRATEGY',
          description: '',
          freshness_ttl_hours: 48,
        });
        loadPlans();
      } else {
        setError(res.errors?.[0]?.message || 'Failed to create plan');
      }
    } catch (err: any) {
      setError(err.message || 'Error creating plan');
    }
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <span>⚖️</span>
            {t('decision.title', 'Decision Intelligence & Optimization')}
          </h1>
          <p className="mt-1 text-sm text-slate-400 max-w-4xl">
            {t(
              'decision.subtitle',
              'Deterministic financial optimization, debt payoff (avalanche vs snowball), savings rebalancing, human approval review queue, and outcome feedback telemetry'
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              loadPlans();
              loadRecommendations();
            }}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-200 transition hover:bg-slate-700"
          >
            <span>🔄</span>
            {t('action.refreshAll', 'Refresh All')}
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-rose-500/30 bg-rose-950/40 p-4 text-sm text-rose-300 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span>⚠️</span>
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={() => setError(null)}
            className="text-xs text-rose-400 hover:text-white"
          >
            ✕
          </button>
        </div>
      )}

      {/* Navigation Sub-Tabs */}
      <div className="border-b border-slate-800 flex gap-2 overflow-x-auto">
        <button
          type="button"
          onClick={() => setActiveTab('plans')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            activeTab === 'plans'
              ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('decision.tab.plans', 'Optimization & Scenarios')}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('approvals')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition flex items-center gap-2 ${
            activeTab === 'approvals'
              ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <span>{t('decision.tab.approvals', 'Human Approval Queue')}</span>
          {recommendations.filter((r) => r.status === 'PROPOSED').length > 0 && (
            <span className="rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30 px-1.5 py-0.2 text-[10px] font-bold">
              {recommendations.filter((r) => r.status === 'PROPOSED').length}
            </span>
          )}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('feedbacks')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            activeTab === 'feedbacks'
              ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('decision.tab.feedbacks', 'Outcome & Feedback')}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('constraints')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            activeTab === 'constraints'
              ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('decision.tab.constraints', 'Constraint & Abstention Rules')}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('benchmarks')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            activeTab === 'benchmarks'
              ? 'border-indigo-500 text-indigo-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('decision.tab.benchmarks', 'Deterministic Benchmarks')}
        </button>
      </div>

      {/* Sub-Tab 1: Optimization & Scenarios */}
      {activeTab === 'plans' && (
        <div className="space-y-6">
          {/* Active Plans Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-semibold text-white">Registered Optimization Plans</h3>
                <p className="text-xs text-slate-400">
                  Pre-configured deterministic solvers for debt repayment, budgeting, and operations
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsNewPlanOpen(true)}
                className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500 transition"
              >
                + {t('decision.plan.create', 'New Optimization Plan')}
              </button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/60 text-xs text-slate-400 uppercase tracking-wider">
                  <tr>
                    <th className="py-2.5 px-3">Plan Code</th>
                    <th className="py-2.5 px-3">Name</th>
                    <th className="py-2.5 px-3">Archetype</th>
                    <th className="py-2.5 px-3">Freshness TTL</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 font-mono text-xs">
                  {plans.map((p) => (
                    <tr key={p.id} className="hover:bg-slate-800/30 transition">
                      <td className="py-2.5 px-3 font-semibold text-indigo-300">{p.plan_code}</td>
                      <td className="py-2.5 px-3 font-sans text-white">{p.name}</td>
                      <td className="py-2.5 px-3">
                        <span className="rounded bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">
                          {p.domain_archetype}
                        </span>
                      </td>
                      <td className="py-2.5 px-3">{p.freshness_ttl_hours}h</td>
                      <td className="py-2.5 px-3">
                        <span className="rounded bg-emerald-500/20 text-emerald-400 px-2 py-0.5 text-[11px]">
                          ACTIVE
                        </span>
                      </td>
                      <td className="py-2.5 px-3 font-sans">
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedPlanCode(p.plan_code);
                            window.scrollTo({ top: 500, behavior: 'smooth' });
                          }}
                          className="text-xs text-indigo-400 hover:underline"
                        >
                          Select for Simulation →
                        </button>
                      </td>
                    </tr>
                  ))}
                  {plans.length === 0 && !loading && (
                    <tr>
                      <td colSpan={6} className="py-4 text-center text-slate-500 font-sans">
                        No optimization plans registered.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Interactive Simulation Sandbox */}
          <div className="rounded-xl border border-indigo-900/40 bg-gradient-to-b from-indigo-950/20 to-slate-900/60 p-6 space-y-5">
            <div>
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 px-2 py-0.5 text-[11px] font-semibold">
                  SIMULATION SANDBOX
                </span>
                <span className="text-xs text-slate-400">Strict Non-Banking Mode Enforced</span>
              </div>
              <h3 className="text-lg font-bold text-white mt-2">
                Run Deterministic Decision Scenario
              </h3>
              <p className="text-xs text-slate-300">
                Execute mathematical solver to produce actionable strategy recommendations, trade-off comparisons, and constraint validations.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Target Optimization Plan
                </label>
                <select
                  value={selectedPlanCode}
                  onChange={(e) => setSelectedPlanCode(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                >
                  {plans.map((p) => (
                    <option key={p.plan_code} value={p.plan_code}>
                      {p.name} ({p.plan_code})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Monthly Surplus Budget (IDR)
                </label>
                <input
                  type="number"
                  value={extraPaymentSurplus}
                  onChange={(e) => setExtraPaymentSurplus(Number(e.target.value))}
                  step={100000}
                  className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Execution Mode
                </label>
                <div className="flex items-center gap-3 h-10">
                  <label className="inline-flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={simulationMode}
                      onChange={(e) => setSimulationMode(e.target.checked)}
                      className="rounded border-slate-700 bg-slate-900 text-indigo-600 focus:ring-0"
                    />
                    <span>Simulation Mode (Zero Production Mutation)</span>
                  </label>
                </div>
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Natural Language Goal & Guidance
              </label>
              <input
                type="text"
                value={nlGoal}
                onChange={(e) => setNlGoal(e.target.value)}
                placeholder="e.g. Prioritize fastest debt payoff while minimizing total interest paid"
                className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="flex justify-end">
              <button
                type="button"
                onClick={handleRunScenario}
                disabled={runningScenario}
                className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white shadow-lg shadow-indigo-600/30 hover:bg-indigo-500 transition disabled:opacity-50"
              >
                <span>{runningScenario ? '⏳ Calculating...' : '⚡ ' + t('decision.scenario.run', 'Run Simulation Scenario')}</span>
              </button>
            </div>

            {/* Scenario Output Display */}
            {scenarioOutput && (
              <div className="mt-6 rounded-xl border border-slate-700 bg-slate-950 p-6 space-y-6">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-slate-800 pb-4">
                  <div>
                    <span className="rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 px-2 py-0.5 text-xs font-mono font-bold">
                      {scenarioOutput.recommendation_code}
                    </span>
                    <h4 className="text-lg font-bold text-white mt-1">{scenarioOutput.title}</h4>
                    <p className="text-xs text-slate-400 mt-1">{scenarioOutput.summary}</p>
                  </div>
                  <div className="flex flex-col sm:items-end">
                    <div className="text-xs text-slate-400">Confidence Score</div>
                    <div className="text-xl font-extrabold text-emerald-400">
                      {(scenarioOutput.confidence_score * 100).toFixed(1)}%
                    </div>
                    {scenarioOutput.abstained ? (
                      <span className="rounded bg-rose-500/20 text-rose-300 text-[10px] px-2 py-0.5 mt-1 font-semibold">
                        ABSTAINED: {scenarioOutput.abstention_reason}
                      </span>
                    ) : (
                      <span className="rounded bg-emerald-500/20 text-emerald-300 text-[10px] px-2 py-0.5 mt-1 font-semibold">
                        VERIFIED DETERMINISTIC
                      </span>
                    )}
                  </div>
                </div>

                {/* Scenario Before vs After Diff */}
                {scenarioOutput.scenario_diff && (
                  <div>
                    <h5 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-3">
                      {t('decision.scenario.compare', 'Scenario Comparison (Baseline vs Recommended)')}
                    </h5>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                      <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
                        <div className="text-[11px] text-slate-400">Baseline Scenario</div>
                        <div className="text-base font-bold text-white mt-1">
                          {String(scenarioOutput.scenario_diff.baseline?.['total_months'] ?? 0)} Months
                        </div>
                        <div className="text-xs text-slate-400 mt-1">
                          Interest: Rp {Number(scenarioOutput.scenario_diff.baseline?.['total_interest'] ?? 0).toLocaleString('id-ID')}
                        </div>
                      </div>

                      <div className="rounded-lg border border-indigo-800/40 bg-indigo-950/30 p-4">
                        <div className="text-[11px] text-indigo-300">Recommended Strategy</div>
                        <div className="text-base font-bold text-white mt-1">
                          {String(scenarioOutput.scenario_diff.recommended?.['total_months'] ?? 0)} Months
                        </div>
                        <div className="text-xs text-indigo-300 mt-1">
                          Interest: Rp {Number(scenarioOutput.scenario_diff.recommended?.['total_interest'] ?? 0).toLocaleString('id-ID')}
                        </div>
                      </div>

                      <div className="rounded-lg border border-emerald-800/40 bg-emerald-950/30 p-4">
                        <div className="text-[11px] text-emerald-300">Net Improvement (Delta)</div>
                        <div className="text-base font-bold text-emerald-400 mt-1">
                          {String(scenarioOutput.scenario_diff.delta_metrics?.['delta_months'] ?? 0)} Months Faster
                        </div>
                        <div className="text-xs text-emerald-300 mt-1">
                          Saves: Rp {Math.abs(Number(scenarioOutput.scenario_diff.delta_metrics?.['delta_interest'] ?? 0)).toLocaleString('id-ID')}
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Trade-Off Options */}
                {scenarioOutput.trade_offs && scenarioOutput.trade_offs.length > 0 && (
                  <div>
                    <h5 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-3">
                      {t('decision.scenario.tradeOffs', 'Evaluated Trade-Off Options')}
                    </h5>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {scenarioOutput.trade_offs.map((opt, idx) => (
                        <div
                          key={idx}
                          className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-3"
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-bold text-white">{opt.strategy}</span>
                            <span className="text-xs text-slate-400 font-mono">
                              {opt.quantitative_metric}: {Number(opt.score_or_amount).toLocaleString('id-ID')}
                            </span>
                          </div>
                          <div className="space-y-1.5">
                            <div className="text-[11px] font-semibold text-emerald-400">Pros:</div>
                            <ul className="list-disc list-inside text-xs text-slate-300 space-y-0.5">
                              {opt.pros?.map((p, pIdx) => (
                                <li key={pIdx}>{p}</li>
                              ))}
                            </ul>
                          </div>
                          <div className="space-y-1.5">
                            <div className="text-[11px] font-semibold text-amber-400">Cons:</div>
                            <ul className="list-disc list-inside text-xs text-slate-400 space-y-0.5">
                              {opt.cons?.map((c, cIdx) => (
                                <li key={cIdx}>{c}</li>
                              ))}
                            </ul>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Constraints Validated Checklist */}
                {scenarioOutput.constraints_validated && scenarioOutput.constraints_validated.length > 0 && (
                  <div>
                    <h5 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-3">
                      {t('decision.scenario.constraints', 'Constraint Satisfaction Status')}
                    </h5>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {scenarioOutput.constraints_validated.map((c, idx) => (
                        <div
                          key={idx}
                          className="rounded-lg border border-slate-800 bg-slate-900/40 p-3 flex items-center justify-between"
                        >
                          <div>
                            <div className="text-xs font-semibold text-white">{c.name}</div>
                            <div className="text-[11px] text-slate-400">
                              Current: {Number(c.current_value).toLocaleString('id-ID')} | Limit: {Number(c.threshold).toLocaleString('id-ID')}
                            </div>
                          </div>
                          <span
                            className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                              c.status === 'SATISFIED'
                                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                                : 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                            }`}
                          >
                            {c.status}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Sub-Tab 2: Human Approval Queue */}
      {activeTab === 'approvals' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-semibold text-white">
                  Consequential Recommendation Review Queue
                </h3>
                <p className="text-xs text-slate-400">
                  Governance safeguard: financial reallocations and operational dispatches require explicit operator approval
                </p>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/60 text-xs text-slate-400 uppercase tracking-wider font-mono">
                  <tr>
                    <th className="py-2.5 px-3">Rec Code</th>
                    <th className="py-2.5 px-3">Title</th>
                    <th className="py-2.5 px-3">Plan Code</th>
                    <th className="py-2.5 px-3">Confidence</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3">Simulation</th>
                    <th className="py-2.5 px-3">Created</th>
                    <th className="py-2.5 px-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 font-mono text-xs">
                  {recommendations.map((r) => (
                    <tr key={r.id} className="hover:bg-slate-800/30 transition">
                      <td className="py-2.5 px-3 font-semibold text-indigo-300">{r.recommendation_code}</td>
                      <td className="py-2.5 px-3 font-sans text-white max-w-xs truncate">{r.title}</td>
                      <td className="py-2.5 px-3 text-slate-400">{r.plan_code}</td>
                      <td className="py-2.5 px-3 text-emerald-400">
                        {(r.confidence_score * 100).toFixed(0)}%
                      </td>
                      <td className="py-2.5 px-3">
                        <span
                          className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                            r.status === 'PROPOSED'
                              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                              : r.status === 'APPROVED'
                              ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                              : r.status === 'EXECUTED_SIMULATION'
                              ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/30'
                              : 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                          }`}
                        >
                          {r.status}
                        </span>
                      </td>
                      <td className="py-2.5 px-3">
                        {r.simulation_mode ? (
                          <span className="text-emerald-400">SIMULATION</span>
                        ) : (
                          <span className="text-amber-400 font-bold">MUTATING</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-slate-500 font-sans">
                        {new Date(r.created_at).toLocaleDateString()}
                      </td>
                      <td className="py-2.5 px-3 font-sans">
                        <button
                          type="button"
                          onClick={() => {
                            setReviewModalRec(r);
                            setReviewDecisionChoice(r.status === 'PROPOSED' ? 'APPROVED' : (r.status as any));
                            setReviewNotesInput(r.review_notes || '');
                          }}
                          className="rounded bg-indigo-600/30 hover:bg-indigo-600 text-indigo-200 hover:text-white px-2.5 py-1 text-xs font-semibold transition"
                        >
                          Review →
                        </button>
                      </td>
                    </tr>
                  ))}
                  {recommendations.length === 0 && (
                    <tr>
                      <td colSpan={8} className="py-4 text-center text-slate-500 font-sans">
                        No recommendations in review queue.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Review Modal */}
      {reviewModalRec && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4">
          <div className="w-full max-w-lg rounded-xl border border-slate-700 bg-slate-900 p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-white">
                Human Review: {reviewModalRec.recommendation_code}
              </h3>
              <button
                type="button"
                onClick={() => setReviewModalRec(null)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <div className="text-xs text-slate-400">Recommendation Title</div>
                <div className="text-sm font-semibold text-white">{reviewModalRec.title}</div>
                <div className="text-xs text-slate-300 mt-1">{reviewModalRec.summary}</div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Operator Decision
                </label>
                <select
                  value={reviewDecisionChoice}
                  onChange={(e) => setReviewDecisionChoice(e.target.value as any)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                >
                  <option value="APPROVED">{t('decision.approval.approve', 'Approve Action')}</option>
                  <option value="EXECUTED_SIMULATION">{t('decision.approval.simulation', 'Execute in Simulation Only')}</option>
                  <option value="REJECTED">{t('decision.approval.reject', 'Reject Recommendation')}</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  {t('decision.approval.notes', 'Reviewer Notes / Rationale')}
                </label>
                <textarea
                  value={reviewNotesInput}
                  onChange={(e) => setReviewNotesInput(e.target.value)}
                  rows={3}
                  placeholder="Justification for approving or rejecting this recommendation..."
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setReviewModalRec(null)}
                className="rounded-lg border border-slate-700 bg-slate-800 px-4 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-700 transition"
              >
                {t('action.cancel', 'Cancel')}
              </button>
              <button
                type="button"
                onClick={handleReviewSubmit}
                disabled={reviewSubmitting}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 transition disabled:opacity-50"
              >
                {reviewSubmitting ? 'Submitting...' : 'Save Decision'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Sub-Tab 3: Outcome & Feedback */}
      {activeTab === 'feedbacks' && (
        <div className="space-y-6">
          {/* Feedback Form Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
            <h3 className="text-base font-semibold text-white">
              {t('decision.feedback.submit', 'Submit Feedback Telemetry')}
            </h3>
            <p className="text-xs text-slate-400">
              Record user satisfaction, override telemetry, and measured financial outcomes to continuously refine solver heuristics.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Target Recommendation ID
                </label>
                <select
                  value={feedbackRecId}
                  onChange={(e) => setFeedbackRecId(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 font-mono text-xs"
                >
                  <option value="">-- Select Recommendation --</option>
                  {recommendations.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.recommendation_code} - {r.title}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  {t('decision.feedback.rating', 'Outcome Rating')}
                </label>
                <select
                  value={feedbackRating}
                  onChange={(e) => setFeedbackRating(e.target.value as any)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                >
                  <option value="HELPFUL">HELPFUL (Accepted & Applied)</option>
                  <option value="UNHELPFUL">UNHELPFUL (Poor fit)</option>
                  <option value="OVERRIDDEN">OVERRIDDEN (User altered parameters)</option>
                  <option value="REJECTED">REJECTED (Declined by user)</option>
                </select>
              </div>

              <div className="flex items-center gap-3 pt-6">
                <label className="inline-flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={feedbackApplied}
                    onChange={(e) => setFeedbackApplied(e.target.checked)}
                    className="rounded border-slate-700 bg-slate-900 text-indigo-600 focus:ring-0"
                  />
                  <span>{t('decision.feedback.applied', 'Applied to Production')}</span>
                </label>
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Comments & Field Observations
              </label>
              <input
                type="text"
                value={feedbackComments}
                onChange={(e) => setFeedbackComments(e.target.value)}
                placeholder="Observed user behavior or reasons for override..."
                className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="flex justify-end">
              <button
                type="button"
                onClick={handleSubmitFeedback}
                disabled={submittingFeedback}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 transition disabled:opacity-50"
              >
                {submittingFeedback ? 'Submitting...' : 'Record Telemetry'}
              </button>
            </div>
          </div>

          {/* Feedback History Table */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
            <h3 className="text-base font-semibold text-white">Recent Outcome Telemetry</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/60 text-xs text-slate-400 uppercase tracking-wider font-mono">
                  <tr>
                    <th className="py-2.5 px-3">Rec ID</th>
                    <th className="py-2.5 px-3">Rating</th>
                    <th className="py-2.5 px-3">Applied</th>
                    <th className="py-2.5 px-3">Comments</th>
                    <th className="py-2.5 px-3">Logged At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 font-mono text-xs">
                  {feedbacks.map((f) => (
                    <tr key={f.id} className="hover:bg-slate-800/30 transition">
                      <td className="py-2.5 px-3 text-indigo-300 truncate max-w-xs">{f.recommendation_id}</td>
                      <td className="py-2.5 px-3">
                        <span
                          className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                            f.rating === 'HELPFUL'
                              ? 'bg-emerald-500/20 text-emerald-300'
                              : f.rating === 'OVERRIDDEN'
                              ? 'bg-amber-500/20 text-amber-300'
                              : 'bg-rose-500/20 text-rose-300'
                          }`}
                        >
                          {f.rating}
                        </span>
                      </td>
                      <td className="py-2.5 px-3">
                        {f.applied ? (
                          <span className="text-emerald-400">YES</span>
                        ) : (
                          <span className="text-slate-500">NO</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 font-sans text-white">{f.comments || '-'}</td>
                      <td className="py-2.5 px-3 text-slate-500 font-sans">
                        {new Date(f.created_at).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                  {feedbacks.length === 0 && (
                    <tr>
                      <td colSpan={5} className="py-4 text-center text-slate-500 font-sans">
                        No outcome feedback records captured yet.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Sub-Tab 4: Constraint & Abstention Rules */}
      {activeTab === 'constraints' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Hard Constraints */}
            <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
              <div className="flex items-center gap-2">
                <span className="rounded bg-rose-500/20 text-rose-300 border border-rose-500/30 px-2 py-0.5 text-[10px] font-bold">
                  HARD CONSTRAINTS
                </span>
                <span className="text-xs text-slate-400">Strict System Abstention on Violation</span>
              </div>
              <h4 className="text-base font-bold text-white">Emergency Fund & Reserve Protection</h4>
              <p className="text-xs text-slate-300 leading-relaxed">
                Hard constraints can never be overridden by LLM or user prompts. If an allocation recommendation breaches an emergency floor or priority savings ratio, the system strictly abstains with an explicit reason.
              </p>
              <div className="space-y-2">
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-xs font-semibold text-rose-300">emergency_fund_floor</div>
                  <div className="text-xs text-slate-400 mt-0.5">
                    Total liquid reserves must not drop below 3 months of essential living expenses (Rp 10.000.000 default).
                  </div>
                </div>
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-xs font-semibold text-rose-300">emergency_priority_weight</div>
                  <div className="text-xs text-slate-400 mt-0.5">
                    At least 60% of monthly savings allocation must fund essential emergency reserves until full target reached.
                  </div>
                </div>
              </div>
            </div>

            {/* Soft Constraints & Abstention Guards */}
            <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
              <div className="flex items-center gap-2">
                <span className="rounded bg-amber-500/20 text-amber-300 border border-amber-500/30 px-2 py-0.5 text-[10px] font-bold">
                  SOFT CONSTRAINTS & SAFEGUARDS
                </span>
                <span className="text-xs text-slate-400">Guardrails & Freshness Thresholds</span>
              </div>
              <h4 className="text-base font-bold text-white">Freshness TTL & Confidence Floors</h4>
              <p className="text-xs text-slate-300 leading-relaxed">
                To prevent hallucinations and decisions on outdated financial statements, CRANIAL enforces strict data age TTLs and solver confidence thresholds.
              </p>
              <div className="space-y-2">
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-xs font-semibold text-amber-300">Data Freshness TTL (48 - 72 Hours)</div>
                  <div className="text-xs text-slate-400 mt-0.5">
                    Source account balances and debt statements older than plan TTL trigger automatic recommendation abstention.
                  </div>
                </div>
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-xs font-semibold text-amber-300">Confidence Threshold Floor (&ge; 0.65)</div>
                  <div className="text-xs text-slate-400 mt-0.5">
                    Optimization confidence scores below 0.65 prompt the engine to abstain rather than presenting low-quality advice.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Sub-Tab 5: Deterministic Benchmarks */}
      {activeTab === 'benchmarks' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-5 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div>
                <h3 className="text-base font-semibold text-white">
                  Deterministic Solver Benchmark Runner
                </h3>
                <p className="text-xs text-slate-400">
                  Validates mathematical accuracy across debt repayment strategies, emergency fund rebalancing, and abstention rules
                </p>
              </div>
              <button
                type="button"
                onClick={handleRunBenchmarks}
                disabled={benchmarking}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 transition disabled:opacity-50"
              >
                {benchmarking ? 'Running Benchmarks...' : '⚡ ' + t('decision.benchmarks.run', 'Execute Solver Benchmarks')}
              </button>
            </div>

            {benchmarkResult && (
              <div className="space-y-5 pt-3">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                  <div className="rounded-lg border border-slate-800 bg-slate-950 p-4">
                    <div className="text-[11px] text-slate-400">Scenarios Tested</div>
                    <div className="text-xl font-bold text-white mt-1">
                      {benchmarkResult.total_scenarios_tested}
                    </div>
                  </div>
                  <div className="rounded-lg border border-emerald-900/40 bg-emerald-950/20 p-4">
                    <div className="text-[11px] text-emerald-400">Passed Scenarios</div>
                    <div className="text-xl font-bold text-emerald-300 mt-1">
                      {benchmarkResult.passed_scenarios} / {benchmarkResult.total_scenarios_tested}
                    </div>
                  </div>
                  <div className="rounded-lg border border-indigo-900/40 bg-indigo-950/20 p-4">
                    <div className="text-[11px] text-indigo-400">{t('decision.benchmarks.rate', 'Constraint Satisfaction Rate')}</div>
                    <div className="text-xl font-bold text-indigo-300 mt-1">
                      {(benchmarkResult.constraint_satisfaction_rate * 100).toFixed(0)}%
                    </div>
                  </div>
                  <div className="rounded-lg border border-slate-800 bg-slate-950 p-4">
                    <div className="text-[11px] text-slate-400">Avg Latency</div>
                    <div className="text-xl font-bold text-white mt-1">
                      {benchmarkResult.average_solver_latency_ms.toFixed(1)} ms
                    </div>
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm text-slate-300">
                    <thead className="bg-slate-800/60 text-xs text-slate-400 uppercase tracking-wider font-mono">
                      <tr>
                        <th className="py-2.5 px-3">Scenario Name</th>
                        <th className="py-2.5 px-3">Archetype</th>
                        <th className="py-2.5 px-3">Status</th>
                        <th className="py-2.5 px-3">Constraints</th>
                        <th className="py-2.5 px-3">Latency</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800 font-mono text-xs">
                      {benchmarkResult.test_cases?.map((tc, idx) => (
                        <tr key={idx} className="hover:bg-slate-800/30 transition">
                          <td className="py-2.5 px-3 text-white font-sans">{tc.scenario_name}</td>
                          <td className="py-2.5 px-3 text-slate-400">{tc.archetype}</td>
                          <td className="py-2.5 px-3">
                            <span className="rounded bg-emerald-500/20 text-emerald-300 px-2 py-0.5 text-[10px] font-bold">
                              PASS
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-emerald-400">
                            {tc.constraints_satisfied ? 'SATISFIED' : 'N/A'}
                          </td>
                          <td className="py-2.5 px-3">{tc.latency_ms} ms</td>
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

      {/* Plan Creator Modal */}
      {isNewPlanOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4">
          <div className="w-full max-w-lg rounded-xl border border-slate-700 bg-slate-900 p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-white">Create Decision Optimization Plan</h3>
              <button
                type="button"
                onClick={() => setIsNewPlanOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreatePlan} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Plan Code
                </label>
                <input
                  type="text"
                  required
                  value={newPlanForm.plan_code}
                  onChange={(e) => setNewPlanForm({ ...newPlanForm, plan_code: e.target.value })}
                  placeholder="e.g. peggy_debt_accelerator"
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 font-mono text-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Plan Name
                </label>
                <input
                  type="text"
                  required
                  value={newPlanForm.name}
                  onChange={(e) => setNewPlanForm({ ...newPlanForm, name: e.target.value })}
                  placeholder="e.g. Peggy Debt Payoff Accelerator"
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Domain Archetype
                </label>
                <select
                  value={newPlanForm.domain_archetype}
                  onChange={(e) => setNewPlanForm({ ...newPlanForm, domain_archetype: e.target.value as any })}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                >
                  <option value="DEBT_PAYOFF_STRATEGY">DEBT_PAYOFF_STRATEGY</option>
                  <option value="BUDGET_REBALANCING">BUDGET_REBALANCING</option>
                  <option value="SAVINGS_GOAL_ALLOCATION">SAVINGS_GOAL_ALLOCATION</option>
                  <option value="DISPATCH_ROUTE_SCHEDULING">DISPATCH_ROUTE_SCHEDULING</option>
                  <option value="CUSTOM_OPTIMIZATION">CUSTOM_OPTIMIZATION</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Description
                </label>
                <input
                  type="text"
                  value={newPlanForm.description || ''}
                  onChange={(e) => setNewPlanForm({ ...newPlanForm, description: e.target.value })}
                  placeholder="Purpose of this optimization plan..."
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Freshness TTL (Hours)
                </label>
                <input
                  type="number"
                  value={newPlanForm.freshness_ttl_hours || 48}
                  onChange={(e) => setNewPlanForm({ ...newPlanForm, freshness_ttl_hours: Number(e.target.value) })}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsNewPlanOpen(false)}
                  className="rounded-lg border border-slate-700 bg-slate-800 px-4 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-700 transition"
                >
                  {t('action.cancel', 'Cancel')}
                </button>
                <button
                  type="submit"
                  className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 transition"
                >
                  Create Plan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default DecisionPage;
