# ADR-0008: Arquitectura del Frontend SPA en React 19 con Vite, TypeScript y Dark Luxury / Glassmorphism UI

* **Estado**: Aceptado
* **Fecha**: 2026-11-04
* **Decisores**: Víctor Venegas (Software Architect & Lead Engineer)
* **Categoría**: Frontend / Arquitectura Web / UI/UX Industrial

---

## Contexto y Declaración del Problema

**StockPilot** requiere una interfaz de usuario operativa orientada a operadores de bodegas, gestores de catálogo B2B y directores de logística. A diferencia de las tiendas e-commerce B2C masivas, una consola de operaciones WMS (Warehouse Management System) se caracteriza por:
1. **Uso prolongado y continuo**: Los operadores trabajan en turnos de 8 a 12 horas frente a pantallas de control.
2. **Alta densidad informativa y transaccional**: Visualización simultánea de SKUs, existencias físicas, reservas activas, trazabilidad contable de auditoría y estados de pedidos.
3. **Control estricto de idempotencia**: Los operadores no deben bajo ninguna circunstancia disparar reservas duplicadas por dobles clics accidentales o latencias en la red local.

---

## Factores de Decisión (Decision Drivers)

* **Performance y Cero Costo de Cómputo de Servidor**: La interfaz debe compilar a artefactos estáticos inmutables (HTML/JS/CSS) que puedan servirse desde un bucket de almacenamiento o un proxy Nginx ligero sin requerir un runtime Node.js del lado del servidor.
* **Seguridad Transaccional por Diseño**: Todo envío mutativo (`POST`, `PUT`, `PATCH`) debe incluir de manera determinista una cabecera `Idempotency-Key` en formato UUID v4 sin depender de que cada componente individual lo implemente manualmente.
* **Ergonomía Visual y Reducción de Fatiga**: El diseño debe minimizar el deslumbramiento y la fatiga ocular en ambientes con iluminación industrial o turnos nocturnos, ofreciendo jerarquía tipográfica rigurosa para evitar errores operativos.
* **Tipado Estricto de Contratos**: Máxima coherencia de tipos con la API REST de Spring Boot (`java.lang.Record`), minimizando errores en tiempo de ejecución.

---

## Opciones Consideradas

1. **Next.js con Server-Side Rendering (SSR) y App Router**:
   * *Ventajas*: Renderizado del lado del servidor, optimización SEO.
   * *Desventajas*: Requiere mantener instancias Node.js corriendo continuamente en el servidor de producción (consumiendo RAM y CPU del tier gratuito de OCI), complejidad de caché y SSR innecesario para una consola privada B2B cerrada detrás de un login.
2. **Thymeleaf / Servlets Renderizados por Spring Boot (Monolito Tradicional)**:
   * *Ventajas*: Todo en un solo proyecto Java, cero tooling frontend moderno.
   * *Desventajas*: Recargas completas de página, experiencia de usuario tosca, dificultad extrema para integrar gráficos reactivos de telemetría y desacoplamiento deficiente.
3. **Single Page Application (SPA) con React 19, TypeScript, Vite y Tailwind CSS v4**:
   * *Ventajas*: Compilación ultrarrápida (Vite HMR en <50ms), bundle JS estático (<730 kB) almacenable a costo \$0, soporte de React 19 con reconciliador concurrente, tipado estricto con `verbatimModuleSyntax: true` y control de diseño modular.

---

## Decisión Adoptada

Se adopta la opción 3: **Single Page Application (SPA) en React 19 + TypeScript + Vite + Tailwind CSS v4**, organizada bajo el patrón arquitectónico de diseño **Dark Luxury / Glassmorphism**.

### Principios Clave de Implementación:
1. **Cliente HTTP Centralizado con Inyección Notarial (`client.ts`)**:
   Toda llamada a endpoints mutativos es interceptada por una función envoltorio que genera `crypto.randomUUID()` como cabecera `Idempotency-Key` si no fue provista, y analiza la cabecera `X-Idempotent-Replay` retornada por el backend.
2. **Estética Dark Luxury & Ergonomía Industrial**:
   - Paleta de fondo oscuro profundo: `#020617` (`slate-950`).
   - Acentos metálicos de alta visibilidad: Ámbar (`amber-400` / `amber-500`) para advertencias y llamadas a la acción, Esmeralda (`emerald-400`) para confirmaciones ACID exitosas, y Carmín (`rose-400`) para cancelaciones.
   - Paneles esmerilados (`.glass-panel`, `.glass-card` con `backdrop-filter: blur(12px)`) para jerarquía visual sin saturar con líneas duras de separación.
3. **Visualización de Telemetría con Recharts**:
   Consumo reactivo de `/actuator/prometheus` para graficar en tiempo real el throughput de pedidos, la tasa de rechazos 409 por falta de stock y la saturación del pool de conexiones HikariCP.

---

## Consecuencias y Trade-offs

* **Positivas**:
  - Despliegue estático a costo \$0 en producción (Nginx o S3/CDN).
  - Experiencia de usuario instantánea sin parpadeos de recarga de página.
  - Blindaje automático contra órdenes duplicadas por doble clic accidental.
* **Negativas / Mitigaciones**:
  - No cuenta con SEO para indexación pública en buscadores (completamente irrelevante para una plataforma logística B2B privada).
  - Requiere configuración de políticas CORS o proxy inverso para conectar el frontend estático con la API REST de backend (resuelto mediante `CorsConfig.java` y el proxy de Vite).
