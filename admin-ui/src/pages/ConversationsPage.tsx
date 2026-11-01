import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import {
  ConversationView,
  ConversationDetailView,
  ConversationMessageView,
  ConversationRetentionPolicyView,
  SlangNormalizeResultView,
  ConversationBenchmarkResultView,
  StructuredUiBlockView,
} from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';
import { StatusBadge } from '../components/StatusBadge';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { ConfirmDialog } from '../components/ConfirmDialog';

type SubTab = 'sessions' | 'chat' | 'slang' | 'safety' | 'retention' | 'benchmarks';

export function ConversationsPage() {
  const { t, language } = useLanguage();

  const [activeSubTab, setActiveSubTab] = useState<SubTab>('sessions');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Sessions state
  const [conversations, setConversations] = useState<ConversationView[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAppFilter, setSelectedAppFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ACTIVE');
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [sessionDetail, setSessionDetail] = useState<ConversationDetailView | null>(null);

  // Chat simulator state
  const [chatAppId, setChatAppId] = useState('00000000-0000-0000-0004-000000000003'); // Peggy by default
  const [chatInput, setChatInput] = useState('');
  const [sendingMessage, setSendingMessage] = useState(false);

  // Slang normalizer state
  const [slangInput, setSlangInput] = useState('Gaji saya 8jt per bulan, makan gocap sehari, kosan 2jt, beli jajan ceban.');
  const [slangResult, setSlangResult] = useState<SlangNormalizeResultView | null>(null);
  const [normalizingSlang, setNormalizingSlang] = useState(false);

  // Retention state
  const [retentionPolicies, setRetentionPolicies] = useState<ConversationRetentionPolicyView[]>([]);
  const [pruning, setPruning] = useState(false);
  const [pruneResult, setPruneResult] = useState<string | null>(null);

  // Benchmarks state
  const [benchmarks, setBenchmarks] = useState<ConversationBenchmarkResultView | null>(null);

  // Feedback modal state
  const [feedbackMessageId, setFeedbackMessageId] = useState<string | null>(null);
  const [feedbackRating, setFeedbackRating] = useState(5);
  const [feedbackComment, setFeedbackComment] = useState('');

  // Delete confirm dialog
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [convsRes, policiesRes, benchRes] = await Promise.all([
        api.listConversations({
          applicationId: selectedAppFilter || undefined,
          query: searchQuery || undefined,
          status: statusFilter || undefined,
        }),
        api.getConversationRetentionPolicies(),
        api.getConversationBenchmarks(),
      ]);

      if (convsRes.data) {
        setConversations(convsRes.data);
        if (convsRes.data.length > 0 && !selectedSessionId) {
          setSelectedSessionId(convsRes.data[0].id);
        }
      }
      if (policiesRes.data) {
        setRetentionPolicies(policiesRes.data);
      }
      if (benchRes.data) {
        setBenchmarks(benchRes.data);
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to load conversational intelligence data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [selectedAppFilter, statusFilter]);

  // Load session detail when selectedSessionId changes
  useEffect(() => {
    if (!selectedSessionId) {
      setSessionDetail(null);
      return;
    }
    api.getConversation(selectedSessionId).then((res) => {
      if (res.data) setSessionDetail(res.data);
    });
  }, [selectedSessionId]);

  const handleSendMessage = async (textToSend?: string) => {
    const text = textToSend || chatInput;
    if (!text.trim()) return;

    setSendingMessage(true);
    try {
      let targetConvId = selectedSessionId;
      if (!targetConvId) {
        // Create new session
        const createRes = await api.createConversationSession(chatAppId, undefined, {
          userId: 'operator_demo_user',
          initialMessage: text,
          locale: language,
        });
        if (createRes.data) {
          targetConvId = createRes.data.conversation.id;
          setSelectedSessionId(targetConvId);
        }
      } else {
        await api.simulateConversationTurn(chatAppId, targetConvId, {
          content: text,
          locale: language,
        });
      }

      setChatInput('');
      if (targetConvId) {
        const detailRes = await api.getConversation(targetConvId);
        if (detailRes.data) setSessionDetail(detailRes.data);
      }
      // Refresh list to update turn counts
      const convListRes = await api.listConversations({});
      if (convListRes.data) setConversations(convListRes.data);
    } catch (err: any) {
      alert(err?.message || 'Failed to send message');
    } finally {
      setSendingMessage(false);
    }
  };

  const handleNormalizeSlang = async () => {
    if (!slangInput.trim()) return;
    setNormalizingSlang(true);
    try {
      const res = await api.testSlangNormalization({ text: slangInput, locale: language });
      if (res.data) setSlangResult(res.data);
    } catch (err: any) {
      alert(err?.message || 'Failed to normalize slang');
    } finally {
      setNormalizingSlang(false);
    }
  };

  const handleExport = async (id: string, format: 'markdown' | 'json') => {
    try {
      const res = await api.exportConversation(id, format);
      if (res.data) {
        const blob = new Blob([res.data.content], { type: format === 'json' ? 'application/json' : 'text/markdown' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `cranial-conversation-${id}.${format === 'json' ? 'json' : 'md'}`;
        a.click();
        URL.revokeObjectURL(url);
      }
    } catch (err: any) {
      alert(err?.message || 'Export failed');
    }
  };

  const handleDelete = async () => {
    if (!deleteTargetId) return;
    try {
      await api.deleteConversation(deleteTargetId, false);
      setDeleteTargetId(null);
      loadData();
    } catch (err: any) {
      alert(err?.message || 'Failed to delete session');
    }
  };

  const handlePruneNow = async () => {
    setPruning(true);
    setPruneResult(null);
    try {
      const res = await api.pruneConversations();
      if (res.data) {
        setPruneResult(`Pruning completed: ${res.data.prunedCount} expired conversation sessions purged.`);
        loadData();
      }
    } catch (err: any) {
      alert(err?.message || 'Pruning failed');
    } finally {
      setPruning(false);
    }
  };

  const handleFeedbackSubmit = async (helpful: boolean) => {
    if (!selectedSessionId) return;
    try {
      await api.submitConversationFeedback(selectedSessionId, {
        messageId: feedbackMessageId || undefined,
        rating: feedbackRating,
        isHelpful: helpful,
        comment: feedbackComment || undefined,
      });
      setFeedbackMessageId(null);
      setFeedbackComment('');
      // Reload session
      const res = await api.getConversation(selectedSessionId);
      if (res.data) setSessionDetail(res.data);
    } catch (err: any) {
      alert(err?.message || 'Failed to submit feedback');
    }
  };

  // Render structured UI block
  const renderStructuredBlock = (block: StructuredUiBlockView, idx: number) => {
    switch (block.type) {
      case 'TABLE':
        return (
          <div key={idx} className="mt-3 overflow-x-auto rounded-lg border border-slate-700 bg-slate-900/80 p-3">
            {block.title && <div className="mb-2 font-semibold text-emerald-400 text-sm">{block.title}</div>}
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-700 text-slate-400">
                  {block.headers?.map((h, i) => (
                    <th key={i} className="pb-2 font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {block.rows?.map((row, rIdx) => (
                  <tr key={rIdx} className="hover:bg-slate-800/40">
                    {row.map((cell, cIdx) => (
                      <td key={cIdx} className="py-2 text-slate-200">{cell}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );

      case 'ACTION_CARD':
        return (
          <div key={idx} className="mt-3 rounded-lg border border-indigo-500/40 bg-indigo-950/30 p-3">
            <div className="font-semibold text-indigo-300 text-sm">{block.title}</div>
            {block.description && <p className="mt-1 text-slate-300 text-xs">{block.description}</p>}
            {block.actionLabel && (
              <button
                type="button"
                onClick={() => alert(`Action triggered: ${block.actionPayload || block.actionLabel}`)}
                className="mt-2 rounded bg-indigo-600 px-3 py-1 text-white text-xs font-medium hover:bg-indigo-500"
              >
                {block.actionLabel}
              </button>
            )}
          </div>
        );

      case 'CHART_KPI':
        return (
          <div key={idx} className="mt-3 flex items-center justify-between rounded-lg border border-emerald-500/30 bg-emerald-950/20 p-3">
            <div>
              <div className="text-xs text-slate-400">{block.title}</div>
              <div className="text-sm font-semibold text-slate-200">{block.metricLabel}</div>
              {block.metricTrend && <div className="text-xs text-emerald-400 mt-0.5">{block.metricTrend}</div>}
            </div>
            <div className="text-2xl font-bold text-emerald-400">{block.metricValue}</div>
          </div>
        );

      case 'CLARIFICATION':
        return (
          <div key={idx} className="mt-3 rounded-lg border border-amber-500/40 bg-amber-950/20 p-3">
            <div className="font-semibold text-amber-300 text-sm">{block.title}</div>
            {block.description && <p className="mt-1 text-slate-300 text-xs">{block.description}</p>}
            <div className="mt-3 flex flex-wrap gap-2">
              {block.options?.map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => handleSendMessage(opt.suggestedInput)}
                  className="rounded-full border border-amber-500/50 bg-amber-900/40 px-3 py-1 text-xs font-medium text-amber-200 hover:bg-amber-800/60 transition"
                  title={opt.description}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        );

      case 'FINANCIAL_DISCLAIMER':
        return (
          <div key={idx} className="mt-3 rounded-lg border border-rose-500/60 bg-rose-950/30 p-3 text-rose-200">
            <div className="flex items-center gap-2 font-semibold text-rose-400 text-sm">
              <span className="text-base">🛡️</span>
              <span>{block.title || t('conversations.safety.bannerTitle')}</span>
            </div>
            <p className="mt-1 text-xs text-rose-300/90 leading-relaxed">{block.content}</p>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
            <span>💬</span>
            <span>{t('conversations.title')}</span>
          </h1>
          <p className="text-sm text-slate-400">{t('conversations.subtitle')}</p>
        </div>

        {/* Live Metrics Quick Badges */}
        {benchmarks && (
          <div className="flex flex-wrap items-center gap-2">
            <div className="rounded-lg bg-slate-800/80 px-3 py-1.5 border border-slate-700 text-xs">
              <span className="text-slate-400">{t('conversations.sessions.turns')}: </span>
              <span className="font-semibold text-indigo-400">{benchmarks.totalMessages}</span>
            </div>
            <div className="rounded-lg bg-slate-800/80 px-3 py-1.5 border border-slate-700 text-xs">
              <span className="text-slate-400">Avg Latency: </span>
              <span className="font-semibold text-emerald-400">{Math.round(benchmarks.averageLatencyMs)}ms</span>
            </div>
            <div className="rounded-lg bg-slate-800/80 px-3 py-1.5 border border-slate-700 text-xs">
              <span className="text-slate-400">Safety Guard: </span>
              <span className="font-semibold text-rose-400">{benchmarks.totalSafetyViolationsIntercepted} Blocked</span>
            </div>
          </div>
        )}
      </div>

      {/* Sub-Navigation Tabs */}
      <div className="flex border-b border-slate-700 gap-4 overflow-x-auto">
        <button
          type="button"
          onClick={() => setActiveSubTab('sessions')}
          className={`pb-3 text-sm font-medium border-b-2 transition whitespace-nowrap ${
            activeSubTab === 'sessions'
              ? 'border-indigo-500 text-indigo-400'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('conversations.tab.sessions')}
        </button>
        <button
          type="button"
          onClick={() => setActiveSubTab('chat')}
          className={`pb-3 text-sm font-medium border-b-2 transition whitespace-nowrap ${
            activeSubTab === 'chat'
              ? 'border-indigo-500 text-indigo-400'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('conversations.tab.chat')}
        </button>
        <button
          type="button"
          onClick={() => setActiveSubTab('slang')}
          className={`pb-3 text-sm font-medium border-b-2 transition whitespace-nowrap ${
            activeSubTab === 'slang'
              ? 'border-indigo-500 text-indigo-400'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('conversations.tab.slang')}
        </button>
        <button
          type="button"
          onClick={() => setActiveSubTab('safety')}
          className={`pb-3 text-sm font-medium border-b-2 transition whitespace-nowrap ${
            activeSubTab === 'safety'
              ? 'border-indigo-500 text-indigo-400'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('conversations.tab.safety')}
        </button>
        <button
          type="button"
          onClick={() => setActiveSubTab('retention')}
          className={`pb-3 text-sm font-medium border-b-2 transition whitespace-nowrap ${
            activeSubTab === 'retention'
              ? 'border-indigo-500 text-indigo-400'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('conversations.tab.retention')}
        </button>
        <button
          type="button"
          onClick={() => setActiveSubTab('benchmarks')}
          className={`pb-3 text-sm font-medium border-b-2 transition whitespace-nowrap ${
            activeSubTab === 'benchmarks'
              ? 'border-indigo-500 text-indigo-400'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          {t('conversations.tab.benchmarks')}
        </button>
      </div>

      {/* SUB-TAB 1: SESSIONS EXPLORER */}
      {activeSubTab === 'sessions' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
            <div className="flex gap-2 w-full sm:w-auto">
              <input
                type="text"
                placeholder={t('conversations.sessions.search')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && loadData()}
                className="rounded-lg bg-slate-900 border border-slate-700 px-3 py-1.5 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-full sm:w-80"
              />
              <button
                type="button"
                onClick={loadData}
                className="rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-700"
              >
                Search
              </button>
            </div>

            <div className="flex gap-2">
              <select
                value={selectedAppFilter}
                onChange={(e) => setSelectedAppFilter(e.target.value)}
                className="rounded-lg bg-slate-900 border border-slate-700 px-3 py-1.5 text-xs text-slate-300 focus:outline-none focus:border-indigo-500"
              >
                <option value="">{t('conversations.sessions.filterApp')}</option>
                {retentionPolicies.map((p) => (
                  <option key={p.applicationId} value={p.applicationId}>
                    {p.applicationName}
                  </option>
                ))}
              </select>

              <button
                type="button"
                onClick={() => {
                  setActiveSubTab('chat');
                  setSelectedSessionId(null);
                  setSessionDetail(null);
                }}
                className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500 transition"
              >
                + {t('conversations.sessions.new')}
              </button>
            </div>
          </div>

          {loading ? (
            <LoadingState message="Loading conversational sessions..." />
          ) : conversations.length === 0 ? (
            <EmptyState title="No Conversations Found" message="Create a conversation or send an assistant query." />
          ) : (
            <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/60 shadow-lg">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-950/80 text-xs uppercase text-slate-400">
                  <tr>
                    <th className="px-4 py-3">Title / User</th>
                    <th className="px-4 py-3">Application</th>
                    <th className="px-4 py-3">Locale</th>
                    <th className="px-4 py-3 text-center">{t('conversations.sessions.turns')}</th>
                    <th className="px-4 py-3 text-center">{t('conversations.sessions.tokens')}</th>
                    <th className="px-4 py-3">{t('conversations.sessions.status')}</th>
                    <th className="px-4 py-3 text-right">{t('conversations.sessions.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {conversations.map((c) => (
                    <tr
                      key={c.id}
                      className={`hover:bg-slate-800/40 transition cursor-pointer ${
                        selectedSessionId === c.id ? 'bg-indigo-950/20 border-l-2 border-indigo-500' : ''
                      }`}
                      onClick={() => {
                        setSelectedSessionId(c.id);
                        setActiveSubTab('chat');
                      }}
                    >
                      <td className="px-4 py-3">
                        <div className="font-medium text-white">{c.title}</div>
                        <div className="text-xs text-slate-500">User: {c.userId} • ID: {c.id.substring(0, 8)}...</div>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-300">{c.applicationName}</td>
                      <td className="px-4 py-3 text-xs font-mono">
                        <span className="rounded bg-slate-800 px-1.5 py-0.5 text-slate-300 uppercase">
                          {c.locale}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-xs font-semibold text-indigo-400">
                        {c.totalTurnCount}
                      </td>
                      <td className="px-4 py-3 text-center text-xs text-slate-400">
                        {c.totalTokensUsed}
                      </td>
                      <td className="px-4 py-3 text-xs">
                        <StatusBadge status={c.status === 'ACTIVE' ? 'AVAILABLE' : 'OFFLINE'} />
                      </td>
                      <td className="px-4 py-3 text-right text-xs space-x-2" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          onClick={() => handleExport(c.id, 'markdown')}
                          className="text-slate-400 hover:text-indigo-400"
                          title={t('conversations.sessions.exportMarkdown')}
                        >
                          MD
                        </button>
                        <button
                          type="button"
                          onClick={() => handleExport(c.id, 'json')}
                          className="text-slate-400 hover:text-indigo-400"
                          title={t('conversations.sessions.exportJson')}
                        >
                          JSON
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteTargetId(c.id)}
                          className="text-rose-400 hover:text-rose-300"
                          title={t('conversations.sessions.delete')}
                        >
                          ✕
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* SUB-TAB 2: INTERACTIVE ASSISTANT & SIMULATOR */}
      {activeSubTab === 'chat' && (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Chat Control Sidebar */}
          <div className="lg:col-span-1 space-y-4 rounded-xl border border-slate-800 bg-slate-900/60 p-4">
            <div>
              <label className="text-xs font-medium text-slate-400 block mb-1">
                {t('conversations.chat.selectApp')}
              </label>
              <select
                value={chatAppId}
                onChange={(e) => setChatAppId(e.target.value)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              >
                {retentionPolicies.map((p) => (
                  <option key={p.applicationId} value={p.applicationId}>
                    {p.applicationName}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-xs font-medium text-slate-400 block mb-1">
                {t('conversations.chat.selectSession')}
              </label>
              <select
                value={selectedSessionId || ''}
                onChange={(e) => setSelectedSessionId(e.target.value || null)}
                className="w-full rounded-lg bg-slate-900 border border-slate-700 px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              >
                <option value="">+ {t('conversations.sessions.new')}</option>
                {conversations.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title.substring(0, 28)}... ({c.totalTurnCount} turns)
                  </option>
                ))}
              </select>
            </div>

            {/* Context Summary Box */}
            {sessionDetail?.conversation.contextSummary && (
              <div className="rounded-lg border border-slate-800 bg-slate-950/60 p-3">
                <div className="text-xs font-medium text-slate-400 mb-1">Rolling Context Summary</div>
                <p className="text-xs text-slate-300 leading-relaxed italic">
                  "{sessionDetail.conversation.contextSummary}"
                </p>
              </div>
            )}

            {/* Quick Test Prompt Shortcuts */}
            <div className="space-y-2 pt-2 border-t border-slate-800">
              <div className="text-xs font-medium text-slate-400">Quick Test Scenarios</div>
              <button
                type="button"
                onClick={() => setChatInput('Gaji saya 8jt per bulan, makan gocap sehari, kosan 2jt. Tolong bantu alokasikan anggarannya.')}
                className="w-full text-left rounded bg-slate-800/60 hover:bg-slate-800 p-2 text-xs text-slate-300 transition"
              >
                💰 Peggy Budget (Slang: 8jt, gocap)
              </button>
              <button
                type="button"
                onClick={() => setChatInput('Tolong transfer uang Rp 500.000 ke rekening BCA sekarang untuk bayar listrik.')}
                className="w-full text-left rounded bg-rose-950/30 hover:bg-rose-900/40 p-2 text-xs text-rose-300 border border-rose-800/40 transition"
              >
                🛡️ Banking Wire (Safety Test)
              </button>
              <button
                type="button"
                onClick={() => setChatInput('Bagaimana pengeluaran saya')}
                className="w-full text-left rounded bg-amber-950/30 hover:bg-amber-900/40 p-2 text-xs text-amber-300 border border-amber-800/40 transition"
              >
                ❓ Ambiguous Query (Clarification)
              </button>
              <button
                type="button"
                onClick={() => setChatInput('Cek stok toko')}
                className="w-full text-left rounded bg-slate-800/60 hover:bg-slate-800 p-2 text-xs text-slate-300 transition"
              >
                📦 SMS Inventory Health
              </button>
            </div>
          </div>

          {/* Chat Messages and Input Stream */}
          <div className="lg:col-span-3 flex flex-col rounded-xl border border-slate-800 bg-slate-900/60 h-[650px]">
            {/* Session Top Bar */}
            <div className="px-4 py-3 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
              <div>
                <div className="font-semibold text-white text-sm">
                  {sessionDetail?.conversation.title || 'Interactive Assistant Simulator'}
                </div>
                <div className="text-xs text-slate-500">
                  {sessionDetail ? `Turns: ${sessionDetail.conversation.totalTurnCount} • Tokens: ${sessionDetail.conversation.totalTokensUsed}` : 'New Unsaved Session'}
                </div>
              </div>

              {sessionDetail && (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => handleExport(sessionDetail.conversation.id, 'markdown')}
                    className="rounded bg-slate-800 px-2 py-1 text-xs text-slate-300 hover:bg-slate-700"
                  >
                    Export
                  </button>
                </div>
              )}
            </div>

            {/* Messages Scroll Area */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {!sessionDetail || sessionDetail.messages.length === 0 ? (
                <div className="h-full flex items-center justify-center text-center">
                  <div className="max-w-md space-y-2">
                    <div className="text-3xl">🤖</div>
                    <div className="text-sm font-semibold text-slate-200">CRANIAL Assistant Ready</div>
                    <p className="text-xs text-slate-400">
                      Type a prompt below or pick a quick test scenario to inspect structured UI responses, slang normalization, and money management safety checks.
                    </p>
                  </div>
                </div>
              ) : (
                sessionDetail.messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex flex-col ${msg.role === 'USER' ? 'items-end' : 'items-start'}`}
                  >
                    <div
                      className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm shadow-md ${
                        msg.role === 'USER'
                          ? 'bg-indigo-600 text-white rounded-br-none'
                          : msg.isSafetyViolation
                          ? 'bg-rose-950/70 border border-rose-800/80 text-rose-100 rounded-bl-none'
                          : 'bg-slate-800 text-slate-100 rounded-bl-none border border-slate-700'
                      }`}
                    >
                      {/* Role Header */}
                      <div className="flex items-center justify-between gap-4 mb-1 text-[11px] opacity-75">
                        <span className="font-semibold uppercase tracking-wider">
                          {msg.role === 'USER' ? 'User' : msg.isSafetyViolation ? 'Safety Interception' : 'Cranial Assistant'}
                        </span>
                        {msg.latencyMs && <span>{msg.latencyMs}ms</span>}
                      </div>

                      {/* Content */}
                      <p className="leading-relaxed whitespace-pre-wrap">{msg.content}</p>

                      {/* Raw Content Badge if slang normalized */}
                      {msg.rawContent && msg.rawContent !== msg.content && (
                        <div className="mt-2 text-xs border-t border-slate-700/60 pt-1 text-slate-300">
                          <span className="text-slate-400">Raw Input: </span>
                          <span className="italic">{msg.rawContent}</span>
                        </div>
                      )}

                      {/* Structured Response Blocks */}
                      {msg.structuredBlocks?.map((block, bIdx) => renderStructuredBlock(block, bIdx))}

                      {/* Grounded Source References */}
                      {msg.sourceReferences && msg.sourceReferences.length > 0 && (
                        <div className="mt-3 border-t border-slate-700/60 pt-2 space-y-1">
                          <div className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">
                            {t('conversations.chat.sourcesTitle')}
                          </div>
                          {msg.sourceReferences.map((src, sIdx) => (
                            <div key={sIdx} className="rounded bg-slate-900/60 p-2 text-xs border border-slate-800">
                              <div className="font-medium text-emerald-300">{src.title}</div>
                              <p className="text-slate-400 text-[11px] mt-0.5 line-clamp-2">{src.snippet}</p>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Assistant Feedback Controls */}
                      {msg.role === 'ASSISTANT' && (
                        <div className="mt-3 flex items-center justify-end gap-2 text-xs border-t border-slate-700/40 pt-2 opacity-80 hover:opacity-100">
                          <button
                            type="button"
                            onClick={() => {
                              setFeedbackMessageId(msg.id);
                              handleFeedbackSubmit(true);
                            }}
                            className="hover:text-emerald-400 flex items-center gap-1"
                          >
                            👍 {t('conversations.chat.feedbackHelpful')}
                          </button>
                          <span>•</span>
                          <button
                            type="button"
                            onClick={() => {
                              setFeedbackMessageId(msg.id);
                              handleFeedbackSubmit(false);
                            }}
                            className="hover:text-rose-400 flex items-center gap-1"
                          >
                            👎 {t('conversations.chat.feedbackUnhelpful')}
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Input Box */}
            <div className="p-3 border-t border-slate-800 bg-slate-950/60">
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleSendMessage();
                }}
                className="flex gap-2"
              >
                <input
                  type="text"
                  placeholder={t('conversations.chat.inputPlaceholder')}
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  disabled={sendingMessage}
                  className="flex-1 rounded-lg bg-slate-900 border border-slate-700 px-4 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 disabled:opacity-50"
                />
                <button
                  type="submit"
                  disabled={sendingMessage || !chatInput.trim()}
                  className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 transition"
                >
                  {sendingMessage ? '...' : t('conversations.chat.send')}
                </button>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 3: SLANG NORMALIZER */}
      {activeSubTab === 'slang' && (
        <div className="space-y-6 max-w-4xl">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4 shadow-lg">
            <div>
              <label className="text-sm font-semibold text-white block mb-1">
                {t('conversations.slang.inputLabel')}
              </label>
              <textarea
                rows={3}
                value={slangInput}
                onChange={(e) => setSlangInput(e.target.value)}
                className="w-full rounded-lg bg-slate-950 border border-slate-700 p-3 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleNormalizeSlang}
                disabled={normalizingSlang || !slangInput.trim()}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 transition"
              >
                {normalizingSlang ? 'Normalizing...' : t('conversations.slang.normalizeBtn')}
              </button>
              <button
                type="button"
                onClick={() => setSlangInput('Gaji saya 8jt per bulan, makan gocap sehari, kosan 2jt, beli jajan ceban.')}
                className="rounded-lg bg-slate-800 px-3 py-2 text-xs text-slate-300 hover:bg-slate-700"
              >
                Sample 1 (Currency)
              </button>
              <button
                type="button"
                onClick={() => setSlangInput('Bulan ini boncos banget, tolong balikin duit pesanan yang saya batalin.')}
                className="rounded-lg bg-slate-800 px-3 py-2 text-xs text-slate-300 hover:bg-slate-700"
              >
                Sample 2 (Colloquialism)
              </button>
            </div>
          </div>

          {slangResult && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4 shadow-lg">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold text-white">
                  {t('conversations.slang.normalizedOutput')}
                </h3>
                <span className="rounded-full bg-indigo-950 border border-indigo-500/50 px-2.5 py-0.5 text-xs text-indigo-300">
                  {slangResult.replacementsCount} Replacements
                </span>
              </div>

              <div className="rounded-lg bg-slate-950 border border-slate-800 p-4 text-emerald-400 font-mono text-sm leading-relaxed">
                {slangResult.normalizedText}
              </div>

              {slangResult.tokensDetected.length > 0 && (
                <div className="space-y-2 pt-2">
                  <div className="text-xs font-semibold uppercase text-slate-400">
                    {t('conversations.slang.tokensTable')}
                  </div>
                  <div className="overflow-x-auto rounded-lg border border-slate-800">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-slate-950 text-slate-400">
                        <tr>
                          <th className="px-3 py-2">Original Slang</th>
                          <th className="px-3 py-2">Standard Replacement</th>
                          <th className="px-3 py-2">Category</th>
                          <th className="px-3 py-2">Explanation</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800 text-slate-300">
                        {slangResult.tokensDetected.map((token, i) => (
                          <tr key={i} className="hover:bg-slate-800/40">
                            <td className="px-3 py-2 font-mono text-rose-300">{token.originalToken}</td>
                            <td className="px-3 py-2 font-mono text-emerald-300 font-medium">{token.normalizedToken}</td>
                            <td className="px-3 py-2 text-slate-400">{token.category}</td>
                            <td className="px-3 py-2 text-slate-400">{token.explanation}</td>
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

      {/* SUB-TAB 4: NON-BANKING SAFETY GUARD */}
      {activeSubTab === 'safety' && (
        <div className="space-y-6 max-w-4xl">
          {/* Policy Law Banner */}
          <div className="rounded-xl border border-rose-500/50 bg-rose-950/20 p-5 space-y-2">
            <div className="flex items-center gap-2 font-bold text-rose-300 text-base">
              <span>🛡️</span>
              <span>{t('conversations.safety.bannerTitle')}</span>
            </div>
            <p className="text-sm text-rose-200/90 leading-relaxed">
              {t('conversations.safety.bannerText')}
            </p>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <h3 className="text-base font-semibold text-white">Non-Banking Safety Invariant Rules</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
              <div className="rounded-lg border border-slate-800 bg-slate-950/60 p-3 space-y-1">
                <div className="font-semibold text-rose-400">⛔ Intercepted Banking Verbs</div>
                <ul className="list-disc pl-4 text-slate-300 space-y-1">
                  <li>Direct fund transfers (wire money, transfer ke rekening BCA)</li>
                  <li>Direct bill/payment execution (bayar listrik sekarang, pay bill now)</li>
                  <li>Account opening & card issuance (buka rekening, issue debit card)</li>
                  <li>ATM cash withdrawals (tarik tunai ATM, withdraw cash)</li>
                </ul>
              </div>

              <div className="rounded-lg border border-slate-800 bg-slate-950/60 p-3 space-y-1">
                <div className="font-semibold text-emerald-400">✅ Permitted Money Management Verbs</div>
                <ul className="list-disc pl-4 text-slate-300 space-y-1">
                  <li>50/30/20 budget framework analysis & savings calculation</li>
                  <li>Historical expense ledger categorization & OCR verification</li>
                  <li>Store performance analytics & deadstock detection</li>
                  <li>Cash flow surplus projection & debt payoff strategy</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 5: RETENTION & GOVERNANCE */}
      {activeSubTab === 'retention' && (
        <div className="space-y-6 max-w-5xl">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-semibold text-white">
              {t('conversations.retention.tableTitle')}
            </h3>
            <button
              type="button"
              onClick={handlePruneNow}
              disabled={pruning}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 transition"
            >
              {pruning ? 'Pruning...' : t('conversations.retention.pruneNow')}
            </button>
          </div>

          {pruneResult && (
            <div className="rounded-lg bg-emerald-950/40 border border-emerald-500/40 p-3 text-emerald-300 text-xs">
              {pruneResult}
            </div>
          )}

          <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/60 shadow-lg">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-950/80 text-xs uppercase text-slate-400">
                <tr>
                  <th className="px-4 py-3">Application</th>
                  <th className="px-4 py-3">{t('conversations.retention.days')}</th>
                  <th className="px-4 py-3">{t('conversations.retention.autoPrune')}</th>
                  <th className="px-4 py-3">{t('conversations.retention.defaultLocale')}</th>
                  <th className="px-4 py-3">{t('conversations.retention.maxTurns')}</th>
                  <th className="px-4 py-3">{t('conversations.retention.maxTokens')}</th>
                  <th className="px-4 py-3">{t('conversations.retention.safetyGuard')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {retentionPolicies.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-800/40">
                    <td className="px-4 py-3 font-medium text-white">{p.applicationName}</td>
                    <td className="px-4 py-3 text-xs font-mono">{p.retentionDays} days</td>
                    <td className="px-4 py-3 text-xs">
                      <span className={`px-2 py-0.5 rounded text-[11px] font-semibold ${p.autoPruneEnabled ? 'bg-emerald-950 text-emerald-400' : 'bg-slate-800 text-slate-400'}`}>
                        {p.autoPruneEnabled ? 'ENABLED' : 'DISABLED'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs font-mono uppercase">{p.defaultLocale}</td>
                    <td className="px-4 py-3 text-xs">{p.maxContextTurns} turns</td>
                    <td className="px-4 py-3 text-xs">{p.maxContextTokens} tokens</td>
                    <td className="px-4 py-3 text-xs">
                      <span className="px-2 py-0.5 rounded bg-rose-950 border border-rose-800/50 text-rose-300 text-[11px] font-semibold">
                        ENFORCED
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* SUB-TAB 6: BENCHMARKS */}
      {activeSubTab === 'benchmarks' && benchmarks && (
        <div className="space-y-6 max-w-5xl">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
              <div className="text-xs text-slate-400">Total Conversations</div>
              <div className="text-2xl font-bold text-white mt-1">{benchmarks.totalConversations}</div>
              <div className="text-xs text-slate-500 mt-0.5">{benchmarks.totalMessages} total turns</div>
            </div>

            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
              <div className="text-xs text-slate-400">Average Turn Latency</div>
              <div className="text-2xl font-bold text-emerald-400 mt-1">{Math.round(benchmarks.averageLatencyMs)}ms</div>
              <div className="text-xs text-emerald-500/80 mt-0.5">Local engine & fast rules</div>
            </div>

            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
              <div className="text-xs text-slate-400">Safety Violations Intercepted</div>
              <div className="text-2xl font-bold text-rose-400 mt-1">{benchmarks.totalSafetyViolationsIntercepted}</div>
              <div className="text-xs text-rose-500/80 mt-0.5">100% money management policy</div>
            </div>

            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
              <div className="text-xs text-slate-400">Slang Expressions Normalized</div>
              <div className="text-2xl font-bold text-indigo-400 mt-1">{benchmarks.totalSlangExpressionsNormalized}</div>
              <div className="text-xs text-indigo-400/80 mt-0.5">Indonesian colloquialisms</div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmDialog
        isOpen={!!deleteTargetId}
        title="Delete Conversation Session"
        message="Are you sure you want to delete this conversation session? All message turn history will be purged."
        confirmLabel="Delete Session"
        variant="danger"
        onConfirm={handleDelete}
        onCancel={() => setDeleteTargetId(null)}
      />
    </div>
  );
}

