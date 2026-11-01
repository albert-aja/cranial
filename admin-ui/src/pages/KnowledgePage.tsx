import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  ChunkPreviewComparisonResult,
  GlossaryLinkResult,
  GlossaryTermView,
  KnowledgeAnswerResult,
  KnowledgeCollectionView,
  KnowledgeConnectorSyncResult,
  KnowledgeConnectorView,
  KnowledgeEvalResult,
  KnowledgeExportBundle,
  KnowledgeFeedbackView,
  KnowledgeRetrievalResult,
  KnowledgeSourceEventView,
  KnowledgeSourceView,
  KnowledgeSynonymView,
  PromotableDocumentView,
} from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';
import { ErrorExplanationBanner } from '../components/ErrorExplanationBanner';

type SubTab =
  | 'collections'
  | 'sources'
  | 'chunkPreview'
  | 'debugger'
  | 'glossary'
  | 'connectors'
  | 'freshness'
  | 'benchmarks';

export default function KnowledgePage() {
  const { language, t } = useLanguage();
  const isId = language === 'id';

  const [subTab, setSubTab] = useState<SubTab>('collections');
  const [collections, setCollections] = useState<KnowledgeCollectionView[]>([]);
  const [selectedCollectionId, setSelectedCollectionId] = useState<string>('');
  const [sources, setSources] = useState<KnowledgeSourceView[]>([]);
  const [sourceHistory, setSourceHistory] = useState<KnowledgeSourceEventView[] | null>(null);
  const [historySourceCode, setHistorySourceCode] = useState<string>('');
  const [staleSources, setStaleSources] = useState<KnowledgeSourceView[]>([]);
  const [glossaryTerms, setGlossaryTerms] = useState<GlossaryTermView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);

  // 4-step Collection Creation Wizard state with HNSW tuning
  const [showWizard, setShowWizard] = useState(false);
  const [wizardStep, setWizardStep] = useState(1);
  const [wizCode, setWizCode] = useState('');
  const [wizName, setWizName] = useState('');
  const [wizDesc, setWizDesc] = useState('');
  const [wizType, setWizType] = useState<'GENERAL_RAG' | 'SOP_MANUAL' | 'FAQ_POLICY' | 'GLOSSARY_ENCYCLOPEDIA'>('GENERAL_RAG');
  const [wizScope, setWizScope] = useState<'APPLICATION' | 'TENANT' | 'GLOBAL_BENCHMARK'>('APPLICATION');
  const [wizRoles, setWizRoles] = useState('');
  const [wizChunkStrategy, setWizChunkStrategy] = useState<'PARAGRAPH' | 'MARKDOWN_SECTION' | 'FAQ_QA' | 'GLOSSARY_ENTRY' | 'FIXED_WINDOW'>('MARKDOWN_SECTION');
  const [wizChunkSize, setWizChunkSize] = useState(600);
  const [wizChunkOverlap, setWizChunkOverlap] = useState(80);
  const [wizHnswM, setWizHnswM] = useState(16);
  const [wizHnswEfConstruction, setWizHnswEfConstruction] = useState(64);
  const [wizHnswEfSearch, setWizHnswEfSearch] = useState(40);
  const [wizRerank, setWizRerank] = useState(true);
  const [wizFreshnessDays, setWizFreshnessDays] = useState(90);
  const [wizRetentionDays, setWizRetentionDays] = useState<string>('');

  // Inline HNSW Tuning Modal state
  const [tuningCol, setTuningCol] = useState<KnowledgeCollectionView | null>(null);
  const [tuningM, setTuningM] = useState(16);
  const [tuningEfCons, setTuningEfCons] = useState(64);
  const [tuningEfSearch, setTuningEfSearch] = useState(40);

  // Export / Import state
  const [exportJsonText, setExportJsonText] = useState<string | null>(null);
  const [importJsonInput, setImportJsonInput] = useState('');
  const [importTargetCode, setImportTargetCode] = useState('');

  // Source ingestion state
  const [srcCode, setSrcCode] = useState('budget_rule_guide');
  const [srcTitle, setSrcTitle] = useState('50-30-20 Monthly Budgeting Rule');
  const [srcUri, setSrcUri] = useState('cranial://peggy/guides/budget-rule');
  const [srcContentType, setSrcContentType] = useState<'MARKDOWN' | 'PLAIN_TEXT' | 'FAQ' | 'GLOSSARY' | 'POLICY_SOP'>('MARKDOWN');
  const [srcPermScope, setSrcPermScope] = useState<'PUBLIC' | 'AUTHENTICATED' | 'TENANT_MEMBERS' | 'ROLE_RESTRICTED' | 'OWNER_ONLY'>('AUTHENTICATED');
  const [srcAllowedRoles, setSrcAllowedRoles] = useState('MEMBER,OWNER');
  const [srcLocaleMeta, setSrcLocaleMeta] = useState('en');
  const [srcSimFail, setSrcSimFail] = useState(false);
  const [srcContent, setSrcContent] = useState(
    '[Page 1]\n# 50-30-20 Household Rule\nAllocate 50% of net take-home income to essentials, 30% to planned lifestyle wants, and 20% to savings or emergency fund reserves.'
  );

  // Document Promotion state
  const [promotableDocs, setPromotableDocs] = useState<PromotableDocumentView[]>([]);
  const [showPromoteModal, setShowPromoteModal] = useState(false);
  const [selectedPromoteDocId, setSelectedPromoteDocId] = useState('');
  const [promoteSourceCode, setPromoteSourceCode] = useState('');
  const [promoteTitle, setPromoteTitle] = useState('');
  const [promotePermScope, setPromotePermScope] = useState('AUTHENTICATED');
  const [promoteRoles, setPromoteRoles] = useState('MEMBER,OWNER');
  const [promoteForceReindex, setPromoteForceReindex] = useState(false);

  // Chunk Previewer state
  const [previewContent, setPreviewContent] = useState(
    `[Page 1]\n# SOP Penerimaan Barang Masuk\nPetugas gudang wajib mencocokkan nomor Surat Jalan fisik dengan pesanan pembelian sebelum membongkar muatan truk.\n\n[Page 2]\n## Penanganan Barang Rusak\nJika ditemukan kemasan penyok atau segel terbuka, foto barang pada area karantina dan catat selisih kuantitas pada formulir pemeriksaan gudang.`
  );
  const [previewContentType, setPreviewContentType] = useState('MARKDOWN');
  const [previewLeftStrat, setPreviewLeftStrat] = useState('MARKDOWN_SECTION');
  const [previewLeftSize, setPreviewLeftSize] = useState(600);
  const [previewLeftOverlap, setPreviewLeftOverlap] = useState(80);
  const [previewRightStrat, setPreviewRightStrat] = useState('FIXED_WINDOW');
  const [previewRightSize, setPreviewRightSize] = useState(400);
  const [previewRightOverlap, setPreviewRightOverlap] = useState(60);
  const [previewResult, setPreviewResult] = useState<ChunkPreviewComparisonResult | null>(null);
  const [previewRunning, setPreviewRunning] = useState(false);

  // Retrieval Debugger & Grounded Ask state
  const [dbgCollectionCode, setDbgCollectionCode] = useState('ops_sop_handbook_ref');
  const [dbgQuery, setDbgQuery] = useState('Apa prosedur jika ditemukan barang rusak atau kemasan penyok saat penerimaan gudang?');
  const [dbgMode, setDbgMode] = useState<'HYBRID' | 'VECTOR' | 'KEYWORD'>('HYBRID');
  const [dbgRerank, setDbgRerank] = useState(true);
  const [dbgExpandParent, setDbgExpandParent] = useState(true);
  const [dbgRoles, setDbgRoles] = useState('WORKER');
  const [dbgLocaleFilter, setDbgLocaleFilter] = useState('');
  const [dbgTopK] = useState(4);
  const [retrievalResult, setRetrievalResult] = useState<KnowledgeRetrievalResult | null>(null);
  const [answerResult, setAnswerResult] = useState<KnowledgeAnswerResult | null>(null);

  // Feedback state
  const [feedbacks, setFeedbacks] = useState<KnowledgeFeedbackView[]>([]);
  const [fbFilterRating, setFbFilterRating] = useState('ALL');
  const [fbSubmittedMsg, setFbSubmittedMsg] = useState<string | null>(null);

  // Glossary & Synonym state
  const [sampleLinkText, setSampleLinkText] = useState(
    'Pastikan Dana Darurat terpenuhi menggunakan Anggaran 50-30-20 agar terlindung dari Inflasi serta menjaga Cash Flow bulanan.'
  );
  const [linkResult, setLinkResult] = useState<GlossaryLinkResult | null>(null);

  // Synonyms Dictionary state
  const [synonyms, setSynonyms] = useState<KnowledgeSynonymView[]>([]);
  const [newSynCanonical, setNewSynCanonical] = useState('');
  const [newSynAliases, setNewSynAliases] = useState('');
  const [newSynDomain, setNewSynDomain] = useState('money_management');

  // Connectors state
  const [connectors, setConnectors] = useState<KnowledgeConnectorView[]>([]);
  const [newConnCode, setNewConnCode] = useState('peggy_budget_faq_live');
  const [newConnName, setNewConnName] = useState('Peggy Sinking Fund Live FAQ Connector');
  const [newConnType, setNewConnType] = useState('INTERNAL_SAMPLE');
  const [newConnUri, setNewConnUri] = useState('cranial://sample-feed/peggy-budget-faq-v1');
  const [newConnTargetSource, setNewConnTargetSource] = useState('peggy_faq_feed_live');
  const [newConnInterval, setNewConnInterval] = useState(60);
  const [syncResult, setSyncResult] = useState<KnowledgeConnectorSyncResult | null>(null);

  // Benchmarks & Retention state
  const [evalResult, setEvalResult] = useState<KnowledgeEvalResult | null>(null);
  const [purgeResult, setPurgeResult] = useState<Record<string, unknown> | null>(null);

  const loadCollections = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await api.listKnowledgeCollections();
      const safe = Array.isArray(resp.data) ? resp.data : [];
      setCollections(safe);
      if (safe.length > 0 && !selectedCollectionId) {
        setSelectedCollectionId(safe[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCollections();
  }, []);

  useEffect(() => {
    if (!selectedCollectionId) return;
    if (subTab === 'sources') {
      api.listKnowledgeSources(selectedCollectionId)
        .then((r) => setSources(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
      api.listPromotableDocuments()
        .then((r) => setPromotableDocs(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
    } else if (subTab === 'glossary') {
      api.listGlossaryTerms(selectedCollectionId)
        .then((r) => setGlossaryTerms(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
      api.listSynonyms(selectedCollectionId)
        .then((r) => setSynonyms(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
    } else if (subTab === 'connectors') {
      api.listConnectors(selectedCollectionId)
        .then((r) => setConnectors(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
    }
  }, [selectedCollectionId, subTab]);

  useEffect(() => {
    if (subTab === 'freshness') {
      api.listStaleKnowledgeSources()
        .then((r) => setStaleSources(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
    } else if (subTab === 'benchmarks') {
      api.listKnowledgeFeedback(undefined, fbFilterRating)
        .then((r) => setFeedbacks(Array.isArray(r.data) ? r.data : []))
        .catch(() => {});
    }
  }, [subTab, fbFilterRating]);

  const handleCreateCollection = async () => {
    setError(null);
    try {
      const resp = await api.createKnowledgeCollection({
        collection_code: wizCode.trim(),
        name: wizName.trim(),
        description: wizDesc.trim(),
        collection_type: wizType,
        visibility_scope: wizScope,
        default_chunk_strategy: wizChunkStrategy,
        chunk_size_chars: wizChunkSize,
        chunk_overlap_chars: wizChunkOverlap,
        hnsw_m: wizHnswM,
        hnsw_ef_construction: wizHnswEfConstruction,
        hnsw_ef_search: wizHnswEfSearch,
        rerank_by_default: wizRerank,
        freshness_ttl_days: wizFreshnessDays,
        retention_days: wizRetentionDays ? Number(wizRetentionDays) : null,
        allowed_roles: wizRoles
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
      });
      const created = resp.data;
      if (!created) return;
      setShowWizard(false);
      setWizardStep(1);
      setWizCode('');
      setWizName('');
      setBanner(
        isId
          ? `Koleksi '${created.collection_code}' berhasil dibuat (HNSW: m=${created.hnsw_m}, ef_cons=${created.hnsw_ef_construction}).`
          : `Collection '${created.collection_code}' created (HNSW: m=${created.hnsw_m}, ef_cons=${created.hnsw_ef_construction}).`
      );
      await loadCollections();
      setSelectedCollectionId(created.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleSaveHnswTuning = async () => {
    if (!tuningCol) return;
    setError(null);
    try {
      const resp = await api.updateHnswTuning(tuningCol.id, {
        hnsw_m: tuningM,
        hnsw_ef_construction: tuningEfCons,
        hnsw_ef_search: tuningEfSearch,
      });
      setBanner(
        isId
          ? `Parameter HNSW untuk '${resp.data?.collection_code}' berhasil diperbarui (m=${resp.data?.hnsw_m}, ef_search=${resp.data?.hnsw_ef_search}).`
          : `HNSW parameters updated for '${resp.data?.collection_code}' (m=${resp.data?.hnsw_m}, ef_search=${resp.data?.hnsw_ef_search}).`
      );
      setTuningCol(null);
      await loadCollections();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleExport = async (collectionId: string) => {
    try {
      const resp = await api.exportKnowledgeCollection(collectionId);
      setExportJsonText(JSON.stringify(resp.data, null, 2));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleImport = async () => {
    setError(null);
    try {
      const parsed = JSON.parse(importJsonInput) as KnowledgeExportBundle;
      const resp = await api.importKnowledgeCollection(parsed, importTargetCode.trim() || undefined);
      const imported = resp.data;
      setBanner(
        isId
          ? `Bundel pengetahuan berhasil diimpor ke '${imported?.collection_code ?? ''}'.`
          : `Imported knowledge bundle into '${imported?.collection_code ?? ''}'.`
      );
      setImportJsonInput('');
      setImportTargetCode('');
      await loadCollections();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleIngestSource = async () => {
    if (!selectedCollectionId) return;
    setError(null);
    try {
      const resp = await api.ingestKnowledgeSource(selectedCollectionId, {
        source_code: srcCode.trim(),
        title: srcTitle.trim(),
        source_uri: srcUri.trim(),
        content_type: srcContentType,
        content: srcContent,
        permission_scope: srcPermScope,
        allowed_roles: srcAllowedRoles
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
        metadata: { locale: srcLocaleMeta },
        simulate_failure: srcSimFail,
      });
      const res = resp.data;
      if (res) {
        setBanner(
          `${res.source_code} v${res.version}: ${res.indexing_status} (${res.last_index_outcome || 'OK'} | +${res.last_chunks_added} / =${res.last_chunks_reused} / -${res.last_chunks_removed})`
        );
      }
      const updated = await api.listKnowledgeSources(selectedCollectionId);
      setSources(Array.isArray(updated.data) ? updated.data : []);
      await loadCollections();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handlePromoteDocument = async () => {
    if (!selectedCollectionId || !selectedPromoteDocId) return;
    setError(null);
    try {
      const resp = await api.promoteDocumentToKnowledge(selectedCollectionId, selectedPromoteDocId, {
        source_code: promoteSourceCode.trim() || undefined,
        title: promoteTitle.trim() || undefined,
        permission_scope: promotePermScope,
        allowed_roles: promoteRoles.split(',').map((s) => s.trim()).filter(Boolean),
        force_reindex: promoteForceReindex,
      });
      const s = resp.data;
      setShowPromoteModal(false);
      setBanner(
        isId
          ? `Dokumen berhasil dipromosikan ke sumber '${s?.source_code}' (${s?.chunk_count} chunk, sitasi [Page N] tersimpan).`
          : `Document promoted into source '${s?.source_code}' (${s?.chunk_count} chunks, [Page N] citations preserved).`
      );
      const updated = await api.listKnowledgeSources(selectedCollectionId);
      setSources(Array.isArray(updated.data) ? updated.data : []);
      await loadCollections();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleInspectHistory = async (s: KnowledgeSourceView) => {
    setLoading(true);
    setError(null);
    try {
      setHistorySourceCode(s.source_code);
      const resp = await api.getKnowledgeSourceHistory(s.id);
      setSourceHistory(Array.isArray(resp.data) ? resp.data : []);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load source history');
    } finally {
      setLoading(false);
    }
  };

  const handleSourceAction = async (action: 'reindex' | 'revoke', s: KnowledgeSourceView) => {
    setLoading(true);
    setError(null);
    try {
      if (action === 'reindex') {
        await api.reindexKnowledgeSource(s.id);
        setBanner(isId ? `Sumber ${s.source_code} berhasil di-indeks ulang.` : `Source ${s.source_code} successfully reindexed.`);
      } else {
        await api.revokeKnowledgeSource(s.id);
        setBanner(isId ? `Sumber ${s.source_code} berhasil dicabut.` : `Source ${s.source_code} revoked.`);
      }
      if (selectedCollectionId) {
        const updated = await api.listKnowledgeSources(selectedCollectionId);
        setSources(Array.isArray(updated.data) ? updated.data : []);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : `Failed to ${action} source`);
    } finally {
      setLoading(false);
    }
  };

  const handleRunChunkPreview = async () => {
    setPreviewRunning(true);
    setError(null);
    try {
      const resp = await api.previewChunkBoundaries({
        content: previewContent,
        content_type: previewContentType,
        left_strategy: previewLeftStrat,
        left_chunk_size_chars: previewLeftSize,
        left_chunk_overlap_chars: previewLeftOverlap,
        right_strategy: previewRightStrat,
        right_chunk_size_chars: previewRightSize,
        right_chunk_overlap_chars: previewRightOverlap,
      });
      setPreviewResult(resp.data ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setPreviewRunning(false);
    }
  };

  const handleRunRetrieve = async () => {
    setError(null);
    setAnswerResult(null);
    try {
      const filters: Record<string, unknown> = {};
      if (dbgLocaleFilter.trim()) filters.locale = dbgLocaleFilter.trim();
      const resp = await api.debugKnowledgeRetrieve({
        collection_code: dbgCollectionCode,
        query: dbgQuery,
        retrieval_mode: dbgMode,
        rerank: dbgRerank,
        top_k: dbgTopK,
        expand_parent_context: dbgExpandParent,
        metadata_filters: filters,
        simulated_roles: dbgRoles
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
        debug: true,
      });
      setRetrievalResult(resp.data ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleRunAsk = async () => {
    setError(null);
    setFbSubmittedMsg(null);
    try {
      const filters: Record<string, unknown> = {};
      if (dbgLocaleFilter.trim()) filters.locale = dbgLocaleFilter.trim();
      const resp = await api.debugKnowledgeAsk({
        collection_code: dbgCollectionCode,
        question: dbgQuery,
        locale: language,
        retrieval_mode: dbgMode,
        rerank: dbgRerank,
        top_k: dbgTopK,
        expand_parent_context: dbgExpandParent,
        metadata_filters: filters,
        simulated_roles: dbgRoles
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
        debug: true,
      });
      setAnswerResult(resp.data ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleFeedbackSubmit = async (rating: 'HELPFUL' | 'UNHELPFUL' | 'CITATION_MISMATCH' | 'MISSING_INFO') => {
    if (!answerResult) return;
    try {
      const citedCodes = (answerResult.citations || []).map((c) => c.source_code);
      await api.submitKnowledgeFeedback({
        collection_code: dbgCollectionCode,
        question_text: answerResult.question,
        answer_text: answerResult.answer,
        rating,
        cited_source_codes: citedCodes,
        comment: isId ? `Umpan balik operator via debugger (${rating})` : `Operator debugger feedback (${rating})`,
      });
      setFbSubmittedMsg(
        isId
          ? `Umpan balik '${rating}' dicatat di antrean evaluasi.`
          : `Feedback '${rating}' recorded in evaluation queue.`
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleCreateSynonym = async () => {
    if (!newSynCanonical.trim() || !newSynAliases.trim()) return;
    setError(null);
    try {
      const synList = newSynAliases.split(',').map((s) => s.trim()).filter(Boolean);
      await api.createSynonym({
        collection_id: selectedCollectionId || undefined,
        canonical_term: newSynCanonical.trim(),
        synonyms: synList,
        locale_pair: 'en_id',
        domain_tag: newSynDomain.trim(),
      });
      setNewSynCanonical('');
      setNewSynAliases('');
      const updated = await api.listSynonyms(selectedCollectionId);
      setSynonyms(Array.isArray(updated.data) ? updated.data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleDeleteSynonym = async (id: string) => {
    try {
      await api.deleteSynonym(id);
      const updated = await api.listSynonyms(selectedCollectionId);
      setSynonyms(Array.isArray(updated.data) ? updated.data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleCreateConnector = async () => {
    if (!selectedCollectionId || !newConnCode.trim() || !newConnUri.trim()) return;
    setError(null);
    try {
      await api.createConnector({
        collection_id: selectedCollectionId,
        connector_code: newConnCode.trim(),
        name: newConnName.trim(),
        connector_type: newConnType,
        feed_uri: newConnUri.trim(),
        target_source_code: newConnTargetSource.trim(),
        poll_interval_minutes: newConnInterval,
      });
      setBanner(isId ? `Konektor '${newConnCode}' terdaftar.` : `Connector '${newConnCode}' registered.`);
      const updated = await api.listConnectors(selectedCollectionId);
      setConnectors(Array.isArray(updated.data) ? updated.data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleSyncConnector = async (id: string, force = false) => {
    setError(null);
    try {
      const resp = await api.syncConnector(id, force);
      setSyncResult(resp.data ?? null);
      const updated = await api.listConnectors(selectedCollectionId);
      setConnectors(Array.isArray(updated.data) ? updated.data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handlePromoteFeedbackToEval = async (fb: KnowledgeFeedbackView) => {
    try {
      await api.promoteFeedbackToEval(fb.id, {
        case_code: `eval_${fb.id.slice(0, 8)}`,
        name: `Feedback promotion: ${fb.question_text.slice(0, 60)}`,
        activate_immediately: true,
      });
      setBanner(
        isId
          ? `Kueri umpan balik dipromosikan ke kasus benchmark aktif!`
          : `Feedback query promoted to active benchmark case!`
      );
      const updated = await api.listKnowledgeFeedback(undefined, fbFilterRating);
      setFeedbacks(Array.isArray(updated.data) ? updated.data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const renderHighlightedText = (res: GlossaryLinkResult) => {
    if (!res.occurrences || res.occurrences.length === 0) {
      return <span>{res.text}</span>;
    }
    const pieces: React.ReactNode[] = [];
    let cursor = 0;
    res.occurrences.forEach((occ, idx) => {
      if (occ.start_offset > cursor) {
        pieces.push(<span key={`plain-${idx}`}>{res.text.slice(cursor, occ.start_offset)}</span>);
      }
      pieces.push(
        <mark
          key={`hit-${idx}`}
          title={`${occ.term}: ${occ.short_definition}`}
          className="rounded bg-indigo-500/30 border border-indigo-400/50 px-1.5 py-0.5 text-indigo-100 font-semibold cursor-help"
        >
          {res.text.slice(occ.start_offset, occ.end_offset)}
        </mark>
      );
      cursor = occ.end_offset;
    });
    if (cursor < res.text.length) {
      pieces.push(<span key="tail">{res.text.slice(cursor)}</span>);
    }
    return pieces;
  };

  const tabs: { id: SubTab; label: string }[] = [
    { id: 'collections', label: t('knowledge.tab.collections', 'Collections & Wizard') },
    { id: 'sources', label: t('knowledge.tab.sources', 'Sources & Versioning') },
    { id: 'chunkPreview', label: t('knowledge.tab.chunkPreview', 'Chunk Previewer') },
    { id: 'debugger', label: t('knowledge.tab.debugger', 'Retrieval Debugger & Ask') },
    { id: 'glossary', label: t('knowledge.tab.glossary', 'Glossary & Synonyms') },
    { id: 'connectors', label: t('knowledge.tab.connectors', 'Feed Connectors (ETag)') },
    { id: 'freshness', label: t('knowledge.tab.freshness', 'Freshness & Retention') },
    { id: 'benchmarks', label: t('knowledge.tab.benchmarks', 'Benchmarks & Feedback') },
  ];

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">
            {t('knowledge.title', 'Knowledge Intelligence & RAG')}
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            {t(
              'knowledge.subtitle',
              'Permission-aware hybrid BM25 + vector retrieval, incremental indexing, citations, bilingual glossary linking & IR evaluation'
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              setShowWizard(true);
              setSubTab('collections');
            }}
            className="rounded-lg bg-indigo-600 hover:bg-indigo-500 px-3.5 py-2 text-xs font-semibold text-white shadow"
          >
            + {isId ? 'Wizard Koleksi Baru' : 'New Collection Wizard'}
          </button>
          <button
            type="button"
            onClick={loadCollections}
            className="rounded-lg border border-slate-700 bg-slate-900 hover:bg-slate-800 px-3 py-2 text-xs font-medium text-slate-200"
          >
            {t('action.refreshAll', 'Refresh All')}
          </button>
        </div>
      </div>

      {/* Security & Domain Guardrail Notice */}
      <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 px-4 py-3 text-xs text-emerald-200 flex flex-wrap items-center justify-between gap-2">
        <span>
          {isId
            ? '🛡️ Keamanan Pra-Retrieval: Cakupan izin (OWNER_ONLY / ROLE_RESTRICTED / TENANT) difilter SEBELUM skor vektor dihitung. Konten pengetahuan bersifat edukasi pengelolaan uang & SOP (bukan transaksi perbankan).'
            : '🛡️ Pre-Retrieval Security: Permission scopes (OWNER_ONLY / ROLE_RESTRICTED / TENANT) are enforced BEFORE vector similarity scoring. Strictly read-only money-management & SOP knowledge.'}
        </span>
        <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-[11px] font-mono text-emerald-300">
          local-semantic-embed-v1 (0 cloud cost)
        </span>
      </div>

      {banner && (
        <div className="rounded-xl border border-blue-500/30 bg-blue-950/30 px-4 py-3 text-xs text-blue-200 flex justify-between items-center">
          <span>{banner}</span>
          <button type="button" onClick={() => setBanner(null)} className="text-blue-400 hover:text-white font-bold ml-4">
            ✕
          </button>
        </div>
      )}

      {error && (
        <ErrorExplanationBanner
          errorCode="KNOWLEDGE_RAG_ERROR"
          errorMessage={error}
          onRetry={() => {
            setError(null);
            void loadAll();
          }}
        />
      )}

      {/* Collection Quick Switcher */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-3 flex flex-wrap items-center gap-3">
        <span className="text-xs font-semibold text-slate-300">{isId ? 'Koleksi Aktif:' : 'Active Collection:'}</span>
        <select
          value={selectedCollectionId}
          onChange={(e) => setSelectedCollectionId(e.target.value)}
          className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-1 focus:ring-indigo-500 min-w-[280px]"
        >
          {collections.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.collection_code}) — {c.indexed_source_count} sources / {c.chunk_count} chunks [{c.vector_index_runtime_mode}]
            </option>
          ))}
        </select>
        {selectedCollectionId && (
          <span className="text-[11px] text-slate-400">
            {collections.find((c) => c.id === selectedCollectionId)?.default_chunk_strategy} •{' '}
            {collections.find((c) => c.id === selectedCollectionId)?.freshness_ttl_days}d TTL • HNSW (m=
            {collections.find((c) => c.id === selectedCollectionId)?.hnsw_m}, ef_cons=
            {collections.find((c) => c.id === selectedCollectionId)?.hnsw_ef_construction}, ef_search=
            {collections.find((c) => c.id === selectedCollectionId)?.hnsw_ef_search})
          </span>
        )}
      </div>

      {/* Navigation Tabs */}
      <div className="flex border-b border-slate-800 gap-1 overflow-x-auto">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setSubTab(tab.id)}
            className={`px-4 py-2 text-xs font-medium border-b-2 whitespace-nowrap transition-colors ${
              subTab === tab.id
                ? 'border-indigo-500 text-indigo-400 bg-indigo-500/10'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* TAB 1: COLLECTIONS & WIZARD & HNSW TUNING */}
      {subTab === 'collections' && (
        <div className="space-y-6">
          {showWizard && (
            <div className="rounded-xl border border-indigo-500/40 bg-slate-900/90 p-5 space-y-4">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3">
                <h2 className="text-sm font-semibold text-white">
                  {isId ? 'Wizard Koleksi Pengetahuan (Langkah ' : 'Knowledge Collection Wizard (Step '}
                  {wizardStep}/4)
                </h2>
                <button
                  type="button"
                  onClick={() => setShowWizard(false)}
                  className="text-xs text-slate-400 hover:text-white"
                >
                  ✕ {isId ? 'Tutup' : 'Close'}
                </button>
              </div>

              {wizardStep === 1 && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Kode Koleksi (Unik)' : 'Collection Code (Unique)'}
                    </label>
                    <input
                      type="text"
                      value={wizCode}
                      onChange={(e) => setWizCode(e.target.value)}
                      placeholder="peggy_family_handbook_v1"
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Nama Tampilan' : 'Display Name'}
                    </label>
                    <input
                      type="text"
                      value={wizName}
                      onChange={(e) => setWizName(e.target.value)}
                      placeholder="Peggy Family Budgeting Guide"
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div className="md:col-span-2">
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Deskripsi' : 'Description'}
                    </label>
                    <input
                      type="text"
                      value={wizDesc}
                      onChange={(e) => setWizDesc(e.target.value)}
                      placeholder="Financial literacy and budgeting guides for household planning"
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                </div>
              )}

              {wizardStep === 2 && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Tipe Koleksi' : 'Collection Type'}
                    </label>
                    <select
                      value={wizType}
                      onChange={(e) => setWizType(e.target.value as any)}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    >
                      <option value="GENERAL_RAG">GENERAL_RAG</option>
                      <option value="SOP_MANUAL">SOP_MANUAL</option>
                      <option value="FAQ_POLICY">FAQ_POLICY</option>
                      <option value="GLOSSARY_ENCYCLOPEDIA">GLOSSARY_ENCYCLOPEDIA</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Cakupan Visibilitas' : 'Visibility Scope'}
                    </label>
                    <select
                      value={wizScope}
                      onChange={(e) => setWizScope(e.target.value as any)}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    >
                      <option value="APPLICATION">APPLICATION</option>
                      <option value="TENANT">TENANT</option>
                      <option value="GLOBAL_BENCHMARK">GLOBAL_BENCHMARK</option>
                    </select>
                  </div>
                  <div className="md:col-span-2">
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Peran Diizinkan (Koma pisah)' : 'Allowed Roles (Comma separated)'}
                    </label>
                    <input
                      type="text"
                      value={wizRoles}
                      onChange={(e) => setWizRoles(e.target.value)}
                      placeholder="MEMBER,OWNER,WORKER"
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                </div>
              )}

              {wizardStep === 3 && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Strategi Chunk Default' : 'Default Chunk Strategy'}
                    </label>
                    <select
                      value={wizChunkStrategy}
                      onChange={(e) => setWizChunkStrategy(e.target.value as any)}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    >
                      <option value="MARKDOWN_SECTION">MARKDOWN_SECTION</option>
                      <option value="PARAGRAPH">PARAGRAPH</option>
                      <option value="FAQ_QA">FAQ_QA</option>
                      <option value="GLOSSARY_ENTRY">GLOSSARY_ENTRY</option>
                      <option value="FIXED_WINDOW">FIXED_WINDOW</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Ukuran Chunk (Karakter)' : 'Chunk Size (Chars)'}
                    </label>
                    <input
                      type="number"
                      value={wizChunkSize}
                      onChange={(e) => setWizChunkSize(Number(e.target.value))}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Overlap Chunk (Karakter)' : 'Chunk Overlap (Chars)'}
                    </label>
                    <input
                      type="number"
                      value={wizChunkOverlap}
                      onChange={(e) => setWizChunkOverlap(Number(e.target.value))}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">HNSW m (4-64)</label>
                    <input
                      type="number"
                      value={wizHnswM}
                      onChange={(e) => setWizHnswM(Number(e.target.value))}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">HNSW ef_construction (16-512)</label>
                    <input
                      type="number"
                      value={wizHnswEfConstruction}
                      onChange={(e) => setWizHnswEfConstruction(Number(e.target.value))}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">HNSW ef_search (10-500)</label>
                    <input
                      type="number"
                      value={wizHnswEfSearch}
                      onChange={(e) => setWizHnswEfSearch(Number(e.target.value))}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                </div>
              )}

              {wizardStep === 4 && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'TTL Kesegaran (Hari)' : 'Freshness TTL (Days)'}
                    </label>
                    <input
                      type="number"
                      value={wizFreshnessDays}
                      onChange={(e) => setWizFreshnessDays(Number(e.target.value))}
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">
                      {isId ? 'Retensi Keras (Hari, Opsional)' : 'Hard Retention (Days, Optional)'}
                    </label>
                    <input
                      type="number"
                      value={wizRetentionDays}
                      onChange={(e) => setWizRetentionDays(e.target.value)}
                      placeholder="Contoh: 180"
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                  <div className="md:col-span-2 flex items-center gap-2 mt-2">
                    <input
                      type="checkbox"
                      id="wizRerank"
                      checked={wizRerank}
                      onChange={(e) => setWizRerank(e.target.checked)}
                      className="rounded border-slate-700 bg-slate-800 text-indigo-600 focus:ring-0"
                    />
                    <label htmlFor="wizRerank" className="text-slate-300">
                      {isId
                        ? 'Aktifkan Reranking cross-feature secara default'
                        : 'Enable cross-feature Reranking by default'}
                    </label>
                  </div>
                </div>
              )}

              <div className="flex justify-between items-center border-t border-slate-800 pt-3">
                <button
                  type="button"
                  disabled={wizardStep === 1}
                  onClick={() => setWizardStep((s) => s - 1)}
                  className="rounded border border-slate-700 px-3 py-1.5 text-xs text-slate-300 disabled:opacity-40"
                >
                  ← {isId ? 'Sebelumnya' : 'Back'}
                </button>
                {wizardStep < 4 ? (
                  <button
                    type="button"
                    onClick={() => setWizardStep((s) => s + 1)}
                    className="rounded bg-indigo-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500"
                  >
                    {isId ? 'Berikutnya' : 'Next'} →
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleCreateCollection}
                    className="rounded bg-emerald-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-emerald-500"
                  >
                    ✓ {isId ? 'Buat Koleksi' : 'Create Collection'}
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Inline HNSW Tuning Modal */}
          {tuningCol && (
            <div className="rounded-xl border border-sky-500/40 bg-slate-900/95 p-5 space-y-4">
              <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                <h3 className="text-sm font-semibold text-sky-300">
                  ⚡ {isId ? 'Penalaan Indeks pgvector HNSW' : 'pgvector HNSW Index Tuning'}: {tuningCol.collection_code}
                </h3>
                <button type="button" onClick={() => setTuningCol(null)} className="text-xs text-slate-400 hover:text-white">✕</button>
              </div>
              <p className="text-xs text-slate-400">
                {isId
                  ? 'Konfigurasikan m (koneksi dua arah per node), ef_construction (kedalaman penelusuran saat membangun indeks), dan ef_search (kedalaman saat retrieval).'
                  : 'Tune m (bidirectional links per node), ef_construction (search depth during index build), and ef_search (search depth at retrieval time).'}
              </p>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                <div>
                  <label className="block text-slate-300 font-medium mb-1">hnsw.m (4 - 64)</label>
                  <input
                    type="number"
                    value={tuningM}
                    onChange={(e) => setTuningM(Number(e.target.value))}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  />
                </div>
                <div>
                  <label className="block text-slate-300 font-medium mb-1">hnsw.ef_construction (16 - 512)</label>
                  <input
                    type="number"
                    value={tuningEfCons}
                    onChange={(e) => setTuningEfCons(Number(e.target.value))}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  />
                </div>
                <div>
                  <label className="block text-slate-300 font-medium mb-1">hnsw.ef_search (10 - 500)</label>
                  <input
                    type="number"
                    value={tuningEfSearch}
                    onChange={(e) => setTuningEfSearch(Number(e.target.value))}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setTuningCol(null)} className="rounded border border-slate-700 px-3 py-1 text-xs text-slate-300">
                  {isId ? 'Batal' : 'Cancel'}
                </button>
                <button type="button" onClick={handleSaveHnswTuning} className="rounded bg-sky-600 px-4 py-1 text-xs font-semibold text-white hover:bg-sky-500">
                  {isId ? 'Terapkan Parameter HNSW' : 'Apply HNSW Tuning'}
                </button>
              </div>
            </div>
          )}

          {/* Collection Cards Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {collections.map((col) => (
              <div
                key={col.id}
                className={`rounded-xl border p-4 space-y-3 transition-all ${
                  col.id === selectedCollectionId
                    ? 'border-indigo-500/80 bg-slate-900/90 shadow-md shadow-indigo-950/40'
                    : 'border-slate-800 bg-slate-900/50 hover:border-slate-700'
                }`}
              >
                <div className="flex justify-between items-start gap-2">
                  <div>
                    <h3 className="text-sm font-semibold text-white">{col.name}</h3>
                    <div className="text-[11px] font-mono text-slate-400">{col.collection_code}</div>
                  </div>
                  <span className="rounded bg-slate-800 px-2 py-0.5 text-[10px] font-mono text-slate-300 uppercase">
                    {col.visibility_scope}
                  </span>
                </div>

                <p className="text-xs text-slate-400 line-clamp-2">{col.description || 'No description provided.'}</p>

                <div className="grid grid-cols-2 gap-2 text-[11px] bg-slate-950/40 p-2.5 rounded-lg border border-slate-800/80">
                  <div>
                    <span className="text-slate-500">{isId ? 'Sumber Terindeks' : 'Indexed Sources'}:</span>{' '}
                    <span className="text-slate-200 font-semibold">{col.indexed_source_count}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">{isId ? 'Total Chunk' : 'Total Chunks'}:</span>{' '}
                    <span className="text-slate-200 font-semibold">{col.chunk_count}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">{isId ? 'Glosarium' : 'Glossary'}:</span>{' '}
                    <span className="text-slate-200 font-semibold">{col.glossary_term_count}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">{isId ? 'Sumber Usang' : 'Stale Sources'}:</span>{' '}
                    <span className={`font-semibold ${col.stale_source_count > 0 ? 'text-amber-400' : 'text-emerald-400'}`}>
                      {col.stale_source_count}
                    </span>
                  </div>
                </div>

                <div className="text-[11px] text-slate-400 flex flex-wrap items-center justify-between gap-1 pt-1 border-t border-slate-800/60">
                  <span className="font-mono text-indigo-300 text-[10px]">{col.vector_index_runtime_mode}</span>
                  <span className="font-mono text-[10px] text-slate-400">
                    HNSW(m={col.hnsw_m}, ef_s={col.hnsw_ef_search})
                  </span>
                </div>

                <div className="flex justify-between items-center pt-2">
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedCollectionId(col.id);
                      setTuningCol(col);
                      setTuningM(col.hnsw_m);
                      setTuningEfCons(col.hnsw_ef_construction);
                      setTuningEfSearch(col.hnsw_ef_search);
                    }}
                    className="text-[11px] text-sky-400 hover:text-sky-300 font-medium"
                  >
                    ⚙️ {isId ? 'Tuning HNSW' : 'HNSW Tuning'}
                  </button>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => handleExport(col.id)}
                      className="text-[11px] text-slate-300 hover:text-white"
                    >
                      {isId ? 'Ekspor JSON' : 'Export JSON'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedCollectionId(col.id);
                        setSubTab('sources');
                      }}
                      className="rounded bg-indigo-600/80 hover:bg-indigo-600 px-2.5 py-1 text-[11px] font-medium text-white"
                    >
                      {isId ? 'Kelola Sumber' : 'Manage Sources'} →
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Export JSON modal / text view */}
          {exportJsonText && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/90 p-4 space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-xs font-semibold text-slate-200">
                  {isId ? 'Bundel Ekspor Pengetahuan (Portabel)' : 'Knowledge Export Bundle (Portable)'}
                </span>
                <button
                  type="button"
                  onClick={() => setExportJsonText(null)}
                  className="text-xs text-slate-400 hover:text-white"
                >
                  ✕ {isId ? 'Tutup' : 'Close'}
                </button>
              </div>
              <textarea
                readOnly
                value={exportJsonText}
                rows={8}
                className="w-full rounded border border-slate-700 bg-slate-950 p-2 text-[11px] font-mono text-slate-300"
              />
            </div>
          )}

          {/* Import JSON Box */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-slate-200">
              {isId ? 'Impor Koleksi Pengetahuan dari JSON' : 'Import Knowledge Collection from JSON'}
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="md:col-span-2">
                <textarea
                  value={importJsonInput}
                  onChange={(e) => setImportJsonInput(e.target.value)}
                  placeholder={
                    isId
                      ? 'Tempelkan objek JSON bundel ekspor koleksi di sini...'
                      : 'Paste exported knowledge collection JSON bundle here...'
                  }
                  rows={3}
                  className="w-full rounded border border-slate-700 bg-slate-800 p-2 text-xs text-slate-200 font-mono"
                />
              </div>
              <div className="space-y-2">
                <input
                  type="text"
                  value={importTargetCode}
                  onChange={(e) => setImportTargetCode(e.target.value)}
                  placeholder="Target code override (optional)"
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs text-white"
                />
                <button
                  type="button"
                  disabled={!importJsonInput.trim()}
                  onClick={handleImport}
                  className="w-full rounded bg-indigo-600 hover:bg-indigo-500 px-3 py-2 text-xs font-semibold text-white disabled:opacity-40"
                >
                  📥 {isId ? 'Impor & Indeks Bundel' : 'Import & Index Bundle'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: SOURCES & VERSIONING & DOCUMENT PROMOTION */}
      {subTab === 'sources' && (
        <div className="space-y-6">
          <div className="flex flex-wrap justify-between items-center gap-3 bg-slate-900/60 p-3 rounded-xl border border-slate-800">
            <div>
              <span className="text-xs text-slate-400">
                {isId ? 'Sumber untuk koleksi terpilih:' : 'Sources in selected collection:'}
              </span>{' '}
              <span className="text-xs font-bold text-white">
                {collections.find((c) => c.id === selectedCollectionId)?.name}
              </span>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setShowPromoteModal(true)}
                className="rounded-lg bg-teal-600 hover:bg-teal-500 px-3 py-1.5 text-xs font-semibold text-white"
              >
                📄 {isId ? 'Promosikan Dokumen OCR' : 'Promote OCR Document'}
              </button>
            </div>
          </div>

          {/* Document Promotion Modal */}
          {showPromoteModal && (
            <div className="rounded-xl border border-teal-500/40 bg-slate-900/95 p-5 space-y-4 shadow-xl">
              <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                <h3 className="text-sm font-semibold text-teal-300">
                  📄 {isId ? 'Pipa Promosi Dokumen-ke-Pengetahuan' : 'Direct Document-to-Knowledge Promotion Pipeline'}
                </h3>
                <button type="button" onClick={() => setShowPromoteModal(false)} className="text-xs text-slate-400 hover:text-white">✕</button>
              </div>
              <p className="text-xs text-slate-400">
                {isId
                  ? 'Promosikan teks hasil OCR per halaman secara langsung ke koleksi pengetahuan dengan sitasi [Page N] otomatis dan tautan bukti asal cranial://documents/{id}.'
                  : 'Promote approved OCR-extracted pages directly into target collection, preserving [Page N] page citations and provenance URI cranial://documents/{id}.'}
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                <div>
                  <label className="block text-slate-300 font-medium mb-1">{isId ? 'Pilih Dokumen OCR' : 'Select OCR Document'}</label>
                  <select
                    value={selectedPromoteDocId}
                    onChange={(e) => {
                      setSelectedPromoteDocId(e.target.value);
                      const d = promotableDocs.find((x) => x.id === e.target.value);
                      if (d) {
                        setPromoteTitle(`Document: ${d.file_name}`);
                        setPromoteSourceCode(`doc_${d.file_name.toLowerCase().replace(/[^a-z0-9_-]/g, '_')}`);
                      }
                    }}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  >
                    <option value="">{isId ? '-- Pilih Dokumen --' : '-- Select Document --'}</option>
                    {promotableDocs.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.file_name} ({d.extracted_page_count} pages OCR) — {d.document_type}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-slate-300 font-medium mb-1">{isId ? 'Kode Sumber Target' : 'Target Source Code'}</label>
                  <input
                    type="text"
                    value={promoteSourceCode}
                    onChange={(e) => setPromoteSourceCode(e.target.value)}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  />
                </div>
                <div>
                  <label className="block text-slate-300 font-medium mb-1">{isId ? 'Judul Sumber' : 'Source Title'}</label>
                  <input
                    type="text"
                    value={promoteTitle}
                    onChange={(e) => setPromoteTitle(e.target.value)}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  />
                </div>
                <div>
                  <label className="block text-slate-300 font-medium mb-1">{isId ? 'Cakupan Izin' : 'Permission Scope'}</label>
                  <select
                    value={promotePermScope}
                    onChange={(e) => setPromotePermScope(e.target.value)}
                    className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                  >
                    <option value="AUTHENTICATED">AUTHENTICATED</option>
                    <option value="ROLE_RESTRICTED">ROLE_RESTRICTED</option>
                    <option value="TENANT_MEMBERS">TENANT_MEMBERS</option>
                    <option value="OWNER_ONLY">OWNER_ONLY</option>
                    <option value="PUBLIC">PUBLIC</option>
                  </select>
                </div>
                {promotePermScope === 'ROLE_RESTRICTED' && (
                  <div>
                    <label className="block text-slate-300 font-medium mb-1">{isId ? 'Peran yang Diizinkan' : 'Allowed Roles'}</label>
                    <input
                      type="text"
                      value={promoteRoles}
                      onChange={(e) => setPromoteRoles(e.target.value)}
                      placeholder="MEMBER,OWNER"
                      className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                    />
                  </div>
                )}
              </div>
              <div className="flex justify-between items-center pt-2 border-t border-slate-800">
                <label className="text-xs text-slate-300 flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={promoteForceReindex}
                    onChange={(e) => setPromoteForceReindex(e.target.checked)}
                    className="rounded bg-slate-800 border-slate-700"
                  />
                  {isId ? 'Paksa Re-indexing Ulang' : 'Force Re-index'}
                </label>
                <div className="flex gap-2">
                  <button type="button" onClick={() => setShowPromoteModal(false)} className="rounded border border-slate-700 px-3 py-1 text-xs text-slate-300">
                    {isId ? 'Batal' : 'Cancel'}
                  </button>
                  <button
                    type="button"
                    disabled={!selectedPromoteDocId}
                    onClick={handlePromoteDocument}
                    className="rounded bg-teal-600 px-4 py-1 text-xs font-semibold text-white hover:bg-teal-500 disabled:opacity-40"
                  >
                    ✓ {isId ? 'Promosikan & Indeks' : 'Promote & Index'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Ingestion Panel */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-white">
              {isId ? 'Ingest / Perbarui Sumber Pengetahuan' : 'Ingest / Incrementally Update Knowledge Source'}
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1">Source Code</label>
                <input
                  type="text"
                  value={srcCode}
                  onChange={(e) => setSrcCode(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Title</label>
                <input
                  type="text"
                  value={srcTitle}
                  onChange={(e) => setSrcTitle(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Source URI (Provenance)</label>
                <input
                  type="text"
                  value={srcUri}
                  onChange={(e) => setSrcUri(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Content Type</label>
                <select
                  value={srcContentType}
                  onChange={(e) => setSrcContentType(e.target.value as any)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                >
                  <option value="MARKDOWN">MARKDOWN</option>
                  <option value="PLAIN_TEXT">PLAIN_TEXT</option>
                  <option value="FAQ">FAQ</option>
                  <option value="GLOSSARY">GLOSSARY</option>
                  <option value="POLICY_SOP">POLICY_SOP</option>
                </select>
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Permission Scope</label>
                <select
                  value={srcPermScope}
                  onChange={(e) => setSrcPermScope(e.target.value as any)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                >
                  <option value="AUTHENTICATED">AUTHENTICATED</option>
                  <option value="ROLE_RESTRICTED">ROLE_RESTRICTED</option>
                  <option value="TENANT_MEMBERS">TENANT_MEMBERS</option>
                  <option value="OWNER_ONLY">OWNER_ONLY</option>
                  <option value="PUBLIC">PUBLIC</option>
                </select>
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Allowed Roles</label>
                <input
                  type="text"
                  value={srcAllowedRoles}
                  onChange={(e) => setSrcAllowedRoles(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Locale Metadata</label>
                <select
                  value={srcLocaleMeta}
                  onChange={(e) => setSrcLocaleMeta(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                >
                  <option value="en">en (English)</option>
                  <option value="id">id (Indonesian)</option>
                </select>
              </div>
              <div className="md:col-span-3">
                <label className="block text-slate-400 mb-1">
                  {isId
                    ? 'Konten Mentah (Gunakan [Page N] untuk sitasi halaman & # Header untuk bagian)'
                    : 'Raw Content (Use [Page N] for page citations & # Headings for section hierarchies)'}
                </label>
                <textarea
                  value={srcContent}
                  onChange={(e) => setSrcContent(e.target.value)}
                  rows={4}
                  className="w-full rounded border border-slate-700 bg-slate-800 p-2.5 text-xs text-slate-200 font-mono"
                />
              </div>
            </div>
            <div className="flex justify-between items-center pt-2">
              <label className="text-xs text-slate-400 flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={srcSimFail}
                  onChange={(e) => setSrcSimFail(e.target.checked)}
                  className="rounded bg-slate-800 border-slate-700"
                />
                {isId ? 'Simulasi Kegagalan Embedding' : 'Simulate Provider Failure'}
              </label>
              <button
                type="button"
                onClick={handleIngestSource}
                className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-2 text-xs font-semibold text-white"
              >
                🚀 {isId ? 'Ingest & Indeks Inkremental' : 'Ingest & Incrementally Index'}
              </button>
            </div>
          </div>

          {/* Sources List Table */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/50 overflow-hidden">
            <div className="p-3 border-b border-slate-800 text-xs font-semibold text-slate-300">
              {isId ? 'Daftar Sumber Terdaftar' : 'Registered Sources'} ({sources.length})
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950/60 text-slate-400 uppercase text-[10px]">
                  <tr>
                    <th className="p-3">Source Code</th>
                    <th className="p-3">Title / Scope</th>
                    <th className="p-3">Ver</th>
                    <th className="p-3">Status</th>
                    <th className="p-3">Chunks</th>
                    <th className="p-3">Freshness</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {sources.map((s) => (
                    <tr key={s.id} className="hover:bg-slate-800/30">
                      <td className="p-3 font-mono text-indigo-300">{s.source_code}</td>
                      <td className="p-3">
                        <div className="font-medium text-white">{s.title}</div>
                        <div className="text-[10px] text-slate-400">
                          {s.permission_scope} {s.allowed_roles.length > 0 && `• [${s.allowed_roles.join(', ')}]`}
                        </div>
                      </td>
                      <td className="p-3 font-mono">v{s.version}</td>
                      <td className="p-3">
                        <span
                          className={`rounded px-2 py-0.5 text-[10px] font-semibold ${
                            s.indexing_status === 'INDEXED'
                              ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                              : s.indexing_status === 'FAILED'
                              ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                              : 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                          }`}
                        >
                          {s.indexing_status}
                        </span>
                      </td>
                      <td className="p-3 font-mono">
                        {s.chunk_count} ch ({s.token_count} tok)
                      </td>
                      <td className="p-3">
                        {s.stale ? (
                          <span className="rounded bg-amber-500/20 text-amber-300 border border-amber-500/30 px-2 py-0.5 text-[10px]">
                            STALE ({s.age_days}d)
                          </span>
                        ) : (
                          <span className="text-emerald-400 text-[11px]">Fresh ({s.age_days}d)</span>
                        )}
                      </td>
                      <td className="p-3 text-right space-x-1">
                        <button
                          type="button"
                          onClick={() => handleInspectHistory(s)}
                          className="rounded border border-slate-700 bg-slate-800 px-2 py-1 text-[11px] text-slate-200 hover:text-white"
                        >
                          {isId ? 'Audit' : 'History'}
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSourceAction('reindex', s)}
                          className="rounded border border-slate-700 bg-slate-800 px-2 py-1 text-[11px] text-indigo-300 hover:text-white"
                        >
                          {isId ? 'Re-indeks' : 'Reindex'}
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSourceAction('revoke', s)}
                          className="rounded border border-amber-500/40 bg-amber-950/20 px-2 py-1 text-[11px] text-amber-300 hover:text-white"
                        >
                          {isId ? 'Cabut' : 'Revoke'}
                        </button>
                      </td>
                    </tr>
                  ))}
                  {sources.length === 0 && (
                    <tr>
                      <td colSpan={7} className="p-4 text-center text-slate-500">
                        {isId ? 'Belum ada sumber di koleksi ini.' : 'No sources indexed in this collection yet.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Audit History Drawer */}
          {sourceHistory && (
            <div className="rounded-xl border border-indigo-500/30 bg-slate-900/90 p-4 space-y-3">
              <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                <span className="text-xs font-semibold text-white">
                  Audit History: {historySourceCode} ({sourceHistory.length} events)
                </span>
                <button
                  type="button"
                  onClick={() => setSourceHistory(null)}
                  className="text-xs text-slate-400 hover:text-white"
                >
                  ✕ {isId ? 'Tutup' : 'Close'}
                </button>
              </div>
              <div className="space-y-2 max-h-64 overflow-y-auto pr-2 text-xs">
                {sourceHistory.map((ev) => (
                  <div key={ev.id} className="rounded border border-slate-800 bg-slate-950/60 p-2.5 flex justify-between gap-3">
                    <div>
                      <div className="font-semibold text-indigo-300">
                        {ev.event_type} (v{ev.source_version})
                      </div>
                      <div className="text-[11px] text-slate-400 mt-0.5">
                        Added: +{ev.chunks_added} • Reused: ={ev.chunks_reused} • Removed: -{ev.chunks_removed} ({ev.duration_ms}ms)
                      </div>
                      <div className="text-[10px] text-slate-500 mt-1 font-mono">
                        Actor: {ev.actor} • Prev SHA: {ev.previous_sha256?.slice(0, 10) || 'none'} → New: {ev.new_sha256?.slice(0, 10)}
                      </div>
                    </div>
                    <span className="text-[10px] text-slate-500 whitespace-nowrap">
                      {new Date(ev.created_at).toLocaleTimeString()}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 3: CHUNK BOUNDARY PREVIEWER */}
      {subTab === 'chunkPreview' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-indigo-500/30 bg-slate-900/60 p-4 space-y-4">
            <div>
              <h2 className="text-sm font-semibold text-white">
                🔍 {isId ? 'Inspektur Batas Chunk Side-by-Side' : 'Side-by-Side Chunk Boundary Previewer'}
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                {isId
                  ? 'Bandingkan dua konfigurasi chunking (Strategi A vs Strategi B, ukuran chunk, overlap, batas token, dan ekstraksi glosarium) sebelum menerapkannya ke koleksi.'
                  : 'Compare two chunking configurations side-by-side (Strategy A vs Strategy B, chunk size, overlap, resulting chunk boundaries, and token distribution) before committing to a collection.'}
              </p>
            </div>

            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <label className="block text-xs font-medium text-slate-300">
                  {isId ? 'Teks Uji Markdown / Dokumen' : 'Markdown / Document Test Text'}
                </label>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] text-slate-400">Content Type:</span>
                  <select
                    value={previewContentType}
                    onChange={(e) => setPreviewContentType(e.target.value)}
                    className="rounded bg-slate-800 border border-slate-700 px-2 py-0.5 text-white text-[11px]"
                  >
                    <option value="MARKDOWN">MARKDOWN</option>
                    <option value="PLAIN_TEXT">PLAIN_TEXT</option>
                    <option value="FAQ">FAQ</option>
                    <option value="POLICY_SOP">POLICY_SOP</option>
                  </select>
                </div>
              </div>
              <textarea
                value={previewContent}
                onChange={(e) => setPreviewContent(e.target.value)}
                rows={5}
                className="w-full rounded border border-slate-700 bg-slate-800 p-2.5 text-xs text-slate-200 font-mono"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-slate-800 pt-3">
              {/* Left Config */}
              <div className="rounded-lg border border-indigo-500/30 bg-slate-950/40 p-3 space-y-2 text-xs">
                <div className="font-semibold text-indigo-300">Strategy A (Left)</div>
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="text-[11px] text-slate-400">Strategy</label>
                    <select
                      value={previewLeftStrat}
                      onChange={(e) => setPreviewLeftStrat(e.target.value)}
                      className="w-full rounded bg-slate-800 border border-slate-700 p-1 text-white text-[11px]"
                    >
                      <option value="MARKDOWN_SECTION">MARKDOWN_SECTION</option>
                      <option value="PARAGRAPH">PARAGRAPH</option>
                      <option value="FAQ_QA">FAQ_QA</option>
                      <option value="FIXED_WINDOW">FIXED_WINDOW</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-[11px] text-slate-400">Size (Chars)</label>
                    <input
                      type="number"
                      value={previewLeftSize}
                      onChange={(e) => setPreviewLeftSize(Number(e.target.value))}
                      className="w-full rounded bg-slate-800 border border-slate-700 p-1 text-white text-[11px]"
                    />
                  </div>
                  <div>
                    <label className="text-[11px] text-slate-400">Overlap</label>
                    <input
                      type="number"
                      value={previewLeftOverlap}
                      onChange={(e) => setPreviewLeftOverlap(Number(e.target.value))}
                      className="w-full rounded bg-slate-800 border border-slate-700 p-1 text-white text-[11px]"
                    />
                  </div>
                </div>
              </div>

              {/* Right Config */}
              <div className="rounded-lg border border-purple-500/30 bg-slate-950/40 p-3 space-y-2 text-xs">
                <div className="font-semibold text-purple-300">Strategy B (Right)</div>
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="text-[11px] text-slate-400">Strategy</label>
                    <select
                      value={previewRightStrat}
                      onChange={(e) => setPreviewRightStrat(e.target.value)}
                      className="w-full rounded bg-slate-800 border border-slate-700 p-1 text-white text-[11px]"
                    >
                      <option value="FIXED_WINDOW">FIXED_WINDOW</option>
                      <option value="MARKDOWN_SECTION">MARKDOWN_SECTION</option>
                      <option value="PARAGRAPH">PARAGRAPH</option>
                      <option value="FAQ_QA">FAQ_QA</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-[11px] text-slate-400">Size (Chars)</label>
                    <input
                      type="number"
                      value={previewRightSize}
                      onChange={(e) => setPreviewRightSize(Number(e.target.value))}
                      className="w-full rounded bg-slate-800 border border-slate-700 p-1 text-white text-[11px]"
                    />
                  </div>
                  <div>
                    <label className="text-[11px] text-slate-400">Overlap</label>
                    <input
                      type="number"
                      value={previewRightOverlap}
                      onChange={(e) => setPreviewRightOverlap(Number(e.target.value))}
                      className="w-full rounded bg-slate-800 border border-slate-700 p-1 text-white text-[11px]"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="button"
                disabled={previewRunning || !previewContent.trim()}
                onClick={handleRunChunkPreview}
                className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-2 text-xs font-semibold text-white disabled:opacity-40"
              >
                {previewRunning ? 'Analyzing...' : '⚡ ' + (isId ? 'Jalankan Perbandingan Batas Chunk' : 'Run Boundary Comparison')}
              </button>
            </div>
          </div>

          {/* Results Side-by-Side Display */}
          {previewResult && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Left Pane */}
              <div className="rounded-xl border border-indigo-500/40 bg-slate-900/70 p-4 space-y-3">
                <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                  <span className="font-semibold text-indigo-300 text-xs">
                    Pane A: {previewResult.left.strategy_used}
                  </span>
                  <span className="text-[11px] text-slate-400">
                    {previewResult.left.total_chunks} chunks • {previewResult.left.total_tokens} tokens (avg{' '}
                    {previewResult.left.avg_tokens_per_chunk} / max {previewResult.left.max_tokens_in_chunk})
                  </span>
                </div>
                {previewResult.left.glossary_terms_detected.length > 0 && (
                  <div className="text-[11px] text-slate-300 flex flex-wrap gap-1">
                    <span className="text-slate-500">Glossary detected:</span>
                    {previewResult.left.glossary_terms_detected.map((g, i) => (
                      <span key={i} className="rounded bg-indigo-500/20 px-1.5 py-0.2 text-[10px] text-indigo-200">
                        {g}
                      </span>
                    ))}
                  </div>
                )}
                <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
                  {previewResult.left.chunks.map((c) => (
                    <div key={c.chunk_index} className="rounded border border-slate-800 bg-slate-950/70 p-2 text-xs space-y-1">
                      <div className="flex justify-between text-[10px] text-slate-400 font-mono">
                        <span>Chunk #{c.chunk_index} {c.page_number && `[Page ${c.page_number}]`} {c.section_title && `§ ${c.section_title}`}</span>
                        <span>{c.token_count} tok ({c.char_end - c.char_start} chars)</span>
                      </div>
                      <p className="text-slate-200 font-mono text-[11px] whitespace-pre-wrap">{c.content}</p>
                      {c.parent_section_preview && (
                        <div className="text-[10px] text-slate-500 italic border-t border-slate-800/60 pt-0.5">
                          Parent section: {c.parent_section_preview}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* Right Pane */}
              <div className="rounded-xl border border-purple-500/40 bg-slate-900/70 p-4 space-y-3">
                <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                  <span className="font-semibold text-purple-300 text-xs">
                    Pane B: {previewResult.right.strategy_used}
                  </span>
                  <span className="text-[11px] text-slate-400">
                    {previewResult.right.total_chunks} chunks • {previewResult.right.total_tokens} tokens (avg{' '}
                    {previewResult.right.avg_tokens_per_chunk} / max {previewResult.right.max_tokens_in_chunk})
                  </span>
                </div>
                {previewResult.right.glossary_terms_detected.length > 0 && (
                  <div className="text-[11px] text-slate-300 flex flex-wrap gap-1">
                    <span className="text-slate-500">Glossary detected:</span>
                    {previewResult.right.glossary_terms_detected.map((g, i) => (
                      <span key={i} className="rounded bg-purple-500/20 px-1.5 py-0.2 text-[10px] text-purple-200">
                        {g}
                      </span>
                    ))}
                  </div>
                )}
                <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
                  {previewResult.right.chunks.map((c) => (
                    <div key={c.chunk_index} className="rounded border border-slate-800 bg-slate-950/70 p-2 text-xs space-y-1">
                      <div className="flex justify-between text-[10px] text-slate-400 font-mono">
                        <span>Chunk #{c.chunk_index} {c.page_number && `[Page ${c.page_number}]`} {c.section_title && `§ ${c.section_title}`}</span>
                        <span>{c.token_count} tok ({c.char_end - c.char_start} chars)</span>
                      </div>
                      <p className="text-slate-200 font-mono text-[11px] whitespace-pre-wrap">{c.content}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 4: RETRIEVAL DEBUGGER & GROUNDED ASK */}
      {subTab === 'debugger' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-white">
              {isId ? 'Debugger Retrieval Multi-Tahap & Playground Tanya Terhubung' : 'Multi-Stage Retrieval Debugger & Grounded Ask'}
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1">Target Collection Code</label>
                <input
                  type="text"
                  value={dbgCollectionCode}
                  onChange={(e) => setDbgCollectionCode(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Retrieval Mode</label>
                <select
                  value={dbgMode}
                  onChange={(e) => setDbgMode(e.target.value as any)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                >
                  <option value="HYBRID">HYBRID (BM25 + Semantic Vector)</option>
                  <option value="VECTOR">VECTOR Only</option>
                  <option value="KEYWORD">KEYWORD Only</option>
                </select>
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Simulated Roles</label>
                <input
                  type="text"
                  value={dbgRoles}
                  onChange={(e) => setDbgRoles(e.target.value)}
                  placeholder="MEMBER,WORKER,OWNER"
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Locale Filter (Optional)</label>
                <input
                  type="text"
                  value={dbgLocaleFilter}
                  onChange={(e) => setDbgLocaleFilter(e.target.value)}
                  placeholder="en / id"
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div className="md:col-span-4">
                <label className="block text-slate-400 mb-1">{isId ? 'Pertanyaan / Kueri Uji' : 'Question / Test Query'}</label>
                <input
                  type="text"
                  value={dbgQuery}
                  onChange={(e) => setDbgQuery(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-2 text-white text-sm"
                />
              </div>
            </div>

            <div className="flex flex-wrap justify-between items-center pt-2 gap-2">
              <div className="flex items-center gap-4 text-xs">
                <label className="text-slate-300 flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={dbgRerank}
                    onChange={(e) => setDbgRerank(e.target.checked)}
                    className="rounded bg-slate-800 border-slate-700"
                  />
                  {isId ? 'Aktifkan Reranker' : 'Enable Reranker'}
                </label>
                <label className="text-slate-300 flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={dbgExpandParent}
                    onChange={(e) => setDbgExpandParent(e.target.checked)}
                    className="rounded bg-slate-800 border-slate-700"
                  />
                  {isId ? 'Ekspansi Konteks Parent Section' : 'Expand Parent Section Context'}
                </label>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleRunRetrieve}
                  className="rounded border border-indigo-500/40 bg-indigo-950/40 hover:bg-indigo-900/60 px-4 py-2 text-xs font-semibold text-indigo-200"
                >
                  🔍 {isId ? 'Debug Retrieval' : 'Debug Retrieval'}
                </button>
                <button
                  type="button"
                  onClick={handleRunAsk}
                  className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-2 text-xs font-semibold text-white shadow"
                >
                  💬 {isId ? 'Jawab Terhubung (Ask)' : 'Grounded Ask'}
                </button>
              </div>
            </div>
          </div>

          {/* Grounded Answer Display */}
          {answerResult && (
            <div className="rounded-xl border border-indigo-500/40 bg-slate-900/90 p-5 space-y-4">
              <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                <div className="text-xs font-semibold text-indigo-300">
                  {answerResult.abstained
                    ? '⚠️ ' + (isId ? 'Model Menolak Menjawab (Abstained)' : 'Model Abstained')
                    : '💡 ' + (isId ? 'Jawaban Terhubung dengan Sitasi & Penaut Glosarium' : 'Grounded Answer with Citations')}
                </div>
                <span className="text-[11px] text-slate-400 font-mono">
                  Model: {answerResult.model_used} • {answerResult.duration_ms}ms
                </span>
              </div>

              <div className="rounded bg-slate-950/60 p-3 text-xs text-slate-200 whitespace-pre-wrap leading-relaxed">
                {answerResult.answer}
              </div>

              {/* 1-Click Answer Feedback Buttons */}
              <div className="rounded-lg bg-slate-950/40 p-2.5 border border-slate-800 flex flex-wrap items-center justify-between gap-2">
                <span className="text-[11px] text-slate-400 font-medium">
                  {isId ? 'Beri Umpan Balik Kualitas Jawaban:' : 'Rate Answer Quality for IR Eval:'}
                </span>
                <div className="flex gap-1.5">
                  <button
                    type="button"
                    onClick={() => handleFeedbackSubmit('HELPFUL')}
                    className="rounded bg-emerald-950/40 border border-emerald-500/30 px-2 py-1 text-[11px] text-emerald-300 hover:bg-emerald-900/60"
                  >
                    👍 Helpful
                  </button>
                  <button
                    type="button"
                    onClick={() => handleFeedbackSubmit('UNHELPFUL')}
                    className="rounded bg-rose-950/40 border border-rose-500/30 px-2 py-1 text-[11px] text-rose-300 hover:bg-rose-900/60"
                  >
                    👎 Unhelpful
                  </button>
                  <button
                    type="button"
                    onClick={() => handleFeedbackSubmit('CITATION_MISMATCH')}
                    className="rounded bg-amber-950/40 border border-amber-500/30 px-2 py-1 text-[11px] text-amber-300 hover:bg-amber-900/60"
                  >
                    ⚠️ Citation Mismatch
                  </button>
                  <button
                    type="button"
                    onClick={() => handleFeedbackSubmit('MISSING_INFO')}
                    className="rounded bg-sky-950/40 border border-sky-500/30 px-2 py-1 text-[11px] text-sky-300 hover:bg-sky-900/60"
                  >
                    ❓ Missing Info
                  </button>
                </div>
              </div>
              {fbSubmittedMsg && (
                <div className="text-[11px] text-emerald-400">{fbSubmittedMsg}</div>
              )}

              {/* Citations Box */}
              {answerResult.citations.length > 0 && (
                <div className="space-y-1.5">
                  <span className="text-xs font-semibold text-slate-300">
                    {isId ? 'Sitasi Bukti Asal:' : 'Evidence Provenance Citations:'}
                  </span>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-[11px]">
                    {answerResult.citations.map((c) => (
                      <div key={c.citation_id} className="rounded border border-slate-800 bg-slate-950/70 p-2 space-y-0.5">
                        <div className="flex justify-between items-center">
                          <span className="font-bold text-indigo-400">[{c.citation_id}]</span>
                          <span className="font-mono text-[10px] text-slate-400">{c.source_code}</span>
                        </div>
                        <div className="text-slate-300 font-medium">
                          {c.source_title} {c.page_number && `(Page ${c.page_number})`} {c.section_title && `§ ${c.section_title}`}
                        </div>
                        <div className="text-[10px] font-mono text-slate-500">{c.source_uri}</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Retrieval Trace & Funnel Display */}
          {retrievalResult && (
            <div className="space-y-4">
              {retrievalResult.debug_trace && (
                <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-4 space-y-3">
                  <div className="flex justify-between text-xs font-semibold text-slate-300">
                    <span>{isId ? 'Funnel Evaluasi Kandidat' : 'Candidate Funnel Trace'}</span>
                    <span className="font-mono text-slate-400">{retrievalResult.duration_ms}ms</span>
                  </div>
                  <div className="grid grid-cols-2 md:grid-cols-6 gap-2 text-center text-xs">
                    <div className="bg-slate-950 p-2 rounded border border-slate-800">
                      <div className="text-slate-500 text-[10px]">Total Chunks</div>
                      <div className="text-sm font-bold text-white">{retrievalResult.debug_trace.total_chunks_in_collection}</div>
                    </div>
                    <div className="bg-slate-950 p-2 rounded border border-slate-800">
                      <div className="text-slate-500 text-[10px]">Isolated</div>
                      <div className="text-sm font-bold text-white">{retrievalResult.debug_trace.after_isolation_chunks}</div>
                    </div>
                    <div className="bg-slate-950 p-2 rounded border border-slate-800">
                      <div className="text-slate-500 text-[10px]">Perm Filtered</div>
                      <div className="text-sm font-bold text-rose-400">{retrievalResult.debug_trace.permission_filtered_chunks}</div>
                    </div>
                    <div className="bg-slate-950 p-2 rounded border border-slate-800">
                      <div className="text-slate-500 text-[10px]">Meta Filtered</div>
                      <div className="text-sm font-bold text-amber-400">{retrievalResult.debug_trace.metadata_filtered_chunks}</div>
                    </div>
                    <div className="bg-slate-950 p-2 rounded border border-slate-800">
                      <div className="text-slate-500 text-[10px]">Below Min Score</div>
                      <div className="text-sm font-bold text-slate-400">{retrievalResult.debug_trace.below_threshold_chunks}</div>
                    </div>
                    <div className="bg-slate-950 p-2 rounded border border-indigo-500/40">
                      <div className="text-slate-500 text-[10px]">Selected Hits</div>
                      <div className="text-sm font-bold text-indigo-300">{retrievalResult.debug_trace.returned_hits}</div>
                    </div>
                  </div>
                </div>
              )}

              {/* Retrieved Hits */}
              <div className="space-y-3">
                <div className="text-xs font-semibold text-slate-300">
                  {isId ? 'Chunk Terpilih (Kandidat Relevan)' : 'Top Retrieved Chunks'} ({retrievalResult.hits.length})
                </div>
                {retrievalResult.hits.map((hit) => (
                  <div key={hit.rank} className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-2 text-xs">
                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-indigo-400">Rank #{hit.rank}</span>
                        {hit.citation && (
                          <span className="font-mono text-slate-300">
                            [{hit.citation.citation_id}] {hit.citation.source_code}{' '}
                            {hit.citation.page_number && `(Page ${hit.citation.page_number})`}{' '}
                            {hit.citation.section_title && `§ "${hit.citation.section_title}"`}
                          </span>
                        )}
                      </div>
                      <div className="font-mono text-[11px] text-slate-400 space-x-2">
                        <span>Score: <b className="text-white">{hit.final_score}</b></span>
                        <span>(vec: {hit.vector_score} • kw: {hit.keyword_score} • boost: +{hit.rerank_boost})</span>
                      </div>
                    </div>
                    <p className="text-slate-200 font-mono text-[11px] bg-slate-950/60 p-2.5 rounded border border-slate-800/80 whitespace-pre-wrap">
                      {hit.content}
                    </p>
                    {hit.expanded_context && (
                      <div className="rounded bg-slate-950/40 p-2 border border-slate-800 text-[10px] text-slate-400 space-y-0.5">
                        <span className="font-semibold text-slate-300">Expanded Parent Section Context:</span>
                        <p className="font-mono text-slate-400 whitespace-pre-wrap">{hit.expanded_context}</p>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 5: GLOSSARY & SYNONYMS */}
      {subTab === 'glossary' && (
        <div className="space-y-6">
          {/* Synonym Dictionary Manager */}
          <div className="rounded-xl border border-indigo-500/30 bg-slate-900/60 p-4 space-y-4">
            <div>
              <h2 className="text-sm font-semibold text-white">
                📖 {isId ? 'Kamus Sinonim Domain Dwibahasa (EN ↔ ID)' : 'Bilingual Domain Synonym Dictionary Editor (EN ↔ ID)'}
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                {isId
                  ? 'Entri sinonim kustom dimuat secara dinamis ke ekspansi kueri BM25 dan proyeksi vektor semantik tanpa perlu mengubah kode sumber.'
                  : 'Custom domain synonym entries are dynamically merged into BM25 query expansion and semantic embedding projection without code deployment.'}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-xs">
              <div>
                <label className="block text-slate-300 font-medium mb-1">
                  {isId ? 'Istilah Kanonikal' : 'Canonical Term'}
                </label>
                <input
                  type="text"
                  value={newSynCanonical}
                  onChange={(e) => setNewSynCanonical(e.target.value)}
                  placeholder="cicilan / emergency fund"
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-slate-300 font-medium mb-1">
                  {isId ? 'Daftar Sinonim (Koma pisah)' : 'Synonyms List (Comma separated)'}
                </label>
                <input
                  type="text"
                  value={newSynAliases}
                  onChange={(e) => setNewSynAliases(e.target.value)}
                  placeholder="installment, angsuran, monthly payment"
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-300 font-medium mb-1">Domain Tag</label>
                <input
                  type="text"
                  value={newSynDomain}
                  onChange={(e) => setNewSynDomain(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
            </div>

            <div className="flex justify-end">
              <button
                type="button"
                onClick={handleCreateSynonym}
                disabled={!newSynCanonical.trim() || !newSynAliases.trim()}
                className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-1.5 text-xs font-semibold text-white disabled:opacity-40"
              >
                + {isId ? 'Tambah Entri Sinonim' : 'Add Synonym Entry'}
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 pt-2">
              {synonyms.map((s) => (
                <div key={s.id} className="rounded-lg border border-slate-800 bg-slate-950/60 p-3 space-y-1 text-xs">
                  <div className="flex justify-between items-start">
                    <span className="font-semibold text-indigo-300">{s.canonical_term}</span>
                    <button
                      type="button"
                      onClick={() => handleDeleteSynonym(s.id)}
                      className="text-slate-500 hover:text-rose-400"
                    >
                      ✕
                    </button>
                  </div>
                  <div className="text-[11px] text-slate-300">
                    ↔ {s.synonyms.join(', ')}
                  </div>
                  <div className="text-[10px] text-slate-500 flex justify-between">
                    <span>tag: {s.domain_tag}</span>
                    <span>{s.locale_pair}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Interactive Term Linking Preview */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
            <h3 className="text-xs font-semibold text-white">
              {isId ? 'Pratinjau Penaut Istilah Glosarium Otomatis' : 'Interactive Glossary Linking Preview'}
            </h3>
            <textarea
              value={sampleLinkText}
              onChange={(e) => setSampleLinkText(e.target.value)}
              rows={3}
              className="w-full rounded border border-slate-700 bg-slate-800 p-2.5 text-xs text-white"
            />
            <div className="flex justify-end">
              <button
                type="button"
                onClick={async () => {
                  const resp = await api.linkGlossaryPreview({
                    collection_id: selectedCollectionId || undefined,
                    collection_code: !selectedCollectionId ? 'peggy_financial_encyclopedia_ref' : undefined,
                    text: sampleLinkText,
                  });
                  setLinkResult(resp.data ?? null);
                }}
                className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-1.5 text-xs font-semibold text-white"
              >
                🔗 {isId ? 'Tautkan Istilah' : 'Link Terms in Text'}
              </button>
            </div>
            {linkResult && (
              <div className="rounded bg-slate-950 p-3 text-xs leading-relaxed border border-slate-800">
                <div className="text-[11px] text-slate-400 mb-1">
                  {isId ? 'Hasil Penautan Istilah' : 'Linked Text Result'} ({linkResult.matched_count} matches):
                </div>
                {renderHighlightedText(linkResult)}
              </div>
            )}
          </div>

          {/* Registered Glossary Terms */}
          {glossaryTerms.length > 0 && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
              <h3 className="text-xs font-semibold text-white">
                📚 {isId ? 'Daftar Glosarium Koleksi' : 'Collection Glossary Terms'} ({glossaryTerms.length})
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2.5">
                {glossaryTerms.map((term) => (
                  <div key={term.id} className="rounded border border-slate-800 bg-slate-950/60 p-2.5 text-xs space-y-1">
                    <div className="font-semibold text-teal-300">{term.term}</div>
                    <div className="text-[11px] text-slate-400">{term.short_definition}</div>
                    {term.aliases.length > 0 && (
                      <div className="text-[10px] text-slate-500 font-mono">Aliases: {term.aliases.join(', ')}</div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 6: FEED CONNECTORS (ETag / If-Modified-Since) */}
      {subTab === 'connectors' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-4">
            <div>
              <h2 className="text-sm font-semibold text-white">
                📡 {isId ? 'Konektor Penarikan Umpan Bersyarat (ETag)' : 'Scheduled / Conditional Feed Connectors (ETag / If-Modified-Since)'}
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                {isId
                  ? 'Konektor baca-saja untuk FAQ/buku panduan aplikasi. Mendukung respons kondisional 304 Not Modified untuk menghemat biaya embedding, serta pengawal SSRF ketat.'
                  : 'Read-only feed connectors for application FAQ/handbook feeds supporting conditional HTTP requests (ETag / If-None-Match & Last-Modified). 304 Not Modified skips re-indexing; SSRF guards protect internal network.'}
              </p>
            </div>

            {/* Create Connector Form */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs border-t border-slate-800 pt-3">
              <div>
                <label className="block text-slate-400 mb-1">Connector Code</label>
                <input
                  type="text"
                  value={newConnCode}
                  onChange={(e) => setNewConnCode(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Display Name</label>
                <input
                  type="text"
                  value={newConnName}
                  onChange={(e) => setNewConnName(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Target Source Code</label>
                <input
                  type="text"
                  value={newConnTargetSource}
                  onChange={(e) => setNewConnTargetSource(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-slate-400 mb-1">Feed URI (HTTP or cranial://sample-feed/...)</label>
                <input
                  type="text"
                  value={newConnUri}
                  onChange={(e) => setNewConnUri(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Type</label>
                <select
                  value={newConnType}
                  onChange={(e) => setNewConnType(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                >
                  <option value="INTERNAL_SAMPLE">INTERNAL_SAMPLE</option>
                  <option value="HTTP_FEED">HTTP_FEED (External SSRF-Guarded)</option>
                </select>
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Poll Interval (min)</label>
                <input
                  type="number"
                  value={newConnInterval}
                  onChange={(e) => setNewConnInterval(Number(e.target.value))}
                  className="w-full rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-white"
                />
              </div>
            </div>

            <div className="flex justify-end">
              <button
                type="button"
                onClick={handleCreateConnector}
                className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-1.5 text-xs font-semibold text-white"
              >
                + {isId ? 'Daftarkan Konektor' : 'Register Connector'}
              </button>
            </div>
          </div>

          {/* Connectors Table */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/50 overflow-hidden">
            <div className="p-3 border-b border-slate-800 text-xs font-semibold text-slate-300">
              {isId ? 'Konektor Terdaftar' : 'Registered Connectors'} ({connectors.length})
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950/60 text-slate-400 uppercase text-[10px]">
                  <tr>
                    <th className="p-3">Code / Name</th>
                    <th className="p-3">URI / Target</th>
                    <th className="p-3">Last Sync Status</th>
                    <th className="p-3">ETag</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {connectors.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-800/30">
                      <td className="p-3">
                        <div className="font-semibold text-indigo-300">{c.connector_code}</div>
                        <div className="text-[11px] text-slate-400">{c.name}</div>
                      </td>
                      <td className="p-3 font-mono text-[11px]">
                        <div>{c.feed_uri}</div>
                        <div className="text-[10px] text-slate-500">→ {c.target_source_code}</div>
                      </td>
                      <td className="p-3">
                        <span className="rounded bg-slate-800 px-2 py-0.5 text-[10px] font-mono text-slate-300">
                          {c.last_sync_status}
                        </span>
                      </td>
                      <td className="p-3 font-mono text-[10px] text-slate-400">
                        {c.last_etag || 'none'}
                      </td>
                      <td className="p-3 text-right space-x-1">
                        <button
                          type="button"
                          onClick={() => handleSyncConnector(c.id, false)}
                          className="rounded border border-indigo-500/40 bg-indigo-950/40 px-2.5 py-1 text-[11px] text-indigo-200 hover:bg-indigo-900/60"
                        >
                          🔄 Sync (Conditional)
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSyncConnector(c.id, true)}
                          className="rounded border border-slate-700 bg-slate-800 px-2 py-1 text-[11px] text-slate-300 hover:text-white"
                        >
                          Force Pull
                        </button>
                      </td>
                    </tr>
                  ))}
                  {connectors.length === 0 && (
                    <tr>
                      <td colSpan={5} className="p-4 text-center text-slate-500">
                        {isId ? 'Belum ada konektor umpan terdaftar.' : 'No feed connectors registered yet.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Sync Result Banner */}
          {syncResult && (
            <div className="rounded-xl border border-sky-500/40 bg-slate-900/90 p-4 text-xs space-y-1">
              <div className="font-semibold text-sky-300">
                Sync Result: {syncResult.connector_code} → HTTP {syncResult.http_status_code} ({syncResult.sync_status})
              </div>
              <p className="text-slate-300">{syncResult.message}</p>
              <div className="text-[10px] font-mono text-slate-500">
                ETag: {syncResult.etag || 'none'} • Reindexed: {String(syncResult.reindexed)} • Chunks added: +{syncResult.chunks_added} / reused: ={syncResult.chunks_reused}
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 7: FRESHNESS & RETENTION */}
      {subTab === 'freshness' && (
        <div className="space-y-6">
          <div className="flex justify-between items-center bg-slate-900/60 p-4 rounded-xl border border-slate-800">
            <div>
              <h2 className="text-sm font-semibold text-white">
                {isId ? 'Sumber Usang (TTL Kesegaran Terlampaui)' : 'Stale Knowledge Sources (TTL Exceeded)'}
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                {isId
                  ? 'Sumber yang umur efektifnya melebihi TTL koleksi. Ditandai pada sitasi retrieval agar jawaban RAG tidak mengandalkan data lama.'
                  : 'Sources whose effective age exceeds the collection freshness TTL. Flagged on citations to warn users.'}
              </p>
            </div>
            <button
              type="button"
              onClick={async () => {
                const resp = await api.purgeExpiredKnowledge();
                setPurgeResult((resp.data as unknown as Record<string, unknown>) ?? null);
                const stale = await api.listStaleKnowledgeSources();
                setStaleSources(Array.isArray(stale.data) ? stale.data : []);
              }}
              className="rounded bg-rose-600 hover:bg-rose-500 px-3.5 py-2 text-xs font-semibold text-white"
            >
              🗑️ {isId ? 'Purge Sumber Kedaluwarsa' : 'Purge Expired Sources'}
            </button>
          </div>

          {purgeResult && (
            <div className="rounded-xl border border-emerald-500/30 bg-slate-900/90 p-3 text-xs text-emerald-300 font-mono">
              ✓ {isId ? 'Pembersihan berhasil' : 'Purge completed'}: {JSON.stringify(purgeResult)}
            </div>
          )}

          <div className="rounded-xl border border-slate-800 bg-slate-900/50 overflow-hidden">
            <div className="p-3 border-b border-slate-800 text-xs font-semibold text-slate-300">
              {isId ? 'Sumber Usang yang Terdeteksi' : 'Detected Stale Sources'} ({staleSources.length})
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="bg-slate-950/60 text-slate-400 uppercase text-[10px]">
                  <tr>
                    <th className="p-3">Source Code</th>
                    <th className="p-3">Title</th>
                    <th className="p-3">Age</th>
                    <th className="p-3">Effective Date</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {staleSources.map((s) => (
                    <tr key={s.id} className="hover:bg-slate-800/30">
                      <td className="p-3 font-mono text-amber-300">{s.source_code}</td>
                      <td className="p-3 text-white font-medium">{s.title}</td>
                      <td className="p-3">
                        <span className="rounded bg-amber-500/20 text-amber-300 border border-amber-500/30 px-2 py-0.5 text-[10px]">
                          {s.age_days} days old
                        </span>
                      </td>
                      <td className="p-3 text-slate-400 text-[11px]">
                        {s.effective_updated_at ? new Date(s.effective_updated_at).toLocaleDateString() : 'N/A'}
                      </td>
                      <td className="p-3 text-right">
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedCollectionId(s.collection_id);
                            setSubTab('sources');
                          }}
                          className="rounded bg-indigo-600/80 hover:bg-indigo-600 px-2.5 py-1 text-[11px] text-white"
                        >
                          {isId ? 'Perbarui Sumber' : 'Update Source'} →
                        </button>
                      </td>
                    </tr>
                  ))}
                  {staleSources.length === 0 && (
                    <tr>
                      <td colSpan={5} className="p-4 text-center text-slate-500">
                        {isId ? 'Semua sumber dalam batas kesegaran TTL aktif.' : 'All sources are fresh within their TTL.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 8: BENCHMARKS & USER FEEDBACK PROMOTION QUEUE */}
      {subTab === 'benchmarks' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 flex flex-wrap justify-between items-center gap-4">
            <div>
              <h2 className="text-sm font-semibold text-white">
                📊 {isId ? 'Suite Evaluasi Relevansi & Regresi Izin RAG' : 'RAG Relevance & Permission Regression Evaluation Suite'}
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                {isId
                  ? 'Ukur metrik IR standar industri: Hit@K, MRR (Mean Reciprocal Rank), Precision@K, Akurasi Sitasi Bagian, dan Tingkat Lolos Penjaga Izin (100% target).'
                  : 'Measures standard IR metrics: Hit@K, MRR, Precision@K, Section Citation Accuracy, and Permission Guard Pass Rate (100% target).'}
              </p>
            </div>
            <button
              type="button"
              disabled={loading}
              onClick={async () => {
                setLoading(true);
                setError(null);
                try {
                  const resp = await api.runKnowledgeBenchmarks();
                  setEvalResult(resp.data ?? null);
                } catch (err) {
                  setError(err instanceof Error ? err.message : String(err));
                } finally {
                  setLoading(false);
                }
              }}
              className="rounded bg-indigo-600 hover:bg-indigo-500 px-4 py-2 text-xs font-semibold text-white disabled:opacity-40 shadow"
            >
              🚀 {isId ? 'Jalankan Benchmark Suite' : 'Execute Benchmark Suite'}
            </button>
          </div>

          {/* Benchmark Metrics Cards */}
          {evalResult && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
                <div className="rounded-xl border border-indigo-500/30 bg-slate-900/60 p-3 text-center">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Hit Rate @ K</div>
                  <div className="text-xl font-bold text-indigo-300 mt-1">
                    {(evalResult.hit_rate_at_k * 100).toFixed(1)}%
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">
                    {evalResult.hit_count}/{evalResult.retrieval_cases_count} cases
                  </div>
                </div>
                <div className="rounded-xl border border-indigo-500/30 bg-slate-900/60 p-3 text-center">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">MRR</div>
                  <div className="text-xl font-bold text-indigo-300 mt-1">
                    {evalResult.mrr.toFixed(3)}
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Mean Reciprocal Rank</div>
                </div>
                <div className="rounded-xl border border-indigo-500/30 bg-slate-900/60 p-3 text-center">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Precision @ K</div>
                  <div className="text-xl font-bold text-indigo-300 mt-1">
                    {(evalResult.mean_precision_at_k * 100).toFixed(1)}%
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Relevant top-K density</div>
                </div>
                <div className="rounded-xl border border-emerald-500/30 bg-slate-900/60 p-3 text-center">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Citation Accuracy</div>
                  <div className="text-xl font-bold text-emerald-400 mt-1">
                    {(evalResult.citation_accuracy * 100).toFixed(1)}%
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Exact section matched</div>
                </div>
                <div className="rounded-xl border border-emerald-500/30 bg-slate-900/60 p-3 text-center">
                  <div className="text-slate-400 text-[10px] uppercase font-semibold">Permission Pass Rate</div>
                  <div className="text-xl font-bold text-emerald-400 mt-1">
                    {(evalResult.permission_pass_rate * 100).toFixed(1)}%
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Zero leakage guaranteed</div>
                </div>
              </div>
            </div>
          )}

          {/* User & Operator Feedback Queue */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/50 overflow-hidden space-y-3 p-4">
            <div className="flex flex-wrap justify-between items-center gap-2 border-b border-slate-800 pb-3">
              <div>
                <h3 className="text-xs font-semibold text-white">
                  📬 {isId ? 'Antrean Umpan Balik Jawaban RAG & Promosi Kasus Benchmark' : 'RAG Answer Feedback Queue & Eval Benchmark Promotion'}
                </h3>
                <p className="text-[11px] text-slate-400 mt-0.5">
                  {isId
                    ? 'Promosikan kueri dengan umpan balik rendah (UNHELPFUL / CITATION_MISMATCH) langsung menjadi kasus uji evaluasi baru.'
                    : 'Promote low-rated queries (UNHELPFUL / CITATION_MISMATCH) directly into permanent evaluation benchmark regression cases.'}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-slate-400">Rating:</span>
                <select
                  value={fbFilterRating}
                  onChange={(e) => setFbFilterRating(e.target.value)}
                  className="rounded border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-white"
                >
                  <option value="ALL">ALL</option>
                  <option value="UNHELPFUL">UNHELPFUL</option>
                  <option value="CITATION_MISMATCH">CITATION_MISMATCH</option>
                  <option value="MISSING_INFO">MISSING_INFO</option>
                  <option value="HELPFUL">HELPFUL</option>
                </select>
              </div>
            </div>

            <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
              {feedbacks.map((fb) => (
                <div key={fb.id} className="rounded-lg border border-slate-800 bg-slate-950/60 p-3 space-y-2 text-xs">
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <span
                        className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                          fb.rating === 'HELPFUL'
                            ? 'bg-emerald-500/20 text-emerald-300'
                            : fb.rating === 'UNHELPFUL'
                            ? 'bg-rose-500/20 text-rose-300'
                            : 'bg-amber-500/20 text-amber-300'
                        }`}
                      >
                        {fb.rating}
                      </span>
                      <span className="font-mono text-slate-400 text-[11px]">{fb.collection_code}</span>
                    </div>
                    {fb.promoted_eval_case_id ? (
                      <span className="rounded bg-indigo-500/20 text-indigo-300 px-2 py-0.5 text-[10px] font-mono">
                        ✓ Promoted to Eval
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handlePromoteFeedbackToEval(fb)}
                        className="rounded border border-indigo-500/40 bg-indigo-950/40 hover:bg-indigo-900/60 px-2.5 py-1 text-[11px] font-medium text-indigo-200"
                      >
                        ⚡ {isId ? 'Promosikan ke Kasus Uji' : 'Promote to Benchmark Case'}
                      </button>
                    )}
                  </div>
                  <p className="font-medium text-white text-xs">{fb.question_text}</p>
                  {fb.answer_text && (
                    <div className="text-[11px] text-slate-400 line-clamp-2 bg-slate-900/40 p-1.5 rounded">
                      {fb.answer_text}
                    </div>
                  )}
                  {fb.comment && (
                    <div className="text-[10px] text-slate-500 italic">Note: {fb.comment}</div>
                  )}
                </div>
              ))}
              {feedbacks.length === 0 && (
                <div className="p-4 text-center text-slate-500 text-xs">
                  {isId ? 'Belum ada entri umpan balik pada filter ini.' : 'No feedback entries matching this filter.'}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
