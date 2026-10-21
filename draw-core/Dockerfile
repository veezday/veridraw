# syntax=docker/dockerfile:1
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY shared/build.gradle.kts shared/
COPY draw-core/build.gradle.kts draw-core/

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew :draw-core:dependencies --no-daemon || true

COPY shared/ shared/
COPY draw-core/ draw-core/

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew :draw-core:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

LABEL version="0.1.0"
LABEL description="VeriDraw Draw Core Service"

RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Создаём non-root пользователя
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --create-home appuser

WORKDIR /app

RUN wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar \
    -O /opt/opentelemetry-javaagent.jar && \
    chmod 444 /opt/opentelemetry-javaagent.jar
# Копируем собранный jar из builder
COPY --from=builder --chown=appuser:appgroup /app/draw-core/build/libs/draw-core.jar app.jar

# Переключаемся на non-root пользователя
USER appuser

# Открываем порт
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -javaagent:/opt/opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]