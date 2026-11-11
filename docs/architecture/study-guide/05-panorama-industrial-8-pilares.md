# 🏢 Módulo 5: Panorama Industrial — Herramientas del Mercado vs. Arquitectura Bare-Metal (Los 8 Pilares)

> **Módulo 5 de la Guía Maestra de Estudio de StockPilot**  
> **Propósito**: Dominio del ecosistema de software empresarial de misión crítica. Gran matriz comparativa y desglose en 3 capas de los 8 pilares: Idempotencia, Bloqueos Distribuidos, Auditoría Inmutable, Disponibilidad en Tiempo Real, Colas y Eventos, Migraciones DDL, TTL y Observabilidad.

---

En la industria del software empresarial y cloud moderno, rara vez un equipo senior reinventa la rueda desde cero si existen soluciones gestionadas o bibliotecas de código abierto maduras. Sin embargo, para alcanzar una **Soberanía Técnica de nivel Solutions Architect**, es fundamental comprender qué herramientas dominan el mercado corporativo, cómo funcionan internamente, por qué en StockPilot elegimos una arquitectura *bare-metal* (basada exclusivamente en capacidades nativas del lenguaje y de la base de datos relacional) y cuál es el **umbral cuantitativo exacto** en que una empresa debe abandonar la solución bare-metal e incorporar la herramienta industrial.

### 5.1 Gran Matriz Comparativa: Los 8 Pilares de Software Empresarial

