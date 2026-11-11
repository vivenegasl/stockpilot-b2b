import React, { useEffect, useState } from 'react';
import { Package, Plus, Search, Tag, CheckCircle2, AlertCircle } from 'lucide-react';
import { catalogApi } from '../../api/catalogApi';
import type { Category, Product, ProductCreateRequest } from '../../types/catalog';

export const CatalogView: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | undefined>();
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);

  // Modal state
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [sku, setSku] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [unitPrice, setUnitPrice] = useState<string>('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [modalError, setModalError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const loadCategories = async () => {
    try {
      const data = await catalogApi.getCategories();
      setCategories(data);
    } catch (err) {
      console.error('Error fetching categories:', err);
    }
  };

  const loadProducts = async () => {
    try {
      setLoading(true);
      const data = await catalogApi.getProducts(page, 12, searchTerm || undefined, selectedCategoryId);
      setProducts(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Error fetching products:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    loadProducts();
  }, [page, searchTerm, selectedCategoryId]);

  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);

    if (!sku.trim() || !name.trim() || !unitPrice || !categoryId) {
      setModalError('Todos los campos obligatorios deben ser completados.');
      return;
    }

    const priceNum = parseFloat(unitPrice);
    if (isNaN(priceNum) || priceNum <= 0) {
      setModalError('El precio unitario debe ser un número positivo.');
      return;
    }

    try {
      setIsSubmitting(true);
      const payload: ProductCreateRequest = {
        sku: sku.trim().toUpperCase(),
        name: name.trim(),
        description: description.trim(),
        unitPrice: priceNum,
        categoryId: Number(categoryId),
      };

      await catalogApi.createProduct(payload);
      setIsModalOpen(false);
      // Reset form
      setSku('');
      setName('');
      setDescription('');
      setUnitPrice('');
      setCategoryId('');
      loadProducts();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : 'Error al registrar el producto');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <Package className="w-6 h-6 text-amber-400" />
            Catálogo de Productos Industriales (SKUs)
          </h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Gestión de componentes normalizados, precios unitarios fijos y control de versiones optimista.
          </p>
        </div>
        <button
          onClick={() => setIsModalOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition shadow-md shadow-amber-500/20"
        >
          <Plus className="w-4 h-4" />
          <span>Nuevo SKU Industrial</span>
        </button>
      </div>

      {/* Filters bar */}
      <div className="glass-panel p-4 rounded-xl flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="relative w-full md:w-96">
          <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Buscar por código SKU o nombre..."
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setPage(0);
            }}
            className="w-full pl-9 pr-4 py-2 rounded-lg bg-slate-900/80 border border-slate-700/80 focus:border-amber-400 text-xs text-white placeholder-slate-500 focus:outline-none transition"
          />
        </div>

        <div className="flex items-center gap-3 w-full md:w-auto">
          <Tag className="w-4 h-4 text-slate-500 hidden sm:inline" />
          <select
            value={selectedCategoryId || ''}
            onChange={(e) => {
              setSelectedCategoryId(e.target.value ? Number(e.target.value) : undefined);
              setPage(0);
            }}
            className="px-3 py-2 rounded-lg bg-slate-900/80 border border-slate-700/80 text-xs text-slate-300 focus:outline-none focus:border-amber-400 w-full sm:w-auto transition"
          >
            <option value="">Todas las Categorías</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
          <span className="text-xs text-slate-400 whitespace-nowrap font-mono">
            {totalElements} productos
          </span>
        </div>
      </div>

      {/* Products Table */}
      <div className="glass-panel rounded-xl overflow-hidden">
        {loading ? (
          <div className="text-center py-16 text-slate-400 text-xs font-mono animate-pulse">
            Consultando catálogo en PostgreSQL...
          </div>
        ) : products.length === 0 ? (
          <div className="text-center py-16 text-slate-500 text-xs">
            No se encontraron productos coincidentes con los criterios de búsqueda.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="text-[11px] uppercase tracking-wider text-slate-400 bg-slate-900/40 border-b border-slate-800">
                <tr>
                  <th className="py-3 px-4">Código SKU</th>
                  <th className="py-3 px-4">Nombre Comercial</th>
                  <th className="py-3 px-4">Categoría</th>
                  <th className="py-3 px-4 text-right">Precio Unitario</th>
                  <th className="py-3 px-4 text-center">Versión (@Version)</th>
                  <th className="py-3 px-4 text-center">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 font-sans">
                {products.map((product) => (
                  <tr key={product.id} className="hover:bg-slate-900/40 transition">
                    <td className="py-3 px-4 font-mono font-bold text-amber-300">
                      {product.sku}
                    </td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-white">{product.name}</div>
                      <div className="text-[11px] text-slate-500 truncate max-w-xs">{product.description}</div>
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700">
                        {product.category?.name || 'General'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-emerald-400">
                      ${Number(product.unitPrice).toLocaleString('es-CL')}
                    </td>
                    <td className="py-3 px-4 text-center font-mono text-[11px] text-slate-400">
                      v{product.version}
                    </td>
                    <td className="py-3 px-4 text-center">
                      {product.active ? (
                        <span className="inline-flex items-center gap-1 text-[11px] text-emerald-400 font-medium">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          Activo
                        </span>
                      ) : (
                        <span className="text-slate-500 text-[11px]">Inactivo</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination controls */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs">
            <span className="text-slate-400">
              Página <span className="font-mono text-white">{page + 1}</span> de{' '}
              <span className="font-mono text-white">{totalPages}</span>
            </span>
            <div className="flex gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded bg-slate-900 border border-slate-700 text-slate-300 disabled:opacity-40 disabled:cursor-not-allowed hover:border-slate-500 transition"
              >
                Anterior
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1.5 rounded bg-slate-900 border border-slate-700 text-slate-300 disabled:opacity-40 disabled:cursor-not-allowed hover:border-slate-500 transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Modal: Create Product */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="glass-panel w-full max-w-md p-6 rounded-2xl border border-slate-700 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Plus className="w-4 h-4 text-amber-400" />
                Registrar Nuevo SKU Industrial
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

            <form onSubmit={handleCreateProduct} className="space-y-3 text-xs">
              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  Código SKU (Único, ej. PLC-SIEM-S71500) *
                </label>
                <input
                  type="text"
                  required
                  placeholder="PLC-SIEM-S71500"
                  value={sku}
                  onChange={(e) => setSku(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white font-mono uppercase focus:border-amber-400 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  Nombre Comercial *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Siemens S7-1500 CPU 1516-3"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-amber-400 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">
                  Descripción Técnica
                </label>
                <textarea
                  rows={2}
                  placeholder="Especificaciones de interfaz, voltaje y protocolos..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-amber-400 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">
                    Precio Unitario ($ CLP) *
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    required
                    placeholder="1250000"
                    value={unitPrice}
                    onChange={(e) => setUnitPrice(e.target.value)}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white font-mono focus:border-amber-400 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-slate-400 font-semibold mb-1">
                    Categoría *
                  </label>
                  <select
                    required
                    value={categoryId}
                    onChange={(e) => setCategoryId(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white focus:border-amber-400 focus:outline-none"
                  >
                    <option value="">Selecciona...</option>
                    {categories.map((cat) => (
                      <option key={cat.id} value={cat.id}>
                        {cat.name}
                      </option>
                    ))}
                  </select>
                </div>
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
                  className="px-4 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold transition disabled:opacity-50"
                >
                  {isSubmitting ? 'Registrando...' : 'Guardar SKU'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
