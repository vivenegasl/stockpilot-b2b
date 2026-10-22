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
    En Alcance - MVP Core
      Catalogo B2B SKUs
      Inventario Multi-Bodega
      Reserva Concurrente FOR UPDATE
      Idempotency Engine 24h
      Ledger Inmutable Append-Only
      UI React 19 + Shadcn
    Siguiente Fase
      Scheduler Expiracion TTL
      Exportacion CSV Streaming
      Transferencia Inter-Bodegas
      Auth JWT + RBAC
    Fuera de Alcance Explicito
      Microservicios Distribuidos
      Pasarelas Pago B2C Transbank
      Tracking Satelital GPS
      Event Sourcing / Kafka
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

$$\forall (p, w) \in \text{StockLevel}: \quad \text{reserved\_qty} \ge 0 \quad \land \quad \text{reserved\_qty} \le \text{total\_qty}$$

$$\text{available\_qty} = \text{total\_qty} - \text{reserved\_qty} \ge 0$$

$$\Delta \text{total\_qty} = \sum \text{LedgerEntries(movement\_type} \in \{\text{INBOUND}, \text{FULFILLMENT}, \text{ADJUSTMENT}\})$$

---

## 4. Diagramas de Arquitectura (C4, ERD, Estados, Secuencia)

### 4.1 C4 — Nivel 2: Diagrama de Contenedores

```mermaid
graph TB
    subgraph Users ["Actores del Sistema"]
        CLIENT["Comprador B2B / ERP Cliente<br/>[HTTPS / REST]"]
        OPERATOR["Operador Logístico Bodega<br/>[Web Browser]"]
    end

    subgraph Boundary ["Límite del Sistema StockPilot (Host / OCI Compute)"]
        NGINX["Nginx 1.25+ (Reverse Proxy & Edge)<br/>[C, Docker]<br/>Terminación TLS, Rate Limiting, Compresión Gzip, Seguridad OWASP"]
        
        SPA["Frontend SPA<br/>[React 19, TypeScript, Vite, Tailwind, Shadcn]<br/>UI reactiva, TanStack Query, gestión de pedidos e inventario"]
        
        subgraph Monolith ["Backend Spring Boot 3.4.x (Monolito Modular) [Java 21 LTS]"]
            API_LAYER["API Gateway / Web Filter Layer<br/>IdempotencyFilter, Security Headers, ProblemDetail (RFC 7807)"]
            
            subgraph Modules ["Módulos de Dominio (Empaquetado Estricto)"]
                MOD_CATALOG["📦 catalog<br/>SKUs, Categorías, Metadatos"]
                MOD_INVENTORY["📊 inventory<br/>StockLevel, Lock Engine, Bodegas"]
                MOD_ORDERING["🛒 ordering<br/>PurchaseOrder, OrderLine, State Machine"]
                MOD_AUDIT["📋 audit<br/>InventoryLedger (Append-Only)"]
            end
        end

        DB[("PostgreSQL 16 Engine<br/>[RDBMS]<br/>Generated Columns, Row-Level Locks, Constraints, Flyway")]
        PROM["Prometheus Agent<br/>Scraping de métricas de Micrometer /actuator/prometheus"]
    end

    CLIENT -->|"Llamadas API REST<br/>JSON / HTTPS"| NGINX
    OPERATOR -->|"Interactúa con SPA<br/>HTTPS"| NGINX
    NGINX -->|"Sirve estáticos SPA"| SPA
    NGINX -->|"Proxy inverso /api/v1/*"| API_LAYER
    API_LAYER --> MOD_CATALOG
    API_LAYER --> MOD_ORDERING
    API_LAYER --> MOD_INVENTORY
    API_LAYER --> MOD_AUDIT
    
    MOD_ORDERING -->|"Invoca reserva atómica"| MOD_INVENTORY
    MOD_ORDERING -->|"Registra eventos"| MOD_AUDIT
    MOD_INVENTORY -->|"Registra movimientos"| MOD_AUDIT
    
    MOD_CATALOG -->|"JDBC (HikariCP)"| DB
    MOD_INVENTORY -->|"SELECT ... FOR UPDATE"| DB
    MOD_ORDERING -->|"JDBC (HikariCP)"| DB
    MOD_AUDIT -->|"INSERT Only"| DB
    
    PROM -->|"Scrape /actuator"| API_LAYER
```

### 4.2 Modelo Entidad-Relación (ERD) Físico

