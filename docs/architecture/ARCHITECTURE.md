# StockPilot — Documento de Arquitectura de Software (SAD)

> **Estado**: Aprobado  
> **Versión**: 1.0.0  
> **Autor Principal**: Víctor Venegas — Lead Full-Stack Engineer & Software Architect  
> **Dominio de Negocio**: Distribución Mayorista B2B de Automatización y Suministros Industriales  

---

## 1. Contexto de Negocio y Dominio

**StockPilot** es una plataforma B2B de misión crítica orientada a la comercialización y despacho mayorista de componentes de automatización industrial, control eléctrico y repuestos de alta demanda en Chile y Latinoamérica (PLCs, variadores de frecuencia, sensores industriales, actuadores y protecciones eléctricas).

### 1.1 El Desafío de Negocio (High Concurrency & Zero Overselling)
En este sector, clientes corporativos (empresas mineras, plantas de celulosa, integradores de sistemas y constructoras EPC) operan con órdenes de compra de gran volumen y alta criticidad temporal. Cuando arriba un lote importado de componentes con stock limitado (ej. 10 unidades de un PLC Siemens S7-1500), múltiples compradores corporativos y sistemas de reaprovisionamiento automatizado lanzan solicitudes de compra simultáneas en cuestión de milisegundos.

**Impacto de la Sobreventa (Overselling):**
1. **Paradas de Planta y Multas Contractuales**: Vender stock inexistente paraliza contratos de integración industrial con multas diarias severas.
2. **Pérdida de Confianza B2B**: En el canal corporativo, la rotura de stock confirmada destruye acuerdos marco anuales.
3. **Invariante Crítica**: **Es una imposibilidad matemática y de negocio vender más unidades que las físicamente disponibles y aseguradas.**

---

## 2. Delimitación Rigurosa del Alcance

```mermaid
mindmap
  root((StockPilot))
    Alcance MVP Core
      Catalogo B2B SKUs
      Inventario Multi-Bodega
      Reserva FOR UPDATE
      Idempotencia 24h
      Ledger Inmutable
      UI React 19 + Shadcn
    Siguiente Fase
      Scheduler TTL
      Exportacion CSV
      Transferencias Bodega
      Auth JWT + RBAC
    Fuera de Alcance
      Microservicios
      Pasarelas B2C
      Tracking GPS
      Event Sourcing
```

### 2.1 En Alcance (Slice 0 & MVP Core)
- **Catálogo B2B de SKUs Industriales**: Gestión de productos con código SKU normalizado, clasificación jerárquica por categoría, fichas técnicas, precios netos mayoristas y versionado optimista de metadatos.
- **Inventario Segregado Multi-Bodega**: Stock físico independiente por nodo logístico regional:
  - `WH-SCL-CENTRAL`: Centro de Distribución Pudahuel (Santiago).
  - `WH-VAP-PORT`: Hub Portuario Valparaíso.
  - `WH-ANF-MINER`: Hub de Suministro Minero Antofagasta.
- **Motor de Reserva Concurrente Pesimista**: Reserva atómica transaccional usando `SELECT ... FOR UPDATE` con timeout explícito de 3000 ms y ordenamiento determinista por `(product_id ASC, warehouse_id ASC)` para erradicar deadlocks.
- **Motor de Idempotencia HTTP**: Servlet Filter que intercepta cabeceras `Idempotency-Key` (UUID v4), impidiendo procesamiento duplicado ante timeouts de red o reintentos agresivos del cliente B2B.
- **Libro Mayor de Inventario (Append-Only Ledger)**: Registro inmutable de trazabilidad que documenta cada variación de stock (`INBOUND`, `RESERVATION`, `RELEASE`, `FULFILLMENT`, `ADJUSTMENT`) con instantánea posterior y autor.
- **Frontend SPA React 19 + TypeScript**: Tableros operacionales con feedback de inventario en tiempo real, catálogo interactivo y terminal de checkout de pedidos.

### 2.2 Siguiente Fase (Roadmap Post-MVP)
- **Motor de Expiración Automática de Reservas**: Tarea programada (`@Scheduled` en Spring Boot) que libera reservas no formalizadas tras una ventana TTL (ej. 15 minutos).
- **Enrutamiento Inteligente y Stock Multibodega**: Algoritmo de despacho que optimiza el surtido desde bodegas geográficamente más cercanas cuando la bodega principal no cuenta con inventario completo.
- **Exportación Masiva en Streaming**: Generación de reportes de auditoría e inventario vía `StreamingResponseBody` / Reactive Streams para evitar presión en el heap de la JVM.
- **Seguridad B2B (JWT + RBAC)**: Control de acceso basado en roles (`ROLE_SALES_REP`, `ROLE_WAREHOUSE_MGR`, `ROLE_AUDITOR`).