| Pilar Arquitectónico | Solución Bare-Metal StockPilot | Herramienta Industrial / Cloud Estándar | Por qué NO se usó en StockPilot (Justificación Técnica & Trade-offs) | Cuándo es OBLIGATORIO migrar (Punto de Inflexión) |
| :--- | :--- | :--- | :--- | :--- |
| **1. Idempotencia & Deduplicación HTTP** | Servlet Filter (`OncePerRequestFilter`) + `ContentCachingResponseWrapper` + tabla `idempotency_record` en PostgreSQL | **Redis** (`SETNX` + Lua Script atómico) / **Kong Idempotency Plugin** / **AWS API Gateway** | **Sobrecarga de infraestructura**: Introducir un clúster de Redis o un API Gateway externo agrega otro punto único de falla (*SPOF*), requiere gestión de secretos y costos cloud adicionales para un sistema B2B con tráfico controlado (< 2,000 RPS). PostgreSQL con índice `UNIQUE` garantiza atomicidad transaccional ACID estricta en el mismo motor de datos. | **Tráfico masivo B2C (> 20,000 RPS)** con picos de Black Friday donde golpear PostgreSQL para verificar claves satura las conexiones del pool; o cuando existen múltiples microservicios independientes que necesitan un API Gateway perimetral unificado. |
| **2. Concurrencia & Bloqueos de Recursos** | Bloqueo Pesimista en motor relacional (`SELECT ... FOR UPDATE` con timeout de 3,000 ms) + ordenamiento `productId ASC` | **Redis Redlock** / **Apache ZooKeeper** / **HashiCorp Consul** / **PostgreSQL Advisory Locks** | **Complejidad de fallos distribuidos**: Los bloqueos distribuidos en memoria (como Redlock) son notoriamente vulnerables a *clock drift* (desfase de reloj NTP) y pausas de recolección de basura (*Stop-the-World GC pauses*), pudiendo causar pérdida de exclusión mutua (según demostró Martin Kleppmann). El lock nativo de fila de PostgreSQL es garantizado por el kernel del motor dentro de la misma transacción ACID de reserva. | **Arquitectura de Microservicios Desacoplados**: Cuando el catálogo de productos y el inventario residen en bases de datos físicamente separadas, imposibilitando el uso de una transacción relacional ACID única. |
| **3. Auditoría e Inmutabilidad de Datos** | Tabla `inventory_ledger` con patrón Append-Only + `@Immutable` en Hibernate + triggers de bloqueo de DDL/DML | **Amazon QLDB** (Quantum Ledger Database) / **Immudb** / **EventStoreDB** (Event Sourcing) / **Hibernate Envers** (`@Audited`) | **Bloqueo de proveedor (*Vendor Lock-in*) y sobrecarga de lectura**: QLDB y EventStoreDB obligan a reconstruir el estado actual agregando millones de eventos (*event replaying* o *snapshots*), lo que incrementa la latencia en lecturas frecuentes. `@Immutable` en PostgreSQL ofrece el 95% del beneficio de cumplimiento normativo (SOX, ISO 27001) a costo cero de licencias o latencia. | **Requerimientos Legales de No Repudio Criptográfico Estricto**: Sector bancario, transacciones de valores bursátiles o aduanas con firma digital vinculante que exigen árboles de Merkle (*Merkle Trees*) con pruebas matemáticas de no manipulación (*tamper-proof proofs*). |
| **4. Cálculo de Disponibilidad de Stock** | Columna Generada de PostgreSQL 16 (`available_qty INT GENERATED ALWAYS AS (total_qty - reserved_qty) STORED`) | **Redis Hashes** (`HINCRBY`) / **Hazelcast** / **Apache Ignite** (In-Memory Data Grids) | **Riesgo de desincronización de memoria vs. disco**: Mantener el inventario en un caché en RAM como Redis obliga a implementar patrones complejos de sincronización (*Cache-Aside*, *Write-Behind*). Si Redis se reinicia o falla la replicación, la memoria diverge del disco y ocurren sobreventas millonarias. Delegar la resta a PostgreSQL 16 `STORED` garantiza consistencia física a nivel de almacenamiento. | **Consultas de solo lectura masivas (> 100,000 consultas/seg)** en marketplaces estilo Amazon o MercadoLibre donde los usuarios navegan el catálogo y necesitan ver disponibilidad aproximada sin golpear la base de datos transaccional. |
| **5. Procesamiento Asíncrono & Colas** | Transacciones ACID locales síncronas en PostgreSQL (< 10 ms de tiempo de respuesta) | **Apache Kafka** / **RabbitMQ** / **Transactional Outbox Pattern** + **Debezium CDC** / **Temporal.io** / **JobRunr** | **Falsa necesidad de asincronía en B2B**: En compras mayoristas B2B, el cliente corporativo necesita confirmación inmediata sincrónica de que su orden reservó stock firme. Introducir colas de mensajería asíncronas genera consistencia eventual (*Eventual Consistency*), introduciendo estados ambiguos de "pedido pendiente de confirmación" que complican la UI. | **Sistemas de Alto Desacoplamiento & Tráfico Asíncrono**: Procesamiento diferido de pagos con pasarelas externas lentas (Transbank, Stripe), emisión de facturas electrónicas tributarias (SII), envío masivo de correos de notificación o despacho a operadores logísticos externos. |
| **6. Versionado y Migración de Esquemas de BD** | **Flyway Community** (Migraciones secuenciales SQL nativas `V1__...`, `V2__...`) | **Liquibase** (Cambios declarativos en YAML/XML con rollbacks automáticos) / **Bytebase** (CI/CD de base de datos corporativa con DDL sin bloqueo vía `pg_repack`) | **Pérdida de control de SQL fino**: Liquibase abstrae el SQL en XML/YAML, lo que oculta optimizaciones específicas del motor (como sintaxis de índices parciales concurrentes o columnas generadas de PostgreSQL 16). Flyway ejecuta SQL puro y transparente, auditado en `flyway_schema_history`. | **Bases de Datos Políglotas Multi-Motor**: Equipos que deben soportar simultáneamente Oracle, SQL Server y PostgreSQL con el mismo código base, o cuando los DBA exigen scripts de *rollback* automáticos generados por el framework. |
| **7. Expiración Temporal de Reservas (TTL)** | Tarea programada en memoria vía Spring `@Scheduled` + consulta SQL de actualización en lote | **RabbitMQ Dead-Letter Exchanges (DLX)** con TTL de mensaje / **Redis Sorted Sets (ZSET)** con score de timestamp / **AWS EventBridge Scheduler** | **Complejidad operativa innecesaria para batching**: Un cron interno de Spring cada 60 segundos es trivial de mantener, no requiere brokers externos y permite procesar cancelaciones masivas en una sola transacción JDBC por lotes. | **Sistemas de Eventos en Tiempo Real con Millones de Reservas Cortas**: Venta de entradas a conciertos (Ticketmaster) o aerolíneas, donde cada reserva debe expirar exactamente al cumplirse 10 minutos con precisión de milisegundos sin barridos periódicos que bloqueen tablas. |
| **8. Observabilidad & Trazabilidad Distribuida** | **Micrometer** + **Prometheus Actuator** + Logs Estructurados JSON con correlación MDC | **OpenTelemetry (OTel)** + **Jaeger** / **Grafana Tempo** / **Datadog APM** / **New Relic** | **Sobrecarga de agentes y costo de ingestión**: OTel y agentes APM comerciales añaden huella de memoria significativa en la JVM y facturación elevada por gigabyte de trazas ingeridas. En un monolito modular, los logs estructurados con MDC (`traceId`, `spanId`) y métricas de Prometheus cubren el 100% del diagnóstico sin costo de licencias. | **Ecosistemas Distribuidos de Decenas de Microservicios**: Cuando una sola transacción atraviesa 8 servicios por HTTP/gRPC y se vuelve humanamente imposible rastrear la causa raíz de un cuello de botella sin trazas distribuidas visuales (*Distributed Tracing Waterfall*). |

