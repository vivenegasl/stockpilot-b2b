# Planificación de Sprint 2 — Inventario Multi-Bodega y Ledger Inmutable

> **Sprint**: Sprint 2  
> **Fechas**: 2026-10-23 a 2026-10-28  
> **Product Owner & Scrum Master**: Víctor Venegas (SPOPC, SMPC)  
> **Capacidad del Equipo**: 8 Story Points

---

## 1. Objetivo del Sprint (Sprint Goal)

> *"Implementar la infraestructura transaccional de inventario multi-bodega con soporte de bloqueo pesimista y auditoría inmutable en el libro mayor (Append-Only Ledger), permitiendo registrar entradas de existencias y consultar disponibilidad física en tiempo real sin riesgo de condiciones de carrera."*

---

## 2. Historias de Usuario Comprometidas (Sprint Backlog)

| ID | Historia de Usuario | Story Points | Prioridad MoSCoW | Módulo |
|---|---|---|---|---|
| **US-003** | Inicialización y Ajuste de Stock en Bodega Física (Inbound / Cycle Count) | 3 SP | 🔴 Must Have | `inventory` + `audit` |
| **US-004** | Consulta de Disponibilidad de Inventario por Bodega | 2 SP | 🔴 Must Have | `inventory` |
| **US-005** | Trazabilidad Completa mediante Ledger de Inventario Inmutable | 3 SP | 🟡 Should Have | `audit` |

**Total de Story Points**: **8 SP**

---

## 3. Criterios de Aceptación Clave (Gherkin BDD)

### US-003: Ajuste de Inventario con Bloqueo Concurrente
```gherkin
Scenario: Ingreso de mercadería (Inbound) exitoso
  Given el SKU "PLC-SIEM-S71500" y la bodega "WH-SCL-CENTRAL"
  When el operador ejecuta POST /api/v1/stock/adjust con delta=+10 y reason="Importación Lote A"
  Then stock_level.total_qty se incrementa en 10
    And available_qty (columna calculada STORED) refleja el nuevo saldo
    And se inserta una fila inmutable en inventory_ledger con movement_type="INBOUND"
    And la transacción es 100% ACID (ambas tablas persisten o ambas hacen rollback)

Scenario: Rechazo de ajuste negativo que violaría stock disponible
  Given un stock con total_qty=10 y reserved_qty=5 (disponible=5)
  When el operador intenta ajustar con delta=-7
  Then la operación se rechaza con HTTP 400 Bad Request
    And no se muta el stock ni se genera registro en el ledger
```

### US-004: Disponibilidad Segregada por Bodega Regional
```gherkin
Scenario: Consulta consolidada de disponibilidad de un SKU
  Given un producto existente en múltiples bodegas
  When un cliente solicita GET /api/v1/stock/product/{id}
  Then la respuesta lista cada bodega con su total_qty, reserved_qty y available_qty
```

### US-005: Trazabilidad Inmutable en el Libro Mayor
```gherkin
Scenario: Auditoría cronológica de movimientos
  Given un historial de movimientos de inventario
  When el auditor consulta GET /api/v1/ledger?productId=1
  Then los movimientos se retornan ordenados descendentemente por timestamp
    And cada registro contiene el saldo snapshot posterior al movimiento
```

---

## 4. Definition of Done (DoD) Específica para Sprint 2

- [ ] Entidad `InventoryLedger` marcada como `@org.hibernate.annotations.Immutable`.
- [ ] Entidad `StockLevel` mapea `available_qty` con `insertable = false, updatable = false`.
- [ ] Método de consulta con lock pesimista: `@Lock(LockModeType.PESSIMISTIC_WRITE)` y `@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})`.
- [ ] DTOs modelados exclusivamente como Java 21 Records inmutables con validación Bean Validation.
- [ ] Pruebas unitarias y de integración que validen el cálculo de saldos y la emisión del evento contable.
- [ ] Pipeline CI en verde en GitHub Actions.
