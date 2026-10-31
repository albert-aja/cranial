import React from 'react';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';

export type NavTab = 'overview' | 'applications' | 'integrations' | 'models' | 'orchestration' | 'documents' | 'knowledge' | 'analytics' | 'decision' | 'customer' | 'tenants' | 'feature-flags' | 'audit';

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

  const navItems: { id: NavTab; labelKey: string; defaultLabel: string }[] = [
    { id: 'overview', labelKey: 'nav.overview', defaultLabel: 'Platform Overview' },
    { id: 'applications', labelKey: 'nav.applications', defaultLabel: 'Applications & Keys' },
    { id: 'integrations', labelKey: 'nav.integrations', defaultLabel: 'App Integrations & Readiness' },
    { id: 'models', labelKey: 'nav.models', defaultLabel: 'Models & Providers' },
    { id: 'orchestration', labelKey: 'nav.orchestration', defaultLabel: 'AI Orchestration & Schemas' },
    { id: 'documents', labelKey: 'nav.documents', defaultLabel: 'Documents & OCR' },
    { id: 'knowledge', labelKey: 'nav.knowledge', defaultLabel: 'Knowledge & RAG' },
    { id: 'analytics', labelKey: 'nav.analytics', defaultLabel: 'Data & Analytics' },
    { id: 'decision', labelKey: 'nav.decision', defaultLabel: 'Decisions & Optimization' },
    { id: 'customer', labelKey: 'nav.customer', defaultLabel: 'Customer Intelligence' },
    { id: 'tenants', labelKey: 'nav.tenants', defaultLabel: 'Tenants & Isolation' },
    { id: 'feature-flags', labelKey: 'nav.featureFlags', defaultLabel: 'Feature Flags' },
    { id: 'audit', labelKey: 'nav.audit', defaultLabel: 'Audit & Security' },
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col md:flex-row">
      <aside className="w-full md:w-64 border-b md:border-b-0 md:border-r border-slate-800 bg-slate-900/70 p-5 flex flex-col justify-between">
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
                className={`px-1.5 py-0.5 rounded transition ${language === 'en'
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                  }`}
                title="Switch to English"
              >
                EN
              </button>
              <button
                type="button"
                onClick={() => setLanguage('id')}
                className={`px-1.5 py-0.5 rounded transition ${language === 'id'
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                  }`}
                title="Ganti ke Bahasa Indonesia"
              >
                ID
              </button>
            </div>
          </div>

          <nav aria-label="Main navigation" className="mt-6 flex md:flex-col gap-1 overflow-x-auto">
            {navItems.map((item) => {
              const active = activeTab === item.id;
              const label = t(item.labelKey, item.defaultLabel);
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onSelectTab(item.id)}
                  className={`whitespace-nowrap rounded-lg px-3 py-2 text-left text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 ${active
                      ? 'bg-indigo-600/20 text-indigo-200 border border-indigo-500/30'
                      : 'text-slate-300 hover:bg-slate-800/70 hover:text-white'
                    }`}
                >
                  {label}
                </button>
              );
            })}
          </nav>
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
    </div>
  );
}