---

### 5.2 Desglose en 3 Capas de los 8 Pilares Industriales

#### Pilar 1: Idempotencia y Deduplicación HTTP
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (PostgreSQL)*: Una alcancía blindada donde cada moneda tiene grabado un número de serie único. Si intentas meter dos veces una moneda con el mismo número, la ranura mecánica la expulsa inmediatamente con un clic metálico.
  * *Herramienta Industrial (Redis `SETNX`)*: Un guardia de seguridad en la puerta con una pizarra blanca y un cronómetro. Cuando llegas con tu ticket, el guardia revisa la pizarra. Si el ticket no está, lo anota con tiza y fija una alarma de 24 horas (`EXPIRE`). Si ya está anotado, no te deja pasar al edificio y te da el resultado que anotó antes.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (Redis `SETNX` + Lua script)*: La aplicación no golpea la base de datos relacional. Envía un comando por socket TCP a Redis en memoria RAM:
    ```lua
    -- Script Lua atómico ejecutado en el hilo único de Redis
    local key = KEYS[1]
    local response = ARGV[1]
    local ttl = ARGV[2]
    local lockAcquired = redis.call('SET', key, response, 'NX', 'EX', ttl)
    return lockAcquired
    ```
    `SET ... NX` (*Not eXists*) asigna el valor únicamente si la clave no existe previamente, en una operación atómica que toma **sub-milisegundos (0.3 ms)** al ejecutarse enteramente en memoria RAM sin I/O de disco. La respuesta previa se guarda serializada (JSON o MessagePack). Si la clave ya existía, Redis retorna el JSON almacenado de inmediato.
  * *Por qué en StockPilot usamos PostgreSQL*: En StockPilot, la idempotencia requiere consistencia estricta con el ciclo de vida del pedido. Si Redis se cae o sufre desalojo de memoria (*LRU eviction*), una clave podría borrarse prematuramente, permitiendo un cobro duplicado. En PostgreSQL, el registro se almacena en la tabla `idempotency_record` con almacenamiento persistente ACID en el Write-Ahead Log (WAL), garantizando que jamás se pierda una clave ante cortes de energía.
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"Para implementar idempotencia en arquitecturas cloud masivas, el estándar de facto es usar Redis mediante comandos atómicos `SETNX` con TTL, o delegarlo al perímetro en un API Gateway como Kong o AWS API Gateway. Esta aproximación aísla la base de datos relacional de tormentas de peticiones repetidas, resolviendo la deduplicación en menos de 1 milisegundo en memoria RAM.
  > 
  > Sin embargo, para StockPilot adoptamos conscientemente una implementación bare-metal mediante un filtro de servlets de Spring y persistencia relacional en PostgreSQL. Los trade-offs son claros: evitamos incorporar la complejidad operacional de un clúster de Redis (costos de clusterización, gestión de alta disponibilidad Sentinel, políticas de desalojo de memoria y riesgo de pérdida de datos por asincronía de replicación). El costo en base de datos es mínimo gracias a un índice B-Tree sobre `idempotency_key` y la limpieza automática por TTL, logrando consistencia transaccional absoluta y reduciendo el TCO (Total Cost of Ownership)."*

---

