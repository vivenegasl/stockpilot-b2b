# StockPilot B2B — Frontend SPA (React 19 + Vite + TypeScript)

> **Módulo de Frontend de StockPilot**: Consola operacional y centro de mando logístico B2B para la gestión de inventario multi-bodega, tramitación de órdenes con bloqueo pesimista y observabilidad en tiempo real.

---

## 🎨 Filosofía de Diseño: Dark Luxury & Glassmorphism Industrial

Diseñado para operadores de bodegas, gestores de catálogo y analistas de operaciones que pasan jornadas completas frente a consolas de control:
* **Paleta de Base**: Fondo ultra-oscuro `#020617` (`slate-950`) que reduce la fatiga visual.
* **Acentos Metálicos**: Ámbar (`amber-400` / `amber-500`) para transacciones y estados en vuelo, Esmeralda (`emerald-400`) para consistencia ACID confirmada y Carmín (`rose-400`) para anulaciones y rechazos de stock.
* **Paneles de Cristal Esmerilado**: Clases `.glass-panel` y `.glass-card` con `backdrop-filter: blur(12px)` para delimitar jerarquías sin saturar con líneas duras.

---

## 🧭 Las 5 Vistas Operacionales

1. **Dashboard (KPIs & Concurrencia)**:
   - Tarjetas de estado del sistema (SKUs activos, Bodegas en línea, Órdenes procesadas).
   - Gráfico de área interactivo con **Recharts** que contrasta el throughput de órdenes confirmadas frente a peticiones bloqueadas por sobreventa (409 Conflict).
   - Acceso directo a órdenes recientes y estado de salud (`/actuator/health`).

2. **Catálogo Industrial (SKUs & Fichas Técnicas)**:
   - Tabla paginada de productos con búsqueda en tiempo real por SKU/nombre y filtrado por categoría.
   - Modal para dar de alta nuevos componentes industriales con validación de precios y categorías.

3. **Bodegas & Ledger (Disponibilidad Física & Auditoría SOX)**:
   - Selector multi-bodega regional (`WH-SCL-CENTRAL`, `WH-VAP-PORT`, `WH-ANF-MINER`).
   - Visualización de la invariante física calculada en PostgreSQL 16: `available_qty STORED`.
   - Modal de ajuste de inventario manual (Inbound / Cycle Count) con delta positivo/negativo y motivo formal.
   - Tabla en vivo del **Libro Mayor Inmutable (`inventory_ledger`)**, con historial inalterable para cumplimiento SOX / ISO 27001.

4. **Mesa de Órdenes (Idempotencia & Locks)**:
   - Listado paginado de órdenes de compra con badges cromáticos de estado (`DRAFT`, `CONFIRMED`, `CANCELLED`, `FULFILLED`).
   - Modal interactivo de creación de pedidos con cálculo dinámico de subtotales por línea.
   - Panel inspector de detalles del pedido con acciones de transición: **Confirmar (Lock Pesimista)**, **Cancelar** y **Despachar**.
   - **Auditoría Notarial de Idempotencia**: Detección transparente de la cabecera `X-Idempotent-Replay: true` con banner dorado explicativo y visualización de la llave UUID v4.

5. **Métricas Prometheus (Actuator & Concurrencia)**:
   - Scraping en tiempo real del endpoint abierto `/actuator/prometheus`.
   - Gráfico de barras de distribución del flujo de órdenes (Creadas, Confirmadas, Despachadas, Canceladas).
   - Gráfico circular de protección de sobreventa (Locks adquiridos vs Rechazos 409).
   - Barra de saturación en tiempo real del pool de conexiones **HikariCP** de PostgreSQL.
   - Ocupación de memoria Heap de la **JVM** (OpenJDK 21).

---

## 🔒 Garantía Arquitectónica: Inyección Notarial de Idempotencia (`client.ts`)

En [`src/api/client.ts`](src/api/client.ts), el cliente HTTP centralizado intercepta toda llamada mutativa:
```typescript
if (['POST', 'PUT', 'PATCH'].includes(options.method?.toUpperCase() || '')) {
  if (!headers.has('Idempotency-Key')) {
    headers.set('Idempotency-Key', crypto.randomUUID());
  }
}
```
**Impacto en Seguridad Transaccional**: Ningún desarrollador puede enviar un pedido o confirmar una reserva sin una clave `Idempotency-Key` (UUID v4), erradicando las dobles compras accidentales en redes lentas.

---

## 🛠️ Scripts Disponibles

En el directorio `frontend/`:

```bash
# Iniciar servidor de desarrollo con Hot Module Replacement (HMR)
npm run dev

# Compilar TypeScript estrictamente y empaquetar para producción con Vite
npm run build

# Previsualizar la compilación de producción en local
npm run preview
```

### Configuración del Proxy de Desarrollo (`vite.config.ts`):
Durante el desarrollo local, Vite reenvía automáticamente las rutas `/api` y `/actuator` a `http://localhost:8080`, facilitando el trabajo local sin fricciones de puertos.