### 2.3 Fuera de Alcance Explícito (Out-of-Scope)
- **Microservicios Distribuidos**: Se descarta la fragmentación en microservicios por introducir transacciones distribuidas innecesarias (2PC/Sagas) y latencia de red en un dominio altamente acoplado.
- **Pasarelas de Pago B2C en Vivo**: En transacciones B2B rigen líneas de crédito a 30/60 días (Facturación Electrónica), no pasarelas de tarjeta de crédito web.
- **Telemetría y GPS de Flotas**: Excede el Bounded Context de gestión de stock y almacén (pertenece a un TMS externo).

---

## 3. Invariantes Fundamentales de Negocio

Toda operación del sistema debe garantizar bajo cualquier condición de estrés las siguientes leyes matemáticas:

| Invariante | Expresión | Significado |
|---|---|---|
| **Rango de Reserva** | `0 <= reserved_qty <= total_qty` | Nunca se puede reservar más de lo físico |
| **Disponibilidad no negativa** | `available_qty = total_qty - reserved_qty >= 0` | El stock libre jamás puede ser negativo |
| **Consistencia del Ledger** | `delta_total = SUM(ledger INBOUND + FULFILLMENT + ADJUSTMENT)` | Todo cambio tiene entrada inmutable en ledger |

Donde las variables se corresponden de forma unívoca con las columnas del esquema relacional:

| Variable | Columna SQL | Significado |
|---|---|---|
| `total_qty` | `stock_level.total_qty` | Existencias físicas reales en estantería |
| `reserved_qty` | `stock_level.reserved_qty` | Unidades comprometidas en órdenes confirmadas |
| `available_qty` | `stock_level.available_qty` | Stock libre para nuevas reservas (columna generada en BD) |

---

## 4. Diagramas de Arquitectura (C4, ERD, Estados, Secuencia)

### 4.1 C4 — Nivel 2: Diagrama de Contenedores

```mermaid
graph TB
    subgraph Users ["Actores"]
        CLIENT["Comprador B2B / ERP<br/>HTTPS REST"]
        OPERATOR["Operador Logístico<br/>Web Browser"]
    end

    subgraph Boundary ["Sistema StockPilot — OCI Compute"]
        NGINX["Nginx 1.25<br/>Reverse Proxy + TLS<br/>Rate Limiting + OWASP"]

        SPA["Frontend SPA<br/>React 19 + TypeScript<br/>Vite + Tailwind + Shadcn"]

        subgraph Monolith ["Backend — Spring Boot 3.4 · Java 21 LTS"]
            API_LAYER["Web Filter Layer<br/>IdempotencyFilter · RFC 7807"]

            subgraph Modules ["Módulos de Dominio"]
                MOD_CATALOG["📦 catalog<br/>SKUs · Categorías"]
                MOD_INVENTORY["📊 inventory<br/>StockLevel · Locks"]
                MOD_ORDERING["🛒 ordering<br/>PurchaseOrder · FSM"]
                MOD_AUDIT["📋 audit<br/>Ledger Append-Only"]
            end
        end

        DB[("PostgreSQL 16<br/>Generated Cols<br/>Row-Level Locks<br/>Flyway")]
        PROM["Prometheus Agent<br/>/actuator/prometheus"]
    end

    CLIENT -->|"REST / HTTPS"| NGINX
    OPERATOR -->|"HTTPS"| NGINX
    NGINX -->|"Estáticos SPA"| SPA
    NGINX -->|"Proxy /api/v1"| API_LAYER
    API_LAYER --> MOD_CATALOG
    API_LAYER --> MOD_ORDERING
    API_LAYER --> MOD_INVENTORY
    API_LAYER --> MOD_AUDIT

    MOD_ORDERING -->|"Reserva atómica"| MOD_INVENTORY
    MOD_ORDERING -->|"Registra eventos"| MOD_AUDIT
    MOD_INVENTORY -->|"Registra movimientos"| MOD_AUDIT

    MOD_CATALOG -->|"HikariCP"| DB
    MOD_INVENTORY -->|"FOR UPDATE"| DB
    MOD_ORDERING -->|"HikariCP"| DB
    MOD_AUDIT -->|"INSERT Only"| DB

    PROM -->|"Scrape"| API_LAYER
```

### 4.2 Modelo Entidad-Relación (ERD) Físico