#### Pilar 2: Concurrencia y Bloqueos de Recursos
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (PostgreSQL `SELECT ... FOR UPDATE`)*: El encargado de la estantería del almacén le pone físicamente un candado de acero a la caja del producto mientras verifica cuántas unidades quedan. Nadie puede abrir la caja hasta que el encargado termine de anotar en la libreta y retire el candado.
  * *Herramienta Industrial (Redis Redlock / ZooKeeper)*: Un tablero central de reservas ubicado en la recepción del edificio. Los vendedores corren a marcar con un marcador magnético quién se adjudica el permiso de compra antes de siquiera caminar hacia el almacén físico.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (Algoritmo Redlock sobre N nodos Redis)*: Para adquirir un bloqueo distribuido en ausencia de una base de datos central única, el cliente:
    1. Toma la marca de tiempo actual en milisegundos con reloj monotónico.
    2. Intenta adquirir el bloqueo en $N$ instancias independientes de Redis (típicamente 5) secuencialmente, usando `SET resource_name my_random_value NX PX 30000`.
    3. Si logra adquirir el bloqueo en una mayoría estricta ($\lfloor N/2 \rfloor + 1$, o sea 3 de 5 nodos) y el tiempo total transcurrido es menor a la validez del bloqueo, el lock se considera concedido.
    *El Peligro Científico (Kleppmann vs. Antirez)*: Martin Kleppmann demostró que si la JVM que tiene el lock sufre una pausa prolongada de Garbage Collection (*Stop-The-World*) o un retraso en la red, el tiempo del lock en Redis expira; otro cliente adquiere el lock, y cuando el primer cliente despierta, escribe creyendo falsamente que aún es dueño del lock, corrompiendo los datos a menos que se implementen *fencing tokens* monótonos crecientes.
  * *Por qué en StockPilot usamos `SELECT ... FOR UPDATE`*: En StockPilot, el inventario reside en un único motor PostgreSQL. Los bloqueos de tupla (*tuple locks*) de PostgreSQL son gestionados por el kernel transaccional mediante el bitmask `HEAP_XMAX_EXCL_LOCK` en el encabezado físico de la tupla en disco. No hay riesgo de reloj desincronizado, no hay riesgo de fallos por pausas de GC y si la conexión muere, PostgreSQL libera el lock automáticamente al abortar la transacción.
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"Los bloqueos distribuidos (como Redis Redlock o Zookeeper ephemeral nodes) son indispensables cuando los recursos a proteger abarcan servicios heterogéneos o bases de datos no relacionales particionadas. No obstante, introducen complejidades severas respecto a la sincronización de relojes NTP, expiración prematura por pausas de Stop-the-World en la JVM y riesgos de consistencia demostrados en la literatura científica.
  > 
  > En StockPilot optamos por la solidez del bloqueo pesimista nativo de PostgreSQL (`PESSIMISTIC_WRITE`). Al ejecutarse en el mismo motor relacional donde residen las existencias, la adquisición del bloqueo y la mutación del saldo ocurren dentro de la misma transacción ACID sin saltos de red adicionales. Prevenimos interbloqueos (*deadlocks*) en la memoria de la aplicación ordenando deterministamente los IDs de productos antes del bloqueo (`productId ASC`), y protegemos el pool de conexiones con un timeout rígido de 3,000 ms."*

---

#### Pilar 3: Auditoría e Inmutabilidad de Datos
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (PostgreSQL Append-Only + `@Immutable`)*: El libro de actas contable de una empresa empastado en cuero con hojas numeradas y tinta indeleble. Está prohibido por ley usar goma de borrar o corrector líquido; si cometiste un error contable, debes escribir en la siguiente línea un asiento de ajuste compensatorio.
  * *Herramienta Industrial (Amazon QLDB / Immudb / Merkle Trees)*: Una cadena de bloques (*blockchain*) privada o base de datos criptográfica donde cada página de datos lleva estampado un sello digital criptográfico (hash SHA-256) que depende matemáticamente del sello de la página anterior. Si alguien altera un solo bit en el disco magnético, la cadena matemática se rompe y el sistema emite una alarma criptográfica mundial.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (Bases de datos criptográficas como Immudb o Amazon QLDB)*: Cada transacción genera un registro de auditoría que se inserta en una estructura de datos de árbol de Merkle (*Merkle Tree*). El cliente puede solicitar en cualquier momento una prueba de inclusión (*Proof of Inclusion*) o de consistencia (*Consistency Proof*): un conjunto logarítmico de hashes intermedios que se pueden verificar contra la raíz criptográfica (*Root Hash*) publicada en un ledger público o certificado de confianza.
  * *Por qué en StockPilot usamos `@Immutable` y Append-Only*: En el 99% de las auditorías de cumplimiento normativo (como Ley Sarbanes-Oxley SOX en finanzas o ISO/IEC 27001 en seguridad de la información), no se requiere la complejidad de un árbol de Merkle ni pagar los costos exorbitantes de QLDB. Basta con demostrar técnicamente que:
    1. A nivel de aplicación, la entidad no expone métodos `setter` ni mutadores y Hibernate la marca con `@Immutable`, arrojando una excepción inmediata si el código intenta un `UPDATE`.
    2. A nivel de base de datos, no existen comandos `UPDATE` o `DELETE` autorizados para el rol de aplicación sobre `inventory_ledger`.
    3. La tabla no posee columna `updated_at`, garantizando que cada registro representa un evento histórico inmutable en la línea de tiempo.
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"Para la inmutabilidad de datos en sectores hiperregulados, existen motores especializados como Immudb o Amazon QLDB que garantizan verificabilidad criptográfica basada en Merkle Trees. Sin embargo, desacoplar el libro de auditoría de la base de datos operativa exige implementar transacciones distribuidas o patrones Outbox para asegurar que el movimiento de inventario y el registro de auditoría queden asentados simultáneamente sin pérdidas por fallas de red.
  > 
  > En StockPilot implementamos un Ledger Inmutable nativo dentro de PostgreSQL. Al cohabitar en el mismo esquema relacional, el incremento de inventario reservado y el asiento contable en `inventory_ledger` ocurren bajo la misma transacción JDBC atómica. La inmutabilidad se blinda en profundidad mediante la directiva `@Immutable` de Hibernate que desactiva el dirty checking en memoria RAM, y la ausencia absoluta de rutas de actualización en la capa de datos."*

