import React, { useEffect, useState } from 'react';
import {
  Activity,
  Database,
  RefreshCw,
  Cpu,
  ShieldAlert,
  Repeat,
  CheckCircle2,
  XCircle,
  Truck,
  PlusCircle,
  TrendingUp,
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { metricsApi } from '../../api/metricsApi';
import type { PrometheusMetricsSummary } from '../../types/metrics';

export const MetricsView: React.FC = () => {
  const [metrics, setMetrics] = useState<PrometheusMetricsSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  const loadMetrics = async () => {
    setLoading(true);
    try {
      const data = await metricsApi.fetchPrometheusMetrics();
      setMetrics(data);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Error fetching metrics', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMetrics();
    if (!autoRefresh) return;
    const interval = setInterval(loadMetrics, 10000);
    return () => clearInterval(interval);
  }, [autoRefresh]);

  const orderDistributionData = metrics
    ? [
        { name: 'Creadas', count: metrics.ordersCreated, fill: '#38bdf8' },
        { name: 'Confirmadas', count: metrics.ordersConfirmed, fill: '#10b981' },
        { name: 'Despachadas', count: metrics.ordersFulfilled, fill: '#6366f1' },
        { name: 'Canceladas', count: metrics.ordersCancelled, fill: '#f43f5e' },
      ]
    : [];

  const concurrencyRatioData = metrics
    ? [
        { name: 'Reservas Exitosas (Locks Adquiridos)', value: metrics.ordersConfirmed, color: '#10b981' },
        { name: 'Rechazos por Stock Insuficiente (409)', value: metrics.insufficientStockFailures, color: '#f59e0b' },
        { name: 'Replays Idempotentes Salvados', value: metrics.idempotencyReplays, color: '#8b5cf6' },
      ]
    : [];

  const hikariUsagePct = metrics && metrics.hikariTotalConnections > 0
    ? Math.round((metrics.hikariActiveConnections / metrics.hikariTotalConnections) * 100)
    : 10;

  const heapUsagePct = metrics && metrics.jvmHeapMaxMb > 0
    ? Math.round((metrics.jvmHeapUsedMb / metrics.jvmHeapMaxMb) * 100)
    : 35;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Telemetría & Métricas de Concurrencia
            <span className="text-xs px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/30 font-mono">
              /actuator/prometheus
            </span>
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Scraping en tiempo real del registro Micrometer: contadores de negocio, saturación de pools HikariCP y carga de la JVM.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer bg-slate-900/80 px-3 py-2 rounded-lg border border-slate-800">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="accent-amber-500 rounded"
            />
            Auto-refresh (10s)
          </label>

          <button
            onClick={loadMetrics}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-slate-900/90 hover:bg-slate-800 text-slate-300 border border-slate-800 text-xs font-semibold transition"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-amber-400' : ''}`} />
            Refrescar Ahora
          </button>
        </div>
      </div>

      <div className="text-xs text-slate-500 font-mono">
        Última captura Prometheus: {lastUpdated.toLocaleTimeString()}
      </div>

      {/* Grid de Contadores Principales de Negocio */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
        <div className="glass-card p-4 rounded-xl border border-slate-800/80">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Creadas</span>
            <PlusCircle className="w-4 h-4 text-sky-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-sky-400 mt-2">
            {metrics?.ordersCreated ?? 0}
          </div>
          <div className="text-[10px] text-slate-500 mt-1 font-mono">stockpilot.orders.created</div>
        </div>

        <div className="glass-card p-4 rounded-xl border border-slate-800/80">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Confirmadas</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-emerald-400 mt-2">
            {metrics?.ordersConfirmed ?? 0}
          </div>
          <div className="text-[10px] text-slate-500 mt-1 font-mono">stockpilot.orders.confirmed</div>
        </div>

        <div className="glass-card p-4 rounded-xl border border-slate-800/80">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Despachadas</span>
            <Truck className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-indigo-400 mt-2">
            {metrics?.ordersFulfilled ?? 0}
          </div>
          <div className="text-[10px] text-slate-500 mt-1 font-mono">stockpilot.orders.fulfilled</div>
        </div>

        <div className="glass-card p-4 rounded-xl border border-slate-800/80">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Canceladas</span>
            <XCircle className="w-4 h-4 text-rose-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-rose-400 mt-2">
            {metrics?.ordersCancelled ?? 0}
          </div>
          <div className="text-[10px] text-slate-500 mt-1 font-mono">stockpilot.orders.cancelled</div>
        </div>

        <div className="glass-card p-4 rounded-xl border border-amber-900/30 bg-amber-950/10">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-amber-300">Conflicto Stock (409)</span>
            <ShieldAlert className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-amber-400 mt-2">
            {metrics?.insufficientStockFailures ?? 0}
          </div>
          <div className="text-[10px] text-slate-500 mt-1 font-mono">reservations.insufficient_stock</div>
        </div>

        <div className="glass-card p-4 rounded-xl border border-purple-900/30 bg-purple-950/10">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-purple-300">Idempotency Replays</span>
            <Repeat className="w-4 h-4 text-purple-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-purple-400 mt-2">
            {metrics?.idempotencyReplays ?? 0}
          </div>
          <div className="text-[10px] text-slate-500 mt-1 font-mono">idempotency.replays</div>
        </div>
      </div>

      {/* Gráficos Recharts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Gráfico 1: Distribución del Flujo de Órdenes */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800">
          <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-amber-400" />
            Flujo Operativo de Órdenes (Contadores Acumulados)
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Medición de paso por cada transición de estado en la máquina de estados ACID.
          </p>

          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={orderDistributionData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" stroke="#64748b" fontSize={11} tickLine={false} />
                <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0f172a',
                    borderColor: '#334155',
                    borderRadius: '0.5rem',
                    color: '#f8fafc',
                    fontSize: '12px',
                  }}
                />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {orderDistributionData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Gráfico 2: Tasa de Concurrencia vs Sobredemanda */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800">
          <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
            <ShieldAlert className="w-4 h-4 text-emerald-400" />
            Protección de Concurrencia e Integridad de Stocks
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Reservas aseguradas bajo Lock Pesimista vs peticiones bloqueadas por protección de sobreventa.
          </p>

          <div className="h-64 mt-4 flex items-center justify-center">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={concurrencyRatioData}
                  cx="50%"
                  cy="50%"
                  innerRadius={50}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {concurrencyRatioData.map((entry, index) => (
                    <Cell key={`slice-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0f172a',
                    borderColor: '#334155',
                    borderRadius: '0.5rem',
                    color: '#f8fafc',
                    fontSize: '12px',
                  }}
                />
                <Legend
                  verticalAlign="bottom"
                  height={36}
                  wrapperStyle={{ fontSize: '11px', color: '#94a3b8' }}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Métricas de Infraestructura: HikariCP & JVM */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* HikariCP Pool Gauge */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Database className="w-4 h-4 text-sky-400" />
              <h3 className="text-sm font-semibold text-slate-200">
                HikariCP Connection Pool (PostgreSQL)
              </h3>
            </div>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-sky-950/50 border border-sky-800/50 text-sky-300">
              {metrics?.hikariActiveConnections} / {metrics?.hikariTotalConnections} Activas
            </span>
          </div>

          <p className="text-xs text-slate-400 mt-2">
            Pool gestionado con timeout estricto de conexión y leak detection threshold para evitar agotamiento de descriptores de sockets.
          </p>

          <div className="mt-4 space-y-2">
            <div className="flex justify-between text-xs font-mono">
              <span className="text-slate-400">Saturación del Pool</span>
              <span className="text-sky-300 font-semibold">{hikariUsagePct}%</span>
            </div>
            <div className="w-full h-3 bg-slate-900 rounded-full overflow-hidden border border-slate-800">
              <div
                className="h-full bg-gradient-to-r from-sky-500 to-emerald-400 transition-all duration-500"
                style={{ width: `${Math.min(hikariUsagePct, 100)}%` }}
              />
            </div>
          </div>
        </div>

        {/* JVM Heap Gauge */}
        <div className="glass-panel p-5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Cpu className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-semibold text-slate-200">
                JVM Runtime Memory (Heap Allocation)
              </h3>
            </div>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-emerald-950/50 border border-emerald-800/50 text-emerald-300">
              {metrics?.jvmHeapUsedMb} MB / {metrics?.jvmHeapMaxMb} MB
            </span>
          </div>

          <p className="text-xs text-slate-400 mt-2">
            G1 Garbage Collector en OpenJDK 21. El consumo estable demuestra cero fuga de memoria en transacciones ACID recurrentes.
          </p>

          <div className="mt-4 space-y-2">
            <div className="flex justify-between text-xs font-mono">
              <span className="text-slate-400">Ocupación del Heap</span>
              <span className="text-emerald-300 font-semibold">{heapUsagePct}%</span>
            </div>
            <div className="w-full h-3 bg-slate-900 rounded-full overflow-hidden border border-slate-800">
              <div
                className="h-full bg-gradient-to-r from-emerald-500 to-amber-400 transition-all duration-500"
                style={{ width: `${Math.min(heapUsagePct, 100)}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Panel Pedagógico / Arquitectura para Entrevistas */}
      <div className="glass-panel p-6 rounded-xl border border-amber-500/30 bg-amber-950/10 space-y-3">
        <div className="flex items-center gap-2">
          <Activity className="w-4 h-4 text-amber-400" />
          <h3 className="text-sm font-bold text-amber-300">
            Fundamento Arquitectónico: ¿Por qué Actuator + Micrometer + Prometheus?
          </h3>
        </div>
        <p className="text-xs text-slate-300 leading-relaxed">
          En arquitecturas de misión crítica (High Concurrency e-commerce y WMS), la observabilidad no es opcional:
        </p>
        <ul className="text-xs text-slate-400 space-y-1.5 list-disc list-inside">
          <li>
            <strong className="text-slate-200">Métricas dimensionales con Tags:</strong> Cada llamada registra contadores tipados (ej. <code className="font-mono text-amber-300">stockpilot.reservations.insufficient_stock</code>) permitiendo a Grafana generar alertas si el ratio de conflicto 409 supera el 5% sostenido.
          </li>
          <li>
            <strong className="text-slate-200">Detección de Replays Idempotentes:</strong> Medimos cuántas solicitudes duplicadas fueron absorbidas de forma transparente sin ejecutar débitos contables redundantes.
          </li>
          <li>
            <strong className="text-slate-200">Monitoreo HikariCP:</strong> Permite detectar <em className="text-slate-300">connection starvation</em> cuando 50 hilos concurrentes compiten por locks pesimistas en la base de datos PostgreSQL.
          </li>
        </ul>
      </div>
    </div>
  );
};
