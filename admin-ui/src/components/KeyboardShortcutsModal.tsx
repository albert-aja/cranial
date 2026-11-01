import React, { useEffect } from 'react';

export interface KeyboardShortcutsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function KeyboardShortcutsModal({ isOpen, onClose }: KeyboardShortcutsModalProps) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const shortcuts = [
    {
      group: 'Navigation & Focus',
      items: [
        { key: 'Cmd / Ctrl + K', description: 'Open Global Command Palette & search' },
        { key: '1 - 9', description: 'Quick jump to primary navigation tabs (1: Overview, 2: Apps, ...)' },
        { key: '?', description: 'Show keyboard shortcuts cheat sheet' },
        { key: 'Esc', description: 'Close open modal, drawer, or search palette' },
        { key: 'Tab / Shift + Tab', description: 'Navigate focusable interactive elements' },
      ],
    },
    {
      group: 'Orchestration & Playgrounds',
      items: [
        { key: 'Ctrl + Enter', description: 'Execute payload validation or tool runner in playground' },
        { key: '↑ / ↓', description: 'Navigate search suggestions and execution trace steps' },
      ],
    },
    {
      group: 'Security & Non-Banking Invariant',
      items: [
        { key: 'Local-First', description: 'Hosted paid LLM fallbacks remain blocked by default' },
        { key: 'Pure Money Mgmt', description: 'No payment, cash disbursement, or money transmission workflows' },
      ],
    },
  ];

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Keyboard Shortcuts Cheatsheet"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-2xl border border-slate-700 bg-slate-900 p-6 shadow-2xl space-y-5"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
              </svg>
            </div>
            <h3 className="text-base font-bold text-white">Keyboard Shortcuts & Navigation</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
          >
            ✕
          </button>
        </div>

        <div className="space-y-4 max-h-[60vh] overflow-y-auto">
          {shortcuts.map((group) => (
            <div key={group.group} className="space-y-2">
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                {group.group}
              </div>
              <div className="space-y-1.5">
                {group.items.map((item) => (
                  <div
                    key={item.key}
                    className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs"
                  >
                    <span className="text-slate-300">{item.description}</span>
                    <kbd className="rounded bg-slate-800 px-2 py-0.5 font-mono text-[11px] font-semibold text-indigo-300 border border-slate-700">
                      {item.key}
                    </kbd>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="pt-3 border-t border-slate-800 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-xs font-medium text-white hover:bg-indigo-500 transition"
          >
            Got it
          </button>
        </div>
      </div>
    </div>
  );
}

