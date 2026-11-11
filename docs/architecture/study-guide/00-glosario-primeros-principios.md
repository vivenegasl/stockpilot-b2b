# 🧭 Glosario de Primeros Principios (Fundamentos sin Jerga)

> **Módulo 0 de la Guía Maestra de Estudio de StockPilot**  
> **Propósito**: Fundamentos de computación e infraestructura sin jerga circular. Todo concepto complejo se define en términos de recursos físicos del computador (RAM, CPU, Disco, Red).

---

## Términos Fundamentales del Ecosistema

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

[⬅️ Volver al Índice Principal](../MASTER_STUDY_GUIDE.md) | [Siguiente Módulo: Anatomía Básica ➡️](01-anatomia-basica-persistencia.md)
