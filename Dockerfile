# ==============================================================================
# Stage 1: Build & Package (JDK 21)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Optimización de caché de capas Docker: Primero dependencias y wrappers
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copia de código fuente y empaquetado del bootJar de producción (omitiendo tests)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ==============================================================================
# Stage 2: Runtime Mínimo y Seguro (JRE 21 Alpine, Non-Root)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Víctor Venegas <vi.venegasl@duocuc.cl>"
LABEL description="StockPilot B2B Platform - Spring Boot 3.4.x Production Runtime"

# Principio de Mínimo Privilegio: Crear usuario del sistema sin permisos root
RUN addgroup -g 1001 -S stockpilot && \
    adduser -u 1001 -S stockpilot -G stockpilot

WORKDIR /app

# Copia selectiva exclusivamente del JAR compilado
COPY --from=builder --chown=stockpilot:stockpilot /workspace/build/libs/*.jar app.jar

# Configuración de variables de entorno seguras por defecto
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Asignar usuario sin privilegios
USER 1001

EXPOSE 8080

# Health check contra Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
