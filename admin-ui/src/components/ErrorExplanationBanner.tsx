import { useState } from 'react';

export interface CranialErrorDetails {
  code: string;
  message: string;
  correlationId?: string;
  explanation: string;
  rootCause: string;
  suggestedAction: string;
}

const ERROR_EXPLANATIONS: Record<string, { explanation: string; rootCause: string; suggestedAction: string }> = {
  TOOL_PERMISSION_DENIED: {
    explanation: 'A client context or operator attempted to invoke a capability tool without the required role or scope.',
    rootCause: 'The signed token claims or API key lack the tool authority (e.g. attempting to run a store owner report from a customer session).',
    suggestedAction: 'Verify application capability entitlements in Applications & Keys and ensure the user token carries the appropriate role (e.g. STORE_OWNER, BUDGET_USER).',
  },
  PROVIDER_CIRCUIT_OPEN: {
    explanation: 'Outbound requests to this AI provider are currently blocked to prevent cascading timeouts.',
    rootCause: 'Consecutive upstream inference calls exceeded the failure threshold or local provider process (e.g. Ollama daemon) is unreachable.',
    suggestedAction: 'Check provider health in Models & Providers, ensure the local daemon is running on port 11434, then click "Reset Circuit".',
  },
  MISSING_SIGNED_CONTEXT: {
    explanation: 'The request was rejected because signed client identity headers are required by this application.',
    rootCause: 'The application policy has requireSignedContext=true, but X-Cranial-Context, X-Cranial-Timestamp, or X-Cranial-Signature were omitted.',
    suggestedAction: 'Use the Code Snippet Generator in Applications & Keys to generate valid HMAC-SHA256 headers before dispatching gateway requests.',
  },
  CONTEXT_TIMESTAMP_EXPIRED: {
    explanation: 'The signed context timestamp falls outside the acceptable clock-skew replay window.',
    rootCause: 'The request timestamp differs from CRANIAL server time by more than 300 seconds (5 minutes).',
    suggestedAction: 'Synchronize system clocks via NTP on the client machine and re-generate a fresh epoch timestamp.',
  },
  RATE_LIMIT_EXCEEDED: {
    explanation: 'The client application exceeded its configured requests-per-minute (RPM) quota.',
    rootCause: 'The gateway token bucket algorithm throttled incoming traffic to protect platform stability.',
    suggestedAction: 'Inspect traffic spikes in Data & Analytics or increase rate_limit_rpm in the application settings.',
  },
  UNSUPPORTED_BANKING_PROMISE: {
    explanation: 'The customer inquiry triggered CRANIAL’s strictly enforced non-banking safety guardrail.',
    rootCause: 'The prompt or canned response attempted to guarantee cash refunds, wire transfers, or direct fund disbursements.',
    suggestedAction: 'CRANIAL and Peggy are purely money management platforms. Direct customer to banking statements or app store billing policies.',
  },
  DOCUMENT_LOW_CONFIDENCE: {
    explanation: 'OCR extraction confidence fell below the auto-approval threshold.',
    rootCause: 'Low resolution, blur, skew, or non-standard receipt layout reduced character recognition confidence.',
    suggestedAction: 'Open the Human Review Queue in Documents & OCR to manually verify and adjust extracted fields.',
  },
  SCHEMA_VALIDATION_FAILED: {
    explanation: 'Model output or tool response failed strict JSON schema validation.',
    rootCause: 'Required fields were missing or types mismatched the registered schema contract.',
    suggestedAction: 'Inspect the schema definition in AI Orchestration & Schemas and verify prompt formatting constraints.',
  },
  APPLICATION_SUSPENDED: {
    explanation: 'Gateway access for this application has been administrative suspended.',
    rootCause: 'An operator manually suspended the application status to halt traffic.',
    suggestedAction: 'Go to Applications & Keys, select the application, and click "Activate".',
  },
};

