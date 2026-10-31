interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  recoveryNote?: string;
  confirmLabel: string;
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  recoveryNote,
  confirmLabel,
  destructive = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
    >
      <div className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
        <h3 id="confirm-dialog-title" className="text-lg font-semibold text-white">
          {title}
        </h3>
        <p className="mt-2 text-sm text-slate-300">{description}</p>
        {recoveryNote && (
          <div className="mt-3 rounded-lg border border-slate-700 bg-slate-950 p-3 text-xs text-slate-400">
            <strong className="text-slate-200">Recovery guidance:</strong> {recoveryNote}
          </div>
        )}
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200 hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`rounded-lg px-4 py-2 text-sm font-medium text-white focus-visible:outline-none focus-visible:ring-2 ${
              destructive
                ? 'bg-rose-600 hover:bg-rose-500 focus-visible:ring-rose-400'
                : 'bg-indigo-600 hover:bg-indigo-500 focus-visible:ring-indigo-400'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

