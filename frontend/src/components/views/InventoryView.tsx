import React, { useEffect, useState } from 'react';
import { Warehouse as WarehouseIcon, History, ArrowDownToLine, RefreshCw, AlertCircle } from 'lucide-react';
import { inventoryApi } from '../../api/inventoryApi';
import { catalogApi } from '../../api/catalogApi';
import type { Warehouse, StockLevel, LedgerEntry, StockAdjustmentRequest } from '../../types/inventory';
import type { Product } from '../../types/catalog';
import { StatusBadge } from '../common/StatusBadge';

export const InventoryView: React.FC = () => {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState<number | null>(null);
  const [stockLevels, setStockLevels] = useState<StockLevel[]>([]);
  const [ledgerEntries, setLedgerEntries] = useState<LedgerEntry[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loadingStock, setLoadingStock] = useState<boolean>(true);
  const [loadingLedger, setLoadingLedger] = useState<boolean>(true);
  const [activeSubTab, setActiveSubTab] = useState<'levels' | 'ledger'>('levels');

  // Adjustment Modal state
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [adjustProductId, setAdjustProductId] = useState<number | ''>('');
  const [deltaQty, setDeltaQty] = useState<string>('');
  const [movementType, setMovementType] = useState<StockAdjustmentRequest['movementType']>('INBOUND');
  const [reason, setReason] = useState<string>('');
  const [performedBy, setPerformedBy] = useState<string>('victor.venegas');
  const [modalError, setModalError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const loadWarehouses = async () => {
    try {
      const data = await inventoryApi.getWarehouses();
      setWarehouses(data);
      if (data.length > 0 && selectedWarehouseId === null) {
        setSelectedWarehouseId(data[0].id);
      }
    } catch (err) {
      console.error('Error fetching warehouses:', err);
    }
  };

  const loadProducts = async () => {
    try {
      const data = await catalogApi.getProducts(0, 100);
      setProducts(data.content || []);
    } catch (err) {
      console.error('Error loading products list:', err);
    }
  };

  const loadStock = async () => {
    if (!selectedWarehouseId) return;
    try {
      setLoadingStock(true);
      const data = await inventoryApi.getStockByWarehouse(selectedWarehouseId, 0, 50);
      setStockLevels(data.content || []);
    } catch (err) {
      console.error('Error loading stock levels:', err);
    } finally {
      setLoadingStock(false);
    }
  };

  const loadLedger = async () => {
    try {
      setLoadingLedger(true);
      const data = await inventoryApi.getLedgerHistory(0, 50, undefined, selectedWarehouseId || undefined);
      setLedgerEntries(data.content || []);
    } catch (err) {
      console.error('Error loading ledger history:', err);
    } finally {
      setLoadingLedger(false);
    }
  };

  useEffect(() => {
    loadWarehouses();
    loadProducts();
  }, []);

  useEffect(() => {
    if (selectedWarehouseId) {
      loadStock();
      loadLedger();
    }
  }, [selectedWarehouseId]);

  const handleAdjustStock = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);

    if (!selectedWarehouseId || !adjustProductId || !deltaQty || !reason.trim()) {
      setModalError('Todos los campos son obligatorios.');
      return;
    }

    const deltaNum = parseInt(deltaQty, 10);
    if (isNaN(deltaNum) || deltaNum === 0) {
      setModalError('La cantidad delta debe ser un número entero distinto de 0.');
      return;
    }

    try {
      setIsSubmitting(true);
      const payload: StockAdjustmentRequest = {
        productId: Number(adjustProductId),
        warehouseId: selectedWarehouseId,
        deltaQty: deltaNum,
        movementType,
        reason: reason.trim(),
        performedBy: performedBy.trim() || 'system.operator',
      };

      await inventoryApi.adjustStock(payload);
      setIsModalOpen(false);
      // Reset
      setAdjustProductId('');
      setDeltaQty('');
      setReason('');
      // Reload views
      loadStock();
      loadLedger();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : 'Error al ajustar inventario');
    } finally {
      setIsSubmitting(false);
    }
  };

  const selectedWarehouse = warehouses.find((w) => w.id === selectedWarehouseId);

  return (
    <div className="space-y-6">
      {/* Header & Warehouse Selector */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <WarehouseIcon className="w-6 h-6 text-emerald-400" />
            Inventario Multi-Bodega & Ledger Inmutable
          </h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Cálculo atómico de disponibilidad (`available_qty STORED`) y pista de auditoría inalterable SOX/ISO 27001.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsModalOpen(true)}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs transition shadow-md shadow-emerald-500/20"
          >
            <ArrowDownToLine className="w-4 h-4" />
            <span>Ajuste Físico de Stock</span>
          </button>
        </div>
      </div>

      {/* Warehouse Selector & SubTabs */}
      <div className="glass-panel p-4 rounded-xl flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-3 w-full md:w-auto">
          <span className="text-xs font-semibold text-slate-400 whitespace-nowrap">Centro Logístico:</span>
          <select
            value={selectedWarehouseId || ''}
            onChange={(e) => setSelectedWarehouseId(Number(e.target.value))}
            className="px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-xs text-white font-medium focus:border-emerald-400 focus:outline-none transition w-full sm:w-64"
          >
            {warehouses.map((wh) => (
              <option key={wh.id} value={wh.id}>
                {wh.code} — {wh.name}
              </option>
            ))}
          </select>
          {selectedWarehouse && (
            <span className="text-[11px] text-slate-400 hidden lg:inline font-mono">
              📍 {selectedWarehouse.address}
            </span>
          )}
        </div>

        <div className="flex gap-2 w-full md:w-auto">
          <button
            onClick={() => setActiveSubTab('levels')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              activeSubTab === 'levels'
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                : 'text-slate-400 hover:text-white bg-slate-900/60'
            }`}
          >
            Existencias por SKU ({stockLevels.length})
          </button>
          <button
            onClick={() => setActiveSubTab('ledger')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition ${
              activeSubTab === 'ledger'
                ? 'bg-purple-500/20 text-purple-300 border border-purple-500/40'
                : 'text-slate-400 hover:text-white bg-slate-900/60'
            }`}
          >
            <History className="w-3.5 h-3.5" />
            <span>Ledger de Auditoría ({ledgerEntries.length})</span>
          </button>
        </div>
      </div>

      {/* Main View: Stock Levels Table */}
      {activeSubTab === 'levels' && (
        <div className="glass-panel rounded-xl overflow-hidden">
          <div className="p-4 border-b border-slate-800 bg-slate-900/40 flex items-center justify-between">
            <div className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              Saldos en Tiempo Real: Bodega {selectedWarehouse?.code}
            </div>
            <button
              onClick={loadStock}
              className="text-slate-400 hover:text-white text-xs flex items-center gap-1 font-mono"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loadingStock ? 'animate-spin' : ''}`} />
              Refrescar
            </button>
          </div>

          {loadingStock ? (
            <div className="text-center py-16 text-slate-400 text-xs font-mono animate-pulse">
              Consultando niveles de existencias en PostgreSQL...
            </div>
          ) : stockLevels.length === 0 ? (
            <div className="text-center py-16 text-slate-500 text-xs">
              No hay existencias registradas en esta bodega. Ejecuta un "Ajuste Físico de Stock" para ingresar unidades.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs font-mono">
                <thead className="text-[11px] uppercase tracking-wider text-slate-400 bg-slate-900/60 border-b border-slate-800 font-sans">
                  <tr>
                    <th className="py-3 px-4">SKU Industrial</th>
                    <th className="py-3 px-4">Descripción Componente</th>
                    <th className="py-3 px-4 text-right">Total Físico (total_qty)</th>
                    <th className="py-3 px-4 text-right">Reservado (reserved_qty)</th>
                    <th className="py-3 px-4 text-right text-emerald-400 font-bold">
                      Disponible (available_qty STORED)
                    </th>
                    <th className="py-3 px-4 text-center">Estado Invariante</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {stockLevels.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-900/40 transition">
                      <td className="py-3 px-4 font-bold text-amber-300">{item.productSku}</td>
                      <td className="py-3 px-4 font-sans text-white">{item.productName}</td>
                      <td className="py-3 px-4 text-right text-slate-300">{item.totalQty}</td>
                      <td className="py-3 px-4 text-right text-purple-400 font-semibold">{item.reservedQty}</td>
                      <td className="py-3 px-4 text-right text-emerald-400 font-bold text-sm">
                        {item.availableQty}
                      </td>
                      <td className="py-3 px-4 text-center">
                        {item.availableQty > 0 ? (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-500/30">
                            DISPONIBLE
                          </span>
                        ) : item.reservedQty > 0 ? (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-950/80 text-amber-400 border border-amber-500/30">
                            100% RESERVADO
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-950/80 text-rose-400 border border-rose-500/30">
                            QUIEBRE (0)
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Main View: Immutable Ledger History */}
      {activeSubTab === 'ledger' && (
        <div className="glass-panel rounded-xl overflow-hidden">
          <div className="p-4 border-b border-slate-800 bg-slate-900/40 flex items-center justify-between">
            <div>
              <div className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-purple-400" />
                Libro Mayor de Auditoría (@Immutable Append-Only)
              </div>
              <p className="text-[11px] text-slate-400 font-sans mt-0.5">
                Cada movimiento es inmutable. No existen comandos UPDATE o DELETE. Cumplimiento normativo SOX e ISO 27001.
              </p>
            </div>
            <button
              onClick={loadLedger}
              className="text-slate-400 hover:text-white text-xs flex items-center gap-1 font-mono"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loadingLedger ? 'animate-spin' : ''}`} />
              Refrescar
            </button>
          </div>

          {loadingLedger ? (
            <div className="text-center py-16 text-slate-400 text-xs font-mono animate-pulse">
              Consultando eventos del Ledger en PostgreSQL...
            </div>
          ) : ledgerEntries.length === 0 ? (
            <div className="text-center py-16 text-slate-500 text-xs">
              No se registran movimientos en el libro mayor para esta bodega.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs font-mono">
                <thead className="text-[11px] uppercase tracking-wider text-slate-400 bg-slate-900/60 border-b border-slate-800 font-sans">
                  <tr>
                    <th className="py-3 px-4">ID Evento</th>
                    <th className="py-3 px-4">Fecha/Hora (Timestamp)</th>
                    <th className="py-3 px-4">Tipo Movimiento</th>
                    <th className="py-3 px-4">SKU</th>
                    <th className="py-3 px-4 text-right">Delta (Δ)</th>
                    <th className="py-3 px-4">Operador Responsable</th>
                    <th className="py-3 px-4">Motivo / Referencia</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {ledgerEntries.map((entry) => (
                    <tr key={entry.id} className="hover:bg-slate-900/40 transition">
                      <td className="py-3 px-4 text-slate-500 font-semibold">#{entry.id}</td>
                      <td className="py-3 px-4 text-slate-300 text-[11px]">
                        {new Date(entry.createdAt).toLocaleString('es-CL')}
                      </td>
                      <td className="py-3 px-4">
                        <StatusBadge status={entry.movementType} />
                      </td>
                      <td className="py-3 px-4 font-bold text-amber-300">{entry.productSku}</td>
                      <td
                        className={`py-3 px-4 text-right font-bold text-sm ${
                          entry.deltaQty > 0
                            ? 'text-emerald-400'
                            : entry.deltaQty < 0
                            ? 'text-rose-400'
                            : 'text-slate-400'
                        }`}
                      >
                        {entry.deltaQty > 0 ? `+${entry.deltaQty}` : entry.deltaQty}
                      </td>
                      <td className="py-3 px-4 text-slate-300 font-sans text-[11px]">{entry.performedBy}</td>
                      <td className="py-3 px-4 text-slate-400 font-sans text-[11px] truncate max-w-xs">
                        {entry.reason}
                        {entry.referenceId && (
                          <span className="block text-slate-500 text-[10px] font-mono">
                            Ref: {entry.referenceId}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Modal: Adjust Stock */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="glass-panel w-full max-w-md p-6 rounded-2xl border border-slate-700 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <ArrowDownToLine className="w-4 h-4 text-emerald-400" />
                Ajuste Físico de Inventario
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="text-slate-400 hover:text-white text-sm"
              >
                ✕
              </button>
            </div>

            {modalError && (
              <div className="p-3 rounded-lg bg-rose-950/80 border border-rose-500/40 text-rose-300 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{modalError}</span>
              </div>
            )}

            <form onSubmit={handleAdjustStock} className="space-y-3 text-xs">
              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  Centro Logístico
                </label>
                <input
                  type="text"
                  disabled
                  value={`${selectedWarehouse?.code} — ${selectedWarehouse?.name}`}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900/60 border border-slate-800 text-slate-400 font-mono"
                />
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  SKU Industrial *
                </label>
                <select
                  required
                  value={adjustProductId}
                  onChange={(e) => setAdjustProductId(Number(e.target.value))}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-emerald-400 focus:outline-none"
                >
                  <option value="">Selecciona componente...</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.sku} — {p.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">
                    Tipo de Movimiento *
                  </label>
                  <select
                    value={movementType}
                    onChange={(e) => setMovementType(e.target.value as StockAdjustmentRequest['movementType'])}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-emerald-400 focus:outline-none"
                  >
                    <option value="INBOUND">INBOUND (Entrada)</option>
                    <option value="PHYSICAL_COUNT">PHYSICAL_COUNT (Conteo)</option>
                    <option value="ADJUSTMENT">ADJUSTMENT (Ajuste)</option>
                    <option value="SCRAP">SCRAP (Merma/Baja)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-slate-400 font-semibold mb-1">
                    Cantidad Delta (Δ) *
                  </label>
                  <input
                    type="number"
                    required
                    placeholder="+10 o -2"
                    value={deltaQty}
                    onChange={(e) => setDeltaQty(e.target.value)}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white font-mono focus:border-emerald-400 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  Motivo Formal de Negocio (Requerido por Auditoría) *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ej: Recepción OC Proveedor #4092, Conteo cíclico estantería A3"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-emerald-400 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  Operador Responsable
                </label>
                <input
                  type="text"
                  value={performedBy}
                  onChange={(e) => setPerformedBy(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-emerald-400 focus:outline-none"
                />
              </div>

              <div className="pt-3 border-t border-slate-800 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-lg bg-slate-900 border border-slate-700 text-slate-300 hover:text-white transition"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-bold transition disabled:opacity-50"
                >
                  {isSubmitting ? 'Asentando en Ledger...' : 'Ejecutar Ajuste'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
