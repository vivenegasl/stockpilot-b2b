# 📜 Módulo 10: Protocolo de Gobierno Vivo y Definition of Done (DoD)

> **Módulo 10 de la Guía Maestra de Estudio de StockPilot**  
> **Propósito**: Establecer el estándar normativo de Definition of Done (DoD) y el protocolo de mantenimiento vivo de la documentación técnica y pedagógica para cada nuevo Slice del sistema.

---

A partir de este momento, y en cumplimiento estricto con las credenciales ágiles del proyecto (**SMPC, SPOPC, SDPC**):

> [!IMPORTANT]
> **Enmienda Oficial a la Definition of Done (DoD)**:  
> Ningún Pull Request o incremento de valor (Slice) podrá ser considerado `DONE` ni fusionado a la rama `main` sin que:
> 1. El módulo correspondiente de la Guía de Estudio (`docs/architecture/study-guide/XX-...md`) haya sido creado o actualizado con las decisiones, desgloses en 3 capas de nuevas anotaciones/patrones y el simulador de preguntas técnicas.
> 2. El archivo maestro índice `docs/architecture/MASTER_STUDY_GUIDE.md` haya sido actualizado reflejando el enlace al nuevo módulo.
> 3. La suite de pruebas mantenga 100% de éxito (`./gradlew check`) incluyendo pruebas unitarias, de integración con Testcontainers y pruebas de estrés concurrentes cuando aplique.
> 4. Los artefactos de arquitectura (ADRs) y registros Scrum del sprint hayan sido debidamente sincronizados.

---

### Protocolo de Modularización y Adición de Futuros Slices

Gracias a la arquitectura desacoplada de la Guía de Estudio en módulos independientes dentro de `docs/architecture/study-guide/`:
* Cada nuevo Slice se documenta creando un archivo atómico dedicado (ej. como se realizó con el **Slice 4: Frontend React 19, Observabilidad Prometheus y Métricas de Concurrencia** en [`11-slice-4-frontend-observabilidad.md`](11-slice-4-frontend-observabilidad.md), o el futuro **Slice 5: Despliegue Cloud en Oracle Cloud OCI y Contenedores** en `12-slice-5-cloud-deploy-oci-k8s.md`).
* Se añade una fila descriptiva y el enlace en el índice maestro `MASTER_STUDY_GUIDE.md`.
* Se actualizan las notas de resumen atómico correspondientes en la bóveda de Obsidian (`PKM`).
* **Ventaja Arquitectónica**: Cero riesgo de conflictos de merge en Git sobre archivos gigantes, edición instantánea, lectura modular y cero pérdida de contexto por límites de tamaño.

---

[⬅️ Módulo 9: Simulador STAR](09-simulador-entrevistas-star.md) | [Índice Principal](../MASTER_STUDY_GUIDE.md)