---

#### Pilar 4: Cálculo de Disponibilidad y Estado de Stock
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (PostgreSQL 16 Generated Column)*: Una balanza de precisión mecánica con dos platos y una aguja indicadora física. En un plato está el inventario total y en el otro las unidades reservadas; la aguja marca automáticamente el saldo disponible por la pura ley física de gravedad. Nadie tiene que hacer cálculos mentales: miras la aguja grabada en la escala y ves la verdad absoluta.
  * *Herramienta Industrial (Redis Hashes `HINCRBY`)*: Una pizarra luminosa digital en la pared de la tienda que muestra los números en luces LED. Se actualiza a la velocidad de la luz en microsegundos, pero si hay un corte de luz o se descalibra la señal inalámbrica con la bodega, el letrero podría marcar 10 unidades cuando en la bodega física ya no queda ninguna.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (In-Memory Data Grids como Redis Hashes o Hazelcast)*: Las lecturas de disponibilidad se resuelven en memoria RAM evitando el cuello de botella del disco:
    ```bash
    # En Redis: Decrementar el stock disponible en RAM
    HINCRBY product:1001 available -5
    ```
    La operación toma **sub-milisegundos** y atiende cientos de miles de peticiones concurrentes. Sin embargo, para persistir los cambios a disco se requiere un proceso asíncrono en segundo plano (*Write-Behind*) que envía los deltas a la base de datos relacional. Si el nodo Redis sufre un fallo de hardware antes de que el worker vacíe los datos a disco, los datos se pierden o se desincronizan, provocando sobreventas.
  * *Por qué en StockPilot usamos `available_qty STORED` en PostgreSQL 16*: La columna generada `available_qty INT GENERATED ALWAYS AS (total_qty - reserved_qty) STORED` se calcula y almacena físicamente en la página de disco de PostgreSQL durante la misma escritura que muta `reserved_qty`.
    - Elimina cualquier posibilidad de que la memoria de Java difiera del disco de PostgreSQL.
    - Las consultas `WHERE available_qty >= :qty` utilizan directamente los índices B-Tree del motor sin evaluar expresiones en tiempo de ejecución.
    - Cero líneas de código propensas a errores humanos en Java (como olvidar restar una variable).
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"En plataformas de comercio electrónico de escala planetaria como Shopify o Amazon, la verificación de stock se descarga en memorias intermedias distribuidas ultrarrápidas como clústeres de Redis o Hazelcast para proteger el motor relacional de millones de lecturas por segundo. El costo asumido en esas arquitecturas es la consistencia eventual y la necesidad de mecanismos de compensación cuando la memoria y el almacenamiento divergen.
  > 
  > En el contexto B2B de StockPilot, donde los pedidos comprometen sumas millonarias y penalizaciones contractuales severas por quiebres de stock, la consistencia fuerte es innegociable. Delegamos el cálculo a las columnas generadas almacenadas (`STORED`) de PostgreSQL 16. Esto garantiza que la regla matemática `available = total - reserved` sea un invariante físico a nivel de almacenamiento atómico, con cero sobrecarga en la JVM y cero riesgo de lecturas sucias entre capas."*

