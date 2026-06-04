# syntax=docker/dockerfile:1
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY gateway/ gateway/

RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle/caches \
        --mount=type=cache,target=/root/.gradle/wrapper \
        ./gradlew :gateway:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="veridraw-team@example.com"
LABEL version="0.1.0"
LABEL description="VeriDraw API Gateway"

# Создаём non-root пользователя
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --create-home appuser

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /app/gateway/build/libs/gateway.jar app.jar

# Переключаемся на non-root пользователя
USER appuser

# Открываем порт
EXPOSE 8081

# Health check (curl есть в jammy-jre)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
    OTEL_SERVICE_NAME=gateway

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]