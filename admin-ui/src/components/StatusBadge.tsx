const STYLES: Record<string, string> = {
  ACTIVE: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  AVAILABLE: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  SUCCESS: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  ROTATING: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
  PLANNED: 'bg-sky-500/15 text-sky-300 border-sky-500/30',
  SUSPENDED: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
  DENIED: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  FAILURE: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  REVOKED: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  EXPIRED: 'bg-slate-700/40 text-slate-300 border-slate-600',
  RETIRED: 'bg-slate-700/40 text-slate-300 border-slate-600',
  DISABLED: 'bg-slate-700/40 text-slate-300 border-slate-600',
  PROD: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/40',
  STAGING: 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30',
  DEV: 'bg-slate-700/50 text-slate-200 border-slate-600',
};

export function StatusBadge({ label }: { label?: string | null }) {
  const upper = (label || 'UNKNOWN').toUpperCase();
  const style = STYLES[upper] || 'bg-slate-800 text-slate-300 border-slate-700';
  return (
    <span className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${style}`}>
      {upper}
    </span>
  );
}

