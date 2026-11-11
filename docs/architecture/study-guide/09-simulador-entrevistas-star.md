# 🎯 Módulo 9: El Simulador de Entrevista Técnica (Top 8 Preguntas de Fuego STAR)

> **Módulo 9 de la Guía Maestra de Estudio de StockPilot**  
> **Propósito**: Guiones de respuesta de nivel Senior Software Engineer y Solutions Architect para las 8 preguntas más punzantes sobre concurrencia, idempotencia, auditoría inmutable y testing multi-hilo, estructuradas bajo el método STAR (Situación, Tarea, Acción, Resultado).

---

### ❓ Pregunta 1: *"¿Por qué implementaste Bloqueo Pesimista con `SELECT ... FOR UPDATE` en lugar de Bloqueo Optimista con `@Version` para el inventario?"*

* **S (Situación)**: En StockPilot manejamos ventas mayoristas B2B de suministros industriales donde lotes escasos reciben múltiples órdenes simultáneas en ventanas de milisegundos.
* **T (Tarea)**: El requerimiento no negociable era garantizar cero sobreventa y mantener un throughput predecible bajo altísima contención en las mismas filas de la base de datos.
* **A (Acción)**: Evalué el Bloqueo Optimista, pero bajo alta contención (ej. 50 hilos compitiendo por la última unidad), el 98% de los hilos fallan con `OptimisticLockException`. Si configuras reintentos automáticos con retroceso exponencial, desatas un *retry storm* que satura la CPU y agota conexiones de base de datos sin aportar valor. Por ello, opté por **Bloqueo Pesimista (`PESSIMISTIC_WRITE`)** configurando un **timeout explícito de 3000 ms** mediante `@QueryHints`.
* **R (Resultado)**: PostgreSQL serializa las peticiones limpiamente a nivel de kernel. El primer hilo reserva en < 10 ms; el segundo hilo entra, lee inmediatamente que el stock disponible es 0, y rechaza la operación sin reintentos inútiles. El comportamiento bajo estrés es determinista y estable.

---

### ❓ Pregunta 2: *"Si dos órdenes intentan reservar los mismos productos simultáneamente, ¿cómo garantizas que el sistema no termine en un Deadlock?"*

* **S (Situación)**: Una orden de compra B2B generalmente incluye múltiples líneas de productos (ej. un PLC y tres sensores). Si dos órdenes concurrentes incluyen los mismos productos pero en orden inverso, las transacciones pueden entrelazarse y quedar bloqueadas mutuamente esperando los recursos de la otra.
* **T (Tarea)**: Eliminar el riesgo de interbloqueos (*deadlocks*) por diseño arquitectónico, sin depender del costoso mecanismo de detección reactivo de PostgreSQL (`deadlock_timeout`).
* **A (Acción)**: Apliqué la ruptura matemática de la condición de **Espera Circular de Coffman**. En `ReservationService.java`, antes de solicitar cualquier bloqueo en la base de datos, ordenamos determinísticamente la lista de productos por su identificador primario (`items.sort(Comparator.comparing(ProductId))`). De este modo, todas las transacciones del sistema siempre adquieren los bloqueos en el mismo orden global estricto ($P_1 < P_2 < \dots < P_n$).
* **R (Resultado)**: El grafo de asignación de recursos de la base de datos se convierte en un **Grafo Acíclico Dirigido (DAG)**. La posibilidad de un ciclo de espera mutua se reduce a cero a nivel matemático, garantizando contención ordenada en cola FIFO sin cancelaciones abruptas por deadlock.

---

### ❓ Pregunta 3: *"¿Por qué implementaste un Ledger Inmutable para el inventario en lugar de simplemente actualizar una columna de existencias?"*

* **S (Situación)**: Las empresas B2B sufren discrepancias físicas de inventario, mermas no explicadas y disputas comerciales por pedidos extraviados. Una columna mutable `quantity` solo muestra el saldo actual, pero borra todo el historial de cómo se llegó a ese número.
* **T (Tarea)**: Garantizar trazabilidad histórica total, cumplimiento normativo (SOX, ISO 27001) y capacidad forense de reconstrucción del inventario en cualquier segundo del pasado (*Point-in-Time Reconstruction*).
* **A (Acción)**: Diseñé la arquitectura basada en un **Libro Mayor Inmutable (*Inventory Ledger*) con patrón Append-Only**. La tabla `inventory_ledger` almacena cada evento como un asiento contable con su delta ($+o-$), motivo formal (`PURCHASE_RECEIPT`, `RESERVATION`, `ADJUSTMENT`), usuario responsable y marca de tiempo inalterable.
* **R (Resultado)**: La base de datos conserva una pista de auditoría matemática perfecta. La tabla de niveles de stock funciona como una vista materializada de saldos para consultas rápidas, pero la verdad absoluta del negocio reside en el libro mayor inmutable, eliminando fraudes internos y errores de conciliación.

