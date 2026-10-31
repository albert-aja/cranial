import React, { useState } from 'react';
import { CranialApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';

export function LoginPage() {
  const { login } = useAuth();
  const { language, setLanguage, t } = useLanguage();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<{ message: string; correlationId?: string } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(email, password);
    } catch (err) {
      if (err instanceof CranialApiError) {
        setError({ message: err.message, correlationId: err.correlationId });
      } else {
        setError({ message: t('login.error', 'Unable to reach CRANIAL backend API.') });
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-8 shadow-2xl">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-indigo-600 flex items-center justify-center text-lg font-bold text-white">
              C
            </div>
            <div>
              <h1 className="text-xl font-bold text-white">CRANIAL</h1>
              <p className="text-xs text-indigo-300">
                {t('brand.category', 'Universal AI Integration Platform')}
              </p>
            </div>
          </div>

          <div className="inline-flex rounded-lg bg-slate-800 p-0.5 border border-slate-700 text-[10px] font-semibold">
            <button
              type="button"
              onClick={() => setLanguage('en')}
              className={`px-1.5 py-0.5 rounded transition ${
                language === 'en'
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              EN
            </button>
            <button
              type="button"
              onClick={() => setLanguage('id')}
              className={`px-1.5 py-0.5 rounded transition ${
                language === 'id'
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              ID
            </button>
          </div>
        </div>

        <p className="mt-2 text-xs text-slate-400">
          {t('brand.tagline', 'One Brain. Many Connections.')} — {t('brand.console', 'Operator Console')}
        </p>

        {error && (
          <div role="alert" className="mt-5 rounded-lg border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-200">
            <div>{error.message}</div>
            {error.correlationId && (
              <div className="mt-1 text-[11px] text-rose-300/80">Correlation ID: {error.correlationId}</div>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label htmlFor="operator-email" className="block text-xs font-medium text-slate-300">
              {t('login.email', 'Operator Email')}
            </label>
            <input
              id="operator-email"
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@cranial.local"
              className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
            />
          </div>

          <div>
            <label htmlFor="operator-password" className="block text-xs font-medium text-slate-300">
              {t('login.password', 'Password')}
            </label>
            <input
              id="operator-password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            {submitting ? t('login.signingIn', 'Authenticating…') : t('login.submit', 'Sign In to Operator Console')}
          </button>
        </form>
      </div>
    </div>
  );
}
