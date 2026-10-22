# ADR-0002: Mitigación de Sobreventa mediante Bloqueo Pesimista con Timeout

* **Estado**: Aceptado
* **Fecha**: 2026-10-22
* **Decisores**: Víctor Venegas (Software Architect)
* **Categoría**: Concurrencia / Persistencia de Datos / Integridad de Inventario

## Contexto y Declaración del Problema

En **StockPilot**, múltiples clientes B2B (o procesos desatendidos de reabastecimiento) compiten con frecuencia por las últimas unidades disponibles de un SKU industrial de alta rotación (ej. última unidad de un PLC industrial). 

Bajo concurrencia masiva (múltiples hilos o transacciones simultáneas), un diseño ingenuo de tipo "leer cantidad, verificar si hay stock en memoria, y luego actualizar" sufre de **Condición de Carrera (Race Condition / Lost Update)**, lo que resulta en **sobreventa (overselling)**: un saldo negativo en bodega que viola las leyes fundamentales del negocio.

Debemos definir el mecanismo de control de concurrencia a nivel de base de datos para garantizar la imposibilidad matemática de sobreventa sin generar deadlocks ni bloqueos indefinidos de hilos de ejecución.

## Factores de Decisión (Decision Drivers)

* **Tolerancia Cero a la Sobreventa**: Ninguna orden puede confirmarse si no hay stock físico disponible (`available_qty >= requested_qty`).
* **Resiliencia bajo Alta Contención (Hot Rows)**: El mecanismo debe comportarse de forma predecible cuando decenas de peticiones compiten por exactamente la misma fila en la tabla `stock_level`.
* **Prevención de Deadlocks (Interbloqueos)**: Si una orden contiene múltiples SKUs, el orden de adquisición de locks no debe cruzarse con el de otras órdenes simultáneas.
* **Prevención de Saturación de Pool de Conexiones**: Ningún hilo debe quedar bloqueado indefinidamente esperando un lock si la base de datos se satura.

## Opciones Consideradas

1. **Bloqueo Optimista (`@Version` en JPA)**:
   * Las transacciones leen el registro sin bloquearlo. Al hacer el `UPDATE`, verifican que la versión no haya cambiado (`UPDATE ... WHERE version = ?`). Si cambió, lanzan `OptimisticLockException`.
   * *Problema*: Bajo alta contención (50 hilos por 1 unidad), 49 hilos fallan consecutivamente. Si se implementan reintentos automáticos (exponential backoff), se genera un **torbellino de reintentos (retry storm / livelock)** que satura la CPU y la base de datos sin aportar trabajo útil.

2. **Bloqueo Pesimista sin Timeout (`SELECT ... FOR UPDATE`)**:
   * Adquiere un lock exclusivo a nivel de fila (`FOR UPDATE`) en PostgreSQL. Los demás hilos esperan su turno en una cola FIFO.
   * *Problema*: Si una transacción sufre lentitud o una anomalía de red, los hilos encolados se acumulan indefinidamente, agotando el pool HikariCP y provocando una caída por denegación de servicio (DoS interno).

3. **Bloqueo Pesimista con Timeout Explícito y Ordenamiento Determinista**:
   * Se ejecuta `SELECT ... FOR UPDATE` sobre la fila `stock_level`.
   * Se configura un **timeout estricto de 3000 ms** mediante la sugerencia JPA `jakarta.persistence.lock.timeout` (`SET LOCAL lock_timeout = '3000ms'`).
   * Se establece un **ordenamiento determinista** forzoso al bloquear múltiples líneas: `ORDER BY product_id ASC, warehouse_id ASC`.
   * Si el stock es insuficiente, se hace rollback y se emite `409 Conflict` (RFC 7807 `ProblemDetail`).
   * Si expira el timeout de 3s, se aborta la transacción con `PessimisticLockingFailureException` y se retorna `503 Service Unavailable / 408 Request Timeout`.

## Decisión y Justificación

Se elige la **Opción 3: Bloqueo Pesimista con Timeout Explícito y Ordenamiento Determinista**.

### Implementación en Spring Data JPA:
```java
public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT sl FROM StockLevel sl WHERE sl.product.id = :productId AND sl.warehouse.id = :warehouseId")
    Optional<StockLevel> findByProductAndWarehouseForUpdate(
        @Param("productId") Long productId,
        @Param("warehouseId") Long warehouseId
    );
}
```

### Reglas Clave:
1. **Serialización Ordenada en Hot Rows**: PostgreSQL encola de forma nativa los hilos en el kernel del motor de base de datos. El primer hilo reserva, descuenta y hace `COMMIT`. El siguiente hilo entra inmediatamente, lee el saldo ya descontado (`available_qty = 0`), detecta que no hay suficiente y lanza `InsufficientStockException` de inmediato, sin saturar con reintentos fallidos.
2. **Defensa contra Deadlocks**: Si una orden requiere el SKU A y el SKU B, los locks siempre se adquieren en orden ascendente por `productId`:
   $$\text{Lock}(A) \rightarrow \text{Lock}(B)$$
   Ninguna otra transacción podrá jamás adquirir $\text{Lock}(B) \rightarrow \text{Lock}(A)$, eliminando matemáticamente el ciclo de espera circular (condición de Coffman).
3. **Safety Net en Base de Datos**: Como salvaguarda de última línea, la tabla `stock_level` posee un constraint DDL de nivel de base de datos:
   ```sql
   CONSTRAINT chk_stock_non_negative CHECK (total_qty >= 0 AND reserved_qty >= 0 AND reserved_qty <= total_qty)
   ```

## Consecuencias y Trade-offs

### Consecuencias Positivas:
* Exactitud matemática absoluta: Cero posibilidad de sobreventa.
* Comportamiento predecible bajo contención: No hay retry storms ni gasto inútil de CPU.
* Resiliencia: El timeout de 3000 ms impide que transacciones colgadas congelen el pool HikariCP.

### Consecuencias Negativas y Mitigaciones:
* **Throughput Acotado en la Misma Fila**: Las reservas del mismo SKU exacto se procesan secuencialmente.  
  * *Mitigación*: La transacción crítica sólo ejecuta lectura, validación, actualización y escritura en ledger (tiempo < 10 ms). Con 10 ms por transacción, una sola fila puede procesar hasta 100 reservas por segundo, volumen sobradamente amplio para B2B mayorista.