---

### ❓ Pregunta 4: *"¿Qué hace exactamente la anotación `@Immutable` de Hibernate en tu entidad `InventoryLedger` y qué pasa si alguien intenta modificar un registro?"*

* **S (Situación)**: Un libro de auditoría contable debe ser inalterable tanto por fallos lógicos en el código Java como por intentos de modificación manual.
* **T (Tarea)**: Blindar la entidad a nivel de framework ORM para que sea computacionalmente imposible emitir sentencias `UPDATE` sobre el historial.
* **A (Acción)**: Anoté la entidad con `@org.hibernate.annotations.Immutable` y no definí ningún método `setter` en la clase Java. A nivel de Hibernate, esto desactiva por completo el mecanismo de *Dirty Checking* para esta entidad en la sesión de persistencia; Hibernate ni siquiera calcula diferencias de atributos al momento del flush.
* **R (Resultado)**: Si un programador intenta invocar una modificación, Hibernate arroja una excepción en tiempo de ejecución (`HibernateException: Entity is immutable`) y rehúsa generar cualquier sentencia `UPDATE`. Si el negocio exige rectificar un error contable, el negocio exige emitir un **asiento de ajuste compensatorio** de tipo `ADJUSTMENT` con el delta inverso y la justificación requerida, preservando la integridad absoluta de la auditoría.

---

### ❓ Pregunta 5: *"En tus pruebas unitarias de controladores usas `@WebMvcTest`. ¿Por qué tus pruebas no cargan la base de datos completa y qué desafío arquitectónico resolviste al separar las configuraciones?"*

* **S (Situación)**: Al escribir pruebas unitarias para la capa web (`ProductControllerIntegrationTest`), el arranque fallaba con un error de inyección de dependencias en `jpaAuditingHandler`.
* **T (Tarea)**: Lograr pruebas rápidas de la capa HTTP y aislar los contratos de transporte de la capa de persistencia pesada.
* **A (Acción)**: Identifiqué que la anotación `@EnableJpaAuditing` estaba colocada en la clase raíz `@SpringBootApplication`. Como `@WebMvcTest` es un *slice test* que deliberadamente apaga Hibernate para que la prueba corra en 200 ms, el auditor de JPA no encontraba el `JpaMappingContext`. En lugar de meter mocks sucios, apliqué el principio de responsabilidad única extrayendo la configuración a una clase dedicada `JpaConfig.java`.
* **R (Resultado)**: Las pruebas de los controladores ejecutan de forma completamente desacoplada en milisegundos, validando serialización JSON, códigos HTTP y reglas de Bean Validation sin requerir contenedores de base de datos para pruebas puramente sintácticas.

---

### ❓ Pregunta 6: *"Si un cliente B2B sufre un timeout de red y reintenta con la misma Idempotency-Key mientras el servidor aún está procesando la orden original, ¿cómo previenes que se duplique?"*

* **S (Situación)**: En operaciones mutantes críticas como `POST /api/v1/orders`, dos peticiones con la misma clave de idempotencia pueden llegar casi al unísono si el cliente reintenta agresivamente ante lentitud de red.
* **T (Tarea)**: Evitar condiciones de carrera en el propio mecanismo de idempotencia y asegurar que la operación no se ejecute dos veces en paralelo.
* **A (Acción)**: Diseñé el `IdempotencyFilter` con una estrategia de **Detección de Operaciones en Vuelo (In-Flight Sentinel)**. Al recibir una clave nueva, el filtro intenta insertar de inmediato un registro en la tabla `idempotency_record` con `response_status = 0` y `response_body = 'IN_PROGRESS'`. Como la columna `idempotency_key` tiene un índice `UNIQUE` estricto en PostgreSQL, la segunda petición colisiona en el motor de base de datos arrojando una violación de unicidad o lee `status = 0`, provocando que el filtro la rechace inmediatamente con **HTTP 409 Conflict** (*"Operation currently in progress"*). Al terminar el procesamiento del controlador, el registro se actualiza con el status final (201) y el cuerpo JSON.
* **R (Resultado)**: Imposibilidad matemática de ejecución paralela duplicada. Si la primera petición tiene éxito, los futuros reintentos reciben un Replay directo del JSON original; si falló con un error 500 no recuperable, el centinela se elimina permitiendo un reintento limpio.

