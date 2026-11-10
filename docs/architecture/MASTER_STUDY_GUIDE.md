# StockPilot — Guía Maestra de Estudio de Arquitectura e Ingeniería (Master Study Guide)

> **Documento Vivo de Soberanía Técnica — Slices 1, 2 y 3**  
> **Autor**: Víctor Venegas — Lead Full-Stack Engineer & Software Architect  
> **Propósito**: Dominio absoluto de cada decisión de diseño, línea de código, anotación de framework y comando SQL del sistema. Sin jerga circular, con rigor de ingeniería de sistemas de misión crítica.

---

## 🧭 Glosario de Primeros Principios (Fundamentos sin Jerga)

Para que este documento sea 100% autocontenido, antes de entrar a las capas desglosamos los términos fundamentales del ecosistema:

* **JVM (Java Virtual Machine / Máquina Virtual de Java)**: Programa ejecutable en C++ instalado en tu sistema operativo que toma el código compilado de Java (archivos `.class` con *bytecode*) y lo traduce a instrucciones binarias nativas que la CPU de tu máquina entiende.
* **Memoria Heap vs. Stack**:
  * *Stack (Pila)*: Memoria ultrarrápida donde vive el hilo de ejecución actual, sus variables locales primitivas (ej. `int i = 5`) y las referencias a los objetos. Se libera automáticamente al salir del método.
  * *Heap (Montículo)*: Gran espacio de memoria compartida donde se instancian los objetos pesados creados con `new` (como `new Product(...)`). Es gestionado por el Garbage Collector (limpiador automático de memoria).
* **Hilo de Ejecución (Thread)**: Secuencia más pequeña de instrucciones que el procesador ejecuta. En Spring Boot tradicional, cada petición HTTP entrante es atendida por un hilo independiente proveniente de una piscina de hilos (*Thread Pool* del servidor Tomcat).
* **Reflection (Reflexión de Java)**: Capacidad que tiene Java para inspeccionar en tiempo de ejecución sus propias clases, métodos y atributos privados, permitiendo a frameworks como Spring y Hibernate leer campos privados (`private Long id`) e invocar métodos sin que el desarrollador escriba código explícito para ello.
* **Proxy Dinámico (Dynamic Proxy)**: Objeto intermediario invisible creado en memoria por Spring que envuelve a tu clase real (como un cascarón). Cuando alguien llama a un método de tu servicio, primero entra al proxy (que abre la transacción o verifica permisos) y luego el proxy llama a tu código real.
* **AOP (Aspect-Oriented Programming / Programación Orientada a Aspectos)**: Técnica que permite ejecutar lógica transversal (como abrir/cerrar transacciones o medir tiempos) antes o después de que se ejecute un método, sin ensuciar la lógica de negocio con código repetitivo. Spring usa Proxies Dinámicos para lograr AOP.
* **Pool de Conexiones (HikariCP)**: Colección de conexiones TCP abiertas previamente hacia la base de datos PostgreSQL y mantenidas vivas en la RAM. En lugar de gastar 50 milisegundos en abrir y cerrar una conexión TCP en cada petición HTTP, el hilo de Spring "pide prestada" una conexión a HikariCP, la usa en 5 milisegundos y la devuelve a la piscina.
* **Transacción ACID**: Grupo de operaciones en la base de datos que se comportan como una sola:
  * *Atomicidad*: O se ejecutan todas las operaciones o ninguna (si algo falla, se revierte todo con un `ROLLBACK`).
  * *Consistencia*: La base de datos pasa de un estado válido a otro, sin romper restricciones de negocio (constraints).
  * *Aislamiento (Isolation)*: Dos transacciones simultáneas no ven los datos a medio guardar de la otra.
  * *Durabilidad*: Una vez hecho el `COMMIT`, los datos quedan guardados en disco magnético o SSD aunque se corte la energía eléctrica inmediatamente después.
* **MVCC (Multi-Version Concurrency Control)**: Mecanismo de PostgreSQL mediante el cual, cuando una fila es actualizada, no se sobrescribe físicamente en el disco de inmediato; en su lugar, PostgreSQL escribe una nueva versión de la fila. Esto permite que los hilos que leen datos no bloqueen a los hilos que escriben datos ("lectores nunca bloquean a escritores").
* **Flyway**: Herramienta de software que ejecuta scripts SQL versionados (`V1__...`, `V2__...`) en orden secuencial al arrancar la aplicación, registrando en una tabla de auditoría (`flyway_schema_history`) qué migraciones ya fueron aplicadas para que la estructura de la base de datos sea idéntica en cualquier máquina o servidor.

---

## 🧩 Módulo 1: Anatomía Básica y Persistencia (Desarmando el Misterio)

```mermaid
graph LR
    subgraph "Capas de Ejecución"
        JAVA["☕ Java 21 (Lenguaje & Tipos)<br/>Records, Sintaxis, Reglas Fuertes"]
        SPRING["🌱 Spring Boot 3.4 (Orquestador)<br/>Inyección Dependencias, Web, AOP"]
        HIBERNATE["🏛️ Hibernate 6 (Traductor ORM)<br/>Java Object <==> PostgreSQL Row"]
        PG["🐘 PostgreSQL 16 (Motor de Datos)<br/>Tablas, Locks, Storage, ACID"]
    end
    JAVA --> SPRING --> HIBERNATE --> PG
```

### 1.1 ¿Qué es realmente Java frente a Spring y frente a Hibernate?
Para defender esta arquitectura, debes tener cristalina la frontera entre las tres tecnologías:

1. **Java (El Lenguaje de Programación y Tipos)**: Es la gramática, las palabras y las estructuras. Define que una clase tiene campos, que un `record` es inmutable, y que un `BigDecimal` maneja precisión monetaria exacta. Java no sabe qué es la web, no sabe qué es HTTP y no sabe qué es una base de datos relacional.
2. **Spring Boot (El Orquestador y Contenedor de Inversión de Control)**: Es el director de escena. Se encarga de levantar el servidor web embebido (Tomcat en el puerto 8080), escuchar las peticiones HTTP que llegan por la red, instanciar los controladores y servicios, e inyectar las dependencias (`Dependency Injection`) para que tú no tengas que hacer `new Service()` a mano.
3. **Hibernate / JPA (El Traductor Objeto-Relacional u ORM)**: JPA (*Jakarta Persistence API*) es la norma escrita en un papel (la especificación/interfaz). Hibernate es el motor real (la implementación). Hibernate toma un objeto en la memoria RAM de Java (como `new Product(...)`) y genera dinámicamente la sentencia SQL correspondiente (`INSERT INTO product...`) para enviarla por el cable de red a PostgreSQL, y viceversa: toma los bytes que devuelve PostgreSQL y los convierte en objetos Java.

---

### 1.2 ¿Qué es exactamente el símbolo `@` (Anotaciones de Java)?

#### Capa 1: La Intuición
Imagina que vas a enviar un paquete por encomienda. La caja contiene tu producto (el código Java). Pegado en la tapa de la caja, colocas un **Post-it adhesivo amarillo** que dice: *"FRÁGIL - MANTENER EN LUGAR FRESCO"*. El Post-it no hace que la caja se enfríe por sí misma; es una **etiqueta informativa** para que el operador logístico que la manipule tome medidas especiales.

#### Capa 2: La Mecánica Exacta (De la RAM al Disco)
Una anotación `@` no es código ejecutable. Si compilas una clase con `@Entity`, el procesador no ejecuta ninguna instrucción extra al momento de instanciarla.
Lo que ocurre en la RAM es lo siguiente:
1. El compilador de Java (`javac`) almacena el nombre y parámetros de la anotación dentro del archivo `.class` como **metadatos de bytecode**.
2. Cuando Spring Boot o Hibernate arrancan, utilizan **Reflection (Reflexión)** para escanear las clases en memoria (`Class.forName("com.stockpilot.catalog.domain.Product")`).
3. Hibernate llama a `clazz.isAnnotationPresent(Entity.class)`. Si devuelve `true`, Hibernate crea un modelo interno en memoria llamado `PersistentClass` y registra esa clase en su catálogo de mapeo relacional.
4. Si nadie lee la anotación mediante Reflexión, la anotación es simplemente texto inerte.