```mermaid
erDiagram
    CATEGORY ||--o{ PRODUCT : "clasifica"
    WAREHOUSE ||--o{ STOCK_LEVEL : "almacena"
    PRODUCT ||--o{ STOCK_LEVEL : "tiene stock"
    STOCK_LEVEL ||--o{ STOCK_RESERVATION : "reserva"
    PURCHASE_ORDER ||--o{ ORDER_LINE : "contiene"
    PURCHASE_ORDER ||--o{ STOCK_RESERVATION : "garantiza"
    WAREHOUSE ||--o{ PURCHASE_ORDER : "despacha"
    PRODUCT ||--o{ INVENTORY_LEDGER : "audita sku"
    WAREHOUSE ||--o{ INVENTORY_LEDGER : "audita bodega"
    PURCHASE_ORDER ||--o{ INVENTORY_LEDGER : "origina mov"

    CATEGORY {
        bigint id PK "IDENTITY"
        varchar name UK "NOT NULL"
        varchar description
        timestamp created_at
        timestamp updated_at
    }

    PRODUCT {
        bigint id PK "IDENTITY"
        varchar sku UK "NOT NULL eg PLC-SIEM-S71500"
        varchar name "NOT NULL"
        varchar technical_spec
        numeric unit_price "CHECK gt 0"
        bigint category_id FK
        boolean active "DEFAULT true"
        integer version "Optimistic Lock"
        timestamp created_at
        timestamp updated_at
    }

    WAREHOUSE {
        bigint id PK "IDENTITY"
        varchar code UK "eg WH-SCL-CENTRAL"
        varchar name "NOT NULL"
        varchar address
        boolean active "DEFAULT true"
        timestamp created_at
        timestamp updated_at
    }

    STOCK_LEVEL {
        bigint id PK "IDENTITY"
        bigint product_id FK
        bigint warehouse_id FK
        integer total_qty "CHECK gte 0"
        integer reserved_qty "DEFAULT 0"
        integer available_qty "GENERATED STORED"
        timestamp created_at
        timestamp updated_at
    }

    PURCHASE_ORDER {
        bigint id PK "IDENTITY"
        varchar order_number UK "eg ORD-20261022-0001"
        uuid idempotency_key UK
        varchar status "DRAFT CONFIRMED FULFILLED CANCELLED"
        bigint warehouse_id FK
        numeric total_net_amount "DEFAULT 0"
        varchar notes
        integer version "Optimistic Lock"
        timestamp created_at
        timestamp updated_at
    }

    ORDER_LINE {
        bigint id PK "IDENTITY"
        bigint order_id FK
        bigint product_id FK
        integer quantity "CHECK gt 0"
        numeric unit_price "Snapshot precio"
        numeric line_total "GENERATED STORED"
        timestamp created_at
    }

    STOCK_RESERVATION {
        bigint id PK "IDENTITY"
        bigint stock_level_id FK
        bigint order_id FK
        integer quantity "CHECK gt 0"
        varchar status "ACTIVE RELEASED FULFILLED"
        timestamp expires_at
        timestamp created_at
        timestamp updated_at
    }

    INVENTORY_LEDGER {
        bigint id PK "IDENTITY"
        bigint product_id FK
        bigint warehouse_id FK
        bigint order_id FK "Nullable"
        varchar movement_type "INBOUND RESERVATION RELEASE FULFILLMENT"
        integer quantity_delta
        integer resulting_total "Snapshot post-tx"
        integer resulting_reserved "Snapshot post-tx"
        varchar reason
        varchar performed_by
        timestamp created_at "clock_timestamp"
    }

    IDEMPOTENCY_RECORD {
        bigint id PK "IDENTITY"
        uuid idempotency_key UK
        varchar http_method
        varchar request_path
        integer response_status
        text response_body
        timestamp created_at
        timestamp expires_at "TTL 24h"
    }
```

### 4.3 Máquinas de Estado

#### Ciclo de Vida de la Orden de Compra (`PurchaseOrder`)
```mermaid
stateDiagram-v2
    [*] --> DRAFT : POST /orders con Idempotency-Key
    
    DRAFT --> CONFIRMED : PUT /orders/{id}/confirm<br/>Lock pesimista y reserva OK
    DRAFT --> CANCELLED : PUT /orders/{id}/cancel<br/>Sin reservas activas
    
    CONFIRMED --> FULFILLED : PUT /orders/{id}/fulfill<br/>Despacho desde bodega
    CONFIRMED --> CANCELLED : PUT /orders/{id}/cancel<br/>Libera reservas en stock
    
    FULFILLED --> [*]
    CANCELLED --> [*]

    note right of CONFIRMED
        Invariantes:
        - reserved_qty incrementado
        - stock_reservation ACTIVE
        - Ledger: RESERVATION
    end note

    note right of FULFILLED
        Invariantes:
        - total_qty decrementado
        - reserved_qty decrementado
        - stock_reservation FULFILLED
        - Ledger: FULFILLMENT
    end note
```

