# Política de Seguridad y Modelo de Amenazas — StockPilot

> **Certificaciones de Respaldo**: CSFPC (Cybersecurity Foundation), CEHPC (Ethical Hacking), LCSPC (Lead Cybersecurity Professional)  
> **Responsable**: Víctor Venegas — Security Champion & Software Architect  
> **Clasificación**: Documento Público de Seguridad / Docs-as-Code

---

## 1. Declaración de Política de Seguridad y Divulgación Responsable

En **StockPilot**, la integridad de los datos de inventario y la continuidad operacional de la cadena de suministro B2B son activos de máxima prioridad. Adoptamos un enfoque de **Seguridad por Diseño (Security by Design)** y **Mínimo Privilegio (Least Privilege)** en todas las fases del ciclo de vida del software (DevSecOps).

### 1.1 Reporte de Vulnerabilidades
Si descubres una vulnerabilidad de seguridad en esta plataforma, solicitamos reportarla de forma ética y responsable:
* **Canal Oficial**: Correo a `vi.venegasl@duocuc.cl` con el asunto `[SECURITY] Reporte de Vulnerabilidad - StockPilot`.
* **SLA de Respuesta**:
  * Acuse de recibo inicial: < 24 horas.
  * Evaluación y triaje del impacto (CVSS v3.1): < 72 horas.
  * Plan de remediación y parche en producción: < 7 días hábiles.
* **Compromiso**: No se emprenderán acciones legales contra investigadores que actúen de buena fe bajo estas pautas.

---

## 2. Modelo de Amenazas Formal (Metodología STRIDE)

Analizamos las amenazas potenciales sobre la arquitectura de **StockPilot** clasificadas bajo el framework **STRIDE** de Microsoft:

| Categoría STRIDE | Amenaza Identificada | Superficie de Ataque | Mitigación Técnica Implementada |
|---|---|---|---|
| **S**poofing (Suplantación) | Falsificación de identidad en solicitudes mutantes de pedidos | `POST /orders`, `PUT /confirm` | Validación de cabeceras seguras, tokens de sesión / API keys y control de origen estricto vía Nginx CORS. |
| **T**ampering (Manipulación) | Inyección SQL para alterar el stock físico o bypassear precios | Parámetros de búsqueda en catálogo y queries de stock | Consultas 100% parametrizadas en JPA/Hibernate. Cero concatenación de cadenas SQL. DTOs estrictamente tipados con Bean Validation. |
| **T**ampering (Manipulación) | Inyección XSS a través de nombres o especificaciones técnicas de productos | Frontend React 19 SPA | React escapa strings de forma nativa en JSX. Cabeceras estrictas de Content-Security-Policy (CSP) aplicadas en Nginx. |
| **R**epudiation (Repudio) | Un usuario niega haber ordenado o liberado un lote de inventario | Operaciones de modificación de stock | **Libro Mayor Inmutable (Inventory Ledger)**: Tabla append-only donde cada mutación queda grabada con ID, timestamp (`clock_timestamp()`), usuario responsable y snapshot resultante. |
| **I**nformation Disclosure (Fuga de Info) | Exposición de stack traces, contraseñas o topología de base de datos en errores | Excepciones no controladas de Spring Boot | `GlobalExceptionHandler` con `@RestControllerAdvice` retornando respuestas estandarizadas **RFC 7807 (ProblemDetail)** sin filtrar datos internos. Actuator restringido. |
| **D**enial of Service (DoS) | Bombardeo masivo de reservas de stock para colgar hilos de la JVM | Endpoint de confirmación de pedidos | 1) Rate Limiting a nivel de Nginx (`limit_req`). 2) Timeout de bloqueo pesimista en JPA (`jakarta.persistence.lock.timeout = 3000ms`). 3) Motor de Idempotencia. |
| **E**levation of Privilege (Escalamiento) | Acceso de root al sistema operativo host mediante escape de contenedor | Contenedor Docker de la aplicación | Contenedor multi-stage ejecutado bajo usuario sin privilegios (`USER 1001` no-root). Sistema de archivos en solo lectura donde sea posible. |

