import { useState } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { LanguageProvider } from './i18n/LanguageContext';
import { AppShell, type NavTab } from './components/AppShell';
import { ApplicationsPage } from './pages/ApplicationsPage';
import { IntegrationsPage } from './pages/IntegrationsPage';
import { AuditLogsPage } from './pages/AuditLogsPage';
import DocumentsPage from './pages/DocumentsPage';
import { FeatureFlagsPage } from './pages/FeatureFlagsPage';
import KnowledgePage from './pages/KnowledgePage';
import AnalyticsPage from './pages/AnalyticsPage';
import DecisionPage from './pages/DecisionPage';
import { CustomerPage } from './pages/CustomerPage';
import { LoginPage } from './pages/LoginPage';
import ModelsPage from './pages/ModelsPage';
import OrchestrationPage from './pages/OrchestrationPage';
import { OverviewPage } from './pages/OverviewPage';
import { TenantsPage } from './pages/TenantsPage';

function ConsoleRouter() {
  const { operator, loading } = useAuth();
  const [activeTab, setActiveTab] = useState<NavTab>('overview');

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center text-sm text-slate-400">
        Initializing CRANIAL Operator Console…
      </div>
    );
  }

  if (!operator) {
    return <LoginPage />;
  }

  return (
    <AppShell activeTab={activeTab} onSelectTab={setActiveTab}>
      {activeTab === 'overview' && <OverviewPage />}
      {activeTab === 'applications' && <ApplicationsPage />}
      {activeTab === 'integrations' && <IntegrationsPage />}
      {activeTab === 'models' && <ModelsPage />}
      {activeTab === 'orchestration' && <OrchestrationPage />}
      {activeTab === 'documents' && <DocumentsPage />}
      {activeTab === 'knowledge' && <KnowledgePage />}
      {activeTab === 'analytics' && <AnalyticsPage />}
      {activeTab === 'decision' && <DecisionPage />}
      {activeTab === 'customer' && <CustomerPage />}
      {activeTab === 'tenants' && <TenantsPage />}
      {activeTab === 'feature-flags' && <FeatureFlagsPage />}
      {activeTab === 'audit' && <AuditLogsPage />}
    </AppShell>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <LanguageProvider>
        <ConsoleRouter />
      </LanguageProvider>
    </AuthProvider>
  );
}
