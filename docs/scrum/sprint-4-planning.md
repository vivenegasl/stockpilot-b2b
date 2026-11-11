# Sprint 4 Planning — Dashboard React 19, Observabilidad con Prometheus y Métricas de Concurrencia

> **Sprint**: Sprint 4  
> **Fechas**: 2026-11-01 al 2026-11-11  
> **Sprint Goal**: Diseñar y construir el Frontend SPA moderno en React 19 + TypeScript + Vite + Tailwind CSS v4 bajo estética *Dark Luxury / Glassmorphism* (US-010), exponer telemetría y métricas dimensionales de negocio y saturación de concurrencia en Prometheus mediante Micrometer (US-011), configurar seguridad CORS perimetral con exposición de cabeceras notariales (`Idempotency-Key`, `X-Idempotent-Replay`), e integrar la documentación viva del Módulo 11 en la Guía Maestra y el PKM de Obsidian.  
> **Capacidad del Equipo**: 10 Story Points  
> **Velocidad Comprometida**: 8 Story Points  
> **Scrum Master / Lead Architect**: Víctor Venegas  

---

## 1. Historias de Usuario Comprometidas (Sprint Backlog)

| ID | Historia de Usuario | SP | Prioridad | Módulo | Criterio de Éxito Clave |
|---|---|---|---|---|---|
| **US-010** | Dashboard de Operaciones en React 19 | 5 | 🔴 Must Have | `frontend` | SPA interactiva con 5 módulos operativos (Dashboard KPIs, Catálogo SKUs con modal de alta, Bodegas & Ledger de auditoría SOX con ajuste manual, Mesa de Órdenes con creación/confirmación/cancelación/despacho, y panel de telemetría). Genera `Idempotency-Key` automática (UUID v4) y renderiza badge visual ante `X-Idempotent-Replay`. Estilo Dark Luxury con Tailwind v4. |
| **US-011** | Métricas de Concurrencia en Prometheus | 3 | 🟢 Could Have | `observability` | Instrumentación con Micrometer en `OrderMetrics`: contadores `stockpilot.orders.created`, `confirmed`, `cancelled`, `fulfilled`, `reservations.insufficient_stock` (409) y `idempotency.replays`. Exposición abierta en `/actuator/prometheus`, monitoreo de saturación del pool HikariCP y consumo de memoria JVM Heap con gráficos Recharts en el frontend. |

**Total Story Points**: 8 SP

---

## 2. Definición de Hecho (Definition of Done - DoD) para Sprint 4

Para declarar terminado cualquier incremento de este sprint, debe cumplirse rigurosamente:

1. **Frontend Moderno & Tipado TypeScript Riguroso**:
   - Inicializado con Vite 8 + React 19.
   - `verbatimModuleSyntax: true` y `noUnusedLocals: true` cumplidos al 100% (importaciones `import type`).
   - Bundle minificado y optimizado sin errores de transpilación (`tsc -b && vite build` exitoso).
   - Componentes modulares, desacoplados y reutilizables (`StatCard`, `StatusBadge`, `IdempotencyBadge`, `Navbar`, `Sidebar`).
2. **Seguridad y Perímetro CORS**:
   - `CorsConfig.java` en Spring Boot con máxima prioridad (`Ordered.HIGHEST_PRECEDENCE`).
   - Cabeceras `Idempotency-Key` y `X-Idempotent-Replay` expuestas formalmente en `Access-Control-Expose-Headers`.
   - Proxy de desarrollo en `vite.config.ts` para `/api` y `/actuator`.
3. **Observabilidad y Telemetría ACID**:
   - Contadores Micrometer registrados e inyectados en la lógica de negocio (`OrderServiceImpl`).
   - Parser en frontend para métricas de `/actuator/prometheus`.
   - Gráficos interactivos en Recharts (distribución de estados de órdenes y ratio de locks exitosos vs conflictos 409).
4. **Pruebas Automatizadas y Suite Verde**:
   - Tests unitarios y de integración de backend actualizados y pasando al 100% (`./gradlew test` exitoso en 15s).
5. **Living Documentation & Soberanía Técnica**:
   - Creación del Módulo 11 en `docs/architecture/study-guide/11-slice-4-frontend-observabilidad.md` bajo el **Sistema de las 3 Capas**.
   - Actualización del índice de la Guía Maestra en `docs/architecture/MASTER_STUDY_GUIDE.md`.
   - Actualización del backlog de producto (`product_backlog.md`).
   - Sincronización en notas de Obsidian PKM.
