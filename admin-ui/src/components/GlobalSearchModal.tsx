import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { NavTab } from './AppShell';

export interface SearchResultItem {
  id: string;
  title: string;
  description: string;
  category: 'Tabs' | 'Applications' | 'Models & AI' | 'Orchestration' | 'Documents';
  tab: NavTab;
  shortcut?: string;
  badge?: string;
}

const SEARCH_INDEX: SearchResultItem[] = [
  // Navigation Tabs
  { id: 'tab-overview', title: 'Platform Overview', description: 'System health, quotas, active applications, and maintenance', category: 'Tabs', tab: 'overview', shortcut: '1' },
  { id: 'tab-apps', title: 'Applications & Keys', description: 'Client applications, HMAC credentials, and capability entitlements', category: 'Tabs', tab: 'applications', shortcut: '2' },
  { id: 'tab-integrations', title: 'App Integrations & Readiness', description: 'Client system adapters (Peggy, SMS, Worker, E-commerce)', category: 'Tabs', tab: 'integrations', shortcut: '3' },
  { id: 'tab-models', title: 'Models & Providers', description: 'Local LLM runtimes, cloud providers, routing policies, and playground', category: 'Tabs', tab: 'models', shortcut: '4' },
  { id: 'tab-orchestration', title: 'AI Orchestration & Schemas', description: 'Tasks, prompt versioning, JSON schema validation, and traces', category: 'Tabs', tab: 'orchestration', shortcut: '5' },
  { id: 'tab-documents', title: 'Documents & OCR', description: 'Document extraction, OCR playground, review queue, and benchmarks', category: 'Tabs', tab: 'documents', shortcut: '6' },
  { id: 'tab-knowledge', title: 'Knowledge & RAG', description: 'Knowledge collections, embeddings, chunking preview, and retrieval debugger', category: 'Tabs', tab: 'knowledge', shortcut: '7' },
  { id: 'tab-analytics', title: 'Data & Analytics', description: 'Financial trends, inventory analytics, and metric aggregations', category: 'Tabs', tab: 'analytics', shortcut: '8' },
  { id: 'tab-decision', title: 'Decisions & Optimization', description: 'Task assignment, delivery routing, and replenishment recommendations', category: 'Tabs', tab: 'decision', shortcut: '9' },
  { id: 'tab-customer', title: 'Customer Intelligence', description: 'Customer inquiry triage, FAQ policies, and interaction history', category: 'Tabs', tab: 'customer' },
  { id: 'tab-tenants', title: 'Tenants & Isolation', description: 'Tenant management, quota tiers, and isolated data scopes', category: 'Tabs', tab: 'tenants' },
  { id: 'tab-feature-flags', title: 'Feature Flags', description: 'Capability rollout toggles, beta features, and gradual rollouts', category: 'Tabs', tab: 'feature-flags' },
  { id: 'tab-audit', title: 'Audit & Security', description: 'Sanitized compliance logs, authentication audits, and security alerts', category: 'Tabs', tab: 'audit' },

  // Applications
  { id: 'app-peggy', title: 'Peggy Financial Assistant', description: 'Personal finance & budget management (strictly non-banking)', category: 'Applications', tab: 'applications', badge: 'PROD' },
  { id: 'app-sms', title: 'Store Management System (SMS)', description: 'Store operations, stock replenishment, and sales intelligence', category: 'Applications', tab: 'applications', badge: 'STAGING' },
  { id: 'app-worker', title: 'Worker Logistics & Delivery App', description: 'Field delivery proof, package OCR, and route optimization', category: 'Applications', tab: 'applications', badge: 'PROD' },
  { id: 'app-ecommerce', title: 'E-commerce Customer Support', description: 'Product inquiries, return policy FAQ, and customer triage', category: 'Applications', tab: 'applications', badge: 'DEV' },

  // Models & AI
  { id: 'mod-ollama', title: 'Ollama Local Provider', description: 'Primary local offline inference runtime (http://localhost:11434)', category: 'Models & AI', tab: 'models', badge: 'HEALTHY' },
  { id: 'mod-vllm', title: 'vLLM High-Throughput Engine', description: 'High-concurrency batching inference server (http://localhost:8000)', category: 'Models & AI', tab: 'models', badge: 'HEALTHY' },
  { id: 'mod-qwen', title: 'Qwen 2.5 Coder 7B', description: 'Local structured output & JSON extraction specialized model', category: 'Models & AI', tab: 'models', badge: 'LOCAL' },
  { id: 'mod-llama', title: 'Llama 3.1 8B Instruct', description: 'Local conversational intelligence & general reasoning model', category: 'Models & AI', tab: 'models', badge: 'LOCAL' },
  { id: 'mod-circuit', title: 'Circuit Breaker Status', description: 'Provider failover safeguards, error rates, and cooldown timers', category: 'Models & AI', tab: 'models' },

  // Orchestration & Schemas
  { id: 'orch-prompts', title: 'Prompt Version Comparison', description: 'Versioned prompt templates with side-by-side diff comparison', category: 'Orchestration', tab: 'orchestration' },
  { id: 'orch-schemas', title: 'JSON Schema Live Validator', description: 'Validate JSON payloads against strict schema contracts in real time', category: 'Orchestration', tab: 'orchestration' },
  { id: 'orch-traces', title: 'Execution Trace Timeline', description: 'Step durations, tool invocation latencies, and execution traces', category: 'Orchestration', tab: 'orchestration' },
  { id: 'orch-tools', title: 'Tool Registry Playground', description: 'Safe application tools execution sandbox with parameter validation', category: 'Orchestration', tab: 'orchestration' },

  // Documents & OCR
  { id: 'doc-ocr', title: 'OCR Playground & Image CV', description: 'Extract text and tabular data from receipts, invoices, and delivery notes', category: 'Documents', tab: 'documents' },
  { id: 'doc-review', title: 'Human Review Queue', description: 'Review low-confidence extractions, edit values, and promote ground truth', category: 'Documents', tab: 'documents' },
  { id: 'doc-benchmarks', title: 'OCR Engine Benchmarks', description: 'Compare Tesseract vs rule-based parser accuracy and latency', category: 'Documents', tab: 'documents' },
];