```mermaid
erDiagram
    CATEGORY ||--o{ PRODUCT : "clasifica"
    WAREHOUSE ||--o{ STOCK_LEVEL : "almacena"
    PRODUCT ||--o{ STOCK_LEVEL : "tiene_stock_en"
    STOCK_LEVEL ||--o{ STOCK_RESERVATION : "reserva"
    PURCHASE_ORDER ||--o{ ORDER_LINE : "contiene"
    PURCHASE_ORDER ||--o{ STOCK_RESERVATION : "garantiza"
    WAREHOUSE ||--o{ PURCHASE_ORDER : "despacha_desde"
    PRODUCT ||--o{ INVENTORY_LEDGER : "audita_sku"
    WAREHOUSE ||--o{ INVENTORY_LEDGER : "audita_bodega"
    PURCHASE_ORDER ||--o{ INVENTORY_LEDGER : "origina_movimiento"

    CATEGORY {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        varchar(100) name UK "NOT NULL, e.g. PLCs y Controladores"
        varchar(500) description
        timestamp created_at "NOT NULL DEFAULT clock_timestamp()"
        timestamp updated_at "NOT NULL DEFAULT clock_timestamp()"
    }

    PRODUCT {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        varchar(50) sku UK "NOT NULL, e.g. PLC-SIEM-S71500"
        varchar(200) name "NOT NULL"
        varchar(2000) technical_spec "Especificación de ingeniería"
        numeric(12_2) unit_price "NOT NULL, CHECK > 0"
        bigint category_id FK "NOT NULL"
        boolean active "NOT NULL DEFAULT true"
        integer version "Optimistic Lock para catálogo"
        timestamp created_at "NOT NULL"
        timestamp updated_at "NOT NULL"
    }

    WAREHOUSE {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        varchar(20) code UK "NOT NULL, e.g. WH-SCL-CENTRAL"
        varchar(100) name "NOT NULL"
        varchar(500) address "NOT NULL"
        boolean active "NOT NULL DEFAULT true"
        timestamp created_at "NOT NULL"
        timestamp updated_at "NOT NULL"
    }

    STOCK_LEVEL {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        bigint product_id FK "NOT NULL"
        bigint warehouse_id FK "NOT NULL"
        integer total_qty "NOT NULL, CHECK >= 0"
        integer reserved_qty "NOT NULL DEFAULT 0, CHECK >= 0"
        integer available_qty "GENERATED ALWAYS AS (total_qty - reserved_qty) STORED"
        timestamp created_at "NOT NULL"
        timestamp updated_at "NOT NULL"
    }

    PURCHASE_ORDER {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        varchar(35) order_number UK "NOT NULL, e.g. ORD-20261022-0001"
        uuid idempotency_key UK "NOT NULL"
        varchar(25) status "DRAFT | CONFIRMED | FULFILLED | CANCELLED"
        bigint warehouse_id FK "NOT NULL"
        numeric(14_2) total_net_amount "NOT NULL DEFAULT 0"
        varchar(500) notes
        integer version "Optimistic Lock"
        timestamp created_at "NOT NULL"
        timestamp updated_at "NOT NULL"
    }

    ORDER_LINE {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        bigint order_id FK "NOT NULL"
        bigint product_id FK "NOT NULL"
        integer quantity "NOT NULL, CHECK > 0"
        numeric(12_2) unit_price "Precio snapshot"
        numeric(14_2) line_total "GENERATED ALWAYS AS (quantity * unit_price) STORED"
        timestamp created_at "NOT NULL"
    }

    STOCK_RESERVATION {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        bigint stock_level_id FK "NOT NULL"
        bigint order_id FK "NOT NULL"
        integer quantity "NOT NULL, CHECK > 0"
        varchar(20) status "ACTIVE | RELEASED | FULFILLED"
        timestamp expires_at "Nullable"
        timestamp created_at "NOT NULL"
        timestamp updated_at "NOT NULL"
    }

    INVENTORY_LEDGER {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        bigint product_id FK "NOT NULL"
        bigint warehouse_id FK "NOT NULL"
        bigint order_id FK "Nullable"
        varchar(30) movement_type "INBOUND | RESERVATION | RELEASE | FULFILLMENT | ADJUSTMENT"
        integer quantity_delta "NOT NULL"
        integer resulting_total "Snapshot post-transacción"
        integer resulting_reserved "Snapshot post-transacción"
        varchar(500) reason "NOT NULL"
        varchar(100) performed_by "NOT NULL"
        timestamp created_at "NOT NULL DEFAULT clock_timestamp()"
    }

    IDEMPOTENCY_RECORD {
        bigint id PK "GENERATED ALWAYS AS IDENTITY"
        uuid idempotency_key UK "NOT NULL"
        varchar(10) http_method "NOT NULL"
        varchar(500) request_path "NOT NULL"
        integer response_status "NOT NULL"
        text response_body "NOT NULL"
        timestamp created_at "NOT NULL DEFAULT clock_timestamp()"
        timestamp expires_at "NOT NULL"
    }
```

### 4.3 Máquinas de Estado

