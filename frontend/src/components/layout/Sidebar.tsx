import React from 'react';
import { LayoutDashboard, Package, Warehouse, ShoppingCart, BarChart3 } from 'lucide-react';

export type NavTab = 'dashboard' | 'catalog' | 'inventory' | 'orders' | 'metrics';

interface SidebarProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onTabChange }) => {
  const navItems = [
    {
      id: 'dashboard' as NavTab,
      label: 'Dashboard',
      subtitle: 'KPIs & Telemetría',
      icon: LayoutDashboard,
    },
    {
      id: 'catalog' as NavTab,
      label: 'Catálogo Industrial',
      subtitle: 'SKUs & Categorías',
      icon: Package,
    },
    {
      id: 'inventory' as NavTab,
      label: 'Bodegas & Ledger',
      subtitle: 'Stock & Auditoría SOX',
      icon: Warehouse,
    },
    {
      id: 'orders' as NavTab,
      label: 'Mesa de Órdenes',
      subtitle: 'Idempotencia & Locks',
      icon: ShoppingCart,
    },
    {
      id: 'metrics' as NavTab,
      label: 'Métricas Prometheus',
      subtitle: 'Actuator & Concurrencia',
      icon: BarChart3,
    },
  ];

  return (
    <aside className="w-64 border-r border-slate-800/80 bg-slate-950/60 backdrop-blur-md p-4 flex flex-col justify-between shrink-0">
      <div className="space-y-6">
        <div>
          <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider px-3">
            Plataforma B2B
          </span>
          <nav className="mt-2 space-y-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => onTabChange(item.id)}
                  className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-left transition-all duration-150 ${
                    isActive
                      ? 'bg-gradient-to-r from-amber-500/15 to-transparent text-amber-300 border-l-2 border-amber-400 font-semibold'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60 font-medium'
                  }`}
                >
                  <Icon
                    className={`w-4 h-4 ${
                      isActive ? 'text-amber-400' : 'text-slate-500'
                    }`}
                  />
                  <div>
                    <div className="text-sm leading-none">{item.label}</div>
                    <div className="text-[11px] text-slate-500 mt-1 leading-none">
                      {item.subtitle}
                    </div>
                  </div>
                </button>
              );
            })}
          </nav>
        </div>

        <div className="glass-card p-3 rounded-lg border border-slate-800/80">
          <div className="text-xs font-semibold text-slate-300">Garantía Invariante</div>
          <div className="text-[11px] text-slate-400 mt-1 leading-relaxed">
            <span className="font-mono text-emerald-400 font-bold">PESSIMISTIC_WRITE</span>
            <br />
            Cero sobreventa bajo 50 hilos concurrentes demostrada en test suite.
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-slate-800/80">
        <div className="flex items-center gap-2 px-2">
          <div className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
          <span className="text-[11px] font-mono text-slate-400">CI/CD Pipeline Green</span>
        </div>
        <div className="text-[10px] text-slate-500 px-2 mt-1">
          StockPilot v1.0.0-PROD
        </div>
      </div>
    </aside>
  );
};