export interface GlobalSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectTab: (tab: NavTab) => void;
}

export function GlobalSearchModal({ isOpen, onClose, onSelectTab }: GlobalSearchModalProps) {
  const [query, setQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setActiveIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  const categories = ['ALL', 'Tabs', 'Applications', 'Models & AI', 'Orchestration', 'Documents'];

  const filteredItems = useMemo(() => {
    const q = query.toLowerCase().trim();
    return SEARCH_INDEX.filter((item) => {
      const matchesCategory = selectedCategory === 'ALL' || item.category === selectedCategory;
      const matchesQuery =
        !q ||
        item.title.toLowerCase().includes(q) ||
        item.description.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q) ||
        (item.badge && item.badge.toLowerCase().includes(q));
      return matchesCategory && matchesQuery;
    });
  }, [query, selectedCategory]);

  useEffect(() => {
    setActiveIndex(0);
  }, [filteredItems.length]);

  const handleSelect = (item: SearchResultItem) => {
    onSelectTab(item.tab);
    onClose();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((prev) => (prev + 1) % Math.max(1, filteredItems.length));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((prev) => (prev - 1 + filteredItems.length) % Math.max(1, filteredItems.length));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (filteredItems[activeIndex]) {
        handleSelect(filteredItems[activeIndex]);
      }
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Global Command Palette Search"
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/70 p-4 pt-16 sm:pt-24 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl rounded-2xl border border-slate-700 bg-slate-900 shadow-2xl overflow-hidden flex flex-col max-h-[80vh]"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        {/* Search Input Bar */}
        <div className="flex items-center gap-3 border-b border-slate-800 px-4 py-3.5 bg-slate-950/80">
          <svg className="w-5 h-5 text-indigo-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search commands, applications, models, schemas, documents..."
            className="w-full bg-transparent text-sm text-white placeholder-slate-400 focus:outline-none"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="text-xs text-slate-400 hover:text-slate-200 px-1.5 py-0.5 rounded"
            >
              Clear
            </button>
          )}
          <kbd className="hidden sm:inline-block rounded bg-slate-800 px-2 py-0.5 text-[10px] font-mono text-slate-400 border border-slate-700">
            ESC
          </kbd>
        </div>

        {/* Category Pills */}
        <div className="flex items-center gap-1.5 px-4 py-2 border-b border-slate-800/80 bg-slate-900/90 overflow-x-auto text-xs">
          {categories.map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setSelectedCategory(cat)}
              className={`rounded-lg px-2.5 py-1 text-xs font-medium whitespace-nowrap transition ${
                selectedCategory === cat
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-800/60 text-slate-300 hover:bg-slate-800 hover:text-white'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Search Results List */}
        <div className="overflow-y-auto flex-1 p-2 space-y-1 divide-y divide-slate-800/40">
          {filteredItems.length === 0 ? (
            <div className="p-8 text-center">
              <svg className="mx-auto h-8 w-8 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="mt-2 text-sm font-medium text-slate-300">No results found</p>
              <p className="text-xs text-slate-500 mt-1">Try another keyword or filter by category.</p>
            </div>
          ) : (
            filteredItems.map((item, idx) => {
              const isSelected = idx === activeIndex;
              return (
                <div
                  key={item.id}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setActiveIndex(idx)}
                  className={`flex items-center justify-between gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition ${
                    isSelected ? 'bg-indigo-600/20 border border-indigo-500/40 text-white' : 'text-slate-300 hover:bg-slate-800/50'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 text-xs font-bold ${
                        isSelected ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-300'
                      }`}
                    >
                      {item.category[0]}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold truncate text-white">{item.title}</span>
                        {item.badge && (
                          <span className="rounded bg-slate-800 border border-slate-700 px-1.5 py-0.2 text-[10px] font-mono text-indigo-300">
                            {item.badge}
                          </span>
                        )}
                        <span className="text-[10px] text-slate-500 rounded bg-slate-800/50 px-1.5 py-0.5">
                          {item.category}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 truncate mt-0.5">{item.description}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    {item.shortcut && (
                      <kbd className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px] font-mono text-slate-400 border border-slate-700">
                        {item.shortcut}
                      </kbd>
                    )}
                    <span className="text-xs text-indigo-400 opacity-0 group-hover:opacity-100">↵</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer shortcuts info */}
        <div className="border-t border-slate-800 px-4 py-2 bg-slate-950 flex items-center justify-between text-[11px] text-slate-400">
          <div className="flex items-center gap-3">
            <span><kbd className="bg-slate-800 px-1 rounded text-slate-300">↑↓</kbd> to navigate</span>
            <span><kbd className="bg-slate-800 px-1 rounded text-slate-300">↵</kbd> to select</span>
            <span><kbd className="bg-slate-800 px-1 rounded text-slate-300">esc</kbd> to close</span>
          </div>
          <span className="text-slate-500">{filteredItems.length} items</span>
        </div>
      </div>
    </div>
  );
}

