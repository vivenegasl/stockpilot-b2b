# ADR-0004: Cálculo de Stock Disponible mediante Columnas Generadas en PostgreSQL

* **Estado**: Aceptado
* **Fecha**: 2026-10-22
* **Decisores**: Víctor Venegas (Software Architect)
* **Categoría**: Base de Datos / Integridad de Datos / Modelo de Persistencia

## Contexto y Declaración del Problema

El stock de un producto en una bodega se compone de:
* `total_qty`: Cantidad física real existente en estanterías.
* `reserved_qty`: Cantidad comprometida en órdenes confirmadas pero aún no despachadas.
* `available_qty`: Cantidad libre para ser reservada por nuevos pedidos.

En muchos sistemas, los desarrolladores mantienen tres columnas separadas y actualizan manualmente las tres en cada operación, o bien calculan la disponibilidad dinámicamente en memoria de la JVM. Esto genera los siguientes riesgos:
1. **Desincronización de Estado (Drift)**: Un bug en un servicio o una consulta SQL manual puede actualizar `reserved_qty` sin actualizar `available_qty`.
2. **Inconsistencia de Concurrencia**: Consultas concurrentes podrían leer combinaciones intermedias inválidas.

Debemos definir dónde y cómo reside la verdad matemática del cálculo de stock disponible.

## Factores de Decisión (Decision Drivers)

* **Single Source of Truth**: El valor disponible debe ser inequívoco y congruente en el 100% de las lecturas.
* **Rendimiento de Consulta (Indexabilidad)**: Debemos poder consultar productos con stock disponible (`WHERE available_qty > 0`) utilizando índices B-Tree de alta velocidad para vistas del catálogo B2B.
* **Integridad Forzosa en Motor DDL**: La base de datos debe impedir la manipulación directa o desajustada de dicha métrica.

## Opciones Consideradas

1. **Cálculo en Memoria en la Capa de Aplicación (Java)**:
   * `StockLevel.java` tiene un método transient `getAvailableQty() { return totalQty - reservedQty; }`.
   * *Problema*: No permite indexar `available_qty` en base de datos para filtrado eficiente en paginaciones de catálogo (`WHERE available_qty > 0`).

2. **Trigger de Base de Datos (`BEFORE INSERT OR UPDATE`)**:
   * Un trigger procedural en PL/pgSQL actualiza `NEW.available_qty := NEW.total_qty - NEW.reserved_qty;`.
   * *Problema*: Sobrecarga innecesaria de triggers procedurales, opacidad en herramientas de auditoría y complejidad de mantenimiento.

3. **Columna Generada Almacenada (`GENERATED ALWAYS AS ... STORED`)**:
   * PostgreSQL 12+ permite definir columnas calculadas materializadas:
     ```sql
     available_qty integer GENERATED ALWAYS AS (total_qty - reserved_qty) STORED
     ```
   * El motor de BD recalcula y almacena físicamente el valor en disco en cada INSERT o UPDATE de `total_qty` o `reserved_qty`.
   * Permite la creación de índices B-Tree estándar: `CREATE INDEX idx_stock_available ON stock_level (warehouse_id, available_qty);`.
   * Impide cualquier intento de hacer `UPDATE stock_level SET available_qty = ...`, arrojando error directo si un desarrollador o script lo intenta.

## Decisión y Justificación

Se elige la **Opción 3: Columna Generada Almacenada en PostgreSQL**.

### Justificación Técnica:
1. **Inmutabilidad de la Fórmula**: Es imposible a nivel de base de datos que `available_qty` sea diferente de `total_qty - reserved_qty`. No existe riesgo de bug de cálculo en el backend.
2. **Lecturas Ultra-Rápidas**: Al ser `STORED`, la lectura no consume CPU en tiempo de ejecución de queries complejas; se lee directamente de la página de disco o buffer pool.
3. **Mapeo Limpio en Hibernate/JPA**:
   En la entidad Java se mapea con `@Column(name = "available_qty", insertable = false, updatable = false)` y `@Generated(GenerationTime.ALWAYS)` (o `@CurrentTimestamp`), dejando en claro a JPA que la columna es sólo lectura gestionada por el motor.

### Evolución Futura:
En fases avanzadas con despacho multibodega y stock virtual agregado, este mecanismo servirá de bloque de construcción atómico por nodo físico, sobre el cual el orquestador logístico consultará disponibilidades distribuidas para consolidar pedidos.

## Consecuencias y Trade-offs

### Consecuencias Positivas:
* Cero probabilidad de inconsistencia entre stock físico, reservado y disponible.
* Índice directo para búsquedas de stock en el catálogo B2B.
* Simplificación drástica de la lógica de negocio en Java: sólo se mutan `total_qty` o `reserved_qty`.

### Consecuencias Negativas y Mitigaciones:
* **Rigidez DDL**: Modificar la expresión de una columna generada requiere recrear la columna o tabla.  
  * *Mitigación*: La fórmula matemática del inventario físico disponible ($T - R = A$) es universal y constante en la gestión de almacenes.
