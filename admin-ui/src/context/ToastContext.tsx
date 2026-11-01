import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  correlationId?: string;
  duration?: number;
}

interface ToastContextValue {
  toasts: ToastItem[];
  addToast: (toast: Omit<ToastItem, 'id'>) => string;
  removeToast: (id: string) => void;
  success: (title: string, message?: string, correlationId?: string) => void;
  error: (title: string, message?: string, correlationId?: string) => void;
  warning: (title: string, message?: string, correlationId?: string) => void;
  info: (title: string, message?: string, correlationId?: string) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback(
    (toast: Omit<ToastItem, 'id'>) => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      const duration = toast.duration ?? (toast.type === 'error' ? 8000 : 4500);

      setToasts((prev) => [...prev, { ...toast, id, duration }]);

      if (duration > 0) {
        setTimeout(() => {
          removeToast(id);
        }, duration);
      }

      return id;
    },
    [removeToast]
  );

  const success = useCallback(
    (title: string, message?: string, correlationId?: string) => {
      addToast({ type: 'success', title, message, correlationId });
    },
    [addToast]
  );

  const error = useCallback(
    (title: string, message?: string, correlationId?: string) => {
      addToast({ type: 'error', title, message, correlationId });
    },
    [addToast]
  );

  const warning = useCallback(
    (title: string, message?: string, correlationId?: string) => {
      addToast({ type: 'warning', title, message, correlationId });
    },
    [addToast]
  );

  const info = useCallback(
    (title: string, message?: string, correlationId?: string) => {
      addToast({ type: 'info', title, message, correlationId });
    },
    [addToast]
  );

  return (
    <ToastContext.Provider
      value={{ toasts, addToast, removeToast, success, error, warning, info }}
    >
      {children}
      <ToastContainer toasts={toasts} onDismiss={removeToast} />
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return ctx;
}

function ToastContainer({
  toasts,
  onDismiss,
}: {
  toasts: ToastItem[];
  onDismiss: (id: string) => void;
}) {
  if (toasts.length === 0) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none"
    >
      {toasts.map((toast) => (
        <ToastCard key={toast.id} toast={toast} onDismiss={() => onDismiss(toast.id)} />
      ))}
    </div>
  );
}

function ToastCard({
  toast,
  onDismiss,
}: {
  toast: ToastItem;
  onDismiss: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const copyId = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!toast.correlationId) return;
    navigator.clipboard.writeText(toast.correlationId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const borderStyles: Record<ToastType, string> = {
    success: 'border-emerald-500/40 bg-slate-900/95 text-emerald-300',
    error: 'border-rose-500/40 bg-slate-900/95 text-rose-300',
    warning: 'border-amber-500/40 bg-slate-900/95 text-amber-300',
    info: 'border-sky-500/40 bg-slate-900/95 text-sky-300',
  };

  const badgeIcons: Record<ToastType, string> = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ',
  };

  return (
    <div
      className={`pointer-events-auto rounded-xl border p-4 shadow-xl backdrop-blur transition-all duration-200 ${borderStyles[toast.type]}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2.5">
          <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-slate-800 text-xs font-bold">
            {badgeIcons[toast.type]}
          </span>
          <div>
            <div className="text-xs font-semibold text-slate-100">{toast.title}</div>
            {toast.message && (
              <div className="mt-0.5 text-[11px] text-slate-300 leading-snug">{toast.message}</div>
            )}
          </div>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="text-slate-400 hover:text-white text-xs transition"
          aria-label="Dismiss notification"
        >
          ✕
        </button>
      </div>

      {toast.correlationId && (
        <div className="mt-2.5 flex items-center justify-between border-t border-slate-800/80 pt-2 text-[10px] font-mono text-slate-400">
          <span className="truncate max-w-[200px]">ID: {toast.correlationId}</span>
          <button
            type="button"
            onClick={copyId}
            className="rounded bg-slate-800 px-1.5 py-0.5 text-slate-300 hover:bg-slate-700 hover:text-white transition"
          >
            {copied ? '✓ Copied' : 'Copy ID'}
          </button>
        </div>
      )}
    </div>
  );
}