#### Capa 3: La Respuesta de Solutions Architect
> *"Las anotaciones en Java no contienen lógica de control de flujo imperativa; son metadatos declarativos procesados en tiempo de arranque mediante el API de Reflexión. En StockPilot las utilizamos para desacoplar el modelo de dominio de las librerías externas: la clase declara su intención semántica (ej. `@Entity`, `@Table`) y el framework construye proxies dinámicos y metamodelos relacionales sin que el código de negocio dependa directamente de sentencias SQL manuales."*

---

### 1.3 El Ciclo de Vida de Persistencia y el Costo Oculto del ORM

#### ¿Por qué no usamos SQL manual tipo `INSERT INTO` en Java?
Escribir consultas SQL a mano mediante JDBC clásico (`PreparedStatement`, `ResultSet.getString("name")`) obliga a escribir cientos de líneas repetitivas propensas a errores tipográficos y desalineaciones de tipos. Hibernate automatiza la hidratación de objetos (convertir filas de BD en grafos de objetos Java).

#### ¿Cuál es el costo oculto de usar un ORM (Trade-off)?
* **Dirty Checking (Verificación Sucia)**: Cada vez que lees una entidad en una transacción abierta, Hibernate guarda en la RAM una copia idéntica (*snapshot*). Al hacer `commit`, compara campo por campo el objeto actual con el snapshot para decidir si envía un `UPDATE`. Esto consume memoria Heap y ciclos de CPU.
* **El Problema de Consulta N+1**: Si cargas 20 productos y cada producto tiene una categoría en modo perezoso (*Lazy*), al intentar leer el nombre de la categoría en un bucle, Hibernate ejecutará 1 consulta para los productos + 20 consultas adicionales para las categorías (21 consultas a la base de datos en total).  
  *Cómo lo mitigamos*: En StockPilot utilizamos **`JOIN FETCH`** en `ProductRepository`, obligando a la base de datos a hacer un solo `INNER JOIN` en una única consulta SQL.

---

### 1.4 La Separación Sagrada: Entidad JPA (`@Entity`) vs. DTO (`record`)

```mermaid
graph TD
    subgraph "Exterior (HTTP / Red Insegura)"
        REQ["Cliente HTTP / Frontend<br/>JSON Payload"]
    end
    
    subgraph "Capa Web (Transporte)"
        DTO_IN["Java 21 Record (ProductCreateRequest)<br/>Inmutable + @Valid (@NotBlank, @Positive)"]
        CTRL["ProductController<br/>Recibe DTO, valida y entrega a Servicio"]
        DTO_OUT["Java 21 Record (ProductResponse)<br/>Solo campos públicos formateados"]
    end
    
    subgraph "Capa de Dominio y Persistencia"
        SRV["ProductService<br/>Aplica reglas de negocio (SKU único)"]
        ENT["Product (@Entity)<br/>Mapeo a tabla PostgreSQL, Version, Audit"]
        DB[("PostgreSQL 16")]
    end
    
    REQ -->|"POST JSON"| DTO_IN --> CTRL --> SRV
    SRV -->|"Muta estado de dominio"| ENT
    ENT <-->|"HikariCP / JDBC"| DB
    SRV -->|"Mapea a DTO seguro"| DTO_OUT --> CTRL -->|"201 Created JSON"| REQ
```

#### ¿Por qué exponer una entidad `@Entity` en un Controller es considerado una mala práctica grave?
1. **Fuga de Abstracción de Base de Datos**: Expone nombres de columnas internas, claves foráneas y estructuras relacionales directamente en contratos públicos de la API.
2. **Vulnerabilidad de Asignación Masiva (Mass Assignment / Over-posting)**: Si expones la entidad y usas enlace automático de datos de Spring, un cliente malicioso puede enviar un JSON con `{"id": 1, "version": 999, "active": false}` y Spring inyectará esos valores en la entidad, permitiendo alterar campos críticos sin autorización.
3. **Serialización Cíclica y Fallos de Jackson**: Si la entidad tiene relaciones `@ManyToOne` o `@OneToMany`, Jackson (el serializador JSON) intentará serializar el hijo, luego el hijo al padre, luego el padre al hijo, causando un desbordamiento de pila fatal (`StackOverflowError`) o una excepción de carga perezosa (`LazyInitializationException`) al ejecutarse fuera de la transacción.
4. **Solución en StockPilot**: Las entidades son confinadas en la capa de persistencia y dominio. Toda entrada y salida hacia la red es un **Java 21 `record` inmutable**, donde cada campo está explícitamente seleccionado y validado con `jakarta.validation`.

---

### 1.5 Desglose de Anotaciones de Persistencia (Sistema de 3 Capas)

#### A. `@Entity` y `@Table(name = "product")`
* **Capa 1 (Intuición)**: Es una tarjeta de registro de nacimiento. Le dice al sistema: *"Cada vez que veas un objeto de este tipo, trátalo como un habitante registrado de la ciudad; no es un objeto cualquiera de la memoria, tiene una casa propia en la base de datos llamada 'product'"*.
* **Capa 2 (Mecánica)**:
  * *Spring/Hibernate*: Al escanear el classpath, Hibernate reconoce la clase y construye un objeto `EntityBinding`. Valida contra la base de datos que la tabla `product` exista (gracias a `spring.jpa.hibernate.ddl-auto: validate`).
  * *Red/PostgreSQL*: En cada consulta, Hibernate traduce cualquier instrucción HQL/JPQL (`SELECT p FROM Product p`) a SQL nativo apuntando a la tabla declarada: `SELECT p.id, p.sku FROM product p`.
* **Capa 3 (Solución Architect)**:
  * *Entrevista*: *"Utilizamos `@Entity` para definir clases de dominio ricas mapeadas al estándar relacional JPA, y desacoplamos el nombre de la clase en Java del esquema relacional mediante `@Table(name = "...")` para aislar el modelo de posibles divergencias de nomenclatura en base de datos."*

#### B. `@Id` y `@GeneratedValue(strategy = GenerationType.IDENTITY)`
* **Capa 1 (Intuición)**: El número de cédula de identidad o pasaporte emitido por el Registro Civil. El ciudadano no inventa su propio número de carnet al nacer; el Estado se lo asigna correlativamente en ventanilla.
* **Capa 2 (Mecánica)**:
  * *Hibernate*: Sabe que este campo es la clave primaria (PK). `GenerationType.IDENTITY` le advierte a Hibernate que **no puede saber el ID del objeto antes de insertarlo**, por lo que se ve obligado a ejecutar inmediatamente la sentencia SQL `INSERT INTO product (...) VALUES (...) RETURNING id;`.
  * *PostgreSQL*: Al recibir el `INSERT`, el motor de base de datos lee su objeto secuencia interno creado por la columna `GENERATED BY DEFAULT AS IDENTITY`, incrementa el número atómicamente y lo almacena en la página de disco de la tabla.
* **Capa 3 (Solución Architect)**:
  * *Trade-off*: *"Elegimos `IDENTITY` nativo de PostgreSQL en lugar de secuencias preasignadas en memoria (`SEQUENCE` con pooling de IDs) porque garantiza total compatibilidad con scripts de migración externos de Flyway y previene 'huecos' de secuencia en sistemas transaccionales directos."*

#### C. `@Column(name = "unit_price", precision = 12, scale = 2)`
* **Capa 1 (Intuición)**: Un casillero bancario de tamaño calibrado. Si intentas meter un billete doblado o monedas fuera de medida, el casillero no cierra.
* **Capa 2 (Mecánica)**:
  * *Java*: El campo se tipa como `BigDecimal`, impidiendo los errores de redondeo de coma flotante de `double` o `float` (ej. `0.1 + 0.2 = 0.30000000000000004`).
  * *Hibernate/PostgreSQL*: Hibernate valida que en PostgreSQL la columna sea de tipo `NUMERIC(12, 2)` (hasta 10 dígitos enteros y 2 decimales exactos). En el cable de red (protocolo binario PostgreSQL), el valor viaja codificado en base-10000 exacta sin pérdida de centavos.
