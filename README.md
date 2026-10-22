# StockPilot ✈️📦

> **Plataforma B2B de Gestión de Inventario y Pedidos de Alta Concurrencia con Cero Sobreventa**  
> *Diseñado e Implementado como Proyecto Central de Portafolio Profesional.*

---

## 👨‍💻 Ingeniero y Autor

**Víctor Venegas**  
- **Ingeniería Civil Informática** — Pontificia Universidad Católica de Valparaíso (PUCV)
- **Analista Programador** — Duoc UC
- **Credenciales Profesionales Evidenciadas**:
  - 🏅 **Scrum Track Completo**: SFPC, SDPC, SPOPC, SMPC (Historias de Usuario BDD/Gherkin, DoD formal, Backlog priorizado por valor).
  - 🚀 **DevOps Track Avanzado**: DEPC, DFPC, DAPC (CI/CD GitHub Actions, Docker Multi-stage Non-root, 12-Factor App, Despliegue en Oracle Cloud OCI).
  - 🛡️ **Ciberseguridad & Ethical Hacking**: CSFPC, CEHPC, LCSPC (Mitigación OWASP Top 10, RFC 7807, Trivy Scanner, Threat Modeling STRIDE en `SECURITY.md`).

---

## 🏛️ Filosofía de Arquitectura: "Docs-as-Code"

Toda la documentación técnica, decisiones arquitectónicas y artefactos ágiles residen dentro del repositorio en formato Markdown versionado:

* 📐 **[Documento de Arquitectura de Software (SAD)](docs/architecture/ARCHITECTURE.md)**: Diagramas C4, Modelo ERD, Máquinas de Estados y Secuencia Concurrente (`SELECT ... FOR UPDATE`).
* 📜 **Registro de Decisiones Arquitectónicas (ADRs)**:
  * [ADR-001: Adopción de Monolito Modular frente a Microservicios](docs/adr/0001-modular-monolith.md)
  * [ADR-002: Mitigación de Sobreventa mediante Bloqueo Pesimista con Timeout](docs/adr/0002-concurrency-locking.md)
  * [ADR-003: Motor de Idempotencia HTTP para Operaciones Mutantes](docs/adr/0003-idempotency-engine.md)
  * [ADR-004: Cálculo de Stock Disponible mediante Columna Generada en PostgreSQL](docs/adr/0004-database-generated-available-qty.md)
  * [ADR-005: Estrategia de Despliegue en Oracle Cloud Infrastructure (OCI)](docs/adr/0005-oracle-cloud-oci-deployment.md)
* 🎯 **[Backlog de Producto Priorizado (Scrum)](docs/scrum/product_backlog.md)**: Historias de usuario con Gherkin Given-When-Then.
* 🛡️ **[Política de Seguridad y Threat Model](SECURITY.md)**: Análisis STRIDE y controles OWASP.
* 📋 **[Definition of Done Formal](.github/PULL_REQUEST_TEMPLATE.md)**: Criterios inquebrantables de aceptación técnica para PRs.

---

## 🧰 Stack Tecnológico de Misión Crítica

- **Backend**: Java 21 LTS, Spring Boot 3.4.x, Spring Data JPA, Hibernate 6, Flyway Migration.
- **Base de Datos**: PostgreSQL 16 (Generated Columns, Row-Level Locking, HikariCP Pool).
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, Shadcn/ui, TanStack Query.
- **Testing**: JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL 16 real), ArchUnit.
- **DevSecOps**: GitHub Actions, Docker Multi-Stage (Non-Root 1001), Trivy Vulnerability Scanner, Oracle Cloud (OCI Always Free).