#### Ciclo de Vida de la Reserva de Stock (`StockReservation`)
```mermaid
stateDiagram-v2
    [*] --> ACTIVE : Confirmacion Orden
    
    ACTIVE --> FULFILLED : Orden Despachada
    ACTIVE --> RELEASED : Cancelada o TTL Expirado
    
    FULFILLED --> [*]
    RELEASED --> [*]
```

### 4.4 Diagrama de Secuencia Concurrente: Carrera por la Última Unidad

```mermaid
sequenceDiagram
    autonumber
    actor A as Comprador A (Minera)
    actor B as Comprador B (Codelco)
    participant API as Spring Boot API
    participant Engine as ReservationEngine
    participant DB as PostgreSQL 16

    Note over A,B: Queda 1 unidad de SKU.<br/>Ambos confirman en t=0.

    A->>+API: PUT /orders/101/confirm
    B->>+API: PUT /orders/202/confirm

    API->>+Engine: executeReservation(101)
    API->>+Engine: executeReservation(202)

    Note over Engine,DB: Isolation: READ COMMITTED.<br/>Orden: product_id, warehouse_id.

    Engine->>+DB: Tx1: SELECT FOR UPDATE id=42<br/>timeout=3000ms
    DB-->>Engine: Tx1: Lock OK (disp=1)

    Engine->>DB: Tx2: SELECT FOR UPDATE id=42<br/>timeout=3000ms
    Note over Engine,DB: Tx2 en WAITING.<br/>Espera liberar lock Tx1.

    Note over Engine,DB: Tx1: disp(1) >= pedido(1) OK
    Engine->>DB: Tx1: UPDATE reserved_qty = 1
    Engine->>DB: Tx1: INSERT stock_reservation
    Engine->>DB: Tx1: INSERT inventory_ledger
    Engine->>DB: Tx1: UPDATE order status=CONFIRMED
    Engine->>-DB: Tx1: COMMIT

    DB-->>Engine: Tx2: Despierta (disp=0)

    Note over Engine,DB: Tx2: disp(0) < pedido(1) FALLA
    Engine->>DB: Tx2: ROLLBACK
    Engine-->>API: InsufficientStockException

    API-->>A: 200 OK (101 CONFIRMED)
    API-->>-B: 409 Conflict (RFC 7807)<br/>Stock insuficiente (disp=0)
```

---

## 5. Decisiones Arquitectónicas (Resumen de ADRs)

| Identificador | Título | Decisión Adoptada | Trade-off Principal Asumido |
|---|---|---|---|
| **ADR-001** | Arquitectura Monolito Modular vs Microservicios | Monolito Modular con paquetes por dominio | Menor escalabilidad independiente a cambio de transacciones ACID locales y cero latencia de red |
| **ADR-002** | Control de Concurrencia y Anti-Sobreventa | Bloqueo Pesimista (`SELECT FOR UPDATE`) con timeout de 3s | Serialización en filas calientes a cambio de exactitud matemática infalible |
| **ADR-003** | Motor de Idempotencia en Capa HTTP | Servlet Filter + Persistencia en PostgreSQL (TTL 24h) | Almacenamiento extra en BD a cambio de protección total ante reintentos de red |
| **ADR-004** | Cálculo de `available_qty` en Base de Datos | Columna Calculada `GENERATED ALWAYS AS STORED` | Rigidez en esquema DDL a cambio de erradicar desincronizaciones en la capa de aplicación |
| **ADR-005** | Estrategia de Despliegue en Oracle Cloud (OCI) | Compute VM + Docker Multi-stage + Nginx Edge Proxy | Gestión de infraestructura IaaS a cambio de costo \$0 (Free Tier) y soberanía de datos |

---

## 6. Convenciones de Empaquetado y Capas Limpias

Para preservar la pureza modular y asegurar que el código no degrade en un "big ball of mud", se establecen las siguientes reglas:

1. **Aislamiento de Módulos**: `com.stockpilot.catalog`, `com.stockpilot.inventory`, `com.stockpilot.ordering`, `com.stockpilot.audit`, `com.stockpilot.shared`.
2. **Dirección de Dependencias**:
   - `ordering` → `inventory`
   - `inventory` → `audit`
   - `ordering` → `audit`
   - **Prohibido**: `audit` → cualquier otro módulo.
   - **Prohibido**: Referencias circulares inter-módulo.
3. **Mapeo Estricto DTO vs Entity**:
   - **Jamás** exponer clases `@Entity` en controladores REST.
   - Todo request/response es un `java.lang.Record` inmutable con anotaciones `jakarta.validation.constraints`.