---

## 3. Matriz de Mitigación OWASP Top 10 (2021)

| Vulnerabilidad OWASP | Riesgo en StockPilot | Contramedida de Ingeniería |
|---|---|---|
| **A01: Broken Access Control** | Manipulación de identificadores (IDOR) en pedidos ajenos | Validación estricta de pertenencia y control de transiciones en `OrderStateMachine`. |
| **A02: Cryptographic Failures** | Interceptación de datos en tránsito | TLS 1.3 forzoso en Nginx Edge Proxy con HSTS preloaded (`max-age=31536000`). Contraseñas y secretos inyectados exclusivamente vía variables de entorno en OCI. |
| **A03: Injection** | SQL Injection, Expression Injection | Spring Data JPA parametrizado. Validación de esquemas y listas blancas de ordenamiento (`Sort`). |
| **A04: Insecure Design** | Sobreventa por condiciones de carrera concurrentes | Bloqueo Pesimista en PostgreSQL (`SELECT ... FOR UPDATE`), constraints a nivel de base de datos (`CHECK (reserved_qty <= total_qty)`). |
| **A05: Security Misconfiguration** | Configuraciones por defecto y puertos expuestos | Deshabilitación de OSIV (`open-in-view: false`). Puertos de base de datos cerrados al exterior en OCI VCN. |
| **A06: Vulnerable and Outdated Components** | CVEs en bibliotecas de terceros | Escaneo automatizado en cada commit y pull request con **Trivy (FS + Container Image)** en el pipeline de GitHub Actions con corte de build (`exit-code: 1`). |
| **A07: Identification and Authentication Failures** | Reutilización de credenciales o fuerza bruta | Rate Limiting por IP en Nginx. Tokens temporales y políticas seguras de sesión. |
| **A08: Software and Data Integrity Failures** | Inserción de código malicioso o manipulación de estado | Verificación de firmas de imágenes en OCI, control de sumas de chequeo (Flyway checksums) y validación de hash de dependencias Gradle. |
| **A09: Security Logging and Monitoring Failures** | Ataques sin detectar en tiempo real | Logging estructurado JSON en stdout/stderr. Métricas de Prometheus en Actuator. Ledger de auditoría inmutable. |
| **A10: Server-Side Request Forgery (SSRF)** | Llamadas salientes no autorizadas desde el backend | La aplicación no realiza peticiones HTTP salientes hacia IPs externas en la capa de negocio. |

---

## 4. Cabeceras de Seguridad del Servidor Web (Nginx Hardening)

Nginx implementa la siguiente configuración de cabeceras de seguridad militarizadas:

```nginx
# Prevención de MIME Sniffing
add_header X-Content-Type-Options "nosniff" always;

# Prevención de Clickjacking
add_header X-Frame-Options "DENY" always;

# Deshabilitación del filtro obsoleto de IE y activación de CSP moderna
add_header X-XSS-Protection "0" always;

# Content Security Policy estricta
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self';" always;

# Referrer Policy para proteger URLs internas
add_header Referrer-Policy "strict-origin-when-cross-origin" always;

# Aislamiento de APIs del dispositivo
add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=()" always;

# HTTP Strict Transport Security (HSTS)
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
```

---

## 5. Gestión de Secretos y Regla 12-Factor

1. **Prohibición Absoluta de Secretos en Git**: Ningún archivo con extensiones `.key`, `.pem`, `.env`, contraseñas o tokens debe incluirse en el repositorio.
2. **Inyección en Tiempo de Ejecución**: Las credenciales de base de datos y llaves de despliegue se suministran exclusivamente mediante **GitHub Secrets** y variables de entorno del sistema host en Oracle Cloud (`${DB_PASSWORD}`, `${OCI_SSH_KEY}`).
3. **Escaneo Pre-Commit**: Uso de `.gitignore` riguroso y verificación estricta en el pipeline de integración continua.