---

#### Pilar 5: Procesamiento Asíncrono, Eventos y Colas
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (Transacciones ACID Locales)*: Vas a la ventanilla del banco, entregas el cheque, el cajero te entrega el fajo de billetes en la mano, estampa el timbre de pagado en tu documento y te vas con el dinero asegurado en el bolsillo antes de cruzar la puerta.
  * *Herramienta Industrial (Apache Kafka / RabbitMQ / Transactional Outbox)*: Llegas a una oficina de trámites masivos y dejas un formulario en un buzón de correspondencia de entrada. Un conserje recoge las cartas cada hora, las sube a una furgoneta y las reparte a tres departamentos distintos que procesarán tu solicitud en las próximas 48 horas mientras tú esperas un correo electrónico de confirmación.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (Transactional Outbox Pattern + Debezium CDC + Kafka)*: Cuando una operación muta datos y debe notificar a otros sistemas:
    1. La aplicación guarda la orden y escribe un evento en una tabla auxiliar local llamada `outbox_table` dentro de la misma transacción relacional.
    2. **Debezium** (un conector de Captura de Datos de Cambio o *Change Data Capture - CDC*) lee continuamente el Write-Ahead Log (WAL) binario de PostgreSQL en disco.
    3. Debezium extrae el evento y lo publica en un *topic* particionado de **Apache Kafka**.
    4. Múltiples microservicios consumidores leen del topic procesando inventario, facturación y notificaciones de forma asíncrona.
  * *Por qué en StockPilot usamos transacciones ACID locales síncronas*:
    - En el Slice 3, el pedido y la reserva de existencias pertenecen a dominios fuertemente cohesionados dentro del mismo monolito modular.
    - El tiempo de respuesta de la transacción ACID completa en PostgreSQL (validación de orden, locking pesimista ordenado de 5 SKUs, creación de reservas e inserción en el ledger) toma **menos de 8 milisegundos**.
    - No hay riesgo de mensajes huérfanos, ni de consumidores de Kafka lentos (*consumer lag*), ni necesidad de orquestadores de flujos complejos como Temporal.io o Camunda para revertir transacciones con compensaciones (*Sagas*).
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"El patrón Transactional Outbox con Debezium y Apache Kafka es la solución de libro para desacoplar sistemas distribuidos y garantizar entrega al menos una vez (*at-least-once delivery*) sin caer en el antipadrón del Two-Phase Commit (2PC). Sin embargo, adoptarlo prematuramente en un monolito modular introduce una complejidad accidental abrumadora: infraestructura de Kafka/Zookeeper/KRaft, retrasos de serialización Avro/Schema Registry y la pérdida de la confirmación inmediata para el cliente.
  > 
  > En StockPilot aplicamos el principio de Local Consistency First: mientras los límites del Bounded Context convivan en el mismo despliegue, ejecutamos la reserva bajo una transacción ACID unificada en PostgreSQL. Obtenemos tiempos de respuesta sub-10 ms, atomicidad garantizada por el motor y una arquitectura radicalmente más económica y fácil de operar. Si en el futuro un módulo requiere desacoplamiento por volumen extremo, el diseño de servicios de dominio limpios permitirá intercalar un publicador de eventos hacia un broker sin alterar la lógica de negocio."*

---

