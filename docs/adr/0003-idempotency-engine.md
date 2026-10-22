# ADR-0003: Motor de Idempotencia HTTP para Operaciones Mutantes

* **Estado**: Aceptado
* **Fecha**: 2026-10-22
* **Decisores**: Víctor Venegas (Software Architect)
* **Categoría**: API REST / Resiliencia de Red / Patrones de Integración

## Contexto y Declaración del Problema

En aplicaciones corporativas B2B, las redes no son 100% confiables (redes móviles de bodega, túneles VPN corporativos, latencias transcontinentales). Cuando un cliente realiza una petición mutante (`POST /api/v1/orders` o `PUT /api/v1/orders/{id}/confirm`), pueden ocurrir dos escenarios de fallo:
1. La petición no llega al servidor (fallo pre-procesamiento).
2. La petición llega, el servidor reserva el stock y crea el pedido, pero se corta la conexión antes de que la respuesta HTTP llegue al cliente (fallo post-procesamiento).

Ante un timeout, el cliente (o su cliente HTTP con reintentos automáticos) vuelve a enviar la solicitud idéntica. Si la API no es idempotente, se crearán **dos órdenes gemelas**, reservando el doble del stock real e incurriendo en duplicidad de cargos financieros.

Debemos dotar a la API de un mecanismo que garantice que múltiples ejecuciones de una misma operación mutante produzcan exactamente el mismo resultado y estado en el sistema.

## Factores de Decisión (Decision Drivers)

* **Seguridad Financiera y Operacional**: No duplicar pedidos ni reservas ante reintentos de clientes o timeouts de proxy inverso (Nginx).
* **Transparencia para los Controladores**: La lógica de idempotencia no debe ensuciar los servicios de negocio; debe resolverse de forma desacoplada en la capa de transporte/filtro.
* **Tolerancia a Concurrencia**: Dos reintentos casi simultáneos con la misma clave no deben ejecutarse en paralelo (prevención de race condition en el chequeo de la llave).
* **Persistencia y Expiración**: La información de idempotencia debe resistir reinicios de la aplicación y expirar limpiamente tras una ventana temporal (TTL de 24 horas).

## Opciones Consideradas

1. **Idempotencia a Nivel de Aplicación en Memoria (Caffeine / In-Memory Map)**:
   * Guardar llaves en memoria local.
   * *Problema*: Si se reinicia el contenedor Spring Boot o se escala a 2 réplicas tras Nginx, la memoria se pierde o no se comparte, fallando la idempotencia.

2. **Redis Distribuido (SETNX con TTL)**:
   * Usar un cluster Redis como almacén central de llaves de idempotencia.
   * *Problema*: Añade un componente de infraestructura extra (costo operacional, mantenimiento, puertos, dependencias) innecesario para un sistema con base de datos PostgreSQL ya disponible.

3. **Filtro Servlet (`IdempotencyFilter`) con Persistencia en PostgreSQL y Bloqueo por Constraint**:
   * Interceptar peticiones HTTP que incluyan la cabecera `Idempotency-Key` (formato UUID v4).
   * Almacenar el registro en una tabla `idempotency_record` con un índice único estricto `UNIQUE(idempotency_key)`.
   * Almacenar el código de estado HTTP (`response_status`) y el cuerpo JSON (`response_body`) para responder directamente en caso de replay.

## Decisión y Justificación

Se elige la **Opción 3: Filtro Servlet con Persistencia en PostgreSQL y Constraint de Llave Única**.

### Flujo de Ejecución del `IdempotencyFilter`:

1. **Extracción y Validación**: Se lee el header `Idempotency-Key`. Si viene vacío en endpoints mutantes marcados como críticos, se rechaza con `400 Bad Request` indicando que la cabecera es obligatoria.
2. **Búsqueda (Cache Lookup)**: Se consulta la tabla `idempotency_record` por la llave.
   * **Cache HIT (Completado)**: Si la llave existe y tiene `response_status` registrado, se retorna de inmediato la respuesta guardada (Replay), agregando el header de respuesta `X-Cache-Lookup: HIT`.
   * **In-Flight Conflict**: Si existe un registro pero aún no tiene respuesta (procesamiento en curso por otra hebra simultánea), se rechaza con `409 Conflict` (o `425 Too Early`), impidiendo la doble ejecución simultánea.
3. **Ejecución y Captura (Cache MISS)**:
   * Si la llave no existe, se inserta un registro preliminar en estado "PROCESSING".
   * Se envuelve la respuesta en un `ContentCachingResponseWrapper`.
   * Se continúa la cadena del filtro (`filterChain.doFilter(...)`).
   * Al retornar del controller, se actualiza el registro con el `status` final (ej. 200, 201, 409) y el `body` serializado.
4. **Política de Expiración**: Los registros tienen un campo `expires_at = clock_timestamp() + interval '24 hours'`. Un job programado purga registros antiguos para no sobrecargar el almacenamiento.

## Consecuencias y Trade-offs

### Consecuencias Positivas:
* Seguridad total contra órdenes duplicadas en la red.
* Implementación 100% nativa en PostgreSQL sin dependencias externas como Redis.
* Total desacoplamiento: los controllers de catálogo, pedidos e inventario desconocen el mecanismo de idempotencia.

### Consecuencias Negativas y Mitigaciones:
* **Overhead de I/O en Base de Datos**: Cada petición mutante requiere 1 lectura y 1 inserción/actualización en `idempotency_record`.  
  * *Mitigación*: La tabla está indexada por un índice único hash/btree sobre UUID (16 bytes), con tiempos de lectura de submilisegundos en SSDs NVMe.
