import React, { useEffect, useState } from 'react';
import { adminApi } from '../api/client';
import type {
  DocumentBatchEstimateResult,
  DocumentComparisonResult,
  DocumentExtractionView,
  DocumentJobView,
  DocumentView,
  ExtractionConfigView,
  OcrBenchmarkResult,
} from '../api/types';
import { useLanguage } from '../i18n/LanguageContext';

type DocTab = 'playground' | 'documents' | 'review' | 'configs' | 'estimator' | 'benchmarks';

const PRESET_RECEIPT_ID = `TOKO NUSANTARA MART
No Struk: STR-2026-0891
Tanggal: 2026-10-31
1. Beras Ramos 5kg | 1 | 72000 | 72000
2. Minyak Goreng 2L | 2 | 34000 | 68000
Subtotal: Rp 140.000
PPN: Rp 15.400
Total: Rp 155.400
Tunai Diterima: Rp 160.000`;

const PRESET_INVOICE_ID = `FAKTUR PEMBELIAN / INVOICE
Vendor: PT SUMBER LOGISTIK PRIMA
Invoice Number: INV-2026-7731
Invoice Date: 2026-10-25
Due Date: 2026-11-24
1. Kardus Kemasan XL | 100 | 4500 | 450000
2. Lakban Bening Roll | 50 | 9000 | 450000
Subtotal: Rp 900.000
Tax: Rp 99.000
Total: Rp 999.000`;

const PRESET_DELIVERY = `SURAT JALAN / DELIVERY NOTE
Document Number: SJ-2026-3319
Sender: Gudang Pusat Cikarang
Recipient: Toko Cabang Bandung Utara
Delivery Date: 2026-10-30
1. Paket Elektronik A1 | 12 | 0 | 0
2. Paket Aksesoris B4 | 8 | 0 | 0
Total Quantity: 20`;