#### Ciclo de Vida de la Orden de Compra (`PurchaseOrder`)
```mermaid
stateDiagram-v2
    [*] --> DRAFT : POST /orders con Idempotency-Key
    
    DRAFT --> CONFIRMED : PUT /orders/{id}/confirm<br/>[Bloqueo pesimista & reserva OK]
    DRAFT --> CANCELLED : PUT /orders/{id}/cancel<br/>[Sin reservas activas]
    
    CONFIRMED --> FULFILLED : PUT /orders/{id}/fulfill<br/>[Despacho de almacén]
    CONFIRMED --> CANCELLED : PUT /orders/{id}/cancel<br/>[Libera reservas en stock_level]
    
    FULFILLED --> [*]
    CANCELLED --> [*]

    note right of CONFIRMED
        Invariante: stock_level.reserved_qty incrementado.
        stock_reservation en ACTIVE.
        Ledger registra 'RESERVATION'.
    end note

    note right of FULFILLED
        Invariante: stock_level.total_qty y reserved_qty decrementados.
        stock_reservation en FULFILLED.
        Ledger registra 'FULFILLMENT'.
    end note
```

#### Ciclo de Vida de la Reserva de Stock (`StockReservation`)
```mermaid
stateDiagram-v2
    [*] --> ACTIVE : Generada en Confirmación de Orden
    
    ACTIVE --> FULFILLED : Orden Despachada
    ACTIVE --> RELEASED : Orden Cancelada o Expirada por TTL
    
    FULFILLED --> [*]
    RELEASED --> [*]
```

### 4.4 Diagrama de Secuencia Concurrente: Carrera por la Última Unidad

```mermaid
sequenceDiagram
    autonumber
    actor Comprador_A as Comprador A (Minera Escondida)
    actor Comprador_B as Comprador B (Codelco Norte)
    participant API as Spring Boot (Filter & Controller)
    participant Engine as ReservationEngine (Service)
    participant DB as PostgreSQL 16 (HikariCP Pool)

    Note over Comprador_A, Comprador_B: Situación: Queda 1 unidad de PLC-SIEM-S71500 en WH-SCL-CENTRAL.<br/>Ambos clientes lanzan confirmación en el mismo milisegundo.

    Comprador_A->>+API: PUT /orders/101/confirm<br/>Idempotency-Key: uuid-alpha
    Comprador_B->>+API: PUT /orders/202/confirm<br/>Idempotency-Key: uuid-beta

    API->>+Engine: executeReservation(orderId: 101)
    API->>+Engine: executeReservation(orderId: 202)

    Note over Engine, DB: Ambas transacciones usan Isolation READ COMMITTED.<br/>Orden de locks: ORDER BY product_id ASC, warehouse_id ASC

    Engine->>+DB: [Tx 1] SELECT * FROM stock_level<br/>WHERE product_id=5 AND warehouse_id=1<br/>FOR UPDATE (lock timeout: 3000ms)
    DB-->>Engine: [Tx 1] Fila bloqueada con éxito ✅ (available_qty = 1)

    Engine->>DB: [Tx 2] SELECT * FROM stock_level<br/>WHERE product_id=5 AND warehouse_id=1<br/>FOR UPDATE (lock timeout: 3000ms)
    Note over Engine, DB: 🛑 [Tx 2] Pasa a estado WAITING en PostgreSQL.<br/>El hilo espera a que Tx 1 libere el bloqueo exclusivo.

    Note over Engine, DB: Tx 1 valida: available_qty (1) >= requested (1) -> OK
    Engine->>DB: [Tx 1] UPDATE stock_level SET reserved_qty = reserved_qty + 1 WHERE id=42
    Engine->>DB: [Tx 1] INSERT INTO stock_reservation (...)
    Engine->>DB: [Tx 1] INSERT INTO inventory_ledger (RESERVATION, delta=-1, ...)
    Engine->>DB: [Tx 1] UPDATE purchase_order SET status = 'CONFIRMED'
    Engine->>-DB: [Tx 1] COMMIT TRANSACTION ✅

    DB-->>Engine: [Tx 2] Despierta. Bloqueo adquirido ✅<br/>Lee estado fresco post-commit: available_qty = 0

    Note over Engine, DB: Tx 2 valida: available_qty (0) < requested (1) -> FALLO
    Engine->>DB: [Tx 2] ROLLBACK TRANSACTION ❌
    Engine-->>API: [Tx 2] Lanza InsufficientStockException

    API-->>Comprador_A: HTTP 200 OK<br/>{"orderId": 101, "status": "CONFIRMED"}
    API-->>-Comprador_B: HTTP 409 Conflict (RFC 7807 ProblemDetail)<br/>{"type": "/problems/insufficient-stock",<br/> "title": "Stock Insuficiente",<br/> "status": 409,<br/> "detail": "SKU PLC-SIEM-S71500 en WH-SCL-CENTRAL: solicitado 1, disponible 0"}
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
   - `ordering` $\rightarrow$ `inventory`
   - `inventory` $\rightarrow$ `audit`
   - `ordering` $\rightarrow$ `audit`
   - **Prohibido**: `audit` $\rightarrow$ cualquier otro módulo.
   - **Prohibido**: Referencias circulares inter-módulo.
3. **Mapeo Estricto DTO vs Entity**:
   - **Jamás** exponer clases `@Entity` en controladores REST.
   - Todo request/response es un `java.lang.Record` inmutable con anotaciones `jakarta.validation.constraints`.
