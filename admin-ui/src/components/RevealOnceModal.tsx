import { useState } from 'react';
import type { CredentialIssuedResult } from '../api/types';

export function RevealOnceModal({
  issued,
  onClose,
}: {
  issued: CredentialIssuedResult | null;
  onClose: () => void;
}) {
  const [acknowledged, setAcknowledged] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  if (!issued) return null;

  const copyText = async (field: string, value: string) => {
    await navigator.clipboard?.writeText(value);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 2000);
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="reveal-secret-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
    >
      <div className="w-full max-w-xl rounded-xl border border-amber-500/40 bg-slate-900 p-6 shadow-2xl">
        <h3 id="reveal-secret-title" className="text-lg font-semibold text-amber-300">
          Reveal-Once Application Credentials
        </h3>
        <p className="mt-2 text-sm text-slate-300">{issued.warning}</p>

        <div className="mt-4 space-y-4">
          <div>
            <label className="block text-xs font-medium uppercase tracking-wider text-slate-400">
              X-Cranial-Api-Key
            </label>
            <div className="mt-1 flex items-center gap-2">
              <code className="flex-1 overflow-x-auto rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-emerald-300">
                {issued.raw_api_key}
              </code>
              <button
                type="button"
                onClick={() => copyText('apiKey', issued.raw_api_key)}
                className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-xs text-slate-200 hover:bg-slate-700 focus-visible:ring-2 focus-visible:ring-indigo-400"
              >
                {copiedField === 'apiKey' ? 'Copied' : 'Copy'}
              </button>
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium uppercase tracking-wider text-slate-400">
              Context Signing Secret (HMAC-SHA256)
            </label>
            <div className="mt-1 flex items-center gap-2">
              <code className="flex-1 overflow-x-auto rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-sky-300">
                {issued.raw_signing_secret}
              </code>
              <button
                type="button"
                onClick={() => copyText('signingSecret', issued.raw_signing_secret)}
                className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-xs text-slate-200 hover:bg-slate-700 focus-visible:ring-2 focus-visible:ring-indigo-400"
              >
                {copiedField === 'signingSecret' ? 'Copied' : 'Copy'}
              </button>
            </div>
          </div>
        </div>

        <label className="mt-5 flex items-center gap-2 text-sm text-slate-200">
          <input
            type="checkbox"
            checked={acknowledged}
            onChange={(e) => setAcknowledged(e.target.checked)}
            className="h-4 w-4 rounded border-slate-600 bg-slate-950 text-indigo-500 focus:ring-indigo-400"
          />
          I have stored both secrets in the client application's secret manager.
        </label>

        <div className="mt-5 flex justify-end">
          <button
            type="button"
            disabled={!acknowledged}
            onClick={() => {
              setAcknowledged(false);
              onClose();
            }}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}

