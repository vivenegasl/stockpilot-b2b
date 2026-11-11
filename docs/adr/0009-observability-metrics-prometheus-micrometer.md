# ADR-0009: Observabilidad y Métricas Dimensionales de Negocio con Micrometer, Prometheus y Spring Boot Actuator

* **Estado**: Aceptado
* **Fecha**: 2026-11-04
* **Decisores**: Víctor Venegas (Software Architect & Lead Engineer)
* **Categoría**: Observabilidad / Telemetría / Concurrencia / Operaciones

---

## Contexto y Declaración del Problema

En un sistema transaccional de misión crítica de alta concurrencia como **StockPilot**, donde se aplican bloqueos pesimistas a nivel de base de datos (`SELECT ... FOR UPDATE`) y se garantiza cero sobreventa (*Zero Overselling*), la ausencia de telemetría en tiempo real crea un riesgo operativo grave:
1. **Invisibilidad de la Contención**: Si múltiples clientes compiten por las mismas filas, la aplicación puede estar acumulando hilos en espera en el pool de conexiones (HikariCP) sin que los operadores ni el equipo de ingeniería se percaten hasta que el pool se agota por completo arrojando `ConnectionTimeoutException`.
2. **Descalce de Demanda vs Stock Físico**: Si una orden falla con HTTP 409 Conflict (*Insufficient Stock*), no se trata necesariamente de un error de software, sino de un evento de negocio clave: la demanda comercial está superando la capacidad física de una bodega específica.
3. **Efectividad de la Idempotencia**: Se necesita cuantificar empíricamente cuántas peticiones redundantes o reintentos de red han sido absorbidos con éxito por la capa de idempotencia perimetral sin tocar la base de datos transaccional.

---

## Factores de Decisión (Decision Drivers)

* **Observabilidad de los 4 Golden Signals de Google SRE**: Latencia, Tráfico, Errores y Saturación.
* **Bajo Impacto en el Rendimiento Transaccional**: La recolección de métricas no debe introducir contención de locks en Java ni penalizar el tiempo de respuesta del servicio transaccional (<10 ms).
* **Compatibilidad con Estándares Abiertos de la Industria**: Exposición de métricas en formato compatible con el ecosistema Cloud Native (Prometheus, Grafana, OpenMetrics).
* **Consumo Dual**: Las métricas deben poder ser leídas tanto por un scraper externo estándar de Prometheus como por el propio Dashboard SPA de StockPilot para auditoría interna inmediata.

---

## Opciones Consideradas

1. **Agentes APM Pesados (New Relic, Datadog Agent, Dynatrace)**:
   * *Ventajas*: Trazado automático de extremo a extremo sin instrumentación manual.
   * *Desventajas*: Costos mensuales prohibitivos para un entorno B2B mediano, huella de memoria RAM no trivial en la JVM y dependencia de proveedores propietarios (*Vendor Lock-in*).
2. **Logging Estructurado Puro en Archivo / stdout (Logstash / ELK)**:
   * *Ventajas*: Simple de emitir mediante Logback.
   * *Desventajas*: Calcular tasas de throughput o porcentajes de error en tiempo real requiere un pipeline de agregación pesado (Elasticsearch) que añade latencia y costos de almacenamiento en disco.
3. **Métricas Dimensionales con Micrometer + Spring Boot Actuator + OpenMetrics (Prometheus)**:
   * *Ventajas*: Incluido nativamente en el ecosistema Spring Boot; contadores basados en variables atómicas en memoria RAM (`AtomicLong` y `DoubleAdder`) que no bloquean hilos; formato de texto abierto estandarizado en `/actuator/prometheus`; cero dependencias propietarias externas.

---

## Decisión Adoptada

Se adopta la opción 3: **Instrumentación Dimensional con Micrometer y Exposición Abierta en `/actuator/prometheus`**.

### Componentes Diseñados:
1. **Instrumentación de Negocio en `OrderMetrics.java`**:
   - `stockpilot.orders.created`: Contador acumulativo de órdenes en borrador.
   - `stockpilot.orders.confirmed`: Contador acumulativo de órdenes confirmadas con reserva exitosa.
   - `stockpilot.orders.cancelled`: Contador acumulativo de órdenes canceladas y stock devuelto.
   - `stockpilot.orders.fulfilled`: Contador acumulativo de órdenes despachadas físicamente.
   - `stockpilot.reservations.insufficient_stock`: Contador de rechazos transaccionales (HTTP 409 Conflict) por stock no disponible.
   - `stockpilot.idempotency.replays`: Contador de solicitudes repetidas absorbidas por la caché de idempotencia.
2. **Monitoreo de Saturación de Infraestructura**:
   - `hikaricp.connections.active` vs `hikaricp.connections.total`: Detección temprana de *connection starvation* o fugas de conexiones bajo 50 hilos concurrentes.
   - `jvm.memory.used` (área Heap) vs `jvm.memory.max`: Medición del footprint de memoria y verificación de la estabilidad del Garbage Collector G1.
3. **Visualizador en Tiempo Real en el Frontend**:
   El frontend consulta periódicamente `/actuator/prometheus`, parsea el estándar OpenMetrics y renderiza gráficos de distribución y ratios de concurrencia mediante la librería Recharts.

---

## Consecuencias y Trade-offs

* **Positivas**:
  - Trazabilidad y visibilidad absoluta de la salud del motor de pedidos y concurrencia.
  - Cero sobrecarga de CPU: Micrometer actualiza contadores en memoria mediante primitivas atómicas de CPU en nanosegundos.
  - Integración inmediata con Grafana y Prometheus en entornos cloud (OCI) sin modificar una sola línea de código Java.
* **Negativas / Mitigaciones**:
  - Exponer `/actuator/prometheus` requiere protección perimetral o reglas de red para evitar que actores maliciosos inspeccionen métricas internas del sistema (configurado mediante políticas de acceso de Spring Security y proxies perimetrales).
