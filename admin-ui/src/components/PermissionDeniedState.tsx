import React from 'react';

interface PermissionDeniedStateProps {
  requiredPermission?: string;
  moduleName?: string;
}

export function PermissionDeniedState({
  requiredPermission = 'app:read',
  moduleName = 'this section',
}: PermissionDeniedStateProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/5 p-10 text-center"
    >
      <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-rose-500/10 text-rose-400">
        <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
        </svg>
      </div>
      <h3 className="text-sm font-semibold text-rose-200">Access Restricted</h3>
      <p className="mt-1 max-w-sm text-xs text-rose-300/80">
        Your operator account lacks the required authority to view or manage {moduleName}.
      </p>
      <div className="mt-3 inline-flex items-center rounded-md bg-rose-950/40 border border-rose-800/60 px-2.5 py-1 text-[11px] font-mono text-rose-300">
        Required Permission: {requiredPermission}
      </div>
    </div>
  );
}