* **Capa 3 (Solución Architect)**:
  * *Trade-off*: *"Bajo ninguna circunstancia se utilizan tipos primitivos de coma flotante IEEE 754 (`double`/`float`) para montos monetarios o inventario físico. `BigDecimal` combinado con `NUMERIC(12, 2)` a nivel DDL garantiza exactitud financiera absoluta."*

#### D. `@Version` (Optimistic Locking / Bloqueo Optimista)
* **Capa 1 (Intuición)**: Un sello de cera en una carta con un número estampado (ej. "Sello #3"). Dos personas leen la carta al mismo tiempo. Quien responde primero rompe el sello y deja la carta con el "Sello #4". Cuando la segunda persona intenta mandar su respuesta asumiendo que la carta estaba en el "Sello #3", el cartero la rechaza porque el número ya cambió.
* **Capa 2 (Mecánica)**:
  * *Java/Hibernate*: Cada vez que se lee un producto, se carga su campo `version` (ej. `0`). Cuando ejecutas `product.setName("Nuevo Nombre")` y finaliza la transacción, Hibernate emite exactamente el siguiente SQL:
    ```sql
    UPDATE product 
    SET name = 'Nuevo Nombre', version = 1, updated_at = now() 
    WHERE id = 10 AND version = 0;
    ```
  * *PostgreSQL*: Si otra transacción ya había modificado ese registro milisegundos antes, la fila en disco ya tiene `version = 1`. Por ende, el `WHERE id = 10 AND version = 0` no encuentra ninguna fila que actualizar (afecta 0 filas).
  * *Hibernate*: Al detectar que el `UPDATE` afectó 0 filas, lanza de inmediato una excepción **`OptimisticLockException`**, impidiendo que se sobreescriban cambios ajenos en silencio.
* **Capa 3 (Solución Architect)**:
  * *Entrevista*: *"Implementamos `@Version` en el agregado `Product` para metadatos del catálogo (nombres, especificaciones técnicas, precios base) porque en el catálogo la contención de escritura es baja: dos administradores rara vez editan el mismo producto al mismo segundo. El bloqueo optimista no requiere locks a nivel de base de datos, lo que maximiza la concurrencia de lectura."*

---

## 🏗️ Módulo 2: Slice 1 — Catálogo, Monolito y CI/CD

### 2.1 ADR-0001 (Monolito Modular vs. Microservicios)

#### ¿Qué es un Bounded Context en la práctica?
Es un término de *Domain-Driven Design (DDD)* que define los límites de significado de un modelo. Por ejemplo:
* En el módulo `catalog`, un producto es una entidad comercial con descripción, categoría y ficha técnica.
* En el módulo `inventory`, al sistema no le importa la ficha técnica ni el color; solo le importa el código SKU, la bodega y cuántas unidades físicas existen en estantería.
* Mantener paquetes separados (`com.stockpilot.catalog`, `com.stockpilot.inventory`, `com.stockpilot.ordering`, `com.stockpilot.audit`) permite que cada contexto evolucione de forma independiente sin contaminar el código ajeno.

#### ¿Por qué los microservicios prematuros matan a una empresa antes de tiempo?
* **Transacciones Distribuidas**: Si separas `ordering` e `inventory` en dos microservicios con dos bases de datos distintas conectadas por red, no puedes usar una transacción ACID. Si la reserva de stock falla después de haber creado el pedido, tienes que implementar el patrón Saga (mensajería asíncrona, eventos compensatorios, colas de reintento).
* **Latencia de Red y Fallos Parciales**: Una llamada interna en memoria tarda menos de 1 microsegundo ($0.001 \text{ ms}$). Una llamada HTTP entre dos contenedores tarda entre 5 y 30 milisegundos, y la red puede fallar en cualquier momento (*fallacies of distributed computing*).
* **Decisión en StockPilot**: Un **Monolito Modular** mantiene la separación limpia de dominios dentro de un solo proceso, permitiendo transacciones ACID locales ultrarrápidas (< 10 ms), con la posibilidad de extraer cualquier módulo a un microservicio en el futuro si la escala del equipo lo requiriera.

---

### 2.2 Flyway y Versionado de Base de Datos

#### ¿Por qué no dejamos que Hibernate cree las tablas automáticamente (`ddl-auto=update`) en producción?
* `ddl-auto=update` es una funcionalidad para prototipos rápidos. En producción es destructivo:
  1. Si renombras un campo en Java, Hibernate no renombra la columna en la BD; crea una columna nueva y deja la antigua huérfana con datos desincronizados.
  2. No tiene trazabilidad histórica: es imposible saber quién aplicó qué cambio o recrear la base de datos exactamente como estaba hace 3 meses.
  3. No puede generar índices condicionales avanzados ni columnas generadas en PostgreSQL como `GENERATED ALWAYS AS ... STORED`.
* **Solución en StockPilot**: `ddl-auto: validate` (Hibernate solo verifica que la estructura coincida) y **Flyway** gestiona scripts SQL versionados (`V1__...`, `V2__...`). Todo cambio de esquema está en el control de versiones de Git y es auditable.

---

### 2.3 Pipeline CI/CD, Docker Multi-Stage y Seguridad en Contenedores

#### ¿Qué hace exactamente el Dockerfile Multi-Stage?
```mermaid
graph TD
    subgraph "Stage 1: Builder (Pesado ~500 MB)"
        JDK["eclipse-temurin:21-jdk-alpine<br/>Compilador javac, Gradle Wrapper, Fuentes"]
        BUILD["Ejecuta ./gradlew bootJar"]
        JAR["app.jar generado en /build/libs"]
        JDK --> BUILD --> JAR
    end
    
    subgraph "Stage 2: Runtime (Ligero ~150 MB)"
        JRE["eclipse-temurin:21-jre-alpine<br/>Solo Runtime de Java (sin compilador)"]
        USER["Crea usuario stockpilot (UID 1001)"]
        COPY["Copia SOLO app.jar desde Stage 1"]
        RUN["Ejecuta java -jar app.jar como USER 1001"]
        JRE --> USER --> COPY --> RUN
    end
    
    JAR -.->|"Transferencia selectiva de artefacto"| COPY
```

1. **Stage 1 (Builder)**: Utiliza una imagen con el JDK completo (compilador `javac`, librerías de compilación). Descarga dependencias y construye el archivo ejecutable `app.jar`.
2. **Stage 2 (Runtime)**: Descarta por completo el Stage 1 (código fuente, caché de Gradle y compiladores desaparecen). Inicia desde una imagen limpia con solo el JRE (mínimo ejecutable de Java). Copia únicamente el archivo `app.jar`.
3. **Beneficio**: Reduce la superficie de ataque y el tamaño de la imagen Docker de ~700 MB a menos de ~200 MB.

#### ¿Por qué el contenedor se corre con usuario `non-root (USER 1001)`?
Si un atacante logra explotar una vulnerabilidad en una librería de Java (como un *Remote Code Execution* tipo Log4Shell) y el contenedor corre como `root` (usuario administrador por defecto en Docker), el atacante tiene privilegios de superusuario dentro del contenedor, facilitando técnicas de escape hacia el sistema operativo host. Al correr como `USER 1001` sin privilegios de sudo, cualquier intento de escribir en el sistema de archivos del sistema o acceder a dispositivos del kernel es bloqueado por el kernel de Linux.

#### ¿Qué busca Trivy al escanear?
Trivy es un escáner estático de vulnerabilidades para contenedores y sistemas de archivos. Cruza las dependencias de Gradle (`build.gradle.kts`) y los paquetes del sistema operativo Alpine contra bases de datos públicas de vulnerabilidades (CVE - *Common Vulnerabilities and Exposures*). Si una librería de terceros tiene un fallo de seguridad crítico documentado, Trivy hace que el pipeline de GitHub Actions aborte la compilación con código de salida `exit-code: 1`.