#### Pilar 6: Versionado y Migración de Esquemas de Base de Datos
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (Flyway SQL Nativo)*: Un cuaderno de planos de ingeniería civil donde cada página (`V1`, `V2`, `V3`) está dibujada a mano con tinta indeleble con las medidas milimétricas exactas de cada viga de acero. Cualquier maestro constructor que tome el cuaderno ejecutará la obra exactamente igual.
  * *Herramienta Industrial (Liquibase Declarativo / Bytebase)*: Un software de arquitectura asistida por computadora (CAD) que te permite diseñar la casa en modelos abstractos tridimensionales, y el software intenta calcular automáticamente qué tornillos y vigas usar según el país donde vayas a construir, ofreciendo un botón de "deshacer" automático si una pared queda torcida.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (Liquibase con ChangeSets XML/YAML o Bytebase)*: Los cambios no se redactan en SQL puro del motor sino en archivos de abstracción genéricos:
    ```xml
    <changeSet id="3" author="victor">
        <createTable tableName="orders">
            <column name="id" type="bigint" autoIncrement="true"/>
        </createTable>
        <rollback>
            <dropTable tableName="orders"/>
        </rollback>
    </changeSet>
    ```
    Herramientas empresariales avanzadas como **Bytebase** o **Liquibase Pro** se integran con utilidades de bajo nivel como `pg_repack` u *Online DDL* para reescribir tablas gigantes en producción creando una tabla copia en segundo plano, copiando los datos, replicando los cambios vía triggers y haciendo un `SWAP` atómico de metadatos en microsegundos sin bloquear lecturas ni escrituras.
  * *Por qué en StockPilot usamos Flyway Community con SQL puro*:
    - Flyway ejecuta scripts SQL nativos (`V1__...sql`, `V2__...sql`). Esto nos permite usar características avanzadas y exclusivas de PostgreSQL 16 (como `GENERATED ALWAYS AS ... STORED`, índices parciales y tipos de bloqueo específicos) sin que una capa de abstracción intermedia los censure o traduzca mal.
    - Cero magia: los scripts SQL que se prueban en Testcontainers son exactamente los mismos que se ejecutan en producción.
    - Registro de auditoría transparente y consultable en la tabla `flyway_schema_history` con checksums SHA-256 para evitar alteraciones accidentales.
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"Para gestión de cambios de esquema en organizaciones con cientos de desarrolladores y bases de datos heterogéneas (Oracle, SQL Server, PostgreSQL), suites como Liquibase o plataformas GitOps como Bytebase ofrecen capacidades de gobernanza excepcionales, incluyendo rollbacks automáticos declarativos y reescritura de esquemas en caliente sin bloqueos.
  > 
  > Para StockPilot elegimos Flyway Community con migraciones SQL directas. Nuestra prioridad arquitectónica es exprimir al máximo las capacidades nativas de PostgreSQL 16 sin que un dialecto abstracto limite el uso de características de última generación (como columnas generadas o sintaxis de constraints específicas). El versionado inmutable mediante hashes en `flyway_schema_history` integrado en nuestro pipeline de CI/CD asegura que ninguna discrepancia de esquema pueda infiltrarse entre entornos de desarrollo, pruebas y producción."*

---

#### Pilar 7: Expiración Temporal de Reservas y TTL
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (Spring `@Scheduled` Batch Cleaner)*: El recepcionista de un hotel que todos los días a las 12:00 del mediodía en punto camina por los pasillos con su lista y revisa qué habitaciones tenían check-out vencido, liberándolas todas en un solo recorrido.
  * *Herramienta Industrial (RabbitMQ DLX / Redis ZSET)*: Un reloj despertador digital individual con bomba de tiempo colocado en la puerta de cada habitación. En el segundo exacto en que se cumplen las 24 horas, el despertador suena y expulsa mecánicamente la llave a la papelera sin esperar a que nadie camine por el pasillo.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (Dead-Letter Exchanges en RabbitMQ o ZSET en Redis)*:
    1. Al crearse la reserva, se publica un mensaje en una cola de RabbitMQ configurada con un `x-message-ttl` de 86,400,000 ms (24 horas) y sin consumidores directos.
    2. Cuando el mensaje expira físicamente en la memoria de RabbitMQ, el broker lo redirige automáticamente a un *Dead-Letter Exchange* (DLX).
    3. Una cola enlazada al DLX es leída por un servicio de cancelación que recibe el evento en el milisegundo exacto de la expiración y revierte la reserva en base de datos.
    *Alternativa en Redis*: Un Sorted Set (`ZSET`) donde la clave es el ID de la reserva y el `score` es el timestamp UNIX en milisegundos de expiración. Un worker consulta `ZRANGEBYSCORE key -inf current_timestamp` para obtener únicamente las reservas vencidas en microsegundos.
  * *Por qué en StockPilot usamos un proceso periódico `@Scheduled`*:
    - En reservas de inventario B2B, los tiempos de vigencia son típicamente de 24 a 48 horas. No existe necesidad de negocio de liberar el stock con precisión de milisegundos (a diferencia de la venta de entradas a recitales).
    - Una consulta de actualización por lotes en PostgreSQL:
      ```sql
      UPDATE stock_reservation SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at < now();
      ```
      resuelve cientos de expiraciones en una sola transacción JDBC ultrarrápida, reduciendo drásticamente el tráfico de red y eliminando la dependencia de un cluster de mensajería externo.
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"Cuando un modelo de negocio B2C hipercompetitivo (ej. ticketing masivo o venta flash de aerolíneas) exige liberar inventario en el milisegundo exacto en que expira un timer de 10 minutos, mecanismos orientados a eventos como Dead-Letter Exchanges (DLX) en RabbitMQ o Redis Sorted Sets son la elección natural porque evitan barridos periódicos sobre tablas masivas.
  > 
  > Sin embargo, en el aprovisionamiento B2B de StockPilot, las ventanas de reserva son de días hábiles. Implementar una infraestructura de colas solo para gestionar TTLs es una complejidad innecesaria. Un trabajo programado en Spring con `@Scheduled` ejecutando un barrido en lote sobre un índice B-Tree en `(status, expires_at)` permite depurar y asentar las compensaciones en el ledger de forma atómica y predecible, consumiendo recursos despreciables de la base de datos."*

