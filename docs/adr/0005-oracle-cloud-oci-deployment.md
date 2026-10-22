# ADR-0005: Estrategia de Despliegue en Oracle Cloud Infrastructure (OCI)

* **Estado**: Aceptado
* **Fecha**: 2026-10-22
* **Decisores**: Víctor Venegas (DevOps Engineer & Software Architect)
* **Categoría**: DevOps / Infraestructura en la Nube / Despliegue Continuo (CD)

## Contexto y Declaración del Problema

Para el portafolio profesional y la demostración en vivo de **StockPilot**, se requiere un entorno de producción accesible públicamente vía HTTPS, con base de datos real, proxy reverso seguro y capacidad de ejecución continua sin costos operativos desproporcionados.

El desarrollador cuenta con competencias certificadas en DevOps (DEPC, DFPC, DAPC) y acceso a **Oracle Cloud Infrastructure (OCI)**. Debemos definir la topología de despliegue y la integración en el pipeline de CI/CD desde el inicio del proyecto.

## Factores de Decisión (Decision Drivers)

* **Disponibilidad Continua y Costo Cero ($0/mes)**: Aprovechar el programa Always Free de OCI (Compute VM.Standard.A1.Flex o E2.1.Micro) para sostener el portafolio permanentemente en línea.
* **Seguridad y Mínimo Privilegio**: Ningún contenedor debe correr como `root`; los secretos no deben residir en el código fuente; Nginx debe manejar TLS/SSL y rate limiting.
* **Automatización Extremo a Extremo**: Un push verificado a la rama `main` en GitHub debe compilar, testear, escanear con Trivy y desplegar automáticamente en OCI sin intervención manual.

## Opciones Consideradas

1. **Kubernetes Gestionado (OKE en OCI)**:
   * Desplegar clusters Kubernetes administrados.
   * *Problema*: Sobrecarga desmedida de configuración (etcd, ingress controllers, PVs) para un monolito modular y consumo innecesario de recursos computacionales.

2. **OCI Container Instances (Serverless Containers)**:
   * Ejecutar contenedores individuales sin gestionar VMs.
   * *Problema*: Costos por segundo tras agotar créditos y menor flexibilidad para montar la base de datos PostgreSQL persistente con volúmenes locales.

3. **Compute VM en OCI + Docker Compose + Nginx Edge Proxy + GitHub Actions CD**:
   * Instancia Compute en OCI (Ubuntu Linux) dentro de una Virtual Cloud Network (VCN) con Security Lists estrictas (puertos 80, 443 expuestos al público; 5432 solo accesible en red interna de Docker).
   * Orquestación local con `docker-compose.yml` ejecutando:
     - `app`: Spring Boot (Java 21, imagen multi-stage Alpine, usuario non-root 1001).
     - `db`: PostgreSQL 16 con volumen persistente en Block Storage OCI.
     - `nginx`: Reverse Proxy con certificados SSL Let's Encrypt / Certbot y cabeceras de ciberseguridad.
     - `spa`: Nginx estático sirviendo el bundle compilado de React 19.

## Decisión y Justificación

Se elige la **Opción 3: Compute VM en OCI + Docker Compose + GitHub Actions CD**.

### Arquitectura de Despliegue OCI:

```
[ Internet ] 
     │ HTTPS (443)
     ▼
[ OCI Virtual Cloud Network (VCN) - Security List ]
     │
     ▼
[ OCI Compute Instance (Ubuntu LTS) ]
     │
     ▼
[ Nginx Reverse Proxy (Docker) ]
     ├── /          ──> [ React 19 SPA (Static Assets) ]
     └── /api/v1/*  ──> [ Spring Boot Monolith (Port 8080) ]
                              │ (Internal Docker Network)
                              ▼
                        [ PostgreSQL 16 (Port 5432) ]
                              │
                              ▼
                        [ OCI Block Storage (Persistente) ]
```

### Pipeline de Despliegue en GitHub Actions:
1. **Build & Test**: Ejecuta `./gradlew check` y pruebas unitarias/integración con Testcontainers.
2. **Security Gate**: Escaneo de dependencias y de imagen Docker con **Trivy** (falla ante hallazgos CRITICAL/HIGH).
3. **CD Trigger (Push to main)**:
   - Conexión segura SSH a la VM de OCI usando secretos cifrados en GitHub Secrets (`OCI_SSH_KEY`, `OCI_HOST`, `OCI_USER`).
   - Pull del código actualizado y ejecución de `docker compose pull && docker compose up -d --remove-orphans`.
   - Health check automático contra `/actuator/health` en OCI antes de confirmar el despliegue exitoso.

## Consecuencias y Trade-offs

### Consecuencias Positivas:
* Portafolio disponible 24/7 sin incurrir en costos de nube.
* Evidencia tangible de competencias DevOps y administración de Linux/OCI.
* Entorno de producción real y completamente reproducible.

### Consecuencias Negativas y Mitigaciones:
* **Mantenimiento del Servidor**: Requiere gestionar actualizaciones del kernel y Docker en la VM.  
  * *Mitigación*: Scripts de aprovisionamiento automáticos y políticas de reinicio seguro con `restart: unless-stopped`.
