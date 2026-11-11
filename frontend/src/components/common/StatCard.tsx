import React from 'react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  trend?: string;
  trendUp?: boolean;
  accentColor?: 'emerald' | 'amber' | 'blue' | 'purple' | 'rose';
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon,
  trend,
  trendUp,
  accentColor = 'blue',
}) => {
  const getBorderColor = () => {
    switch (accentColor) {
      case 'emerald':
        return 'hover:border-emerald-500/40';
      case 'amber':
        return 'hover:border-amber-500/40';
      case 'purple':
        return 'hover:border-purple-500/40';
      case 'rose':
        return 'hover:border-rose-500/40';
      default:
        return 'hover:border-sky-500/40';
    }
  };

  return (
    <div className={`glass-card p-5 rounded-xl transition-all duration-200 ${getBorderColor()}`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">{title}</span>
        <div className="p-2 rounded-lg bg-slate-900/60 border border-slate-800 text-slate-300">
          {icon}
        </div>
      </div>
      <div className="mt-3 flex items-baseline justify-between">
        <span className="text-2xl font-bold tracking-tight text-white font-mono">{value}</span>
        {trend && (
          <span
            className={`text-xs font-medium px-2 py-0.5 rounded ${
              trendUp ? 'bg-emerald-950/60 text-emerald-400' : 'bg-rose-950/60 text-rose-400'
            }`}
          >
            {trend}
          </span>
        )}
      </div>
      {subtitle && <p className="mt-1 text-xs text-slate-500">{subtitle}</p>}
    </div>
  );
};
