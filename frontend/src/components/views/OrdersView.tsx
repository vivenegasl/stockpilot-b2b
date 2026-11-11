import React, { useEffect, useState } from 'react';
import { ShoppingCart, Plus, CheckCircle, XCircle, Truck, AlertCircle, RefreshCw, Key, ShieldCheck } from 'lucide-react';
import { orderApi } from '../../api/orderApi';
import { catalogApi } from '../../api/catalogApi';
import { inventoryApi } from '../../api/inventoryApi';
import type { Order, OrderStatus, CreateOrderRequest } from '../../types/order';
import type { Product } from '../../types/catalog';
import type { Warehouse } from '../../types/inventory';
import { StatusBadge } from '../common/StatusBadge';
import { IdempotencyBadge } from '../common/IdempotencyBadge';

export const OrdersView: React.FC = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [selectedStatus, setSelectedStatus] = useState<OrderStatus | undefined>();
  const [loading, setLoading] = useState<boolean>(true);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);

  // Modal create order
  const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);
  const [products, setProducts] = useState<Product[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [orderWarehouseId, setOrderWarehouseId] = useState<number | ''>('');
  const [orderNotes, setOrderNotes] = useState<string>('');
  const [orderItems, setOrderItems] = useState<{ productId: number; quantity: number }[]>([
    { productId: 0, quantity: 1 },
  ]);
  const [customKey, setCustomKey] = useState<string>('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [lastReplayNotice, setLastReplayNotice] = useState<{ isReplay: boolean; key: string } | null>(null);

  // Action status message
  const [actionFeedback, setActionFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const loadOrders = async () => {
    try {
      setLoading(true);
      const data = await orderApi.getOrders(selectedStatus, page, 10);
      setOrders(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);

      // Refresh selected order if open
      if (selectedOrder) {
        const refreshed = (data.content || []).find((o) => o.id === selectedOrder.id);
        if (refreshed) setSelectedOrder(refreshed);
      }
    } catch (err) {
      console.error('Error fetching orders:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadReferenceData = async () => {
    try {
      const [prods, whs] = await Promise.all([
        catalogApi.getProducts(0, 100),
        inventoryApi.getWarehouses(),
      ]);
      setProducts(prods.content || []);
      setWarehouses(whs);
      if (whs.length > 0) setOrderWarehouseId(whs[0].id);
      if (prods.content && prods.content.length > 0) {
        setOrderItems([{ productId: prods.content[0].id, quantity: 1 }]);
      }
    } catch (err) {
      console.error('Error loading reference data for orders:', err);
    }
  };

  useEffect(() => {
    loadReferenceData();
  }, []);

  useEffect(() => {
    loadOrders();
  }, [page, selectedStatus]);

  const handleAddItemRow = () => {
    if (products.length > 0) {
      setOrderItems([...orderItems, { productId: products[0].id, quantity: 1 }]);
    }
  };

  const handleRemoveItemRow = (index: number) => {
    if (orderItems.length > 1) {
      setOrderItems(orderItems.filter((_, i) => i !== index));
    }
  };

  const handleItemChange = (index: number, field: 'productId' | 'quantity', value: number) => {
    const updated = [...orderItems];
    updated[index] = { ...updated[index], [field]: value };
    setOrderItems(updated);
  };

  const calculateEstimatedTotal = () => {
    return orderItems.reduce((acc, item) => {
      const prod = products.find((p) => p.id === item.productId);
      return acc + (prod ? prod.unitPrice * item.quantity : 0);
    }, 0);
  };

  const handleCreateOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);
    setLastReplayNotice(null);

    if (!orderWarehouseId || orderItems.some((i) => !i.productId || i.quantity <= 0)) {
      setCreateError('Verifica el centro logístico y que cada línea tenga un producto y cantidad > 0.');
      return;
    }

    try {
      setIsSubmitting(true);
      const payload: CreateOrderRequest = {
        warehouseId: Number(orderWarehouseId),
        notes: orderNotes.trim(),
        items: orderItems.map((i) => ({ productId: Number(i.productId), quantity: Number(i.quantity) })),
      };

      const result = await orderApi.createOrderWithIdempotency(payload, customKey.trim() || undefined);
      setLastReplayNotice({
        isReplay: result.isIdempotentReplay,
        key: result.idempotencyKey,
      });

      setIsCreateOpen(false);
      setOrderNotes('');
      setCustomKey('');
      setSelectedOrder(result.data);
      loadOrders();
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Error al registrar orden');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleConfirmOrder = async (orderId: number) => {
    setActionFeedback(null);
    try {
      const updated = await orderApi.confirmOrder(orderId);
      setSelectedOrder(updated);
      setActionFeedback({
        type: 'success',
        message: `Orden '${updated.orderNumber}' CONFIRMADA exitosamente. Bloqueo pesimista adquirido y stock reservado.`,
      });
      loadOrders();
    } catch (err) {
      setActionFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Fallo en la confirmación de la orden',
      });
    }
  };

  const handleCancelOrder = async (orderId: number) => {
    setActionFeedback(null);
    try {
      const updated = await orderApi.cancelOrder(orderId);
      setSelectedOrder(updated);
      setActionFeedback({
        type: 'success',
        message: `Orden '${updated.orderNumber}' CANCELADA. Reservas activas restituidas limpiamente al stock disponible.`,
      });
      loadOrders();
    } catch (err) {
      setActionFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Error al cancelar orden',
      });
    }
  };

  const handleFulfillOrder = async (orderId: number) => {
    setActionFeedback(null);
    try {
      const updated = await orderApi.fulfillOrder(orderId);
      setSelectedOrder(updated);
      setActionFeedback({
        type: 'success',
        message: `Orden '${updated.orderNumber}' DESPACHADA (FULFILLED). Saldo físico total_qty rebajado definitivamente.`,
      });
      loadOrders();
    } catch (err) {
      setActionFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Error al despachar orden',
      });
    }
  };

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <ShoppingCart className="w-6 h-6 text-amber-400" />
            Mesa de Órdenes & Reserva Concurrente B2B
          </h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Máquina de estados estricta (DRAFT → CONFIRMED → FULFILLED), idempotencia HTTP perimetral y prevención de deadlocks.
          </p>
        </div>
        <button
          onClick={() => {
            setCreateError(null);
            setIsCreateOpen(true);
          }}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition shadow-md shadow-amber-500/20"
        >
          <Plus className="w-4 h-4" />
          <span>Crear Orden B2B (DRAFT)</span>
        </button>
      </div>

      {/* Global Notice / Idempotency Replay alert */}
      {lastReplayNotice && (
        <div className="p-4 rounded-xl bg-slate-900 border border-amber-500/40 flex items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2 text-slate-200">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>Última petición procesada con Idempotency-Key:</span>
            <span className="font-mono text-amber-400 font-bold">{lastReplayNotice.key}</span>
          </div>
          <IdempotencyBadge isReplay={lastReplayNotice.isReplay} />
        </div>
      )}

      {/* Action feedback */}
      {actionFeedback && (
        <div
          className={`p-3 rounded-lg text-xs flex items-center gap-2 ${
            actionFeedback.type === 'success'
              ? 'bg-emerald-950/80 border border-emerald-500/40 text-emerald-300'
              : 'bg-rose-950/80 border border-rose-500/40 text-rose-300'
          }`}
        >
          {actionFeedback.type === 'success' ? (
            <CheckCircle className="w-4 h-4 shrink-0 text-emerald-400" />
          ) : (
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
          )}
          <span>{actionFeedback.message}</span>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="glass-panel p-3 rounded-xl flex items-center justify-between gap-2 overflow-x-auto">
        <div className="flex items-center gap-1.5 text-xs font-semibold">
          {(['ALL', 'DRAFT', 'CONFIRMED', 'CANCELLED', 'FULFILLED'] as const).map((st) => {
            const isSelected = (st === 'ALL' && !selectedStatus) || selectedStatus === st;
            return (
              <button
                key={st}
                onClick={() => {
                  setSelectedStatus(st === 'ALL' ? undefined : (st as OrderStatus));
                  setPage(0);
                }}
                className={`px-3 py-1.5 rounded-lg transition whitespace-nowrap ${
                  isSelected
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                    : 'text-slate-400 hover:text-white hover:bg-slate-900/60'
                }`}
              >
                {st === 'ALL' ? 'Todas' : st}
              </button>
            );
          })}
        </div>

        <button
          onClick={loadOrders}
          className="text-slate-400 hover:text-white text-xs flex items-center gap-1 font-mono pr-2"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refrescar ({totalElements})
        </button>
      </div>

      {/* Grid: Orders List & Detail Panel */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Table Column */}
        <div className={`glass-panel rounded-xl overflow-hidden ${selectedOrder ? 'lg:col-span-7' : 'lg:col-span-12'}`}>
          {loading ? (
            <div className="text-center py-16 text-slate-400 text-xs font-mono animate-pulse">
              Consultando pedidos en PostgreSQL...
            </div>
          ) : orders.length === 0 ? (
            <div className="text-center py-16 text-slate-500 text-xs">
              No hay órdenes registradas con el estado seleccionado.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="text-[11px] uppercase tracking-wider text-slate-400 bg-slate-900/40 border-b border-slate-800">
                  <tr>
                    <th className="py-3 px-4">Número de Orden</th>
                    <th className="py-3 px-4">Bodega</th>
                    <th className="py-3 px-4">Estado</th>
                    <th className="py-3 px-4 text-right">Monto Total</th>
                    <th className="py-3 px-4 text-center">Acción</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {orders.map((order) => {
                    const isSelected = selectedOrder?.id === order.id;
                    return (
                      <tr
                        key={order.id}
                        onClick={() => setSelectedOrder(order)}
                        className={`cursor-pointer transition ${
                          isSelected
                            ? 'bg-amber-500/10 border-l-2 border-amber-400'
                            : 'hover:bg-slate-900/40'
                        }`}
                      >
                        <td className="py-3 px-4 font-mono font-bold text-amber-300">
                          {order.orderNumber}
                        </td>
                        <td className="py-3 px-4 text-slate-300">
                          {order.warehouseCode}
                        </td>
                        <td className="py-3 px-4">
                          <StatusBadge status={order.status} />
                        </td>
                        <td className="py-3 px-4 text-right font-mono font-bold text-emerald-400">
                          ${Number(order.totalNetAmount).toLocaleString('es-CL')}
                        </td>
                        <td className="py-3 px-4 text-center">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedOrder(order);
                            }}
                            className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px]"
                          >
                            Ver Detalle
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="p-3 border-t border-slate-800 flex items-center justify-between text-xs">
              <span className="text-slate-400">Pág. {page + 1} de {totalPages}</span>
              <div className="flex gap-2">
                <button
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="px-2.5 py-1 rounded bg-slate-900 border border-slate-700 text-slate-300 disabled:opacity-40"
                >
                  Anterior
                </button>
                <button
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="px-2.5 py-1 rounded bg-slate-900 border border-slate-700 text-slate-300 disabled:opacity-40"
                >
                  Siguiente
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Detail Panel */}
        {selectedOrder && (
          <div className="glass-panel p-5 rounded-xl lg:col-span-5 space-y-4 border-amber-500/30">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div>
                <span className="text-[11px] font-mono text-slate-500 uppercase tracking-wider">Detalle de Orden</span>
                <h3 className="text-lg font-bold font-mono text-amber-300">{selectedOrder.orderNumber}</h3>
              </div>
              <StatusBadge status={selectedOrder.status} />
            </div>

            {/* Info Grid */}
            <div className="grid grid-cols-2 gap-2 text-xs font-mono">
              <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800">
                <div className="text-[10px] text-slate-500 uppercase font-sans">Centro Logístico</div>
                <div className="font-semibold text-white mt-0.5">{selectedOrder.warehouseCode}</div>
                <div className="text-[11px] text-slate-400 font-sans">{selectedOrder.warehouseName}</div>
              </div>
              <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800">
                <div className="text-[10px] text-slate-500 uppercase font-sans">Total Neto (CLP)</div>
                <div className="text-base font-bold text-emerald-400 mt-0.5">
                  ${Number(selectedOrder.totalNetAmount).toLocaleString('es-CL')}
                </div>
              </div>
            </div>

            {/* Idempotency Token Info */}
            <div className="p-2.5 rounded bg-slate-900/80 border border-slate-800 text-xs">
              <div className="text-[10px] text-slate-500 uppercase font-mono flex items-center gap-1.5">
                <Key className="w-3 h-3 text-amber-400" />
                Idempotency-Key Vinculada (UUID v4)
              </div>
              <div className="font-mono text-slate-300 text-[11px] mt-1 break-all select-all">
                {selectedOrder.idempotencyKey}
              </div>
            </div>

            {/* Order Lines Table */}
            <div>
              <div className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-2">
                Líneas de la Orden ({selectedOrder.lines.length})
              </div>
              <div className="border border-slate-800 rounded-lg overflow-hidden">
                <table className="w-full text-left text-xs font-mono">
                  <thead className="bg-slate-900 text-[10px] uppercase text-slate-400">
                    <tr>
                      <th className="py-2 px-3">SKU</th>
                      <th className="py-2 px-3 text-right">Cant.</th>
                      <th className="py-2 px-3 text-right">Unitario</th>
                      <th className="py-2 px-3 text-right">Subtotal</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {selectedOrder.lines.map((line) => (
                      <tr key={line.id} className="hover:bg-slate-900/40">
                        <td className="py-2 px-3">
                          <span className="font-bold text-amber-300">{line.productSku}</span>
                          <span className="block text-[10px] text-slate-400 font-sans">{line.productName}</span>
                        </td>
                        <td className="py-2 px-3 text-right text-white font-bold">{line.quantity}</td>
                        <td className="py-2 px-3 text-right text-slate-400">${Number(line.unitPrice).toLocaleString('es-CL')}</td>
                        <td className="py-2 px-3 text-right text-emerald-400 font-bold">${Number(line.lineTotal).toLocaleString('es-CL')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Lifecycle State Actions */}
            <div className="pt-3 border-t border-slate-800 space-y-2">
              <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider font-mono">
                Transiciones de la Máquina de Estados
              </div>

              {selectedOrder.status === 'DRAFT' && (
                <div className="flex gap-2">
                  <button
                    onClick={() => handleConfirmOrder(selectedOrder.id)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs transition shadow-md shadow-emerald-600/20"
                  >
                    <CheckCircle className="w-3.5 h-3.5" />
                    <span>Confirmar con Lock Pesimista</span>
                  </button>
                  <button
                    onClick={() => handleCancelOrder(selectedOrder.id)}
                    className="inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg bg-slate-900 border border-slate-700 hover:border-rose-500 text-rose-400 text-xs font-semibold transition"
                  >
                    <XCircle className="w-3.5 h-3.5" />
                    <span>Cancelar</span>
                  </button>
                </div>
              )}

              {selectedOrder.status === 'CONFIRMED' && (
                <div className="flex gap-2">
                  <button
                    onClick={() => handleFulfillOrder(selectedOrder.id)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg bg-sky-600 hover:bg-sky-500 text-white font-bold text-xs transition shadow-md shadow-sky-600/20"
                  >
                    <Truck className="w-3.5 h-3.5" />
                    <span>Despachar (Fulfill Stock)</span>
                  </button>
                  <button
                    onClick={() => handleCancelOrder(selectedOrder.id)}
                    className="inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg bg-rose-950/80 border border-rose-500/40 text-rose-300 text-xs font-semibold transition"
                  >
                    <XCircle className="w-3.5 h-3.5" />
                    <span>Liberar Reserva</span>
                  </button>
                </div>
              )}

              {selectedOrder.status === 'FULFILLED' && (
                <div className="p-3 rounded-lg bg-sky-950/40 border border-sky-500/30 text-sky-300 text-xs flex items-center gap-2">
                  <Truck className="w-4 h-4 text-sky-400 shrink-0" />
                  <span>Orden completada y despachada. Inventario físico deducido definitivamente.</span>
                </div>
              )}

              {selectedOrder.status === 'CANCELLED' && (
                <div className="p-3 rounded-lg bg-rose-950/40 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2">
                  <XCircle className="w-4 h-4 text-rose-400 shrink-0" />
                  <span>Orden cancelada. Estado terminal sin reservas activas.</span>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Modal: Create Order */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm">
          <div className="glass-panel w-full max-w-2xl p-6 rounded-2xl border border-slate-700 shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <ShoppingCart className="w-4 h-4 text-amber-400" />
                Nueva Orden de Compra B2B (DRAFT)
              </h3>
              <button
                onClick={() => setIsCreateOpen(false)}
                className="text-slate-400 hover:text-white text-sm"
              >
                ✕
              </button>
            </div>

            {createError && (
              <div className="p-3 rounded-lg bg-rose-950/80 border border-rose-500/40 text-rose-300 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{createError}</span>
              </div>
            )}

            <form onSubmit={handleCreateOrder} className="space-y-4 text-xs">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">
                    Centro Logístico de Despacho *
                  </label>
                  <select
                    required
                    value={orderWarehouseId}
                    onChange={(e) => setOrderWarehouseId(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-amber-400 focus:outline-none"
                  >
                    {warehouses.map((wh) => (
                      <option key={wh.id} value={wh.id}>
                        {wh.code} — {wh.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-slate-400 font-semibold mb-1 flex items-center justify-between">
                    <span>Idempotency-Key Personalizada</span>
                    <span className="text-[10px] text-slate-500 font-normal">Opcional (Auto-generada si vacía)</span>
                  </label>
                  <input
                    type="text"
                    placeholder="UUID v4 para simular replay..."
                    value={customKey}
                    onChange={(e) => setCustomKey(e.target.value)}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white font-mono text-[11px] focus:border-amber-400 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">Notas del Pedido</label>
                <input
                  type="text"
                  placeholder="Ej: Despacho prioritario para faena minera norte"
                  value={orderNotes}
                  onChange={(e) => setOrderNotes(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-amber-400 focus:outline-none"
                />
              </div>

              {/* Items List */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-slate-300 font-bold uppercase tracking-wider text-[11px]">
                    Líneas de Productos
                  </label>
                  <button
                    type="button"
                    onClick={handleAddItemRow}
                    className="text-amber-400 hover:text-amber-300 text-xs font-semibold flex items-center gap-1"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Agregar SKU</span>
                  </button>
                </div>

                <div className="space-y-2">
                  {orderItems.map((item, idx) => {
                    const selectedProd = products.find((p) => p.id === item.productId);
                    const subtotal = selectedProd ? selectedProd.unitPrice * item.quantity : 0;
                    return (
                      <div key={idx} className="flex items-center gap-2 p-2 rounded-lg bg-slate-900/60 border border-slate-800">
                        <select
                          value={item.productId}
                          onChange={(e) => handleItemChange(idx, 'productId', Number(e.target.value))}
                          className="flex-1 px-2.5 py-1.5 rounded bg-slate-900 border border-slate-700 text-white text-xs"
                        >
                          {products.map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.sku} — {p.name} (${Number(p.unitPrice).toLocaleString('es-CL')})
                            </option>
                          ))}
                        </select>

                        <div className="w-24">
                          <input
                            type="number"
                            min="1"
                            value={item.quantity}
                            onChange={(e) => handleItemChange(idx, 'quantity', parseInt(e.target.value, 10) || 1)}
                            className="w-full px-2 py-1.5 rounded bg-slate-900 border border-slate-700 text-white text-xs font-mono text-center"
                          />
                        </div>

                        <div className="w-28 text-right font-mono font-bold text-emerald-400 text-xs">
                          ${subtotal.toLocaleString('es-CL')}
                        </div>

                        {orderItems.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemoveItemRow(idx)}
                            className="text-slate-500 hover:text-rose-400 p-1"
                          >
                            ✕
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Total preview */}
              <div className="p-3 rounded-lg bg-slate-900 border border-slate-800 flex items-center justify-between">
                <span className="text-slate-400 font-semibold">Total Estimado de la Orden:</span>
                <span className="text-lg font-bold font-mono text-emerald-400">
                  ${calculateEstimatedTotal().toLocaleString('es-CL')} CLP
                </span>
              </div>

              <div className="pt-3 border-t border-slate-800 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 rounded-lg bg-slate-900 border border-slate-700 text-slate-300 hover:text-white transition"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold transition disabled:opacity-50"
                >
                  {isSubmitting ? 'Creando Orden...' : 'Generar Orden en DRAFT'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
