# ADR-0010: Seguridad Perimetral CORS y Exposición de Cabeceras Transaccionales de Auditoría

* **Estado**: Aceptado
* **Fecha**: 2026-11-04
* **Decisores**: Víctor Venegas (Software Architect & Lead Engineer)
* **Categoría**: Seguridad Web / Perímetro HTTP / Integración Frontend-Backend

---

## Contexto y Declaración del Problema

Con la adopción de una arquitectura desacoplada donde el Frontend SPA reside en un servidor o puerto independiente (`http://localhost:5173` en desarrollo, o un dominio CDN como `wms.stockpilot.internal` en producción) y la API REST de Spring Boot opera en `http://localhost:8080`, los navegadores modernos aplican estrictamente la **Política del Mismo Origen (Same-Origin Policy - SOP)**:
1. **Peticiones Preflight Bloqueadas**: Métodos mutativos (`POST`, `PUT`, `DELETE`) o peticiones que incluyen cabeceras personalizadas (`Idempotency-Key`) disparan una petición preliminar `HTTP OPTIONS`. Si el backend no responde con las directivas CORS correctas, el navegador aborta la petición antes de que llegue a la lógica de negocio.
2. **Ocultamiento de Cabeceras de Respuesta**: Por defecto, el estándar CORS prohíbe que el código JavaScript de la aplicación cliente lea cabeceras HTTP de respuesta personalizadas (como `X-Idempotent-Replay` o `Idempotency-Key`). Sin una configuración explícita en el servidor, `response.headers.get('X-Idempotent-Replay')` retorna siempre `null`, rompiendo la experiencia visual y la auditoría del operador.

---

## Factores de Decisión (Decision Drivers)

* **Cumplimiento Estricto de Seguridad**: Prevenir ataques de tipo Cross-Origin Request Forgery (CSRF) y lecturas de datos no autorizadas desde orígenes maliciosos.
* **Transparencia Transaccional en el Cliente**: El frontend debe ser capaz de determinar si una respuesta provino de una ejecución fresca en base de datos o de un replay de la caché de idempotencia para renderizar los badges de estado correspondientes.
* **Prioridad Perimetral en la Cadena de Filtros de Spring**: El procesamiento de CORS debe ocurrir **antes** de los filtros de autenticación y antes del filtro de idempotencia (`IdempotencyFilter`), de modo que las peticiones pre-flight `OPTIONS` no requieran `Idempotency-Key` ni abran transacciones JDBC innecesarias.

---

## Opciones Consideradas

1. **Uso de Anotaciones `@CrossOrigin` a Nivel de Cada Controlador**:
   * *Ventajas*: Rápido de configurar en clases individuales.
   * *Desventajas*: Altamente propenso a errores humanos (olvidar la anotación en un nuevo endpoint), no aplica a endpoints de infraestructura como `/actuator/**` y no ofrece un control centralizado de cabeceras expuestas.
2. **Desactivar CORS en Desarrollo Mediante Plugins de Navegador**:
   * *Ventajas*: Cero cambios en código backend.
   * *Desventajas*: Práctica inaceptable en ingeniería de software; introduce discrepancias críticas entre el entorno local y producción (*Works on my machine syndrome*).
3. **Filtro Global Centralizado con `CorsFilter` y Máxima Precedencia (`CorsConfig.java`)**:
   * *Ventajas*: Aplica uniformemente sobre todas las rutas (`/api/v1/**` y `/actuator/**`), se ejecuta con `Ordered.HIGHEST_PRECEDENCE` antes de cualquier otro filtro y permite configurar de forma granular `Access-Control-Expose-Headers`.

---

## Decisión Adoptada

Se adopta la opción 3: **Configuración Centralizada de CORS mediante `CorsConfig.java`**.

### Reglas Clave Configuradas:
1. **Orígenes Permitidos**:
   - `http://localhost:5173` (Vite dev server).
   - `http://localhost:3000` (alternativa local).
   - Patrón adaptable para subdominios corporativos en producción (`*.stockpilot.internal`).
2. **Métodos Permitidos**:
   `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`.
3. **Cabeceras Permitidas de Entrada**:
   `Authorization`, `Content-Type`, `Idempotency-Key`, `Accept`, `Origin`, `X-Requested-With`.
4. **Cabeceras Expuestas de Salida (`Access-Control-Expose-Headers`)**:
   ```java
   configuration.setExposedHeaders(List.of(
       "Idempotency-Key",
       "X-Idempotent-Replay",
       "Location"
   ));
   ```
5. **Caché de Preflight (`Access-Control-Max-Age`)**:
   Se establece un TTL de preflight de 3600 segundos (1 hora), evitando que el navegador despache peticiones `OPTIONS` redundantes en cada llamada recurrente.

---

## Consecuencias y Trade-offs

* **Positivas**:
  - Comunicación fluida y segura entre la SPA y la API REST.
  - El frontend puede leer `X-Idempotent-Replay` e informar al usuario en tiempo real con un badge dorado si la orden fue recuperada de caché.
  - Las peticiones `OPTIONS` se resuelven en el perímetro sin tocar controladores ni bases de datos.
* **Negativas / Mitigaciones**:
  - Los orígenes permitidos deben mantenerse sincronizados con los dominios oficiales de despliegue en producción (parametrizados mediante variables de entorno en perfiles de Spring).
