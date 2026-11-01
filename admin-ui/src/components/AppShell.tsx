import React, { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { GlobalSearchModal } from './GlobalSearchModal';
import { KeyboardShortcutsModal } from './KeyboardShortcutsModal';

export type NavTab =
  | 'overview'
  | 'applications'
  | 'integrations'
  | 'models'
  | 'orchestration'
  | 'documents'
  | 'knowledge'
  | 'analytics'
  | 'decision'
  | 'customer'
  | 'conversations'
  | 'tenants'
  | 'feature-flags'
  | 'audit';

export function AppShell({
  activeTab,
  onSelectTab,
  children,
}: {
  activeTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  children: React.ReactNode;
}) {
  const { operator, logout } = useAuth();
  const { language, setLanguage, t } = useLanguage();

  const [searchOpen, setSearchOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const navItems: { id: NavTab; labelKey: string; defaultLabel: string; shortcutIndex: number }[] = [
    { id: 'overview', labelKey: 'nav.overview', defaultLabel: 'Platform Overview', shortcutIndex: 1 },
    { id: 'applications', labelKey: 'nav.applications', defaultLabel: 'Applications & Keys', shortcutIndex: 2 },
    { id: 'integrations', labelKey: 'nav.integrations', defaultLabel: 'App Integrations & Readiness', shortcutIndex: 3 },
    { id: 'models', labelKey: 'nav.models', defaultLabel: 'Models & Providers', shortcutIndex: 4 },
    { id: 'orchestration', labelKey: 'nav.orchestration', defaultLabel: 'AI Orchestration & Schemas', shortcutIndex: 5 },
    { id: 'documents', labelKey: 'nav.documents', defaultLabel: 'Documents & OCR', shortcutIndex: 6 },
    { id: 'knowledge', labelKey: 'nav.knowledge', defaultLabel: 'Knowledge & RAG', shortcutIndex: 7 },
    { id: 'analytics', labelKey: 'nav.analytics', defaultLabel: 'Data & Analytics', shortcutIndex: 8 },
    { id: 'decision', labelKey: 'nav.decision', defaultLabel: 'Decisions & Optimization', shortcutIndex: 9 },
    { id: 'customer', labelKey: 'nav.customer', defaultLabel: 'Customer Intelligence', shortcutIndex: 0 },
    { id: 'conversations', labelKey: 'nav.conversations', defaultLabel: 'Conversations & Assistant', shortcutIndex: 0 },
    { id: 'tenants', labelKey: 'nav.tenants', defaultLabel: 'Tenants & Isolation', shortcutIndex: 0 },
    { id: 'feature-flags', labelKey: 'nav.featureFlags', defaultLabel: 'Feature Flags', shortcutIndex: 0 },
    { id: 'audit', labelKey: 'nav.audit', defaultLabel: 'Audit & Security', shortcutIndex: 0 },
  ];

  // Global Keyboard Navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const activeEl = document.activeElement;
      const isInput =
        activeEl instanceof HTMLInputElement ||
        activeEl instanceof HTMLTextAreaElement ||
        activeEl instanceof HTMLSelectElement;

      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setSearchOpen((prev) => !prev);
      } else if (!isInput && e.key === '?') {
        e.preventDefault();
        setShortcutsOpen((prev) => !prev);
      } else if (e.key === 'Escape') {
        setSearchOpen(false);
        setShortcutsOpen(false);
        setMobileMenuOpen(false);
      } else if (!isInput && e.key >= '1' && e.key <= '9') {
        const num = parseInt(e.key, 10);
        const target = navItems.find((n) => n.shortcutIndex === num);
        if (target) {
          e.preventDefault();
          onSelectTab(target.id);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [navItems, onSelectTab]);

  const handleTabSelect = (tab: NavTab) => {
    onSelectTab(tab);
    setMobileMenuOpen(false);
  };

  const renderNavList = () => (
    <nav aria-label="Main navigation" className="flex flex-col gap-1">
      {navItems.map((item) => {
        const active = activeTab === item.id;
        const label = t(item.labelKey, item.defaultLabel);
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => handleTabSelect(item.id)}
            className={`flex items-center justify-between rounded-lg px-3 py-2 text-left text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 ${
              active
                ? 'bg-indigo-600/20 text-indigo-200 border border-indigo-500/30'
                : 'text-slate-300 hover:bg-slate-800/70 hover:text-white'
            }`}
          >
            <span className="truncate">{label}</span>
            {item.shortcutIndex > 0 && (
              <span
                aria-hidden="true"
                className="ml-2 rounded bg-slate-800/80 px-1.5 py-0.5 text-[10px] font-mono text-slate-400 border border-slate-700/60 hidden md:inline-block"
              >
                {item.shortcutIndex}
              </span>
            )}
          </button>
        );
      })}
    </nav>
  );

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col md:flex-row">
      {/* Mobile Top Bar */}
      <div className="md:hidden flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 py-3 sticky top-0 z-30">
        <div className="flex items-center gap-2.5">
          <button
            type="button"
            aria-label="Toggle navigation menu"
            onClick={() => setMobileMenuOpen(true)}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded bg-indigo-600 flex items-center justify-center font-bold text-xs text-white">
              C
            </div>
            <span className="font-bold text-sm text-white">CRANIAL</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Quick Search Button on Mobile */}
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            aria-label="Open global search"
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </button>

          {/* Quick Shortcuts Button on Mobile */}
          <button
            type="button"
            onClick={() => setShortcutsOpen(true)}
            aria-label="Open keyboard shortcuts"
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white font-mono text-xs font-bold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            ?
          </button>
        </div>
      </div>

      {/* Mobile Drawer Overlay */}
      {mobileMenuOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Mobile Navigation Menu"
          className="fixed inset-0 z-40 bg-black/70 backdrop-blur-sm md:hidden flex"
          onClick={() => setMobileMenuOpen(false)}
        >
          <aside
            className="w-72 max-w-[85vw] bg-slate-900 border-r border-slate-800 p-5 flex flex-col justify-between h-full"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="overflow-y-auto">
              <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                <div className="flex items-center gap-2">
                  <div className="h-8 w-8 rounded bg-indigo-600 flex items-center justify-center font-bold text-white shadow">
                    C
                  </div>
                  <div>
                    <div className="text-sm font-bold text-white">CRANIAL</div>
                    <div className="text-[10px] text-slate-400">{t('brand.tagline', 'One Brain. Many Connections.')}</div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setMobileMenuOpen(false)}
                  className="rounded-lg p-1.5 text-slate-400 hover:text-white hover:bg-slate-800"
                >
                  ✕
                </button>
              </div>

              <div className="mt-4">{renderNavList()}</div>
            </div>

            <div className="border-t border-slate-800 pt-3 text-xs">
              <div className="font-medium text-slate-200 truncate">{operator?.display_name}</div>
              <div className="text-slate-400 truncate">{operator?.email}</div>
              <button
                type="button"
                onClick={logout}
                className="mt-3 w-full rounded-lg border border-slate-700 px-3 py-1.5 text-slate-300 hover:bg-slate-800"
              >
                {t('nav.signOut', 'Sign Out')}
              </button>
            </div>
          </aside>
        </div>
      )}

      {/* Desktop Sidebar */}
      <aside className="hidden md:flex w-64 border-r border-slate-800 bg-slate-900/70 p-5 flex-col justify-between shrink-0">
        <div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="h-9 w-9 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white shadow">
                C
              </div>
              <div>
                <div className="text-base font-bold tracking-wide text-white">CRANIAL</div>
                <div className="text-[11px] text-slate-400">{t('brand.tagline', 'One Brain. Many Connections.')}</div>
              </div>
            </div>
          </div>

          <div className="mt-2 flex items-center justify-between">
            <div className="text-[11px] text-indigo-300/90 font-medium">
              {t('brand.category', 'Universal AI Integration Platform')}
            </div>
            {/* Language Switcher Pill */}
            <div className="inline-flex rounded-lg bg-slate-800 p-0.5 border border-slate-700 text-[10px] font-semibold">
              <button
                type="button"
                onClick={() => setLanguage('en')}
                className={`px-1.5 py-0.5 rounded transition ${
                  language === 'en' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                }`}
                title="Switch to English"
              >
                EN
              </button>
              <button
                type="button"
                onClick={() => setLanguage('id')}
                className={`px-1.5 py-0.5 rounded transition ${
                  language === 'id' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                }`}
                title="Ganti ke Bahasa Indonesia"
              >
                ID
              </button>
            </div>
          </div>

          {/* Quick Search & Shortcuts Trigger Bar */}
          <div className="mt-4 flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setSearchOpen(true)}
              className="flex-1 flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/80 px-2.5 py-1.5 text-xs text-slate-400 hover:border-slate-700 hover:text-slate-200 transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
            >
              <span className="flex items-center gap-1.5">
                <svg className="w-3.5 h-3.5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <span>Search...</span>
              </span>
              <kbd className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px] font-mono text-slate-400 border border-slate-700">
                Ctrl K
              </kbd>
            </button>
            <button
              type="button"
              onClick={() => setShortcutsOpen(true)}
              title="Keyboard shortcuts (?)"
              aria-label="Keyboard shortcuts"
              className="rounded-lg border border-slate-800 bg-slate-950/80 p-1.5 text-xs font-mono font-semibold text-slate-400 hover:border-slate-700 hover:text-slate-200 transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
            >
              ?
            </button>
          </div>

          <div className="mt-5">{renderNavList()}</div>
        </div>

        <div className="mt-6 border-t border-slate-800 pt-4 text-xs">
          <div className="font-medium text-slate-200 truncate">{operator?.display_name}</div>
          <div className="text-slate-400 truncate">{operator?.email}</div>
          <div className="mt-1.5 flex flex-wrap gap-1">
            {operator?.roles.map((r) => (
              <span
                key={r}
                className="rounded bg-slate-800 px-1.5 py-0.5 text-[10px] font-medium text-slate-300"
              >
                {r}
              </span>
            ))}
          </div>
          <button
            type="button"
            onClick={logout}
            className="mt-3 w-full rounded-lg border border-slate-700 px-3 py-1.5 text-slate-300 hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            {t('nav.signOut', 'Sign Out')}
          </button>
        </div>
      </aside>

      <main className="flex-1 p-5 md:p-8 overflow-y-auto">{children}</main>

      {/* Global Modals */}
      <GlobalSearchModal
        isOpen={searchOpen}
        onClose={() => setSearchOpen(false)}
        onSelectTab={(tab) => onSelectTab(tab)}
      />
      <KeyboardShortcutsModal
        isOpen={shortcutsOpen}
        onClose={() => setShortcutsOpen(false)}
      />
    </div>
  );
}
