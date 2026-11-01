import React from 'react';

export function LoadingState({ message = 'Loading data…' }: { message?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center p-12 text-center"
    >
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
      <span className="mt-3 text-xs font-medium text-slate-400">{message}</span>
    </div>
  );
}

