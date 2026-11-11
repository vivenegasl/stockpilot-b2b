# Sprint 4 Review & Retrospective — Dashboard React 19 y Observabilidad Prometheus

> **Sprint**: Sprint 4  
> **Fechas**: 2026-11-01 al 2026-11-11  
> **Sprint Goal**: Diseñar y construir el Frontend SPA moderno en React 19 + TypeScript + Vite + Tailwind CSS v4 bajo estética *Dark Luxury / Glassmorphism* (US-010), exponer telemetría y métricas dimensionales de negocio y saturación de concurrencia en Prometheus mediante Micrometer (US-011), configurar seguridad CORS perimetral con exposición de cabeceras notariales (`Idempotency-Key`, `X-Idempotent-Replay`), e integrar la documentación viva del Módulo 11 en la Guía Maestra y el PKM de Obsidian.  
> **Resultado del Sprint Goal**: 100% CUMPLIDO ✅  
> **Story Points Comprometidos**: 8 SP | **Story Points Completados**: 8 SP | **Velocidad Lograda**: 8 SP  
> **Scrum Master / Lead Architect**: Víctor Venegas (SMPC, SPOPC, SDPC, SFPC)  

---

## 1. Demostración de Incremento de Producto (Sprint Review)

### Historias de Usuario Entregadas:

| ID | Historia de Usuario | SP | Estado | Evidencia de Aceptación Técnica |
|---|---|---|---|---|
| **US-010** | Dashboard de Operaciones en React 19 | 5 | ✅ DONE | SPA modular con 5 vistas operativas: Dashboard con KPIs y gráficos Recharts, Catálogo con modal de alta de SKUs y validación de precios, Bodegas & Ledger inmutable SOX con ajuste manual de existencias, Mesa de Órdenes con transiciones DRAFT/CONFIRMED/CANCELLED/FULFILLED y banner dorado `X-Idempotent-Replay: true`, y panel de telemetría Prometheus. Cliente HTTP centralizado (`client.ts`) que inyecta automáticamente `Idempotency-Key` (UUID v4) en peticiones mutativas. Estética Dark Luxury con Tailwind CSS v4. |
| **US-011** | Métricas de Concurrencia en Prometheus | 3 | ✅ DONE | Instrumentación con Micrometer en `OrderMetrics`: contadores `stockpilot.orders.created`, `confirmed`, `cancelled`, `fulfilled`, `reservations.insufficient_stock` (409) y `idempotency.replays`. Exposición en formato OpenMetrics en `/actuator/prometheus`. Visualizador en frontend con Recharts (distribución de estados y gráfico circular de protección de sobreventa), junto con indicadores de saturación del pool HikariCP y memoria JVM Heap. |

### Métricas de Calidad de Ingeniería:
* **Compilación Frontend**: `tsc -b && vite build` ejecutado en 1.26 segundos con **0 errores de TypeScript** bajo `verbatimModuleSyntax: true` y `noUnusedLocals: true`.
* **Pruebas de Backend**: 100% de la suite de pruebas unitarias, de integración y de estrés con 50 hilos concurrentes pasando en verde (`./gradlew.bat check` exitoso en 10s).
* **Seguridad Perimetral CORS**: `CorsConfig.java` implementado con máxima precedencia (`Ordered.HIGHEST_PRECEDENCE`), exponiendo formalmente `Access-Control-Expose-Headers: Idempotency-Key, X-Idempotent-Replay, Location`.
* **Desacoplamiento Arquitectónico**: Monolito modular backend completamente intacto, comunicándose exclusivamente vía REST/JSON y OpenMetrics con el frontend estático.

---

## 2. Retrospectiva del Sprint (Sprint Retrospective)

### 🟢 ¿Qué funcionó excepcionalmente bien? (What went well)
1. **Inyección Centralizada de Idempotencia en el Cliente Web**: Centralizar la generación de UUIDs v4 en `client.ts` garantizó que ningún componente de React pueda olvidar la cabecera `Idempotency-Key`, eliminando errores de omisión por diseño.
2. **Estética Dark Luxury y Ergonomía B2B**: La combinación de paleta ultra-oscura (`#020617`), bordes esmerilados (`glass-panel`) y acentos ámbar redujo drásticamente el ruido visual, ofreciendo una experiencia industrial óptima para operadores de centros de distribución.
3. **Métricas Abiertas con Micrometer**: La elección de contadores dimensionales en Micrometer permitió que tanto el frontend interno (vía parser regex) como herramientas externas de la industria (Prometheus/Grafana) consuman la misma telemetría sin acoplamiento de código.

### 🔴 Desafíos Encontrados y Soluciones Técnicas (Challenges & Adaptations)
1. **Ocultamiento de Cabeceras Custom en el Navegador por CORS**:
   * *Problema*: Al realizar peticiones `fetch` desde `localhost:5173` hacia `localhost:8080`, el backend respondía con `X-Idempotent-Replay: true`, pero en JavaScript `response.headers.get('X-Idempotent-Replay')` retornaba `null`.
   * *Causa Raíz*: La especificación W3C CORS estipula que los navegadores ocultan todas las cabeceras personalizadas de respuesta salvo que el servidor las autorice explícitamente.
   * *Solución Senior*: Configuramos `configuration.setExposedHeaders(List.of("Idempotency-Key", "X-Idempotent-Replay", "Location"))` en `CorsConfig.java`, permitiendo la lectura inmediata en el frontend sin vulnerar la Same-Origin Policy.
2. **TypeScript 5.x y `verbatimModuleSyntax` en Vite**:
   * *Problema*: Vite inicializó el proyecto con `verbatimModuleSyntax: true`. Al importar tipos e interfaces como `import { Order } from '../types/order'`, el compilador arrojaba `error TS1484: 'Order' is a type and must be imported using a type-only import`.
   * *Solución Senior*: Refactorizamos todas las importaciones de contratos para usar la sintaxis canónica `import type { ... }`, permitiendo que el bundler elimine al 100% los metadatos de tipos y genere un bundle JS liviano (<730 kB).

### 🎯 Acuerdos y Siguiente Paso (Sprint 5):
* **Sprint 5 Goal**: Preparar el empaquetado de producción con Docker Compose multi-contenedor (Frontend Nginx + Backend Spring Boot + PostgreSQL 16), manifiestos Helm/Kubernetes y despliegue cloud en Oracle Cloud Infrastructure (OCI Always Free) bajo el ADR-0005.