export function parseCranialError(err: unknown): CranialErrorDetails {
  let code = 'INTERNAL_ERROR';
  let message = 'An unexpected platform error occurred.';
  let correlationId: string | undefined;

  if (typeof err === 'string') {
    message = err;
  } else if (err && typeof err === 'object') {
    const anyErr = err as Record<string, unknown>;
    if (typeof anyErr.code === 'string') code = anyErr.code;
    if (typeof anyErr.message === 'string') message = anyErr.message;
    if (typeof anyErr.correlationId === 'string') correlationId = anyErr.correlationId;
  }

  const lookup = ERROR_EXPLANATIONS[code] || {
    explanation: 'A system-level error or unhandled condition was reported.',
    rootCause: message,
    suggestedAction: 'Review the audit log using the correlation ID for full stack trace and diagnostic events.',
  };

  return {
    code,
    message,
    correlationId,
    explanation: lookup.explanation,
    rootCause: lookup.rootCause,
    suggestedAction: lookup.suggestedAction,
  };
}

export interface ErrorExplanationBannerProps {
  error?: unknown;
  errorCode?: string;
  errorMessage?: string;
  correlationId?: string;
  onDismiss?: () => void;
  onAction?: () => void;
  actionLabel?: string;
  onRetry?: () => void;
}

export function ErrorExplanationBanner({
  error,
  errorCode,
  errorMessage,
  correlationId,
  onDismiss,
  onAction,
  actionLabel,
  onRetry,
}: ErrorExplanationBannerProps) {
  const [copied, setCopied] = useState(false);

  const effectiveError =
    error !== undefined
      ? error
      : {
          code: errorCode || 'INTERNAL_ERROR',
          message: errorMessage || 'An unexpected platform error occurred.',
          correlationId,
        };

  const details = parseCranialError(effectiveError);

  const copyCorrelationId = () => {
    if (!details.correlationId) return;
    navigator.clipboard.writeText(details.correlationId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div
      role="alert"
      className="rounded-xl border border-rose-500/30 bg-rose-950/30 p-4 text-xs text-rose-200 shadow-sm space-y-3"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-full bg-rose-500/20 text-rose-400 font-bold text-xs">
            !
          </span>
          <span className="font-semibold text-rose-100">{details.code}</span>
          <span className="rounded bg-rose-900/60 px-1.5 py-0.5 text-[10px] font-mono text-rose-300">
            {details.message}
          </span>
        </div>
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss error"
            className="text-rose-400 hover:text-white transition focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-rose-400"
          >
            ✕
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-[11px] pt-1">
        <div className="rounded-lg bg-slate-950/60 border border-rose-900/40 p-2.5">
          <span className="font-semibold text-rose-300">Why it occurred: </span>
          <span className="text-slate-300">{details.rootCause}</span>
        </div>
        <div className="rounded-lg bg-slate-950/60 border border-emerald-900/40 p-2.5">
          <span className="font-semibold text-emerald-300">Suggested Operator Action: </span>
          <span className="text-slate-300">{details.suggestedAction}</span>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-rose-900/40 text-[10px]">
        {details.correlationId ? (
          <div className="flex items-center gap-2 font-mono text-slate-400">
            <span>Correlation ID:</span>
            <span className="text-indigo-300 font-medium">{details.correlationId}</span>
            <button
              type="button"
              onClick={copyCorrelationId}
              className="rounded bg-slate-800 px-1.5 py-0.5 text-slate-300 hover:bg-slate-700 hover:text-white transition"
              title="Copy correlation ID"
            >
              {copied ? '✓ Copied' : 'Copy ID'}
            </button>
          </div>
        ) : (
          <span className="text-slate-500">Local validation error</span>
        )}

        <div className="flex items-center gap-2">
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="rounded bg-indigo-600 hover:bg-indigo-500 text-white font-medium px-2.5 py-1 transition"
            >
              Retry Action
            </button>
          )}
          {actionLabel && onAction && (
            <button
              type="button"
              onClick={onAction}
              className="rounded bg-rose-600 hover:bg-rose-500 text-white font-medium px-2.5 py-1 transition"
            >
              {actionLabel}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
