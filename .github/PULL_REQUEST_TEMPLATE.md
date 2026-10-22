## 📋 Descripción del Cambio
<!-- Resume brevemente el propósito de este Pull Request y qué problema o User Story resuelve -->
**User Story / Issue Vinculado**: Closes #...

---

## 🛡️ Checklist de Definition of Done (DoD) Formal

Antes de solicitar revisión o fusionar este Pull Request a `main`, el autor certifica el cumplimiento estricto de cada uno de los siguientes puntos:

### 1. Calidad de Código y Arquitectura
- [ ] El código compila sin errores ni advertencias (`-Werror`).
- [ ] No se exponen entidades `@Entity` de JPA en controladores REST; se utilizan Java Records inmutables como DTOs.
- [ ] Se aplican validaciones formales con Bean Validation (`@NotNull`, `@Positive`, `@Size`, etc.).
- [ ] Las dependencias respetan las fronteras del Monolito Modular (sin referencias circulares ni violaciones de capas).
- [ ] En operaciones de bloqueo pesimista, se configuró explícitamente el timeout de 3000 ms (`jakarta.persistence.lock.timeout`).
- [ ] Si se bloquean múltiples recursos, se aplicó ordenamiento determinista para prevenir deadlocks.

### 2. Testing y Cobertura
- [ ] Pruebas Unitarias implementadas con JUnit 5 y Mockito para la lógica de negocio pura (cobertura >= 80% en líneas del módulo).
- [ ] Pruebas de Integración implementadas contra una base de datos real con **Testcontainers (PostgreSQL 16)**.
- [ ] Si involucra operaciones de inventario, se implementaron pruebas de concurrencia multihilo comprobando la ausencia de condiciones de carrera.
- [ ] Todos los tests pasan localmente y en el pipeline de CI (`./gradlew check`).

### 3. Base de Datos y Persistencia
- [ ] Todo cambio en el esquema está versionado en un script de migración SQL de **Flyway** (`V{n}__{desc}.sql`).
- [ ] Las claves foráneas, índices únicos y constraints de no negatividad (`CHECK`) fueron debidamente aplicados.
- [ ] Las migraciones fueron probadas exitosamente desde cero en un contenedor limpio.

### 4. Ciberseguridad & OWASP (CSFPC, CEHPC, LCSPC)
- [ ] Consultas a base de datos 100% parametrizadas (sin concatenación dinámica de cadenas para prevenir SQL Injection).
- [ ] Manejo centralizado de excepciones con respuestas RFC 7807 (`ProblemDetail`), sin filtrar stack traces ni datos internos al cliente.
- [ ] El escaneo de seguridad en CI con **Trivy** no arroja vulnerabilidades `CRITICAL` ni `HIGH`.
- [ ] No existen credenciales, llaves API ni secretos quemados en el código fuente.

### 5. Documentación y DevOps
- [ ] Documentación OpenAPI / Swagger actualizada y verificada.
- [ ] El archivo `CHANGELOG.md` registra los cambios incorporados.
- [ ] El contenedor Docker multi-stage compila y ejecuta con usuario no-root (`USER 1001`).
- [ ] Pipeline de GitHub Actions ejecutado en verde.