---

## ⚡ Módulo 3: Slice 2 — Concurrencia, Bodegas y Ledger Inmutable

### 3.1 PostgreSQL 16 y Columnas Generadas (`available_qty STORED`)

#### ¿Por qué delegar una resta matemática a la base de datos en lugar de hacer `total - reserved` en Java?
* **El Problema del Drift (Desincronización)**: Si la lógica de disponibilidad vive en Java, cualquier administrador que ejecute un script SQL directo para ajustar el inventario físico, o cualquier consulta externa de BI (Business Intelligence), vería datos inconsistentes si no se recalcula manualmente en cada query.
* **Indexabilidad Inmediata**: Al definir la columna como `STORED`, PostgreSQL calcula el resultado al momento de insertar o actualizar y **lo almacena físicamente en el bloque de disco de la tabla**. Esto permite crear índices B-Tree estándar:
  ```sql
  CREATE INDEX idx_stock_level_available ON stock_level (warehouse_id, available_qty) WHERE available_qty > 0;
  ```
  La consulta para ver qué productos tienen stock libre en una bodega tarda **0.1 milisegundos**, ejecutando un *Index Scan* directo en disco sin calcular restas en la CPU para millones de filas.

#### Desglose Técnico en Hibernate:
```java
@Generated(event = {EventType.INSERT, EventType.UPDATE})
@Column(name = "available_qty", insertable = false, updatable = false)
private Integer availableQty;
```
* **`insertable = false, updatable = false`**: Le indica a Hibernate: *"Cuando generes las sentencias `INSERT INTO stock_level (...)` o `UPDATE stock_level SET ...`, NUNCA incluyas la columna `available_qty` en el SQL"*. Si Hibernate intentara incluirla, PostgreSQL arrojaría un error fatal porque una columna `GENERATED ALWAYS` rechaza cualquier valor externo.
* **`@Generated`**: Le indica a Hibernate: *"Inmediatamente después de que hagas un INSERT o UPDATE sobre esta entidad, ejecuta internamente un `RETURNING available_qty` para refrescar el atributo en la memoria RAM de Java con el valor exacto que la base de datos acaba de calcular"*.

---

### 3.2 ADR-0002: Mitigación de Sobreventa mediante Bloqueo Pesimista (`SELECT ... FOR UPDATE`)

#### ¿Qué es una Condición de Carrera (Race Condition)? — Simulación al Milisegundo
Imagina que queda **1 sola unidad** del SKU `PLC-SIEM-S71500` en la bodega `WH-SCL-CENTRAL`. Dos clientes corporativos (Comprador A y Comprador B) presionan "Confirmar Pedido" exactamente en el mismo instante.

```
TIEMPO (ms)   HILO A (Comprador A)                    HILO B (Comprador B)                    BASE DE DATOS (stock_level)
-------------------------------------------------------------------------------------------------------------------------
t=0           Lee: total=1, reserved=0 (disp=1)       --                                      Fila libre (available=1)
t=1           --                                      Lee: total=1, reserved=0 (disp=1)       Fila libre (available=1)
t=2           Calcula en Java: 1 >= 1 (OK)            Calcula en Java: 1 >= 1 (OK)            --
t=3           UPDATE reserved_qty = 1                 --                                      reserved=1, available=0
t=4           --                                      UPDATE reserved_qty = 2                 reserved=2, available=-1 💥
```
**Resultado Catastrófico**: Se reservaron 2 unidades habiendo solo 1 física en almacén. **Sobreventa consumada (Overselling)**.

---

#### ¿Cómo lo soluciona `SELECT ... FOR UPDATE` a nivel de PostgreSQL?

```mermaid
sequenceDiagram
    autonumber
    actor Hilo_A as Hilo A (Transacción 1)
    actor Hilo_B as Hilo B (Transacción 2)
    participant PG as PostgreSQL Engine (Kernel de Locks)
    participant Disk as Disco / Tabla stock_level

    Note over Hilo_A, Hilo_B: Queda 1 unidad disponible.<br/>Ambos hilos lanzan consulta simultánea.

    Hilo_A->>+PG: SELECT * FROM stock_level WHERE id=42 FOR UPDATE (timeout=3000ms)
    PG->>Disk: Bloquea tupla exclusiva (Exclusive Lock en cabecera de fila)
    PG-->>Hilo_A: Retorna tupla bloqueada ✅ (total=1, reserved=0)

    Hilo_B->>+PG: SELECT * FROM stock_level WHERE id=42 FOR UPDATE (timeout=3000ms)
    Note over Hilo_B, PG: 🛑 PostgreSQL encola al Hilo B en espera.<br/>El Hilo B se duerme esperando que Hilo A termine.

    Hilo_A->>PG: UPDATE stock_level SET reserved_qty = 1 WHERE id=42
    Hilo_A->>PG: INSERT INTO inventory_ledger (...)
    Hilo_A->>PG: COMMIT WORK ✅
    PG->>Disk: Escribe cambios en disco y WAL. Libera lock exclusivo de la fila.

    PG-->>-Hilo_B: Despierta al Hilo B y le entrega el lock exclusivo.
    Note over Hilo_B, PG: Hilo B lee el estado FRESCO post-commit de A:<br/>total=1, reserved=1 => available_qty = 0

    Note over Hilo_B: Hilo B detecta available_qty (0) < solicitado (1).<br/>Aborto limpio: Lanza InsufficientStockException.
    Hilo_B->>PG: ROLLBACK WORK ❌
    PG-->>-Hilo_B: Transacción revertida
```

#### ¿Por qué configuramos un timeout estricto de 3000 ms (`jakarta.persistence.lock.timeout`)?
Si una transacción adquiere un lock pesimista sobre un producto y por alguna anomalía (ej. lentitud de red o un proceso colgado) tarda más de la cuenta, todos los hilos que intenten reservar ese mismo producto se quedarán congelados esperando. Si se acumulan 20 hilos esperando indefinidamente, **agotan el pool de conexiones HikariCP** y tiran abajo el servidor completo. Con el timeout de 3000 ms, si en 3 segundos no se obtiene el lock, PostgreSQL aborta la espera y Spring lanza `PessimisticLockingFailureException`, la cual nuestro `GlobalExceptionHandler` captura y responde limpiamente al cliente con **`503 Service Unavailable`**, manteniendo el resto de la plataforma en pie.

#### ¿Qué es un Deadlock (Interbloqueo) y cómo se previene?
Un Deadlock ocurre cuando dos transacciones intentan bloquear múltiples recursos en orden inverso:
* Transacción 1: Bloquea el SKU A y luego intenta bloquear el SKU B.
* Transacción 2: Bloquea el SKU B y luego intenta bloquear el SKU A.
* **Resultado**: Ambas transacciones se quedan esperando mutuamente por siempre hasta que PostgreSQL detecta el ciclo y mata una de ellas forzosamente (`DeadlockException`).
* **Prevención en StockPilot**: **Ordenamiento determinista**. En Slice 3, cuando una orden reserve múltiples SKUs, la consulta siempre ordenará los bloqueos lexicográficamente:
  ```sql
  ORDER BY product_id ASC, warehouse_id ASC
  ```
  Al respetarse siempre este orden, es matemáticamente imposible que dos transacciones generen un ciclo cerrado de espera (se rompe la condición de espera circular de Coffman).

---

### 3.3 ADR-0006 e Inmutabilidad del Libro Mayor (`InventoryLedger`)

#### ¿Qué hace `@Immutable` a nivel de Hibernate?
```java
@Entity
@Table(name = "inventory_ledger")
@org.hibernate.annotations.Immutable
public class InventoryLedger { ... }
```
1. **Desactivación de Dirty Checking**: Le ordena a Hibernate que no cree una copia snapshot de la entidad en memoria RAM al cargarla. Esto ahorra hasta un 50% de memoria Heap en consultas de auditoría masivas.
2. **Prohibición de UPDATE**: Si algún programador intenta invocar un método o ejecutar un `save()` modificando atributos de una entrada existente del ledger, Hibernate arroja una excepción en tiempo de ejecución (`HibernateException: Entity is immutable`) y se niega a generar cualquier sentencia `UPDATE`.