export default function DocumentsPage() {
  const { t } = useLanguage();
  const [tab, setTab] = useState<DocTab>('playground');
  const [error, setError] = useState<string | null>(null);

  // Playground state
  const [fileName, setFileName] = useState('peggy-receipt-id.txt');
  const [rawText, setRawText] = useState(PRESET_RECEIPT_ID);
  const [contentBase64, setContentBase64] = useState<string | undefined>(undefined);
  const [mimeType, setMimeType] = useState<string>('text/plain');
  const [ocrLanguage, setOcrLanguage] = useState('eng+ind');
  const [docTypeHint, setDocTypeHint] = useState('AUTO');
  const [pageRange, setPageRange] = useState('');
  const [rotateDegrees, setRotateDegrees] = useState(0);
  const [deskew, setDeskew] = useState(true);
  const [denoise, setDenoise] = useState(true);
  const [contrastEnhance, setContrastEnhance] = useState(true);
  const [runningPlayground, setRunningPlayground] = useState(false);
  const [playgroundResult, setPlaygroundResult] = useState<DocumentView | null>(null);

  // Documents & Jobs state
  const [documents, setDocuments] = useState<DocumentView[]>([]);
  const [jobs, setJobs] = useState<DocumentJobView[]>([]);
  const [selectedDoc, setSelectedDoc] = useState<DocumentView | null>(null);
  const [leftDocId, setLeftDocId] = useState('');
  const [rightDocId, setRightDocId] = useState('');
  const [comparison, setComparison] = useState<DocumentComparisonResult | null>(null);

  // Human Review Queue state
  const [reviewQueue, setReviewQueue] = useState<DocumentExtractionView[]>([]);
  const [editingExtractionId, setEditingExtractionId] = useState<string | null>(null);
  const [correctedJson, setCorrectedJson] = useState('{}');
  const [reviewNotes, setReviewNotes] = useState('');
  const [promoteGroundTruth, setPromoteGroundTruth] = useState(false);

  // Configs state
  const [configs, setConfigs] = useState<ExtractionConfigView[]>([]);
  const [cfgCode, setCfgCode] = useState('');
  const [cfgDocType, setCfgDocType] = useState('RECEIPT');
  const [cfgSchemaCode, setCfgSchemaCode] = useState('receipt_extraction_v1');
  const [cfgLang, setCfgLang] = useState('eng+ind');
  const [cfgThreshold, setCfgThreshold] = useState(0.75);
  const [cfgRetention, setCfgRetention] = useState(168);

  // Batch Estimator state
  const [estCount, setEstCount] = useState(10);
  const [estPages, setEstPages] = useState(25);
  const [estBytesMb, setEstBytesMb] = useState(15);
  const [estOcr, setEstOcr] = useState(true);
  const [estPrep, setEstPrep] = useState(true);
  const [estimateResult, setEstimateResult] = useState<DocumentBatchEstimateResult | null>(null);

  // Benchmarks state
  const [benchmarkResult, setBenchmarkResult] = useState<OcrBenchmarkResult | null>(null);
  const [runningBenchmarks, setRunningBenchmarks] = useState(false);

  const loadAll = async () => {
    setError(null);
    try {
      const [docsRes, jobsRes, revRes, cfgRes] = await Promise.all([
        adminApi.listDocuments({ limit: 30 }),
        adminApi.listDocumentJobs(),
        adminApi.listDocumentReviewQueue('PENDING_REVIEW'),
        adminApi.listDocumentConfigs(),
      ]);
      setDocuments(docsRes.data || []);
      setJobs(jobsRes.data || []);
      setReviewQueue(revRes.data || []);
      setConfigs(cfgRes.data || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load document intelligence state');
    }
  };

  useEffect(() => {
    void loadAll();
  }, []);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    setMimeType(file.type || 'application/octet-stream');
    const reader = new FileReader();
    reader.onload = () => {
      const res = typeof reader.result === 'string' ? reader.result : '';
      const base64 = res.includes(',') ? res.split(',')[1] : res;
      setContentBase64(base64);
      setRawText('');
    };
    reader.readAsDataURL(file);
  };

  const handleRunPlayground = async () => {
    setRunningPlayground(true);
    setError(null);
    try {
      const res = await adminApi.runDocumentPlayground({
        file_name: fileName,
        mime_type: mimeType,
        content_base64: contentBase64,
        raw_text: contentBase64 ? undefined : rawText,
        document_type_hint: docTypeHint === 'AUTO' ? undefined : docTypeHint,
        ocr_language: ocrLanguage,
        page_range: pageRange.trim() || undefined,
        preprocess: {
          rotate_degrees: rotateDegrees,
          deskew,
          denoise,
          contrast_enhance: contrastEnhance,
        },
      });
      setPlaygroundResult(res.data || null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Playground execution failed');
    } finally {
      setRunningPlayground(false);
    }
  };

  const handleRevealDocument = async (docId: string) => {
    setError(null);
    try {
      const res = await adminApi.getDocument(docId, true);
      setSelectedDoc(res.data || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to inspect document');
    }
  };

  const handleDeleteDocument = async (docId: string) => {
    setError(null);
    try {
      await adminApi.deleteDocument(docId);
      if (selectedDoc?.id === docId) setSelectedDoc(null);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete document');
    }
  };

  const handleResumeJob = async (jobId: string) => {
    setError(null);
    try {
      await adminApi.resumeDocumentJob(jobId);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to resume job');
    }
  };

  const handleCompare = async () => {
    if (!leftDocId || !rightDocId) return;
    setError(null);
    try {
      const res = await adminApi.compareDocuments(leftDocId, rightDocId);
      setComparison(res.data || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Document comparison failed');
    }
  };

  const handleReviewDecision = async (extractionId: string, decision: 'APPROVED' | 'CORRECTED' | 'REJECTED') => {
    setError(null);
    try {
      let parsedFields: Record<string, unknown> | undefined;
      if (decision === 'CORRECTED') {
        parsedFields = JSON.parse(correctedJson) as Record<string, unknown>;
      }
      await adminApi.decideDocumentReview(extractionId, {
        decision,
        corrected_fields: parsedFields,
        reviewer_notes: reviewNotes || undefined,
        promote_to_ground_truth: promoteGroundTruth,
      });
      setEditingExtractionId(null);
      setReviewNotes('');
      setPromoteGroundTruth(false);
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Review submission failed');
    }
  };

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await adminApi.saveDocumentConfig({
        config_code: cfgCode,
        document_type: cfgDocType,
        schema_code: cfgSchemaCode,
        default_ocr_language: cfgLang,
        confidence_threshold: cfgThreshold,
        auto_review_low_confidence: true,
        default_retention_hours: cfgRetention,
      });
      setCfgCode('');
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save extraction config');
    }
  };

  const handleEstimate = async () => {
    setError(null);
    try {
      const res = await adminApi.estimateDocumentBatch({
        document_count: estCount,
        estimated_pages: estPages,
        total_bytes: estBytesMb * 1024 * 1024,
        ocr_enabled: estOcr,
        preprocess_enabled: estPrep,
        llm_assist_enabled: false,
      });
      setEstimateResult(res.data || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Batch estimation failed');
    }
  };

  const handleRunBenchmarks = async () => {
    setRunningBenchmarks(true);
    setError(null);
    try {
      const res = await adminApi.runOcrBenchmarks();
      setBenchmarkResult(res.data || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Benchmark run failed');
    } finally {
      setRunningBenchmarks(false);
    }
  };

  const tabs: { id: DocTab; label: string; badge?: number }[] = [
    { id: 'playground', label: t('docs.tab.playground', 'OCR Playground') },
    { id: 'documents', label: t('docs.tab.documents', 'Documents & Jobs'), badge: documents.length },
    { id: 'review', label: t('docs.tab.review', 'Human Review Queue'), badge: reviewQueue.length },
    { id: 'configs', label: t('docs.tab.configs', 'Extraction Configs'), badge: configs.length },
    { id: 'estimator', label: t('docs.tab.estimator', 'Batch Estimator') },
    { id: 'benchmarks', label: t('docs.tab.benchmarks', 'OCR Benchmarks') },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">{t('docs.title', 'Document Intelligence & OCR')}</h1>
          <p className="text-sm text-slate-400 mt-1">
            {t(
              'docs.subtitle',
              'Local-first OCR (EN/ID), CV preprocessing, PDF page splitting, table extraction, human review queue & benchmarks'
            )}
          </p>
        </div>
        <button
          type="button"
          onClick={() => void loadAll()}
          className="rounded-lg border border-slate-700 bg-slate-800/80 px-3.5 py-2 text-xs font-medium text-slate-200 hover:bg-slate-700"
        >
          {t('action.refreshAll', 'Refresh All')}
        </button>
      </div>

      {error && (
        <div className="rounded-lg border border-rose-500/40 bg-rose-950/40 p-3.5 text-sm text-rose-200">{error}</div>
      )}

      <div className="flex flex-wrap gap-2 border-b border-slate-800 pb-3">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setTab(item.id)}
            className={`rounded-lg px-3.5 py-2 text-xs font-semibold transition flex items-center gap-2 ${
              tab === item.id
                ? 'bg-indigo-600 text-white shadow'
                : 'bg-slate-900 text-slate-300 border border-slate-800 hover:bg-slate-800'
            }`}
          >
            <span>{item.label}</span>
            {typeof item.badge === 'number' && (
              <span className="rounded-full bg-slate-950/60 px-1.5 py-0.5 text-[10px]">{item.badge}</span>
            )}
          </button>
        ))}
      </div>

      {/* TAB 1: OCR & DOCUMENT PLAYGROUND */}
      {tab === 'playground' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-white">Document Intake & OCR Controls</h2>
              <span className="rounded bg-emerald-500/15 px-2 py-0.5 text-[11px] font-medium text-emerald-300 border border-emerald-500/30">
                Read-Only Extraction Preview · Zero Payment Execution
              </span>
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => {
                  setFileName('peggy-receipt-id.txt');
                  setMimeType('text/plain');
                  setContentBase64(undefined);
                  setRawText(PRESET_RECEIPT_ID);
                  setOcrLanguage('ind');
                }}
                className="rounded border border-slate-700 bg-slate-800 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-700"
              >
                🇮🇩 Peggy Receipt (IDR)
              </button>
              <button
                type="button"
                onClick={() => {
                  setFileName('sms-invoice-id.txt');
                  setMimeType('text/plain');
                  setContentBase64(undefined);
                  setRawText(PRESET_INVOICE_ID);
                  setOcrLanguage('eng+ind');
                }}
                className="rounded border border-slate-700 bg-slate-800 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-700"
              >
                🧾 SMS Supplier Invoice
              </button>
              <button
                type="button"
                onClick={() => {
                  setFileName('worker-surat-jalan.txt');
                  setMimeType('text/plain');
                  setContentBase64(undefined);
                  setRawText(PRESET_DELIVERY);
                  setOcrLanguage('eng+ind');
                }}
                className="rounded border border-slate-700 bg-slate-800 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-700"
              >
                📦 Worker Surat Jalan
              </button>
            </div>

            <div>
              <label className="block text-xs text-slate-400 mb-1">Upload PDF / Image / Text File (Optional)</label>
              <input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.bmp,.webp,.txt,.csv,.md"
                onChange={handleFileUpload}
                className="w-full text-xs text-slate-300 file:mr-3 file:rounded-lg file:border-0 file:bg-indigo-600 file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-white"
              />
              {contentBase64 && (
                <div className="mt-1 text-[11px] text-indigo-300">
                  Loaded binary file: <span className="font-mono">{fileName}</span> ({mimeType})
                </div>
              )}
            </div>

            {!contentBase64 && (
              <div>
                <label className="block text-xs text-slate-400 mb-1">Sample Document Content</label>
                <textarea
                  rows={7}
                  value={rawText}
                  onChange={(e) => setRawText(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 p-2.5 font-mono text-xs text-slate-200"
                />
              </div>
            )}

            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs text-slate-400 mb-1">OCR Language</label>
                <select
                  value={ocrLanguage}
                  onChange={(e) => setOcrLanguage(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-200"
                >
                  <option value="eng+ind">Bilingual (eng+ind)</option>
                  <option value="ind">Bahasa Indonesia (ind)</option>
                  <option value="eng">English (eng)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Type Classification</label>
                <select
                  value={docTypeHint}
                  onChange={(e) => setDocTypeHint(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-200"
                >
                  <option value="AUTO">Auto-Classify</option>
                  <option value="RECEIPT">RECEIPT</option>
                  <option value="INVOICE">INVOICE</option>
                  <option value="DELIVERY_NOTE">DELIVERY_NOTE</option>
                  <option value="FORM">FORM</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">PDF Page Range</label>
                <input
                  type="text"
                  placeholder="e.g. 1-3 or 1,3"
                  value={pageRange}
                  onChange={(e) => setPageRange(e.target.value)}
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-200"
                />
              </div>
            </div>

            <div className="rounded-lg border border-slate-800 bg-slate-950/60 p-3 space-y-2">
              <div className="text-xs font-medium text-slate-300">Image CV Preprocessing</div>
              <div className="flex flex-wrap items-center gap-4 text-xs text-slate-300">
                <label className="flex items-center gap-1.5">
                  <span>Rotate:</span>
                  <select
                    value={rotateDegrees}
                    onChange={(e) => setRotateDegrees(Number(e.target.value))}
                    className="rounded border border-slate-700 bg-slate-900 px-2 py-0.5 text-xs"
                  >
                    <option value={0}>0°</option>
                    <option value={90}>90°</option>
                    <option value={180}>180°</option>
                    <option value={270}>270°</option>
                  </select>
                </label>
                <label className="flex items-center gap-1.5">
                  <input type="checkbox" checked={deskew} onChange={(e) => setDeskew(e.target.checked)} />
                  <span>Deskew</span>
                </label>
                <label className="flex items-center gap-1.5">
                  <input type="checkbox" checked={denoise} onChange={(e) => setDenoise(e.target.checked)} />
                  <span>3x3 Median Denoise</span>
                </label>
                <label className="flex items-center gap-1.5">
                  <input
                    type="checkbox"
                    checked={contrastEnhance}
                    onChange={(e) => setContrastEnhance(e.target.checked)}
                  />
                  <span>Auto-Contrast</span>
                </label>
              </div>
            </div>

            <button
              type="button"
              disabled={runningPlayground}
              onClick={() => void handleRunPlayground()}
              className="w-full rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
            >
              {runningPlayground ? 'Processing OCR & Schema Extraction…' : 'Run Document Intelligence Pipeline'}
            </button>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
            <h2 className="text-base font-semibold text-white">Structured Extraction & Provenance Output</h2>
            {!playgroundResult ? (
              <div className="text-xs text-slate-400 py-12 text-center">
                Execute a document or preset on the left to inspect classification, structured fields, provenance, line items, and page OCR diagnostics.
              </div>
            ) : (
              <div className="space-y-4 text-xs">
                <div className="flex flex-wrap gap-2 items-center">
                  <span className="rounded bg-indigo-500/20 px-2 py-0.5 font-semibold text-indigo-200">
                    Type: {playgroundResult.document_type} ({Math.round((playgroundResult.classification_confidence || 0) * 100)}%)
                  </span>
                  <span
                    className={`rounded px-2 py-0.5 font-semibold ${
                      playgroundResult.extraction?.schema_valid
                        ? 'bg-emerald-500/20 text-emerald-300'
                        : 'bg-amber-500/20 text-amber-300'
                    }`}
                  >
                    Schema Valid: {String(Boolean(playgroundResult.extraction?.schema_valid))}
                  </span>
                  <span className="rounded bg-slate-800 px-2 py-0.5 text-slate-300">
                    Confidence: {Math.round((playgroundResult.extraction?.overall_confidence || 0) * 100)}%
                  </span>
                  <span className="rounded bg-slate-800 px-2 py-0.5 text-slate-300">
                    Review: {playgroundResult.extraction?.review_status}
                  </span>
                  {playgroundResult.is_duplicate && (
                    <span className="rounded bg-amber-500/20 px-2 py-0.5 text-amber-300">
                      Duplicate ({playgroundResult.duplicate_type})
                    </span>
                  )}
                </div>

                {playgroundResult.extraction && (
                  <div className="space-y-2">
                    <div className="font-semibold text-slate-200">Extracted Fields & Provenance</div>
                    <div className="overflow-x-auto rounded-lg border border-slate-800">
                      <table className="w-full text-left text-xs">
                        <thead className="bg-slate-950 text-slate-400">
                          <tr>
                            <th className="p-2">Field</th>
                            <th className="p-2">Value</th>
                            <th className="p-2">Status</th>
                            <th className="p-2">Provenance</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800">
                          {Object.entries(playgroundResult.extraction.field_details).map(([key, detail]) => (
                            <tr key={key}>
                              <td className="p-2 font-mono text-indigo-300">{key}</td>
                              <td className="p-2 text-slate-100">{detail.value !== null && detail.value !== undefined ? String(detail.value) : '—'}</td>
                              <td className="p-2">
                                <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px]">{detail.status}</span>
                              </td>
                              <td className="p-2 text-[11px] text-slate-400">
                                {detail.provenance
                                  ? `P${detail.provenance.page_number}:L${detail.provenance.line_number} (${detail.provenance.region_hint})`
                                  : '—'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {playgroundResult.extraction && playgroundResult.extraction.line_items.length > 0 && (
                  <div className="space-y-1.5">
                    <div className="font-semibold text-slate-200">
                      Extracted Line Items ({playgroundResult.extraction.line_items.length})
                    </div>
                    <div className="overflow-x-auto rounded-lg border border-slate-800">
                      <table className="w-full text-left text-xs">
                        <thead className="bg-slate-950 text-slate-400">
                          <tr>
                            <th className="p-2">Item</th>
                            <th className="p-2">Qty</th>
                            <th className="p-2">Unit Price</th>
                            <th className="p-2">Amount</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800">
                          {playgroundResult.extraction.line_items.map((li) => (
                            <tr key={li.index}>
                              <td className="p-2 text-slate-200">{li.description}</td>
                              <td className="p-2">{li.quantity ?? '—'}</td>
                              <td className="p-2">{li.unit_price ?? '—'}</td>
                              <td className="p-2 font-medium text-emerald-300">{li.amount ?? '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {playgroundResult.extraction && playgroundResult.extraction.warnings.length > 0 && (
                  <div className="rounded-lg border border-amber-500/30 bg-amber-950/30 p-2.5 text-amber-200 space-y-1">
                    {playgroundResult.extraction.warnings.map((w, idx) => (
                      <div key={idx}>⚠️ {w}</div>
                    ))}
                  </div>
                )}

                <div className="space-y-1">
                  <div className="font-semibold text-slate-200">Page-Level OCR Diagnostics</div>
                  {playgroundResult.pages.map((p) => (
                    <div key={p.id} className="rounded border border-slate-800 bg-slate-950 p-2 text-[11px] text-slate-300">
                      Page {p.page_number} · Engine: <span className="font-mono">{p.engine_used}</span> · Confidence:{' '}
                      {Math.round((p.ocr_confidence || 0) * 100)}% · Steps:{' '}
                      {p.preprocess_applied.length > 0 ? p.preprocess_applied.join(' → ') : 'native'}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: DOCUMENTS, COMPARISON & RESUMABLE JOBS */}
      {tab === 'documents' && (
        <div className="space-y-6">
          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-3">
            <h2 className="text-base font-semibold text-white">Document Comparison & Change Detection</h2>
            <div className="flex flex-wrap gap-3 items-end">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Left Document</label>
                <select
                  value={leftDocId}
                  onChange={(e) => setLeftDocId(e.target.value)}
                  className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-slate-200"
                >
                  <option value="">Select document…</option>
                  {documents.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.file_name} ({d.document_type})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Right Document</label>
                <select
                  value={rightDocId}
                  onChange={(e) => setRightDocId(e.target.value)}
                  className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-slate-200"
                >
                  <option value="">Select document…</option>
                  {documents.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.file_name} ({d.document_type})
                    </option>
                  ))}
                </select>
              </div>
              <button
                type="button"
                onClick={() => void handleCompare()}
                className="rounded-lg bg-indigo-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500"
              >
                Compare Documents
              </button>
            </div>
            {comparison && (
              <div className="rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs space-y-2">
                <div className="text-slate-200">
                  Text Similarity: <span className="font-bold">{Math.round(comparison.text_similarity_score * 100)}%</span> ·
                  Identical Bytes: <span className="font-mono">{String(comparison.identical_bytes)}</span> · Page Delta:{' '}
                  {comparison.page_count_delta}
                </div>
                <div className="space-y-1">
                  {comparison.field_changes
                    .filter((fc) => fc.change_type !== 'UNCHANGED')
                    .map((fc) => (
                      <div key={fc.field_name} className="font-mono text-[11px] text-amber-300">
                        [{fc.change_type}] {fc.field_name}: {String(fc.left_value ?? 'null')} → {String(fc.right_value ?? 'null')}
                      </div>
                    ))}
                </div>
              </div>
            )}
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-3">
            <h2 className="text-base font-semibold text-white">Processed Documents (Raw Text Redacted by Default)</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400">
                  <tr>
                    <th className="p-2.5">File</th>
                    <th className="p-2.5">Type</th>
                    <th className="p-2.5">Pages</th>
                    <th className="p-2.5">Status</th>
                    <th className="p-2.5">Duplicate</th>
                    <th className="p-2.5">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {documents.map((d) => (
                    <tr key={d.id}>
                      <td className="p-2.5 font-medium text-slate-200">{d.file_name}</td>
                      <td className="p-2.5">{d.document_type}</td>
                      <td className="p-2.5">{d.page_count}</td>
                      <td className="p-2.5">{d.status}</td>
                      <td className="p-2.5">{d.is_duplicate ? d.duplicate_type : 'Unique'}</td>
                      <td className="p-2.5 flex gap-2">
                        <button
                          type="button"
                          onClick={() => void handleRevealDocument(d.id)}
                          className="rounded border border-indigo-500/40 px-2 py-0.5 text-[11px] text-indigo-300 hover:bg-indigo-500/10"
                        >
                          Audited Reveal
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleDeleteDocument(d.id)}
                          className="rounded border border-rose-500/40 px-2 py-0.5 text-[11px] text-rose-300 hover:bg-rose-500/10"
                        >
                          Purge
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {selectedDoc && (
              <div className="rounded-lg border border-indigo-500/30 bg-slate-950 p-3 text-xs space-y-2">
                <div className="font-semibold text-indigo-200">
                  Audited Reveal: {selectedDoc.file_name} (logged to ai_audit_logs)
                </div>
                {selectedDoc.pages.map((p) => (
                  <pre key={p.id} className="whitespace-pre-wrap font-mono text-[11px] text-slate-300 bg-slate-900 p-2 rounded">
                    {p.extracted_text}
                  </pre>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-3">
            <h2 className="text-base font-semibold text-white">Durable & Resumable Document Jobs</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400">
                  <tr>
                    <th className="p-2.5">Job ID</th>
                    <th className="p-2.5">Status</th>
                    <th className="p-2.5">Progress</th>
                    <th className="p-2.5">Checkpoint</th>
                    <th className="p-2.5">Attempts</th>
                    <th className="p-2.5">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {jobs.map((j) => (
                    <tr key={j.id}>
                      <td className="p-2.5 font-mono text-[11px]">{j.id.slice(0, 8)}…</td>
                      <td className="p-2.5">{j.status}</td>
                      <td className="p-2.5">
                        {j.processed_pages}/{j.total_pages} (failed: {j.failed_pages})
                      </td>
                      <td className="p-2.5">Page {j.last_completed_page}</td>
                      <td className="p-2.5">
                        {j.attempt_count}/{j.max_attempts}
                      </td>
                      <td className="p-2.5">
                        {(j.status === 'FAILED' || j.status === 'PARTIAL_SUCCESS') && (
                          <button
                            type="button"
                            onClick={() => void handleResumeJob(j.id)}
                            className="rounded bg-amber-600 px-2.5 py-1 text-[11px] font-semibold text-white hover:bg-amber-500"
                          >
                            Resume from Checkpoint
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

      {/* TAB 3: HUMAN REVIEW QUEUE */}
      {tab === 'review' && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
          <h2 className="text-base font-semibold text-white">Human Review Queue (Low-Confidence & Incomplete Extractions)</h2>
          {reviewQueue.length === 0 ? (
            <div className="text-xs text-slate-400 py-8 text-center">No extractions awaiting human review.</div>
          ) : (
            <div className="space-y-4">
              {reviewQueue.map((item) => (
                <div key={item.id} className="rounded-lg border border-slate-800 bg-slate-950 p-4 space-y-3 text-xs">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="font-semibold text-white">
                      {item.document_type} · Confidence: {Math.round(item.overall_confidence * 100)}%
                    </div>
                    <div className="text-amber-300">{item.review_reasons.join(', ')}</div>
                  </div>
                  <pre className="rounded bg-slate-900 p-2.5 font-mono text-[11px] text-slate-300 overflow-x-auto">
                    {JSON.stringify(item.extracted_fields, null, 2)}
                  </pre>

                  {editingExtractionId === item.id ? (
                    <div className="space-y-2">
                      <textarea
                        rows={5}
                        value={correctedJson}
                        onChange={(e) => setCorrectedJson(e.target.value)}
                        className="w-full rounded border border-slate-700 bg-slate-900 p-2 font-mono text-xs text-slate-100"
                      />
                      <input
                        type="text"
                        placeholder="Reviewer notes…"
                        value={reviewNotes}
                        onChange={(e) => setReviewNotes(e.target.value)}
                        className="w-full rounded border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-xs text-slate-200"
                      />
                      <label className="flex items-center gap-2 text-slate-300">
                        <input
                          type="checkbox"
                          checked={promoteGroundTruth}
                          onChange={(e) => setPromoteGroundTruth(e.target.checked)}
                        />
                        <span>Promote corrected fields to OCR Regression Ground Truth dataset</span>
                      </label>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => void handleReviewDecision(item.id, 'CORRECTED')}
                          className="rounded bg-emerald-600 px-3 py-1.5 font-semibold text-white"
                        >
                          Save Corrected Fields
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingExtractionId(null)}
                          className="rounded border border-slate-700 px-3 py-1.5 text-slate-300"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => void handleReviewDecision(item.id, 'APPROVED')}
                        className="rounded bg-emerald-600/80 px-3 py-1 text-white hover:bg-emerald-600"
                      >
                        Approve As-Is
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setEditingExtractionId(item.id);
                          setCorrectedJson(JSON.stringify(item.extracted_fields, null, 2));
                        }}
                        className="rounded bg-indigo-600 px-3 py-1 text-white hover:bg-indigo-500"
                      >
                        Edit & Correct
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleReviewDecision(item.id, 'REJECTED')}
                        className="rounded border border-rose-500/40 px-3 py-1 text-rose-300 hover:bg-rose-950"
                      >
                        Reject
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 4: EXTRACTION SCHEMA CONFIGS */}
      {tab === 'configs' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <form onSubmit={(e) => void handleSaveConfig(e)} className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-3 text-xs">
            <h2 className="text-base font-semibold text-white">Configure Extraction Schema</h2>
            <div>
              <label className="block text-slate-400 mb-1">Config Code</label>
              <input
                required
                value={cfgCode}
                onChange={(e) => setCfgCode(e.target.value)}
                placeholder="peggy_receipt_custom"
                className="w-full rounded border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-slate-200"
              />
            </div>
            <div>
              <label className="block text-slate-400 mb-1">Document Type</label>
              <select
                value={cfgDocType}
                onChange={(e) => setCfgDocType(e.target.value)}
                className="w-full rounded border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-slate-200"
              >
                <option value="RECEIPT">RECEIPT</option>
                <option value="INVOICE">INVOICE</option>
                <option value="DELIVERY_NOTE">DELIVERY_NOTE</option>
                <option value="FORM">FORM</option>
              </select>
            </div>
            <div>
              <label className="block text-slate-400 mb-1">Schema Code</label>
              <input
                required
                value={cfgSchemaCode}
                onChange={(e) => setCfgSchemaCode(e.target.value)}
                className="w-full rounded border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-slate-200"
              />
            </div>
            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="block text-slate-400 mb-1">OCR Lang</label>
                <input
                  value={cfgLang}
                  onChange={(e) => setCfgLang(e.target.value)}
                  className="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-slate-200"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Min Conf</label>
                <input
                  type="number"
                  step="0.05"
                  min="0.1"
                  max="1.0"
                  value={cfgThreshold}
                  onChange={(e) => setCfgThreshold(Number(e.target.value))}
                  className="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-slate-200"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Retain (h)</label>
                <input
                  type="number"
                  min="1"
                  value={cfgRetention}
                  onChange={(e) => setCfgRetention(Number(e.target.value))}
                  className="w-full rounded border border-slate-700 bg-slate-950 px-2 py-1.5 text-slate-200"
                />
              </div>
            </div>
            <button type="submit" className="w-full rounded-lg bg-indigo-600 py-2 font-semibold text-white hover:bg-indigo-500">
              Save Extraction Policy
            </button>
          </form>

          <div className="lg:col-span-2 rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-3">
            <h2 className="text-base font-semibold text-white">Active Document Type & Schema Policies</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400">
                  <tr>
                    <th className="p-2.5">Code</th>
                    <th className="p-2.5">Doc Type</th>
                    <th className="p-2.5">Schema</th>
                    <th className="p-2.5">OCR Lang</th>
                    <th className="p-2.5">Review Threshold</th>
                    <th className="p-2.5">Retention</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {configs.map((c) => (
                    <tr key={c.id}>
                      <td className="p-2.5 font-mono text-indigo-300">{c.config_code}</td>
                      <td className="p-2.5">{c.document_type}</td>
                      <td className="p-2.5 font-mono">{c.schema_code}</td>
                      <td className="p-2.5">{c.default_ocr_language}</td>
                      <td className="p-2.5">{Math.round(c.confidence_threshold * 100)}%</td>
                      <td className="p-2.5">{c.default_retention_hours}h</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 5: BATCH RESOURCE ESTIMATOR */}
      {tab === 'estimator' && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4 max-w-2xl">
          <h2 className="text-base font-semibold text-white">Pre-Batch Compute & Resource Estimator</h2>
          <div className="grid grid-cols-3 gap-3 text-xs">
            <div>
              <label className="block text-slate-400 mb-1">Document Count</label>
              <input
                type="number"
                min={1}
                value={estCount}
                onChange={(e) => setEstCount(Number(e.target.value))}
                className="w-full rounded border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-slate-200"
              />
            </div>
            <div>
              <label className="block text-slate-400 mb-1">Estimated Pages</label>
              <input
                type="number"
                min={1}
                value={estPages}
                onChange={(e) => setEstPages(Number(e.target.value))}
                className="w-full rounded border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-slate-200"
              />
            </div>
            <div>
              <label className="block text-slate-400 mb-1">Total Payload (MB)</label>
              <input
                type="number"
                min={1}
                value={estBytesMb}
                onChange={(e) => setEstBytesMb(Number(e.target.value))}
                className="w-full rounded border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-slate-200"
              />
            </div>
          </div>
          <div className="flex gap-4 text-xs text-slate-300">
            <label className="flex items-center gap-1.5">
              <input type="checkbox" checked={estOcr} onChange={(e) => setEstOcr(e.target.checked)} />
              <span>OCR Pass Enabled</span>
            </label>
            <label className="flex items-center gap-1.5">
              <input type="checkbox" checked={estPrep} onChange={(e) => setEstPrep(e.target.checked)} />
              <span>Image CV Preprocessing</span>
            </label>
          </div>
          <button
            type="button"
            onClick={() => void handleEstimate()}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500"
          >
            Calculate Estimate
          </button>

          {estimateResult && (
            <div className="rounded-lg border border-slate-800 bg-slate-950 p-4 text-xs space-y-2">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <div>
                  <div className="text-slate-400">Est. Duration</div>
                  <div className="text-base font-bold text-white">{(estimateResult.estimated_duration_ms / 1000).toFixed(1)}s</div>
                </div>
                <div>
                  <div className="text-slate-400">Peak RAM</div>
                  <div className="text-base font-bold text-white">{estimateResult.estimated_memory_mb} MB</div>
                </div>
                <div>
                  <div className="text-slate-400">External Cost</div>
                  <div className="text-base font-bold text-emerald-400">$0.00 (Local)</div>
                </div>
                <div>
                  <div className="text-slate-400">Recommended Mode</div>
                  <div className="text-sm font-mono text-indigo-300">{estimateResult.recommended_execution_mode}</div>
                </div>
              </div>
              <div className="text-slate-400 pt-1">{estimateResult.guidance_note}</div>
            </div>
          )}
        </div>
      )}

      {/* TAB 6: OCR REGRESSION BENCHMARKS */}
      {tab === 'benchmarks' && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-white">OCR & Extraction Regression Dataset Suite</h2>
              <p className="text-xs text-slate-400">
                Evaluates receipts (ID/EN), supplier invoices, worker delivery notes, forms, and promoted ground-truth cases.
              </p>
            </div>
            <button
              type="button"
              disabled={runningBenchmarks}
              onClick={() => void handleRunBenchmarks()}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
            >
              {runningBenchmarks ? 'Running Suite…' : 'Run Regression Benchmarks'}
            </button>
          </div>

          {benchmarkResult && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-slate-400">Classification Accuracy</div>
                  <div className="text-xl font-bold text-emerald-400">
                    {Math.round(benchmarkResult.classification_accuracy * 100)}%
                  </div>
                </div>
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-slate-400">Field Extraction Accuracy</div>
                  <div className="text-xl font-bold text-emerald-400">
                    {Math.round(benchmarkResult.field_accuracy * 100)}%
                  </div>
                </div>
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-slate-400">Schema-Valid Rate</div>
                  <div className="text-xl font-bold text-indigo-300">
                    {Math.round(benchmarkResult.schema_valid_rate * 100)}%
                  </div>
                </div>
                <div className="rounded-lg border border-slate-800 bg-slate-950 p-3">
                  <div className="text-slate-400">Cases Executed</div>
                  <div className="text-xl font-bold text-white">
                    {benchmarkResult.total_cases} ({benchmarkResult.total_duration_ms} ms)
                  </div>
                </div>
              </div>

              <div className="overflow-x-auto rounded-lg border border-slate-800">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400">
                    <tr>
                      <th className="p-2.5">Case</th>
                      <th className="p-2.5">Source</th>
                      <th className="p-2.5">Predicted Type</th>
                      <th className="p-2.5">Schema Valid</th>
                      <th className="p-2.5">Field Match</th>
                      <th className="p-2.5">Line Items</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800">
                    {benchmarkResult.cases.map((c) => (
                      <tr key={c.case_code}>
                        <td className="p-2.5 font-medium text-slate-200">{c.name}</td>
                        <td className="p-2.5 font-mono text-[11px]">{c.source_type}</td>
                        <td className="p-2.5">{c.predicted_type}</td>
                        <td className="p-2.5">{c.schema_valid ? '✅ Valid' : '❌ Invalid'}</td>
                        <td className="p-2.5">
                          {c.matched_fields}/{c.total_expected_fields} ({Math.round(c.field_accuracy * 100)}%)
                        </td>
                        <td className="p-2.5">{c.extracted_line_items}</td>
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
  );
}

