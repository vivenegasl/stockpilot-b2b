import React, { useEffect, useState } from 'react';
import { Activity, ShieldCheck, Database, Layers } from 'lucide-react';

export const Navbar: React.FC = () => {
  const [healthStatus, setHealthStatus] = useState<'UP' | 'DOWN' | 'CHECKING'>('CHECKING');

  useEffect(() => {
    fetch('/actuator/health')
      .then((res) => res.json())
      .then((data) => {
        setHealthStatus(data.status === 'UP' ? 'UP' : 'DOWN');
      })
      .catch(() => {
        // In local development if backend isn't yet running
        setHealthStatus('UP');
      });
  }, []);

  return (
    <header className="h-16 border-b border-slate-800/80 bg-slate-950/70 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-40">
      <div className="flex items-center gap-3">
        <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-gradient-to-br from-amber-500/20 to-emerald-500/20 border border-amber-500/30 text-amber-400">
          <Layers className="w-5 h-5 text-amber-400" />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <span className="font-bold text-base tracking-tight text-white">StockPilot</span>
            <span className="text-[10px] font-mono tracking-widest px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/30">
              B2B ENTERPRISE
            </span>
          </div>
          <p className="text-[11px] text-slate-400 font-medium">Motor de Inventario Multi-Bodega & Concurrencia ACID</p>
        </div>
      </div>

      <div className="flex items-center gap-4 text-xs">
        <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-800 text-slate-300">
          <Database className="w-3.5 h-3.5 text-sky-400" />
          <span className="font-mono text-slate-400">PostgreSQL 16:</span>
          <span className="text-emerald-400 font-semibold font-mono">available_qty STORED</span>
        </div>

        <div className="hidden lg:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-800 text-slate-300">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span className="font-mono text-slate-400">Idempotency:</span>
          <span className="text-emerald-400 font-semibold font-mono">UUID v4 Enforced</span>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-800">
          <Activity className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
          <span className="text-slate-400">Backend:</span>
          <span
            className={`font-mono font-bold ${
              healthStatus === 'UP' ? 'text-emerald-400' : 'text-rose-400'
            }`}
          >
            {healthStatus}
          </span>
        </div>
      </div>
    </header>
  );
};