#### ¿Por qué la tabla no tiene columna `updated_at` ni métodos setters?
Porque un libro mayor de contabilidad física representa un **hecho inmutable en la línea del tiempo**. En contabilidad real, si te equivocaste en un asiento contable, no borras la hoja con típex ni la arrancas del libro; generas un **nuevo asiento contable compensatorio**.
* Si ingresaron 10 unidades por error, se emite un nuevo registro de tipo `ADJUSTMENT` con delta `-10` y el motivo `"Corrección por error de digitación"`.
* Ambas líneas quedan grabadas con su fecha, hora y operador responsable.

#### Relación con Normativas SOX / ISO 27001 y Modelo STRIDE
* **Normativas SOX (Sarbanes-Oxley Act)**: Exigen que los registros de inventario que respaldan los estados financieros de una empresa pública o corporación no puedan ser manipulados de forma opaca por administradores de sistemas.
* **ISO/IEC 27001 (Control A.12.4 - Registro y Supervisión)**: Los eventos de modificación de activos críticos deben ser inalterables y conservarse para auditoría forense.
* **Mitigación STRIDE**:
  * **Tampering (Manipulación)**: Es imposible alterar registros pasados a través del software.
  * **Repudiation (No Repudio)**: Cada fila almacena el usuario exacto (`performedBy`), el motivo formal (`reason`) y la marca temporal de base de datos (`clock_timestamp()`).

---

## ⚡ Módulo 4: Slice 3 — Motor de Pedidos, Idempotencia HTTP y Concurrencia Extrema

```mermaid
graph TD
    REQ["Petición HTTP: POST /orders<br/>Header: Idempotency-Key (UUID)"]
    FILTER{"IdempotencyFilter<br/>¿Clave existe en PostgreSQL?"}
    REPLAY["Replay Inmediato<br/>Status y JSON cacheados<br/>Header: X-Idempotent-Replay: true"]
    IN_FLIGHT["Inserta centinela status=0 (IN_PROGRESS)<br/>Bloqueo de peticiones concurrentes"]
    ORDER_SRV["OrderService: valida DRAFT y calcula totales"]
    SORT["Ordenamiento determinista:<br/>items.sort(productId ASC)"]
    LOCK_DB["SELECT ... FOR UPDATE (3000ms)<br/>en stock_level"]
    RESERVE["Incrementa reserved_qty<br/>Crea stock_reservation: ACTIVE<br/>Asienta RESERVATION en ledger"]
    SAVE_RESP["Persiste status final en idempotency_record<br/>TTL de 24 horas"]

    REQ --> FILTER
    FILTER -- "Sí (Cache HIT)" --> REPLAY
    FILTER -- "No (Cache MISS)" --> IN_FLIGHT
    IN_FLIGHT --> ORDER_SRV --> SORT --> LOCK_DB --> RESERVE --> SAVE_RESP
```

---

### 4.1 Idempotencia HTTP y Filtros Servlet (`IdempotencyFilter` & `ContentCachingResponseWrapper`)

#### Capa 1: La Intuición / Analogía del Mundo Real
Imagina la ventanilla de recepción de encomiendas de una aduana internacional. Cuando entregas una caja, el funcionario estampa en el paquete un sello notarial con un código de barra único irrepetible (UUID v4). Si el camión de despacho sufre una avería en el camino y tú, presa de la impaciencia, vuelves al día siguiente al mostrador exigiendo respuesta con el mismo recibo en mano, el funcionario no te cobra un nuevo envío ni fabrica un segundo paquete idéntico; busca en su libro el código que ya tiene archivado, te entrega una copia exacta del recibo timbrado original y te dice: *"Aquí tiene su comprobante previo, su trámite ya fue procesado"*.

#### Capa 2: La Mecánica Exacta (De la RAM al Disco)
1. **Llegada y Validación Perimetral**: La petición HTTP (`POST /api/v1/orders` o `PUT /api/v1/orders/{id}/confirm`) entra a la JVM y es interceptada por `IdempotencyFilter`, el cual hereda de `OncePerRequestFilter`. El filtro busca la cabecera `Idempotency-Key`. Si no existe o no tiene formato de UUID v4 válido, aborta de inmediato con **HTTP 400 Bad Request** y un RFC 7807 `ProblemDetail`, sin gastar ciclos de CPU en controllers ni abrir transacciones JDBC.
2. **Detección de Concurrencia en Vuelo (*In-Flight Detection*)**: El filtro consulta la tabla `idempotency_record` en PostgreSQL por la clave UUID.
   - Si no existe: Inserta de inmediato una fila preliminar con `response_status = 0`, `response_body = 'IN_PROGRESS'` y `expires_at = now() + 24 hours`. Si dos peticiones idénticas llegan en el mismo nanosegundo, el índice `UNIQUE(idempotency_key)` de PostgreSQL hace que la segunda falle con `DataIntegrityViolationException`, retornando de inmediato **HTTP 409 Conflict** (*"A request with this Idempotency-Key is currently in progress"*), blindando al sistema contra carreras en la red.
3. **El Envoltorio de Memoria (`ContentCachingResponseWrapper`)**: En la arquitectura de Servlets tradicional, el flujo de respuesta HTTP (`ServletOutputStream`) se envía directamente por el socket TCP al cliente (*streaming*); una vez emitidos los bytes, no se pueden volver a leer en Java. Spring proporciona `ContentCachingResponseWrapper`, que intercepta la salida y copia cada byte JSON que el controlador escribe en un buffer interno en la memoria RAM (`ByteArrayOutputStream`).
4. **Persistencia Post-Ejecución**: Una vez que el `OrderController` retorna exitosamente (ej. HTTP 201 Created), el filtro toma el control de vuelta, extrae los bytes del buffer (`new String(wrappedResponse.getContentAsByteArray())`), actualiza la fila en PostgreSQL con el status real (`201`), el cuerpo JSON y persiste el registro. Finalmente, invoca `wrappedResponse.copyBodyToResponse()` para vaciar físicamente los bytes al cliente.
5. **Replay Inmediato (Cache HIT)**: Si el cliente reintenta la misma petición minutos después, el filtro encuentra la fila en PostgreSQL con `response_status > 0` y fecha válida. Escribe directamente el código HTTP y el cuerpo JSON en la respuesta, agrega la cabecera **`X-Idempotent-Replay: true`** y **cortocircuita la cadena de filtros**, retornando en menos de 1 milisegundo sin tocar controladores, sin consultar productos y sin bloquear stock.

#### Capa 3: La Respuesta de Nivel Solutions Architect
> *"En arquitecturas distribuidas y redes móviles corporativas rige el principio de entrega al menos una vez (At-Least-Once Delivery): los reintentos automáticos de clientes o proxys inversos (Nginx) ante caídas de conexión son inevitables. Para garantizar semántica de ejecución a lo sumo una vez (At-Most-Once Execution), implementamos un patrón de Idempotencia Perimetral a nivel de filtro Servlet respaldado por PostgreSQL.
>
> Descartamos Redis por dos factores de arquitectura: primero, para una plataforma B2B de suministros industriales con miles de órdenes diarias, PostgreSQL con un índice B-Tree sobre una columna UUID de 16 bytes resuelve la búsqueda en submilisegundos en almacenamiento NVMe, evitando introducir la sobrecarga operacional, costos de infraestructura y consistencia dual que acarrearía un cluster Redis. Segundo, al almacenar el código HTTP y el cuerpo de respuesta serializado con un TTL estricto de 24 horas, permitimos que clientes con redes degradadas reciban exactamente la misma respuesta original sin ejecutar una segunda transacción.
>
> Además, desacoplamos la registración del filtro mediante `FilterRegistrationBean` en `IdempotencyConfig.java` para proteger nuestros slice tests `@WebMvcTest`, impidiendo que las pruebas unitarias de capa web fallen por dependencias de persistencia JPA."*

