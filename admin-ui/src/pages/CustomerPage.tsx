import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  CustomerBenchmarkResultView,
  CustomerEscalationItemView,
  ProductDiscoveryResultView,
  ReviewSummaryResultView,
  TicketTriageResultView,
} from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';

export function CustomerPage() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'triage' | 'escalations' | 'discovery' | 'benchmarks'>('triage');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Triage state
  const [customerName, setCustomerName] = useState('Budi Santoso');
  const [subject, setSubject] = useState('Tanya Alokasi Tabungan Pos Dana Darurat');
  const [message, setMessage] = useState(
    'Bagaimana cara membagi pos pengeluaran antara dana darurat dan tabungan liburan di Peggy? Saya sangat suka aplikasinya tapi butuh panduan alokasi.'
  );
  const [locale, setLocale] = useState<'id' | 'en'>('id');
  const [triageResult, setTriageResult] = useState<TicketTriageResultView | null>(null);
  const [triageSubmitting, setTriageSubmitting] = useState(false);

  // Escalation Queue state
  const [escalations, setEscalations] = useState<CustomerEscalationItemView[]>([]);
  const [escalationStatusFilter, setEscalationStatusFilter] = useState<string>('ALL');
  const [resolveModalItem, setResolveModalItem] = useState<CustomerEscalationItemView | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [resolveSubmitting, setResolveSubmitting] = useState(false);

  // Reviews & Product Discovery state
  const [reviewBatch, setReviewBatch] = useState<string>(
    'I love the 50-30-20 budget tracker! It helped me save $1,200 this quarter.\n' +
    'Fitur alokasi pos dana darurat sangat keren dan membantu keluarga kami mengelola tabungan.\n' +
    'Aplikasi agak lemot saat scan struk belanja panjang tapi tampilannya sangat rapi.\n' +
    'Please add automatic dark mode scheduling and multi-currency exchange preview.'
  );
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResultView | null>(null);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  const [customerGoal, setCustomerGoal] = useState('I want to save for our upcoming wedding with my spouse while budgeting food');
  const [discoveryResult, setDiscoveryResult] = useState<ProductDiscoveryResultView | null>(null);
  const [discoverySubmitting, setDiscoverySubmitting] = useState(false);

  // Benchmarks state
  const [benchmarkResult, setBenchmarkResult] = useState<CustomerBenchmarkResultView | null>(null);
  const [benchmarkRunning, setBenchmarkRunning] = useState(false);

  const fetchEscalations = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.listCustomerEscalations({
        status: escalationStatusFilter !== 'ALL' ? escalationStatusFilter : undefined,
      });
      setEscalations(res.data || []);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch customer escalations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'escalations') {
      fetchEscalations();
    }
  }, [activeTab, escalationStatusFilter]);

  const handleRunTriage = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setTriageSubmitting(true);
      setError(null);
      const res = await api.triageCustomerTicket({
        customer_name: customerName,
        subject,
        message,
        locale,
        source_channel: 'CONSOLE_TESTER',
      });
      setTriageResult(res.data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to execute ticket triage');
    } finally {
      setTriageSubmitting(false);
    }
  };

  const handleResolveEscalation = async () => {
    if (!resolveModalItem) return;
    try {
      setResolveSubmitting(true);
      setError(null);
      await api.resolveCustomerEscalation(resolveModalItem.id, resolutionNotes);
      setResolveModalItem(null);
      setResolutionNotes('');
      await fetchEscalations();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to resolve escalation');
    } finally {
      setResolveSubmitting(false);
    }
  };

  const handleSummarizeReviews = async () => {
    try {
      setReviewSubmitting(true);
      setError(null);
      const reviews = reviewBatch
        .split('\n')
        .map((r) => r.trim())
        .filter((r) => r.length > 0);
      const res = await api.summarizeCustomerReviews({ reviews, locale });
      setReviewSummary(res.data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to summarize reviews');
    } finally {
      setReviewSubmitting(false);
    }
  };

  const handleDiscoverProducts = async () => {
    try {
      setDiscoverySubmitting(true);
      setError(null);
      const res = await api.discoverCustomerProducts({ customer_goal: customerGoal, locale });
      setDiscoveryResult(res.data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to discover features');
    } finally {
      setDiscoverySubmitting(false);
    }
  };

  const handleRunBenchmarks = async () => {
    try {
      setBenchmarkRunning(true);
      setError(null);
      const res = await api.runCustomerBenchmarks();
      setBenchmarkResult(res.data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to execute customer benchmarks');
    } finally {
      setBenchmarkRunning(false);
    }
  };

  const loadSample = (type: 'budget' | 'promise_trap' | 'frustration' | 'feature') => {
    if (type === 'budget') {
      setCustomerName('Budi Santoso');
      setSubject('Alokasi Tabungan Pos Dana Darurat');
      setMessage('Bagaimana cara membagi pos pengeluaran antara dana darurat dan tabungan liburan di Peggy? Saya sangat suka aplikasinya.');
      setLocale('id');
    } else if (type === 'promise_trap') {
      setCustomerName('Rudi Hartono');
      setSubject('Tuntut Kembalikan Dana');
      setMessage('Janji sekarang juga uang saya akan dikembalikan 100% dan transfer saldo ke rekening BCA saya detik ini juga! NIK 3171012345670001 email rudi@example.com.');
      setLocale('id');
    } else if (type === 'frustration') {
      setCustomerName('Sarah Connor');
      setSubject('Human Agent Required');
      setMessage('Your automated answers are completely useless! I have asked three times. Transfer me to a human manager immediately.');
      setLocale('en');
    } else {
      setCustomerName('Alex Rivera');
      setSubject('Feature Suggestion');
      setMessage('Can you please add automatic dark mode scheduling and multi-currency exchange rate preview? Love the budgeting pockets.');
      setLocale('en');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-white">
              {t('nav.customer', 'Customer Intelligence')}
            </h1>
            <span className="rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-semibold text-emerald-400 border border-emerald-500/20">
              Phase 9
            </span>
            <span className="rounded-full bg-indigo-500/10 px-2.5 py-0.5 text-xs font-medium text-indigo-300 border border-indigo-500/20">
              Money Management Safe
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-400">
            Multilingual sentiment analysis, PII masking, ticket categorization, promise safety guardrails, and human escalation handoff workflows.
          </p>
        </div>

        {/* Sub-tab navigation */}
        <div className="flex rounded-lg bg-slate-900 p-1 border border-slate-800">
          <button
            type="button"
            onClick={() => setActiveTab('triage')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition ${activeTab === 'triage'
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
              }`}
          >
            Live Triage & Sentiment
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('escalations')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition ${activeTab === 'escalations'
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
              }`}
          >
            Human Escalation Queue
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('discovery')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition ${activeTab === 'discovery'
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
              }`}
          >
            Reviews & Feature Discovery
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('benchmarks')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition ${activeTab === 'benchmarks'
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
              }`}
          >
            Regression Benchmarks
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg bg-red-950/40 border border-red-800/60 p-4 text-sm text-red-300 flex items-center justify-between">
          <span>{error}</span>
          <button type="button" onClick={() => setError(null)} className="text-red-400 hover:text-red-200">
            ×
          </button>
        </div>
      )}

      {/* TAB 1: LIVE TRIAGE & SENTIMENT ANALYZER */}
      {activeTab === 'triage' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-6 space-y-4">
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 shadow-sm">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
                <h2 className="text-sm font-semibold text-white">Ticket & Sentiment Simulator</h2>
                <div className="flex gap-1.5">
                  <button
                    type="button"
                    onClick={() => loadSample('budget')}
                    className="text-[11px] px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300"
                  >
                    Budget Inquiry (ID)
                  </button>
                  <button
                    type="button"
                    onClick={() => loadSample('promise_trap')}
                    className="text-[11px] px-2 py-1 rounded bg-rose-950/50 hover:bg-rose-900 text-rose-300 border border-rose-800/40"
                  >
                    Refund Promise Trap
                  </button>
                  <button
                    type="button"
                    onClick={() => loadSample('frustration')}
                    className="text-[11px] px-2 py-1 rounded bg-amber-950/50 hover:bg-amber-900 text-amber-300 border border-amber-800/40"
                  >
                    Human Demand (EN)
                  </button>
                </div>
              </div>

              <form onSubmit={handleRunTriage} className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">Customer Name</label>
                    <input
                      type="text"
                      value={customerName}
                      onChange={(e) => setCustomerName(e.target.value)}
                      className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-400 mb-1">Language Locale</label>
                    <select
                      value={locale}
                      onChange={(e) => setLocale(e.target.value as 'id' | 'en')}
                      className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                    >
                      <option value="id">Bahasa Indonesia (id)</option>
                      <option value="en">English (en)</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">Inquiry Subject</label>
                  <input
                    type="text"
                    value={subject}
                    onChange={(e) => setSubject(e.target.value)}
                    className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">Raw Customer Message</label>
                  <textarea
                    rows={4}
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    placeholder="Enter customer support message or review text..."
                    className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 font-mono text-xs"
                  />
                  <div className="mt-1 text-[11px] text-slate-500">
                    Supports PII detection (NIK, Credit Card, Email, Phone) and Promise Policy verification.
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={triageSubmitting || !message.trim()}
                  className="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-50 transition"
                >
                  {triageSubmitting ? 'Triaging Customer Ticket...' : 'Analyze Sentiment & Triage Ticket'}
                </button>
              </form>
            </div>
          </div>

          <div className="lg:col-span-6 space-y-4">
            {triageResult ? (
              <div className="space-y-4">
                {/* Promise Guard Alert Banner */}
                {triageResult.promise_guard_flagged && (
                  <div className="rounded-xl border border-rose-800/70 bg-rose-950/30 p-4 text-rose-200 space-y-2">
                    <div className="flex items-center gap-2 font-semibold text-rose-300 text-sm">
                      <span className="text-base">⚠️</span> Promise Guard Triggered (Banking Violation Prevention)
                    </div>
                    <p className="text-xs text-rose-200/90 leading-relaxed">
                      Customer requested commitment regarding:{' '}
                      <span className="font-mono bg-rose-900/60 px-1 py-0.5 rounded text-white">
                        {triageResult.promise_guard_reasons?.join(', ')}
                      </span>
                      . CRANIAL enforced money-management boundary: NO banking execution or fund transfer commitments allowed.
                    </p>
                  </div>
                )}

                {/* Classification Cards */}
                <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono bg-slate-800 px-2 py-0.5 rounded text-indigo-300 font-semibold">
                        {triageResult.ticket_number}
                      </span>
                      <span className="text-xs text-slate-400">Status: {triageResult.escalation_recommended ? 'ESCALATED' : 'TRIAGED'}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-xs px-2 py-0.5 rounded font-semibold ${triageResult.sentiment === 'POSITIVE'
                          ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                          : triageResult.sentiment === 'NEGATIVE'
                            ? 'bg-rose-950 text-rose-400 border border-rose-800'
                            : 'bg-slate-800 text-slate-300'
                          }`}
                      >
                        {triageResult.sentiment} ({triageResult.sentiment_score > 0 ? '+' : ''}
                        {triageResult.sentiment_score})
                      </span>
                      <span
                        className={`text-xs px-2 py-0.5 rounded font-semibold ${triageResult.priority === 'URGENT'
                          ? 'bg-rose-950 text-rose-400 border border-rose-800'
                          : triageResult.priority === 'HIGH'
                            ? 'bg-amber-950 text-amber-400 border border-amber-800'
                            : 'bg-slate-800 text-slate-300'
                          }`}
                      >
                        Priority: {triageResult.priority}
                      </span>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <div className="text-slate-400">Classified Category</div>
                      <div className="font-semibold text-white mt-0.5">{triageResult.category}</div>
                    </div>
                    <div>
                      <div className="text-slate-400">Assigned Routing Queue</div>
                      <div className="font-semibold text-indigo-300 mt-0.5">{triageResult.routing_queue}</div>
                    </div>
                  </div>

                  {/* Redacted Message */}
                  <div>
                    <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
                      <span>PII Redacted Message:</span>
                      {triageResult.pii_detected ? (
                        <span className="text-emerald-400 font-semibold">✓ {triageResult.pii_entities.length} PII entities masked</span>
                      ) : (
                        <span className="text-slate-500">No PII detected</span>
                      )}
                    </div>
                    <div className="rounded-lg bg-slate-950 p-3 text-xs text-slate-300 font-mono leading-relaxed border border-slate-800">
                      {triageResult.redacted_message}
                    </div>
                  </div>

                  {/* Suggested Canned Response */}
                  <div>
                    <div className="text-xs text-slate-400 mb-1 font-semibold">Suggested Grounded Canned Response:</div>
                    <div className="rounded-lg bg-indigo-950/30 border border-indigo-800/40 p-3 text-xs text-indigo-200 leading-relaxed">
                      {triageResult.suggested_canned_response}
                    </div>
                  </div>

                  {/* Escalation Handoff Preview */}
                  {triageResult.escalation_package && (
                    <div className="rounded-lg border border-amber-800/50 bg-amber-950/20 p-3 space-y-2 text-xs">
                      <div className="flex items-center justify-between text-amber-300 font-semibold">
                        <span>Human Escalation Handoff Package</span>
                        <span className="bg-amber-900/60 px-1.5 py-0.5 rounded text-[10px]">
                          {triageResult.escalation_package.reason}
                        </span>
                      </div>
                      <div className="text-slate-300">{triageResult.escalation_package.executive_summary}</div>
                      <div className="text-[11px] text-amber-200/80">
                        {triageResult.escalation_package.customer_sentiment_summary}
                      </div>
                      <div className="text-slate-400 font-mono text-[11px]">
                        Recommended Next Steps:{' '}
                        {triageResult.escalation_package.recommended_actions?.join(' • ')}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-slate-800 bg-slate-900/30 p-12 text-center text-slate-500 text-sm">
                Enter customer inquiry or select sample scenario to test sentiment scoring, PII masking, and triage rules.
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: HUMAN ESCALATION QUEUE */}
      {activeTab === 'escalations' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-sm font-semibold text-white">Active Escalation Queue</span>
              <span className="text-xs text-slate-400">({escalations.length} total)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400">Filter status:</span>
              <select
                value={escalationStatusFilter}
                onChange={(e) => setEscalationStatusFilter(e.target.value)}
                className="rounded-lg bg-slate-900 border border-slate-800 px-2.5 py-1 text-xs text-slate-200"
              >
                <option value="ALL">All Statuses</option>
                <option value="PENDING_HANDOFF">Pending Handoff</option>
                <option value="IN_REVIEW">In Review</option>
                <option value="RESOLVED">Resolved</option>
              </select>
              <button
                type="button"
                onClick={fetchEscalations}
                className="rounded-lg bg-slate-800 px-3 py-1 text-xs text-slate-300 hover:bg-slate-700"
              >
                Refresh
              </button>
            </div>
          </div>

          {loading ? (
            <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-8 text-center text-slate-400 text-sm">
              Loading escalations...
            </div>
          ) : escalations.length === 0 ? (
            <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-8 text-center text-slate-400 text-sm">
              No human escalations found for filter: {escalationStatusFilter}.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {escalations.map((esc) => (
                <div key={esc.id} className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <div>
                      <span className="font-mono text-xs text-indigo-300 font-semibold">{esc.ticket_number}</span>
                      <span className="text-xs text-slate-400 ml-2">Customer: {esc.customer_name || 'Anonymous'}</span>
                    </div>
                    <span
                      className={`text-[10px] px-2 py-0.5 rounded font-semibold ${esc.status === 'PENDING_HANDOFF'
                        ? 'bg-amber-950 text-amber-400 border border-amber-800'
                        : esc.status === 'RESOLVED'
                          ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                          : 'bg-indigo-950 text-indigo-400'
                        }`}
                    >
                      {esc.status}
                    </span>
                  </div>

                  <div>
                    <div className="text-xs font-semibold text-white">{esc.subject}</div>
                    <div className="text-xs text-slate-300 mt-1">{esc.executive_summary}</div>
                  </div>

                  <div className="rounded bg-slate-950 p-2 text-[11px] text-amber-200/80 border border-slate-800">
                    {esc.customer_sentiment_summary}
                  </div>

                  <div className="text-[11px] text-slate-400">
                    <span className="font-semibold text-slate-300">Action items:</span>{' '}
                    {esc.recommended_actions?.join(' • ')}
                  </div>

                  {esc.status !== 'RESOLVED' && (
                    <button
                      type="button"
                      onClick={() => {
                        setResolveModalItem(esc);
                        setResolutionNotes('');
                      }}
                      className="w-full rounded bg-indigo-600 hover:bg-indigo-500 py-1.5 text-xs font-medium text-white transition shadow-sm"
                    >
                      Resolve Escalation
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* RESOLVE MODAL */}
      {resolveModalItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-xl space-y-4">
            <h3 className="text-base font-bold text-white">Resolve Human Escalation</h3>
            <p className="text-xs text-slate-400">
              Ticket: <span className="font-mono text-indigo-300">{resolveModalItem.ticket_number}</span> ({resolveModalItem.subject})
            </p>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Resolution Summary / Notes</label>
              <textarea
                rows={3}
                value={resolutionNotes}
                onChange={(e) => setResolutionNotes(e.target.value)}
                placeholder="Explained pocket money allocation rules and confirmed ledger balance..."
                className="w-full rounded-lg bg-slate-950 border border-slate-800 p-2 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setResolveModalItem(null)}
                className="rounded px-3 py-1.5 text-xs text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={resolveSubmitting}
                onClick={handleResolveEscalation}
                className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-1.5 text-xs font-semibold text-white"
              >
                {resolveSubmitting ? 'Saving...' : 'Mark Resolved'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: REVIEWS & PRODUCT DISCOVERY */}
      {activeTab === 'discovery' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Review Batch Summarizer */}
          <div className="lg:col-span-6 space-y-4">
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
              <h2 className="text-sm font-semibold text-white">Review & Feedback Summarizer</h2>
              <p className="text-xs text-slate-400">
                Aggregates customer app store reviews, extracts pros, cons, and top requested money-management features.
              </p>
              <textarea
                rows={5}
                value={reviewBatch}
                onChange={(e) => setReviewBatch(e.target.value)}
                placeholder="One review per line..."
                className="w-full rounded-lg bg-slate-950 border border-slate-800 p-2.5 text-xs text-white font-mono"
              />
              <button
                type="button"
                disabled={reviewSubmitting || !reviewBatch.trim()}
                onClick={handleSummarizeReviews}
                className="w-full rounded bg-indigo-600 hover:bg-indigo-500 py-2 text-xs font-semibold text-white transition"
              >
                {reviewSubmitting ? 'Summarizing Reviews...' : 'Summarize Customer Reviews'}
              </button>

              {reviewSummary && (
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-4 space-y-3 text-xs">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <span className="text-slate-400">Analyzed {reviewSummary.total_analyzed} reviews</span>
                    <span className="font-semibold text-emerald-400">Sentiment: {reviewSummary.overall_sentiment}</span>
                  </div>
                  <div className="text-slate-300 leading-relaxed">{reviewSummary.executive_summary}</div>
                  <div>
                    <span className="font-semibold text-emerald-400">Pros:</span>
                    <ul className="list-disc list-inside text-slate-300 mt-1 space-y-0.5">
                      {reviewSummary.pros?.map((p, idx) => (
                        <li key={idx}>{p}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <span className="font-semibold text-amber-400">Cons / Friction:</span>
                    <ul className="list-disc list-inside text-slate-300 mt-1 space-y-0.5">
                      {reviewSummary.cons?.map((c, idx) => (
                        <li key={idx}>{c}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Product & Feature Discovery */}
          <div className="lg:col-span-6 space-y-4">
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
              <h2 className="text-sm font-semibold text-white">Money Management Feature Discovery</h2>
              <p className="text-xs text-slate-400">
                Matches customer financial goals to non-banking features (pockets, 50-30-20 rules, shared budgets).
              </p>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Customer Goal / Need</label>
                <input
                  type="text"
                  value={customerGoal}
                  onChange={(e) => setCustomerGoal(e.target.value)}
                  className="w-full rounded-lg bg-slate-950 border border-slate-800 px-3 py-2 text-xs text-white"
                />
              </div>
              <button
                type="button"
                disabled={discoverySubmitting || !customerGoal.trim()}
                onClick={handleDiscoverProducts}
                className="w-full rounded bg-indigo-600 hover:bg-indigo-500 py-2 text-xs font-semibold text-white transition"
              >
                {discoverySubmitting ? 'Matching Features...' : 'Discover Tailored Features'}
              </button>

              {discoveryResult && (
                <div className="space-y-3">
                  <div className="rounded-lg bg-indigo-950/30 border border-indigo-800/40 p-3 text-[11px] text-indigo-300">
                    <span className="font-semibold">Boundary Guard:</span> {discoveryResult.money_management_boundary_disclaimer}
                  </div>
                  {discoveryResult.recommended_features?.map((feat) => (
                    <div key={feat.feature_code} className="rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs space-y-1.5">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-white">{feat.feature_name}</span>
                        <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-emerald-400 font-semibold">
                          Match: {Math.round(feat.match_score * 100)}%
                        </span>
                      </div>
                      <p className="text-slate-300 text-[11px]">{feat.rationale}</p>
                      <div className="text-[11px] text-slate-400">
                        <span className="text-indigo-300 font-semibold">Setup Steps:</span>{' '}
                        {feat.setup_guidance?.join(' • ')}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* TAB 4: REGRESSION BENCHMARKS */}
      {activeTab === 'benchmarks' && (
        <div className="space-y-5">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
              <div>
                <h2 className="text-sm font-semibold text-white">Customer Intelligence Evaluation Suite</h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Automated benchmark evaluation tracking sentiment accuracy, Promise Guard safety rates, and escalation precision across EN/ID cases.
                </p>
              </div>
              <button
                type="button"
                disabled={benchmarkRunning}
                onClick={handleRunBenchmarks}
                className="rounded-lg bg-indigo-600 hover:bg-indigo-500 px-4 py-2 text-xs font-semibold text-white shadow transition"
              >
                {benchmarkRunning ? 'Running Evaluation Suite...' : 'Run Customer Intelligence Benchmarks'}
              </button>
            </div>

            {benchmarkResult ? (
              <div className="space-y-4">
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                  <div className="rounded-lg bg-slate-950 p-3 border border-slate-800">
                    <div className="text-[11px] text-slate-400">Total Cases</div>
                    <div className="text-xl font-bold text-white mt-1">{benchmarkResult.total_evaluated}</div>
                    <div className="text-[10px] text-emerald-400 mt-0.5">
                      {benchmarkResult.passed_cases} passed • {benchmarkResult.failed_cases} failed
                    </div>
                  </div>
                  <div className="rounded-lg bg-slate-950 p-3 border border-slate-800">
                    <div className="text-[11px] text-slate-400">Promise Guard Safety</div>
                    <div className="text-xl font-bold text-emerald-400 mt-1">
                      {benchmarkResult.promise_guard_safety_rate_percent}%
                    </div>
                    <div className="text-[10px] text-slate-500 mt-0.5">Zero banking promises permitted</div>
                  </div>
                  <div className="rounded-lg bg-slate-950 p-3 border border-slate-800">
                    <div className="text-[11px] text-slate-400">Sentiment Accuracy</div>
                    <div className="text-xl font-bold text-indigo-300 mt-1">
                      {benchmarkResult.sentiment_accuracy_percent}%
                    </div>
                    <div className="text-[10px] text-slate-500 mt-0.5">Bilingual EN & ID match</div>
                  </div>
                  <div className="rounded-lg bg-slate-950 p-3 border border-slate-800">
                    <div className="text-[11px] text-slate-400">Classification F1</div>
                    <div className="text-xl font-bold text-white mt-1">
                      {benchmarkResult.classification_f1_score}
                    </div>
                    <div className="text-[10px] text-emerald-400 mt-0.5">Overall benchmark quality</div>
                  </div>
                </div>

                <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-950">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-900/80 text-[11px] text-slate-400 uppercase tracking-wider">
                      <tr>
                        <th className="px-3 py-2">Suite</th>
                        <th className="px-3 py-2">Locale</th>
                        <th className="px-3 py-2">Input Snippet</th>
                        <th className="px-3 py-2">Sentiment</th>
                        <th className="px-3 py-2">Promise Safety</th>
                        <th className="px-3 py-2">Escalation</th>
                        <th className="px-3 py-2">Result</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800">
                      {benchmarkResult.evaluation_details?.map((item: Record<string, unknown>, idx) => (
                        <tr key={idx} className="hover:bg-slate-900/40">
                          <td className="px-3 py-2 font-mono text-[10px] text-indigo-300">{String(item.suite_code)}</td>
                          <td className="px-3 py-2 font-mono text-[10px]">{String(item.locale).toUpperCase()}</td>
                          <td className="px-3 py-2 max-w-xs truncate">{String(item.input)}</td>
                          <td className="px-3 py-2">
                            {item.sentiment_match !== undefined ? (item.sentiment_match ? '✓' : '✗') : '-'}
                          </td>
                          <td className="px-3 py-2">
                            {item.promise_safety_match !== undefined ? (item.promise_safety_match ? '✓' : '✗') : '-'}
                          </td>
                          <td className="px-3 py-2">
                            {item.escalation_match !== undefined ? (item.escalation_match ? '✓' : '✗') : '-'}
                          </td>
                          <td className="px-3 py-2">
                            <span
                              className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${item.passed ? 'bg-emerald-950 text-emerald-400' : 'bg-rose-950 text-rose-400'
                                }`}
                            >
                              {item.passed ? 'PASS' : 'FAIL'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-slate-800 bg-slate-950 p-8 text-center text-slate-500 text-xs">
                Click "Run Customer Intelligence Benchmarks" to evaluate ground-truth test cases.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default CustomerPage;

