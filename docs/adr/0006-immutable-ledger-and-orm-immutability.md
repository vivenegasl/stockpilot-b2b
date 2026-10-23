# ADR-0006: Inmutabilidad Estricta del Libro Mayor de Inventario (Append-Only Ledger)

* **Estado**: Aceptado
* **Fecha**: 2026-10-23
* **Decisores**: Víctor Venegas (Software Architect & Lead Engineer)
* **Categoría**: Persistencia de Datos / Auditoría / Ciberseguridad

## Contexto y Declaración del Problema

En sistemas B2B de inventario y pedidos de misión crítica, la trazabilidad de existencias físicas y monetarias es un activo sujeto a escrutinio legal, tributario y normativo (SOX, ISO 27001). 

Si un sistema permite mutar o eliminar registros de auditoría históricos (mediante `UPDATE` o `DELETE`), se abren graves vulnerabilidades de seguridad y gobierno:
1. **Manipulación de Datos (STRIDE: *Tampering*)**: Un atacante interno o externo podría encubrir mermas, robos o sobreventas alterando registros históricos de inventario.
2. **Repudio (STRIDE: *Repudiation*)**: La imposibilidad de probar qué operador o proceso autorizó un movimiento específico.
3. **Pérdida de Integridad Referencial Histórica**: Modificar eventos pasados destruye la capacidad de reconstruir el estado del almacén en un momento dado en el tiempo (*Point-in-Time Reconstruction*).

Debemos definir las salvaguardas arquitectónicas para garantizar que el libro mayor de inventario (`inventory_ledger`) sea estrictamente **Append-Only** (solo inserciones) tanto a nivel de aplicación como en la base de datos.

## Factores de Decisión (Decision Drivers)

* **Inalterabilidad Física y Lógica**: Ningún registro contable de stock debe poder ser modificado ni borrado una vez persistido.
* **Defensa en Profundidad (Defense in Depth)**: La restricción debe operar en múltiples capas: modelo de dominio, capa ORM (Hibernate) y repositorio.
* **Rendimiento de Consulta Cronológica**: La tabla debe estar optimizada para lecturas secuenciales rápidas ordenadas por timestamp.

## Opciones Consideradas

1. **Confianza Convencional en la Capa de Servicios**:
   * Mantener una entidad JPA estándar con métodos setters y simplemente "no llamar" a métodos de actualización o borrado en el servicio.
   * *Problema*: Fragilidad ante errores humanos; cualquier desarrollador o mantenimiento futuro podría inyectar un `save()` sobre una entidad existente o exponer un endpoint `DELETE`.

2. **Trigger de Base de Datos para Rechazar `UPDATE` o `DELETE`**:
   * Crear un trigger en PostgreSQL que lance una excepción ante cualquier sentencia no-INSERT.
   * *Problema*: La falla se detecta tarde (en tiempo de ejecución de BD), y Hibernate no está enterado de la inmutabilidad, manteniendo objetos en el contexto de persistencia (*dirty checking* innecesario).

3. **Inmutabilidad Forzosa a Nivel ORM con `@Immutable` de Hibernate y Entidad sin Mutadores (Elegida)**:
   * Declarar la entidad JPA con `@org.hibernate.annotations.Immutable`.
   * Modelar la clase `InventoryLedger` sin métodos *setters* (campos solo lectura una vez instanciados por constructor).
   * Omitir intencionalmente la columna `updated_at`.
   * Configurar el repositorio `InventoryLedgerRepository` sin métodos de mutación.

## Decisión y Justificación

Se elige la **Opción 3: Entidad Inmutable `@Immutable` sin mutadores**.

### Implementación Técnica:

```java
@Entity
@Table(name = "inventory_ledger")
@org.hibernate.annotations.Immutable
public class InventoryLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    // Campos inmutables...
    // Cero métodos setters. Solo getters.
}
```

### Ventajas de `@Immutable` en Hibernate 6:
1. **Optimización de Rendimiento**: Hibernate omite por completo el proceso de verificación de cambios (*dirty checking*) en la sesión de persistencia para esta entidad. Nunca genera sentencias SQL `UPDATE`.
2. **Protección contra Errores**: Si por error de código se intenta modificar un atributo o fusionar la entidad, Hibernate arroja una excepción antes de emitir cualquier query.
3. **Auditoría Forense Confiable**: Todo movimiento queda fijado en el tiempo con su `clock_timestamp()` original, garantizando no repudio.

## Consecuencias y Trade-offs

### Consecuencias Positivas:
* Cumplimiento estricto de auditoría y no repudio.
* Menor consumo de memoria y CPU en la JVM al no requerir copias snapshot para dirty checking en el EntityManager.
* Código auto-documentado: la propia clase explicita que representa un hecho histórico inmutable (*Event / Ledger Entry*).

### Consecuencias Negativas y Mitigaciones:
* **Correcciones de Stock**: No se puede "editar" una entrada errónea.  
  * *Mitigación*: Se implementa el principio contable de partida doble: si se cometió un error, se genera un **nuevo movimiento** compensatorio de tipo `ADJUSTMENT` con delta inverso, preservando la trazabilidad de la corrección.
