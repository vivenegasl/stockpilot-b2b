# Sprint 3 Review & Retrospective — Motor de Pedidos y Reserva Concurrente

> **Sprint**: Sprint 3  
> **Fechas**: 2026-10-24 al 2026-10-31  
> **Sprint Goal**: Implementar el motor transaccional de órdenes de compra B2B (`PurchaseOrder`), la protección perimetral de idempotencia HTTP (`Idempotency-Key` UUID v4 con persistencia en PostgreSQL), la reserva atómica pesimista con prevención de deadlocks (ordenamiento determinista por `productId ASC`), y demostrar empíricamente mediante un test de estrés con 50 hilos concurrentes la imposibilidad de sobreventa (Zero Overselling).  
> **Resultado del Sprint Goal**: 100% CUMPLIDO ✅  
> **Story Points Comprometidos**: 23 SP | **Story Points Completados**: 23 SP | **Velocidad Lograda**: 23 SP  
> **Scrum Master / Lead Architect**: Víctor Venegas  

---

## 1. Demostración de Incremento de Producto (Sprint Review)

### Historias de Usuario Entregadas:

| ID | Historia de Usuario | SP | Estado | Evidencia de Aceptación Técnica |
|---|---|---|---|---|
| **US-006** | Creación de Orden de Compra en Estado Borrador (DRAFT) | 5 | ✅ DONE | `POST /api/v1/orders` genera orden con identificador correlativo `ORD-YYYYMMDD-XXXX`, snapshot de precios en líneas, cálculo automático en PostgreSQL y validación de `Idempotency-Key`. |
| **US-007** | Confirmación de Pedido con Reserva Atómica Pesimista | 8 | ✅ DONE | `PUT /api/v1/orders/{id}/confirm` transiciona a `CONFIRMED`. Adquiere bloqueos `SELECT ... FOR UPDATE` ordenados por `productId ASC`, incrementa `reserved_qty`, crea reserva `ACTIVE` y asienta `RESERVATION` en el ledger. |
| **US-008** | Cancelación de Pedido y Liberación Determinista | 5 | ✅ DONE | `PUT /api/v1/orders/{id}/cancel` transiciona a `CANCELLED`. Libera reservas activas (`reserved_qty -= qty`), pasa reservas a `RELEASED` y asienta `RELEASE` en el ledger. |
| **US-009** | Despacho Físico de Pedido (Fulfillment) | 5 | ✅ DONE | `PUT /api/v1/orders/{id}/fulfill` transiciona a `FULFILLED`. Descuenta físicamente el inventario (`total_qty -= qty`, `reserved_qty -= qty`), pasa reservas a `FULFILLED` y asienta `FULFILLMENT` en el ledger. |

### Métricas de Calidad de Ingeniería:
* **Cobertura de Pruebas**: 50 tests automatizados ejecutados al 100% de éxito (`BUILD SUCCESSFUL`).
* **Prueba de Fuego Concurrente**: 50 hilos disparados simultáneamente mediante `CountDownLatch` compitiendo por 1 sola unidad disponible. Resultado: 1 confirmación exitosa (HTTP 200), 49 rechazos controlados (HTTP 409 Conflict ProblemDetail), cero sobreventa en base de datos.
* **Seguridad de Red e Idempotencia**: `IdempotencyFilter` probado contra duplicidad en vuelo y repetición transparente (*Cache HIT Replay*) con cabecera `X-Idempotent-Replay: true`.

---

## 2. Retrospectiva del Sprint (Sprint Retrospective)

### ¿Qué funcionó excepcionalmente bien? (What went well)
1. **Prevención Matemática de Deadlocks**: El ordenamiento determinista por `productId ASC` previo a la adquisición de locks pesimistas eliminó cualquier posibilidad de interbloqueo circular entre pedidos concurrentes.
2. **Idempotencia In-Flight**: El uso del centinela `status = 0` permitió detectar peticiones simultáneas con la misma llave sin requerir alterar el DDL de la base de datos de producción.
3. **Desacoplamiento Modular Limpio**: La separación entre `ordering` (ciclo del pedido) e `inventory` (gestión física de stock y reservas) mantuvo la regla arquitectónica de dependencias unidireccionales sin ciclos.

### ¿Qué problemas encontramos y cómo los solucionamos? (Challenges & Adaptations)
1. **Contaminación de `@WebMvcTest` por Filtro con Repositorio JPA**:
   * *Problema*: Al anotar `IdempotencyFilter` como un `@Component` genérico, Spring Boot intentaba inyectarlo automáticamente en todos los tests de controlador (`@WebMvcTest`), fallando por falta de `IdempotencyRecordRepository` (ya que los slice tests apagan deliberadamente la capa JPA).
   * *Solución Senior*: Retiramos `@Component` del filtro y lo registramos explícitamente mediante un `FilterRegistrationBean` en una clase de configuración dedicada `IdempotencyConfig.java`. De este modo, los `@WebMvcTest` se mantienen aislados y ultrarrápidos, mientras que el filtro opera en el contexto completo de la aplicación.
2. **Serialización de `ProblemDetail` en Tests Unitarios**:
   * *Problema*: `ObjectMapper` por defecto no soportaba `java.time.Instant`.
   * *Solución*: Registro automático con `findAndRegisterModules()`.

### Acuerdos de Mejora para el Sprint 4:
* Iniciar el diseño y construcción del Frontend SPA en React 19 + TypeScript + Tailwind + Shadcn/ui para dotar de una interfaz visual de primer nivel a este robusto motor transaccional.