---

#### Pilar 8: Observabilidad, Trazabilidad Distribuida y Métricas
* **Capa 1: La Intuición / Analogía del Mundo Real**:
  * *Bare-Metal (Micrometer + Prometheus Actuator)*: El tablero analógico del velocímetro, tacómetro y temperatura del motor de un camión de transporte. Las agujas indican en tiempo real cómo está funcionando el vehículo para que el conductor las vea de un vistazo en la cabina.
  * *Herramienta Industrial (OpenTelemetry + Jaeger / Datadog APM)*: Un enjambre de cámaras de alta velocidad y sensores GPS telemétricos instalados en cada tornillo, neumático y pistón del camión que transmiten continuamente telemetría satelital a un centro de control en la nube en Houston, permitiendo reconstruir en 3D la trayectoria exacta de cada partícula de combustible.
* **Capa 2: La Mecánica Exacta (De la RAM al Disco y por la Red)**:
  * *En la industria (OpenTelemetry SDK & OTLP Collector)*: Cada método y llamada HTTP o JDBC genera un *Span* con identificador de traza de 128 bits (`trace_id`) y de tramo de 64 bits (`span_id`).
    - El SDK de OTel en la JVM instrumenta el bytecode en tiempo de ejecución o compila trazas en memoria.
    - Los spans se acumulan en un buffer circular y un hilo exportador los envía periódicamente mediante protocolo binario gRPC (puerto 4317) a un **OpenTelemetry Collector**.
    - El colector procesa, muestrea (*sampling*) y despacha las trazas a motores de búsqueda distribuidos como **Jaeger**, **Grafana Tempo** o **Datadog APM**, permitiendo ver cascadas de latencia visuales entre decenas de microservicios.
  * *Por qué en StockPilot usamos Micrometer + Prometheus*:
    - Para un monolito modular, la correlación de trazas se resuelve nativamente inyectando el `traceId` en el contexto de diagnóstico mapeado (MDC) del registrador de logs (SLF4J/Logback).
    - Micrometer está embebido en el núcleo de Spring Boot Actuator, exponiendo métricas estándar en `/actuator/prometheus` (latencia HTTP percentiles p95/p99, uso de memoria heap, conexiones activas de HikariCP y tiempos de ejecución de consultas JDBC) con una huella de memoria prácticamente nula y sin agentes externos residentes.
* **Capa 3: La Respuesta de Nivel Solutions Architect**:
  > *"OpenTelemetry se ha consolidado como el estándar unificado de la industria para observabilidad moderna, siendo imprescindible en topologías de microservicios donde una única petición de usuario viaja a través de múltiples redes y lenguajes.
  > 
  > No obstante, en un monolito modular de alto rendimiento como StockPilot, instrumentar OTel con exportación constante de spans gRPC genera una sobrecarga no despreciable de CPU, memoria y costos de almacenamiento de telemetría. Nuestra estrategia se basa en métricas dimensionales con Micrometer y Prometheus, combinadas con logs estructurados en formato JSON enriquecidos mediante MDC (Mapped Diagnostic Context). Obtenemos visibilidad exhaustiva de saturación de pools de HikariCP, percentiles de latencia de endpoints y trazabilidad de errores con una fracción del costo operativo."*

---

[⬅️ Módulo 4: Slice 3](04-slice-3-pedidos-idempotencia-concurrencia.md) | [Índice Principal](../MASTER_STUDY_GUIDE.md) | [Siguiente Módulo: Mapeo Curricular ➡️](06-mapeo-curricular-soberania-academica.md)
