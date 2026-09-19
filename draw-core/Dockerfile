# syntax=docker/dockerfile:1
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Копируем файлы сборки и зависимости
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# Копируем общие модули и сам модуль draw-core
COPY shared/ shared/
COPY draw-core/ draw-core/

RUN chmod +x gradlew

# Собираем jar-файл конкретно для модуля draw-core
RUN --mount=type=cache,target=/root/.gradle/caches \
        --mount=type=cache,target=/root/.gradle/wrapper \
        ./gradlew :draw-core:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

LABEL version="0.1.0"
LABEL description="VeriDraw Draw Core Service"

# Создаём non-root пользователя
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --create-home appuser

WORKDIR /app

# Копируем собранный jar из builder
COPY --from=builder --chown=appuser:appgroup /app/draw-core/build/libs/draw-core.jar app.jar

# Переключаемся на non-root пользователя
USER appuser

# Открываем порт
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
    OTEL_SERVICE_NAME=draw-core

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]