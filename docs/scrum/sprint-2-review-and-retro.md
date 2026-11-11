# Sprint 2 Review & Retrospective — Inventario Multi-Bodega y Ledger Inmutable

> **Sprint**: Sprint 2  
> **Fechas**: 2026-10-23 al 2026-10-28  
> **Sprint Goal**: Implementar la infraestructura transaccional de inventario multi-bodega con soporte de bloqueo pesimista y auditoría inmutable en el libro mayor (Append-Only Ledger), permitiendo registrar entradas de existencias y consultar disponibilidad física en tiempo real sin riesgo de condiciones de carrera.  
> **Resultado del Sprint Goal**: 100% CUMPLIDO ✅  
> **Story Points Comprometidos**: 8 SP | **Story Points Completados**: 8 SP | **Velocidad Lograda**: 8 SP  
> **Scrum Master / Lead Architect**: Víctor Venegas (SMPC, SPOPC, SDPC, SFPC)  

---

## 1. Demostración de Incremento de Producto (Sprint Review)

### Historias de Usuario Entregadas:

| ID | Historia de Usuario | SP | Estado | Evidencia de Aceptación Técnica |
|---|---|---|---|---|
| **US-003** | Inicialización y Ajuste de Stock en Bodega Física (Inbound / Cycle Count) | 3 | ✅ DONE | `POST /api/v1/stock/adjust` permite ajustes positivos (`delta > 0`) y negativos (`delta < 0`). Adquiere bloqueo pesimista `SELECT ... FOR UPDATE` sobre `stock_level`, valida que el stock disponible resultante no sea negativo, persiste el nuevo total y asienta un registro de auditoría inmutable en `inventory_ledger` en una única transacción ACID. |
| **US-004** | Consulta de Disponibilidad de Inventario por Bodega | 2 | ✅ DONE | `GET /api/v1/stock/warehouse/{warehouseId}` y `GET /api/v1/stock/product/{productId}` retornan niveles de existencias desglosados en `total_qty`, `reserved_qty` y `available_qty`. Utiliza la columna generada en base de datos `available_qty GENERATED ALWAYS AS (total_qty - reserved_qty) STORED`. |
| **US-005** | Trazabilidad Completa mediante Ledger de Inventario Inmutable | 3 | ✅ DONE | `GET /api/v1/stock/ledger` y endpoints asociados devuelven el historial contable completo. La entidad `InventoryLedger` está protegida con `@org.hibernate.annotations.Immutable`, deshabilitando el dirty checking y prohibiendo sentencias `UPDATE` o `DELETE` para cumplimiento estricto con normativas SOX e ISO 27001. |

### Métricas de Calidad de Ingeniería:
* **Pruebas Automatizadas**: 35 tests automatizados ejecutados al 100% de éxito (`BUILD SUCCESSFUL`).
* **Integridad de Base de Datos**: Migraciones Flyway V2, V3 y V4 aplicadas exitosamente en PostgreSQL 16.
* **Garantía Invariante**: Columna `available_qty STORED` en base de datos impide desincronizaciones entre memoria y disco.

---

## 2. Retrospectiva del Sprint (Sprint Retrospective)

### 🟢 ¿Qué funcionó excepcionalmente bien? (What went well)
1. **Delegación de la Invariante `available_qty` a PostgreSQL**: Implementar la columna como `GENERATED ALWAYS AS (total_qty - reserved_qty) STORED` eliminó de raíz la posibilidad de que un error en el código Java altere el cálculo de disponibilidad disponible.
2. **Inmutabilidad ORM con `@Immutable`**: Configurar la entidad `InventoryLedger` como inmutable en Hibernate protegió la integridad contable ante modificaciones accidentales en código, desactivando el dirty checking y optimizando el consumo de CPU.
3. **Validación Preventiva de Stocks Negativos**: La combinación de restricciones `CHECK (available_qty >= 0)` a nivel de base de datos y validaciones de servicio garantizó que ninguna transacción pueda dejar existencias negativas.

### 🔴 Desafíos Encontrados y Soluciones Técnicas (Challenges & Adaptations)
1. **Mapeo de Columnas Calculadas en Hibernate 6**:
   * *Problema*: Hibernate intentaba incluir `available_qty` en las sentencias `INSERT` generadas automáticamente, lo que causaba error en PostgreSQL (`cannot insert into column "available_qty"`).
   * *Solución Senior*: Mapeamos el campo con `@Column(name = "available_qty", insertable = false, updatable = false)` y `@Generated(GenerationTime.ALWAYS)`, instruyendo a Hibernate a omitir la columna al escribir y refrescar su valor desde la base de datos tras el flush.

### 🎯 Acuerdos para el Sprint 3:
* Abordar el motor de pedidos de compra (`PurchaseOrder`), integrando reservas atómicas, prevención algorítmica de deadlocks y filtro de idempotencia HTTP perimetral.
