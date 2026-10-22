# ADR-0001: Adopción de Monolito Modular frente a Microservicios

* **Estado**: Aceptado
* **Fecha**: 2026-10-22
* **Decisores**: Víctor Venegas (Software Architect)
* **Categoría**: Arquitectura de Software / Estructura del Sistema

## Contexto y Declaración del Problema

El sistema **StockPilot** requiere procesar transacciones B2B de inventario y pedidos con consistencia estricta (ACID) y alta concurrencia. La operación de confirmación de pedido exige validar el catálogo de productos, verificar y reservar el stock en una bodega específica, registrar la reserva y emitir un movimiento en el ledger contable inmutable.

En la industria actual existe la tentación de adoptar una arquitectura de microservicios distribuida desde el día uno. Debemos decidir si organizar el sistema como un conjunto de microservicios independientes comunicados por red o como un Monolito Modular desplegado en una única unidad de ejecución.

## Factores de Decisión (Decision Drivers)

* **Garantía de Consistencia Transaccional (ACID)**: La reserva de stock no tolera consistencia eventual ni estados intermedios inconsistentes.
* **Sobrecarga Operacional y Costos**: Un equipo de ingeniería ágil y recursos de infraestructura acotados (Oracle Cloud Free Tier) deben minimizar la complejidad de orquestación, service discovery y service mesh.
* **Latencia de Red en Operaciones Críticas**: Reducir el tiempo de retención de bloqueos pesimistas (`SELECT FOR UPDATE`) requiere eliminar saltos de red HTTP/gRPC entre servicios.
* **Mantenibilidad y Límites de Dominio Claros**: Posibilidad de evolucionar a microservicios en el futuro si la escala organizacional lo amerita, sin pagar el costo prematuro hoy.

## Opciones Consideradas

1. **Microservicios Distribuidos**: Servicios independientes (`catalog-service`, `inventory-service`, `order-service`, `audit-service`) con bases de datos segregadas, coordinados mediante Saga Pattern o dos fases (2PC).
2. **Monolito Tradicional ("Big Ball of Mud")**: Una sola aplicación sin barreras arquitectónicas internas, donde cualquier capa accede directamente a las tablas de cualquier dominio.
3. **Monolito Modular (Modular Monolith)**: Una única unidad desplegable (single JAR / container) con una única base de datos relacional, pero con módulos de código estrictamente aislados por paquetes de dominio (`catalog`, `inventory`, `ordering`, `audit`), interfaces públicas bien definidas y cumplimiento de reglas de dependencia.

## Decisión y Justificación

Se elige la **Opción 3: Monolito Modular**.

### Justificación Técnica:
1. **Transacciones ACID Locales**: La confirmación de pedido y la reserva de stock ocurren dentro de una única transacción de base de datos (`@Transactional`), garantizando atomicidad inmediata sin la fragilidad ni los fallos parciales del patrón Saga.
2. **Minimización de Latencia en Row-Locks**: Al ejecutar la lógica en un solo proceso con conexiones en pool local (HikariCP), las transacciones de reserva duran < 15 ms, minimizando drásticamente el tiempo de contención del bloqueo pesimista en PostgreSQL.
3. **Simplicidad Operativa 12-Factor**: Un solo artefacto Docker simplifica el CI/CD en GitHub Actions y el despliegue en un nodo de cómputo en Oracle Cloud Infrastructure (OCI).
4. **Barreras de Dominio Respetadas**: Cada módulo tiene su propio ciclo interno (`controller -> service -> repository -> domain -> dto`). Las llamadas inter-módulo se realizan mediante servicios tipados de Java, sin I/O de red, facilitando una futura separación si el volumen lo justificara.

## Consecuencias y Trade-offs

### Consecuencias Positivas:
* Cero latencia de red inter-módulos.
* Transacciones ACID nativas sin necesidad de infraestructura de mensajería (Kafka/RabbitMQ) para sincronización básica.
* Pruebas de integración rápidas y confiables usando una sola instancia de PostgreSQL en Testcontainers.
* Despliegue atómico sin problemas de versionado cruzado de APIs internas.

### Consecuencias Negativas y Mitigaciones:
* **Riesgo de Acoplamiento no deseado**: Desarrolladores podrían accidentalmente inyectar repositorios de otro módulo.  
  * *Mitigación*: Se implementan pruebas de arquitectura con **ArchUnit** en el pipeline de CI para verificar que ningún módulo viole las reglas de dependencia.
* **Escalado Horizontal Unificado**: Escalar el módulo de reservas implica escalar todo el monolito.  
  * *Mitigación*: La aplicación es completamente stateless; múltiples réplicas del contenedor pueden correr tras Nginx, delegando la serialización a PostgreSQL.