---

### 4.2 Erradicación de Deadlocks por Ordenamiento Determinista (`productId ASC`)

#### Capa 1: La Intuición / Analogía del Mundo Real
Imagina un quirófano con dos cirujanos operando en mesas contiguas. Ambos necesitan simultáneamente dos instrumentos únicos: el bisturí (Instrumento 1) y el separador (Instrumento 2). Si el Cirujano A toma primero el bisturí y extiende la mano esperando el separador, mientras el Cirujano B toma el separador y espera el bisturí, ambos quedan congelados mirándose con las manos ocupadas: ninguno puede operar y ninguno soltará lo que ya tiene (interbloqueo mortal).
¿Cuál es la regla hospitalaria infalible que erradica este problema? La norma de protocolo: *"Todo cirujano que requiera múltiples instrumentos debe solicitar siempre primero el instrumento con el número de serie más bajo"*. Siguiendo esta regla, ambos intentarán tomar primero el bisturí (1). El que lo tome primero operará y luego tomará el separador (2); el segundo esperará con las manos vacías sin retener nada. Cero interbloqueos.

#### Capa 2: La Mecánica Exacta (De la RAM al Disco)
1. **Las 4 Condiciones de Edward G. Coffman (1971)**: En ciencias de la computación, un Deadlock sólo puede existir si se cumplen cuatro condiciones matemáticas de forma simultánea:
   - *Exclusión Mutua*: Los recursos no se comparten (bloqueo exclusivo `FOR UPDATE` sobre una fila).
   - *Retención y Espera*: Una transacción retiene un lock mientras solicita otro.
   - *No Expropiación*: La base de datos no puede quitarle un lock a una transacción activa a la fuerza sin matarla.
   - *Espera Circular*: Existe una cadena cerrada de transacciones donde $T_1$ espera a $T_2$ y $T_2$ espera a $T_1$.
2. **El Problema en PostgreSQL**: Cuando una orden B2B incluye el Producto 10 y el Producto 20, si la Orden A los bloquea en orden $[10 \rightarrow 20]$ y la Orden B en orden $[20 \rightarrow 10]$, en el kernel de PostgreSQL se genera un ciclo en el grafo de espera (*Wait-For Graph*). Tras cumplirse el parámetro `deadlock_timeout` (por defecto 1000 ms), el daemon detector de interbloqueos de PostgreSQL despierta, detecta el ciclo y ejecuta un `ABORT` forzoso sobre una de las transacciones arrojando `ERROR: 40P01: deadlock detected`.
3. **La Solución Algorítmica**: En `ReservationServiceImpl.java`, antes de iterar y solicitar los bloqueos a la base de datos, ordenamos la lista de ítems de forma ascendente:
   ```java
   List<ReservationItem> sortedItems = items.stream()
           .sorted(Comparator.comparing(ReservationItem::productId))
           .toList();
   ```
   Al imponer que toda transacción en el sistema operativo bloquee siempre en la dirección matemática estricta:
   $$\text{Lock}(P_1) \rightarrow \text{Lock}(P_2) \rightarrow \dots \rightarrow \text{Lock}(P_n) \quad \text{donde } P_1 < P_2 < \dots < P_n$$
   se **rompe matemáticamente la condición de Espera Circular de Coffman**. El grafo de dependencias de la base de datos se convierte en un **Grafo Acíclico Dirigido (DAG)**, haciendo que los deadlocks entre pedidos sean físicamente imposibles.

#### Capa 3: La Respuesta de Nivel Solutions Architect
> *"En sistemas de alta concurrencia transaccional, depender de la detección reactiva de deadlocks de la base de datos (`deadlock_timeout`) es un antipatrón costoso: desperdicia ciclos de CPU del motor corriendo algoritmos de detección de ciclos en grafos y produce cancelaciones aleatorias de transacciones que degradan la experiencia del usuario.
>
> En StockPilot implementamos prevención proactiva por diseño aplicando la ruptura de las condiciones de Coffman mediante ordenamiento determinista. Al forzar que todas las líneas de reserva se ordenen por `productId ASC` en la memoria de la JVM previo a la adquisición de bloqueos `PESSIMISTIC_WRITE`, transformamos cualquier contención concurrente en una cola ordenada FIFO a nivel de tupla en PostgreSQL. El costo computacional de ordenar 20 ítems en la JVM mediante TimSort es de fracciones de microsegundo, mientras que el beneficio es la eliminación total de excepciones `CannotAcquireLockException` en producción."*

---

### 4.3 Máquina de Estados de la Orden (`PurchaseOrder`) y Transaccionalidad ACID

#### Capa 1: La Intuición / Analogía del Mundo Real
Imagina un contrato de compraventa industrial. Mientras está en estado "Borrador" (`DRAFT`), puedes modificar los productos, cambiar cantidades o tirar el papel a la papelera sin consecuencias legales ni comerciales; el almacén no toca una sola caja. Cuando ambas partes firman formalmente el documento (`CONFIRMED`), en ese mismo segundo el almacén precinta las cajas en una jaula de seguridad exclusiva: esas unidades ya no existen para nadie más. Si la orden se anula (`CANCELLED`), los precintos se rompen y las cajas regresan al pasillo general. Pero si el camión ya cargó la mercadería y salió por la puerta del centro logístico (`FULFILLED`), el contrato está cerrado y el inventario rebajado: no puedes "cancelar" una orden que ya está en camino a una mina.

#### Capa 2: La Mecánica Exacta (De la RAM al Disco)
1. **La Máquina de Estados en el Dominio**: Las transiciones permitidas están codificadas en los métodos de negocio de la entidad `PurchaseOrder.java`:
   - `addLine()`: Permitido **únicamente** en `DRAFT`.
   - `confirm()`: Permitido **únicamente** desde `DRAFT`. Valida que existan líneas.
   - `cancel()`: Permitido desde `DRAFT` o `CONFIRMED`.
   - `fulfill()`: Permitido **únicamente** desde `CONFIRMED`.
   Cualquier intento de transición inválida (ej. llamar a `confirm()` sobre una orden `CANCELLED`) arroja `InvalidOrderStateException`, mapeada automáticamente a **HTTP 409 Conflict** en la capa API.
2. **Atomicidad Multi-Entidad en una Conexión JDBC**: Cuando el cliente invoca `PUT /api/v1/orders/{id}/confirm`, Spring abre una única transacción JDBC física (`@Transactional`):
   - Actualiza el estado de `PurchaseOrder` a `CONFIRMED`.
   - Llama a `ReservationService`, que adquiere bloqueos pesimistas ordenados en `stock_level`.
   - Incrementa `stock_level.reserved_qty`.
   - Inserta una fila en `stock_reservation` con estado `ACTIVE`.
   - Inserta un asiento de auditoría en `inventory_ledger` con tipo `RESERVATION` y el delta negativo correspondiente.
   - Si cualquiera de estas operaciones falla (por ejemplo, el tercer SKU no tiene stock suficiente y lanza `InsufficientStockException`), el interceptor transaccional de Spring ejecuta un **`ROLLBACK WORK` integral en PostgreSQL**. La base de datos revierte absolutamente todas las escrituras: la orden permanece en `DRAFT`, los dos primeros SKUs liberan sus locks y el ledger queda limpio.
3. **Snapshot de Precios Inmutable**: En B2B, los precios del catálogo fluctúan por inflación y tipo de cambio. Al crear una línea en `DRAFT`, `OrderLine` toma un snapshot inmutable de `product.getUnitPrice()`. La columna `line_total` es calculada directamente por PostgreSQL (`GENERATED ALWAYS AS (quantity * unit_price) STORED`), garantizando exactitud contable inalterable ante futuros cambios en el catálogo.

