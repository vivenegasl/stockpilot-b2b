# Sprint 3 Planning — Motor de Pedidos, Idempotencia y Concurrencia de Alta Criticidad

> **Sprint**: Sprint 3  
> **Fechas**: 2026-10-24 al 2026-10-31  
> **Sprint Goal**: Implementar el motor transaccional de órdenes de compra B2B (`PurchaseOrder`), la protección perimetral de idempotencia HTTP (`Idempotency-Key` UUID v4 con persistencia en PostgreSQL), la reserva atómica pesimista con prevención de deadlocks (ordenamiento determinista por `productId ASC`), y demostrar empíricamente mediante un test de estrés con 50 hilos concurrentes la imposibilidad de sobreventa (Zero Overselling).  
> **Capacidad del Equipo**: 25 Story Points  
> **Velocidad Comprometida**: 23 Story Points  
> **Scrum Master / Lead Architect**: Víctor Venegas  

---

## 1. Historias de Usuario Comprometidas (Sprint Backlog)

| ID | Historia de Usuario | SP | Prioridad | Módulo | Criterio de Éxito Clave |
|---|---|---|---|---|---|
| **US-006** | Creación de Orden de Compra en Estado Borrador (DRAFT) | 5 | 🔴 Must Have | `ordering` | Genera orden correlativa `ORD-YYYYMMDD-XXXX` con snapshot de precios y sin comprometer stock. Requiere cabecera `Idempotency-Key`. |
| **US-007** | Confirmación de Pedido con Reserva Atómica Pesimista | 8 | 🔴 Must Have | `ordering` / `inventory` | Transiciona de `DRAFT` a `CONFIRMED`. Adquiere bloqueos `SELECT FOR UPDATE` ordenados por `productId ASC`. Incrementa `reserved_qty`, crea `stock_reservation` y asienta `RESERVATION` en el ledger. |
| **US-008** | Cancelación de Pedido y Liberación Determinista | 5 | 🔴 Must Have | `ordering` / `inventory` | Transiciona a `CANCELLED`. Libera reservas activas (`reserved_qty -= qty`), pasa reservas a `RELEASED` y asienta `RELEASE` en el ledger. |
| **US-009** | Despacho Físico de Pedido (Fulfillment) | 5 | 🟡 Should Have | `ordering` / `inventory` | Transiciona a `FULFILLED`. Descuenta inventario físico total (`total_qty -= qty`, `reserved_qty -= qty`), pasa reservas a `FULFILLED` y asienta `FULFILLMENT` en el ledger. |

**Total Story Points**: 23 SP

---

## 2. Definición de Hecho (Definition of Done - DoD) para Sprint 3

Para declarar terminado cualquier incremento de este sprint, debe cumplirse rigurosamente:

1. **Clean Code & Tipado Fuerte**:
   - Entidades JPA (`PurchaseOrder`, `OrderLine`, `StockReservation`, `IdempotencyRecord`) mapeadas contra esquemas Flyway V5, V6, V7 y V9.
   - Todo DTO de entrada y salida debe ser un `java.lang.Record` inmutable con validaciones `@NotNull`, `@Min(1)`, etc.
   - Cero exposición de entidades JPA en controladores REST.
2. **Control de Concurrencia y Resiliencia**:
   - Bloqueo pesimista `SELECT ... FOR UPDATE` con timeout estricto de 3000 ms (`jakarta.persistence.lock.timeout`).
   - Ordenamiento determinista forzoso por `productId ASC` previo a la adquisición de locks para romper la condición de Coffman (espera circular).
   - Manejo de excepciones con RFC 7807 `ProblemDetail` (404 Not Found, 409 Conflict, 400 Bad Request).
3. **Idempotencia HTTP**:
   - Filtro Servlet `IdempotencyFilter` que intercepta `POST` y `PUT` en `/api/v1/orders/**`.
   - Replay inmediato de respuestas previas (`X-Idempotent-Replay: true`) sin re-ejecutar transacciones ni controladores.
   - Manejo de peticiones concurrentes en vuelo (centinela `status = 0`).
4. **Pruebas Automatizadas y Stress Test**:
   - Pruebas unitarias de servicios con Mockito.
   - Pruebas de integración de controladores con MockMvc (`@WebMvcTest`).
   - **Prueba de Fuego Concurrente**: Test multi-hilo con 50 hilos en paralelo (`CountDownLatch`) sobre 1 sola unidad de stock, certificando exactamente 1 éxito (200 OK), 49 rechazos (409 Conflict), y cero sobreventa.
5. **Living Documentation & Soberanía Técnica**:
   - Actualización de `docs/scrum/product_backlog.md` y revisión del sprint.
   - Actualización obligatoria de `docs/architecture/MASTER_STUDY_GUIDE.md` bajo el **Sistema de las 3 Capas** (Intuición, Mecánica RAM-Disco, Respuesta Solutions Architect).
