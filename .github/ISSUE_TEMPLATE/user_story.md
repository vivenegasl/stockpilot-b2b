---
name: User Story (Historia de Usuario)
about: Plantilla ágil estándar para especificación de valor y criterios de aceptación
title: 'US-[ID]: [Título conciso de la funcionalidad]'
labels: ['user-story', 'needs-estimation']
assignees: ''
---

## 📌 Declaración de Valor de Negocio

**Como** [rol del usuario del sistema: e.g. Operador de Bodega, Comprador B2B, Auditor de Finanzas],  
**Quiero** [acción específica o funcionalidad a ejecutar],  
**Para** [beneficio o valor medible para el negocio / organización].

---

## 🎯 Criterios de Aceptación (Formato Gherkin BDD)

### Escenario 1: Camino Feliz (Happy Path)
```gherkin
Given [estado inicial del sistema y precondiciones requeridas]
  And [condiciones contextuales complementarias]
When [acción disparada por el usuario o evento del sistema]
Then [resultado medible y observable esperado]
  And [estado subsiguiente de la base de datos o efecto secundario]
```

### Escenario 2: Caso Borde o Validación de Negocio
```gherkin
Given [contexto donde los recursos son limitados o inválidos]
When [el usuario intenta ejecutar la acción]
Then [el sistema rechaza la operación de forma segura]
  And [se retorna un error semántico con formato RFC 7807 ProblemDetail]
```

### Escenario 3: Concurrencia o Idempotencia (Si Aplica)
```gherkin
Given [múltiples solicitudes idénticas o simultáneas sobre el mismo recurso]
When [las operaciones compiten en la ventana de tiempo]
Then [la invariante de negocio se preserva sin inconsistencias ni duplicidades]
```

---

## ⚙️ Consideraciones Técnicas y de Arquitectura
- **Módulo Afectado**: `catalog` | `inventory` | `ordering` | `audit` | `shared`
- **Impacto en Base de Datos**: ¿Requiere migración Flyway? (Sí/No)
- **Control de Concurrencia**: ¿Aplica bloqueo pesimista `SELECT ... FOR UPDATE`?
- **Idempotencia**: ¿Requiere cabecera obligatoria `Idempotency-Key`?
- **Mapeo de Datos**: Definir Records DTO de entrada y salida (sin exponer entidades JPA).

---

## 📊 Estimación Ágil
- **Story Points**: [ 1 | 2 | 3 | 5 | 8 | 13 ]
- **Prioridad MoSCoW**: [ 🔴 Must Have | 🟡 Should Have | 🟢 Could Have | ⚪ Won't Have ]
- **Sprint Asignado**: [ Sprint 1 | Sprint 2 | Sprint 3 ]