---

### ❓ Pregunta 7: *"¿Por qué desacoplaste el `IdempotencyFilter` en una clase `IdempotencyConfig` mediante `FilterRegistrationBean` en lugar de simplemente anotarlo con `@Component`?"*

* **S (Situación)**: Al agregar `IdempotencyFilter` como un `@Component` genérico de Spring, todas las pruebas unitarias de controladores con `@WebMvcTest` (`ProductControllerIntegrationTest`, `StockControllerIntegrationTest`) comenzaron a fallar con `NoSuchBeanDefinitionException` para `IdempotencyRecordRepository`.
* **T (Tarea)**: Permitir que el filtro proteja la API en producción sin contaminar el aislamiento de los *slice tests* unitarios de la capa web.
* **A (Acción)**: `@WebMvcTest` escanea automáticamente controladores y cualquier clase `@Component` que implemente la interfaz `Filter`. Como `IdempotencyFilter` requiere inyectar un repositorio JPA (`IdempotencyRecordRepository`) y los slice tests apagan deliberadamente la capa de datos JPA, Spring intentaba instanciar el filtro sin poder resolver el repositorio. Retiré `@Component` de la clase del filtro y creé `IdempotencyConfig.java` anotada con `@Configuration`, registrando el filtro explícitamente mediante un `FilterRegistrationBean`. Como `@WebMvcTest` no carga clases `@Configuration` arbitrarias, las pruebas de controlador volvieron a quedar perfectamente aisladas.
* **R (Resultado)**: 100% de la suite de pruebas unitarias (`@WebMvcTest`) y de integración ejecutando limpiamente, respetando el principio de diseño modular y la separación estricta de contextos de prueba.

---

### ❓ Pregunta 8: *"¿Cómo testeas concurrencia real y demuestras empíricamente la ausencia de condiciones de carrera y sobreventa antes de ir a producción?"*

* **S (Situación)**: En sistemas de gestión de inventario, afirmar en papel que un bloqueo pesimista `SELECT ... FOR UPDATE` funciona no es suficiente para un sistema de misión crítica; bajo cargas concurrentes masivas pueden manifestarse condiciones de carrera sutiles o cuellos de botella no detectados en pruebas unitarias normales con dobles de prueba (mocks).
* **T (Tarea)**: Construir una prueba automatizada en el pipeline de CI/CD que someta al código a una contención extrema destructiva: 50 hilos intentando reservar la misma y única unidad física de stock exactamente en el mismo nanosegundo contra una base de datos PostgreSQL 16 real.
* **A (Acción)**: Diseñé `ReservationConcurrencyIntegrationTest` combinando Testcontainers (PostgreSQL 16 en contenedor Docker real) con las herramientas de concurrencia de bajo nivel de Java de Doug Lea. Utilicé un pool de 50 hilos administrado por `ExecutorService` y coordiné el disparo simultáneo mediante un `CountDownLatch(1)` como cerrojo de largada (*startGun*). Cada hilo entra en estado `WAITING` suspendido en el AQS; al invocar `startGun.countDown()`, los 50 hilos son liberados en el mismo ciclo de reloj contra el pool de HikariCP y la tabla `stock_level`. Para contabilizar los resultados sin introducir contención de software en la JVM, utilicé contadores atómicos libres de bloqueo `AtomicInteger` basados en la instrucción de CPU `LOCK CMPXCHG`.
* **R (Resultado)**: La prueba demuestra con rigor matemático y 100% de reproducibilidad que exactamente 1 hilo adquiere la reserva (`status = ACTIVE`, orden `CONFIRMED`) y los 49 hilos restantes reciben limpiamente `InsufficientStockException`. El estado final verificado en disco es `total_qty = 1`, `reserved_qty = 1`, `available_qty = 0`, demostrando científicamente Cero Sobreventa bajo máxima presión de concurrencia.

---

[⬅️ Módulo 8: Hoja de Ruta de Estudio](08-hoja-de-ruta-syllabus-estudio.md) | [Índice Principal](../MASTER_STUDY_GUIDE.md) | [Siguiente Módulo: Protocolo DoD ➡️](10-protocolo-gobierno-vivo-dod.md)