#### Capa 3: La Respuesta de Nivel Solutions Architect
> *"Frente a la tendencia común de desacoplar pedidos e inventario en dos microservicios separados mediante patrones de consistencia eventual (como Sagas orquestadas o coreografiadas con Kafka), decidimos conscientemente mantener ambos módulos dentro de nuestro Monolito Modular con transacciones ACID locales.
>
> En un dominio de alta contención con tolerancia cero a la sobreventa, una Saga introduce ventanas de inconsistencia temporal (*dirty reads* y compensaciones complejas): la orden se crea como confirmada pero el stock falla 500 milisegundos después en otro servicio, obligando a deshacer la orden y notificar al cliente de forma reactiva. En StockPilot, la confirmación de la orden, la reserva pesimista de stock y el asiento inmutable en el ledger ocurren en una única transacción de base de datos local que se resuelve en menos de 10 milisegundos, entregando consistencia inmediata absoluta (Strong Consistency) y eliminando por completo la necesidad de transacciones distribuidas 2PC o lógica de compensación."*

---

### 4.4 El Banco de Pruebas de Concurrencia Masiva (`CountDownLatch` con 50 Hilos)

#### Capa 1: La Intuición / Analogía del Mundo Real
Imagina la final olímpica de los 100 metros planos con 50 atletas en la pista. Si permites que cada corredor salga cuando se le dé la gana, saldrá el primero, cruzará la meta caminando, tomará la medalla y los demás no tendrán con quién competir; no hubo carrera real. Para evaluar la resistencia física bajo máxima presión, colocas a los 50 corredores agachados en los tacos de salida, les ordenas no moverse mientras el juez levanta el brazo (`await()`), y cuando todos están en máxima tensión, se dispara la pistola de fogueo (`countDown()`). Los 50 cuerpos se lanzan disparados al unísono en el mismo nanosegundo hacia el mismo metro cuadrado.

#### Capa 2: La Mecánica Exacta (De la RAM al Disco)
1. **Configuración del Escenario**: En `OrderReservationConcurrencyTest.java`, preparamos la base de datos con un SKU industrial de alta demanda (`PLC-SIEM-S71500`) en la bodega `WH-SCL-CENTRAL` con **exactamente 1 unidad física disponible** (`total_qty = 1, reserved_qty = 0, available_qty = 1`).
2. **Creación de las 50 Órdenes**: Pre-creamos en PostgreSQL 50 órdenes independientes en estado `DRAFT`, cada una solicitando reservar esa 1 unidad.
3. **El Pool de Hilos y los Latches de Sincronización**:
   ```java
   ExecutorService executor = Executors.newFixedThreadPool(50);
   CountDownLatch startGun = new CountDownLatch(1);
   CountDownLatch finishLatch = new CountDownLatch(50);
   ```
   Se despachan 50 tareas al executor. Cada hilo entra y ejecuta `startGun.await()`. La JVM coloca a los 50 hilos en estado `WAITING`, suspendiendo su ejecución en el kernel del sistema operativo sin consumir CPU.
4. **El Disparo Simultáneo**: La hebra principal ejecuta `startGun.countDown()`. El cerrojo se abre y la JVM despierta a los 50 hilos simultáneamente.
5. **La Contención en el Motor Relacional**: Los 50 hilos solicitan conexiones a HikariCP y disparan al mismo microsegundo la consulta:
   ```sql
   SELECT * FROM stock_level WHERE product_id = 1 AND warehouse_id = 1 FOR UPDATE;
   ```
   PostgreSQL otorga el bloqueo exclusivo a la primera transacción que ganó la contención a nivel de kernel. Esa transacción verifica `available_qty = 1 >= 1`, incrementa `reserved_qty = 1`, asienta en el ledger y ejecuta `COMMIT`.
6. **Rechazo en Cascada sin Retry Storms**: Los 49 hilos restantes, que estaban esperando en la cola FIFO de PostgreSQL, despiertan uno a uno. Leen la fila fresca post-commit con `available_qty = 0`, detectan que no hay disponibilidad, lanzan de inmediato `InsufficientStockException`, ejecutan `ROLLBACK` y liberan su conexión.
7. **Aserción Científica de Cero Sobreventa**:
   - `successCounter.get() == 1` (exactamente 1 orden transiciona a `CONFIRMED`).
   - `conflictCounter.get() == 49` (exactamente 49 órdenes reciben `InsufficientStockException` y permanecen en `DRAFT`).
   - `stockLevel.total_qty == 1, stockLevel.reserved_qty == 1, stockLevel.available_qty == 0`.
   - **Invariante Matemática Demostrada**: Cero sobreventa bajo máxima presión de concurrencia.

#### Capa 3: La Respuesta de Nivel Solutions Architect
> *"En sistemas de misión crítica, la concurrencia no se asume en papel: se demuestra científicamente mediante pruebas de carga multi-hilo deterministas. Diseñé un harness de prueba de estrés en Java 21 utilizando `CountDownLatch` y Testcontainers con PostgreSQL 16 real. Al usar un latch de cuenta regresiva como barrera de sincronización, logramos que 50 hilos ataquen el pool HikariCP en el mismo nanosegundo compitiendo por una sola unidad física disponible.
>
> La prueba valida de extremo a extremo que el bloqueo pesimista `SELECT ... FOR UPDATE` serializa adecuadamente las peticiones en el motor de base de datos, logrando exactamente 1 éxito y 49 rechazos limpios por `InsufficientStockException`, con cero inconsistencias en el ledger de auditoría y cero sobreventa. Este test corre en nuestro pipeline de CI/CD en GitHub Actions, garantizando que ninguna regresión de concurrencia pueda llegar a producción jamás."*

---

## 🎯 Módulo 5: El Simulador de Entrevista Técnica (Top 7 Preguntas de Fuego)

Estructuradas bajo la metodología **STAR** (*Situación, Tarea, Acción, Resultado*), el estándar de oro en entrevistas para roles de Senior Software Engineer y Solutions Architect:

---

### ❓ Pregunta 1: *"¿Por qué implementaste Bloqueo Pesimista con `SELECT ... FOR UPDATE` en lugar de Bloqueo Optimista con `@Version` para el inventario?"*

* **S (Situación)**: En StockPilot manejamos ventas mayoristas B2B de suministros industriales donde lotes escasos reciben múltiples órdenes simultáneas en ventanas de milisegundos.
* **T (Tarea)**: El requerimiento no negociable era garantizar cero sobreventa y mantener un throughput predecible bajo altísima contención en las mismas filas de la base de datos.
* **A (Acción)**: Evalué el Bloqueo Optimista, pero bajo alta contención (ej. 50 hilos compitiendo por la última unidad), el 98% de los hilos fallan con `OptimisticLockException`. Si configuras reintentos automáticos con retroceso exponencial, desatas un *retry storm* que satura la CPU y agota conexiones de base de datos sin aportar valor. Por ello, opté por **Bloqueo Pesimista (`PESSIMISTIC_WRITE`)** configurando un **timeout explícito de 3000 ms** mediante `@QueryHints`.
* **R (Resultado)**: PostgreSQL serializa las peticiones limpiamente a nivel de kernel. El primer hilo reserva en < 10 ms; el segundo hilo entra, lee inmediatamente que el stock disponible es 0, y rechaza la operación sin reintentos inútiles. El comportamiento bajo estrés es determinista y estable.

---

### ❓ Pregunta 2: *"Si dos órdenes intentan reservar los mismos productos simultáneamente, ¿cómo garantizas que el sistema no termine en un Deadlock?"*

* **S (Situación)**: Una orden de compra B2B generalmente incluye múltiples líneas de productos (ej. un PLC y tres sensores). Si dos órdenes concurrentes incluyen los mismos productos pero en orden inverso, las transacciones pueden bloquearse mutuamente.
* **T (Tarea)**: Erradicar la posibilidad matemática de Deadlocks sin depender de reintentos ciegos a nivel de aplicación.
* **A (Acción)**: Apliqué la ruptura de la condición de Coffman mediante **ordenamiento determinista estricto**. Antes de solicitar los bloqueos `FOR UPDATE` en la base de datos, el servicio ordena la lista de líneas por su identificador único:
  ```java
  items.stream().sorted(Comparator.comparing(ReservationItem::productId)).toList();
  ```
  Así, ninguna transacción intentará adquirir el Lock(B) antes que el Lock(A).
