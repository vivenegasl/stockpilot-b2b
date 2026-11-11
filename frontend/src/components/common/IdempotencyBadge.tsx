import React from 'react';
import { ShieldCheck, RefreshCw, Key } from 'lucide-react';

interface IdempotencyBadgeProps {
  idempotencyKey?: string;
  isReplay?: boolean;
  compact?: boolean;
}

export const IdempotencyBadge: React.FC<IdempotencyBadgeProps> = ({
  idempotencyKey,
  isReplay,
  compact = false,
}) => {
  if (isReplay) {
    return (
      <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/40 shadow-sm animate-pulse">
        <RefreshCw className="w-3.5 h-3.5 text-amber-400" />
        <span>X-Idempotent-Replay: true</span>
        <span className="text-amber-500/70 font-normal hidden sm:inline">(Respuesta servida desde caché sin mutación)</span>
      </div>
    );
  }

  if (compact && idempotencyKey) {
    return (
      <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono bg-slate-900/80 text-slate-400 border border-slate-800">
        <Key className="w-3 h-3 text-emerald-400" />
        <span>{idempotencyKey.substring(0, 8)}...</span>
      </div>
    );
  }

  if (idempotencyKey) {
    return (
      <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-mono bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
        <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
        <span className="text-slate-400 font-sans">Key:</span>
        <span>{idempotencyKey}</span>
      </div>
    );
  }

  return null;
};