* **R (Resultado)**: Eliminación total de interbloqueos circulares en la capa de persistencia, reduciendo la tasa de excepciones `CannotAcquireLockException` a cero en escenarios concurrentes.

---

### ❓ Pregunta 3: *"¿Por qué decidiste calcular `available_qty` como una columna `STORED` en PostgreSQL en lugar de calcularla en memoria en la clase Java?"*

* **S (Situación)**: El inventario disponible se compone de la resta entre stock físico en estantería (`total_qty`) y stock comprometido en órdenes (`reserved_qty`).
* **T (Tarea)**: Necesitábamos una única fuente de la verdad inmutable que pudiera ser consultada con velocidad de milisegundos por el catálogo web sin riesgo de desincronización (*drift*).
* **A (Acción)**: En lugar de un campo transient en Java o un trigger procedural en PL/pgSQL, definí en el DDL de Flyway:
  ```sql
  available_qty INTEGER GENERATED ALWAYS AS (total_qty - reserved_qty) STORED
  ```
  y lo mapeé en Hibernate con `insertable = false, updatable = false` junto con `@Generated`.
* **R (Resultado)**: Es físicamente imposible que un bug en Java o una consulta manual directa en la base de datos desincronice el stock disponible. Además, al ser `STORED`, pudimos crear un índice B-Tree parcial (`WHERE available_qty > 0`), permitiendo que el catálogo filtre productos con disponibilidad en submilisegundos mediante *Index Scans*.

---

### ❓ Pregunta 4: *"Veo que la entidad `InventoryLedger` no tiene setters ni columna `updated_at`. ¿Cómo manejas un error humano si un operador registra mal un stock?"*

* **S (Situación)**: En entornos logísticos e industriales regulados, la trazabilidad de inventario equivale a un balance contable de dinero.
* **T (Tarea)**: Garantizar el principio de No Repudio y cumplimiento normativo (SOX / ISO 27001), impidiendo que cualquier usuario o proceso altere la historia contable.
* **A (Acción)**: Diseñé la tabla como un **Libro Mayor Append-Only** protegido a nivel de ORM con la anotación `@Immutable` de Hibernate, desactivando el dirty checking y bloqueando cualquier sentencia `UPDATE`. Si un operador comete un error, el sistema prohíbe editar la fila existente; en su lugar, la regla de negocio exige emitir un **asiento de ajuste compensatorio** de tipo `ADJUSTMENT` con el delta inverso y la justificación requerida.
* **R (Resultado)**: Integridad absoluta de auditoría. Es posible reconstruir con exactitud milimétrica el estado del inventario de cualquier bodega en cualquier segundo del pasado (*Point-in-Time Reconstruction*).

---

### ❓ Pregunta 5: *"En tus pruebas unitarias de controladores usas `@WebMvcTest`. ¿Por qué tus pruebas no cargan la base de datos completa y qué desafío arquitectónico resolviste al separar las configuraciones?"*

* **S (Situación)**: Al escribir pruebas unitarias para la capa web (`ProductControllerIntegrationTest`), el arranque fallaba con un error de inyección de dependencias en `jpaAuditingHandler`.
* **T (Tarea)**: Lograr pruebas rápidas de la capa HTTP y aislar los contratos de transporte de la capa de persistencia pesada.
* **A (Acción)**: Identifiqué que la anotación `@EnableJpaAuditing` estaba colocada en la clase raíz `@SpringBootApplication`. Como `@WebMvcTest` es un *slice test* que deliberadamente apaga Hibernate para que la prueba corra en 200 ms, el auditor de JPA no encontraba el `JpaMappingContext`. En lugar de meter mocks sucios, apliqué el principio de responsabilidad única extrayendo la configuración a una clase dedicada `JpaConfig.java`.
* **R (Resultado)**: Las pruebas de los controladores ejecutan de forma completamente desacoplada en milisegundos, validando serialización JSON, códigos HTTP y reglas de Bean Validation sin requerir contenedores de base de datos para pruebas puramente sintácticas.

---

### ❓ Pregunta 6: *"Si un cliente B2B sufre un timeout de red y reintenta con la misma Idempotency-Key mientras el servidor aún está procesando la orden original, ¿cómo previenes que se duplique?"*

* **S (Situación)**: En operaciones mutantes críticas como `POST /api/v1/orders`, dos peticiones con la misma clave de idempotencia pueden llegar casi al unísono si el cliente reintenta agresivamente ante lentitud de red.
* **T (Tarea)**: Evitar condiciones de carrera en el propio mecanismo de idempotencia y asegurar que la operación no se ejecute dos veces en paralelo.
* **A (Acción)**: Diseñé el `IdempotencyFilter` con una estrategia de **Detección de Operaciones en Vuelo (In-Flight Sentinel)**. Al recibir una clave nueva, el filtro intenta insertar de inmediato un registro en la tabla `idempotency_record` con `response_status = 0` y `response_body = 'IN_PROGRESS'`. Como la columna `idempotency_key` tiene un índice `UNIQUE` estricto en PostgreSQL, la segunda petición colisiona en el motor de base de datos arrojando una violación de unicidad o lee `status = 0`, provocando que el filtro la rechace inmediatamente con **HTTP 409 Conflict** (*"Operation currently in progress"*). Al terminar el procesamiento del controlador, el registro se actualiza con el status final (201) y el cuerpo JSON.
* **R (Resultado)**: Imposibilidad matemática de ejecución paralela duplicada. Si la primera petición tiene éxito, los futuros reintentos reciben un Replay directo del JSON original; si falló con un error 500 no recuperable, el centinela se elimina permitiendo un reintento limpio.

---

### ❓ Pregunta 7: *"¿Por qué desacoplaste el `IdempotencyFilter` en una clase `IdempotencyConfig` mediante `FilterRegistrationBean` en lugar de simplemente anotarlo con `@Component`?"*

* **S (Situación)**: Al agregar `IdempotencyFilter` como un `@Component` genérico de Spring, todas las pruebas unitarias de controladores con `@WebMvcTest` (`ProductControllerIntegrationTest`, `StockControllerIntegrationTest`) comenzaron a fallar con `NoSuchBeanDefinitionException` para `IdempotencyRecordRepository`.
* **T (Tarea)**: Permitir que el filtro proteja la API en producción sin contaminar el aislamiento de los *slice tests* unitarios de la capa web.
* **A (Acción)**: `@WebMvcTest` escanea automáticamente controladores y cualquier clase `@Component` que implemente la interfaz `Filter`. Como `IdempotencyFilter` requiere inyectar un repositorio JPA (`IdempotencyRecordRepository`) y los slice tests apagan deliberadamente la capa de datos JPA, Spring intentaba instanciar el filtro sin poder resolver el repositorio. Retiré `@Component` de la clase del filtro y creé `IdempotencyConfig.java` anotada con `@Configuration`, registrando el filtro explícitamente mediante un `FilterRegistrationBean`. Como `@WebMvcTest` no carga clases `@Configuration` arbitrarias, las pruebas de controlador volvieron a quedar perfectamente aisladas.
* **R (Resultado)**: 100% de la suite de pruebas unitarias (`@WebMvcTest`) y de integración ejecutando limpiamente, respetando el principio de diseño modular y la separación estricta de contextos de prueba.

---

## 📜 Módulo 6: Protocolo de Gobierno Vivo y Definition of Done (DoD)

A partir de este momento, y en cumplimiento estricto con las credenciales ágiles del proyecto (**SMPC, SPOPC, SDPC**):

> [!IMPORTANT]
> **Enmienda Oficial a la Definition of Done (DoD)**:  
> Ningún Pull Request o incremento de valor (Slice) podrá ser considerado `DONE` ni fusionado a la rama `main` sin que el archivo `docs/architecture/MASTER_STUDY_GUIDE.md` haya sido actualizado con las decisiones, desgloses en 3 capas de nuevas anotaciones/patrones y el simulador de preguntas técnicas correspondiente a dicho Slice.
