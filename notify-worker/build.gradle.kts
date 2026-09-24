plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":shared"))

    // 1. Core & Concurrency: Java 21 Virtual Threads
    // Senior-подход: для I/O-bound воркеров виртуальные потоки + стандартный Tomcat
    // проще, производительнее и легче в дебаге, чем реактивный стек (WebFlux).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // 2. Messaging: Kafka Consumer
    // Для асинхронного получения событий (например, WinnerIdentifiedEvent)
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // 3. Persistence: Idempotency & State Tracking
    // Используем стандартный JDBC/JPA. С виртуальными потоками нет штрафа за блокировку,
    // а транзакционная работа с таблицей идемпотентности (processed_events) гораздо проще, чем в R2DBC.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 4. Resilience: Отказоустойчивость внешних вызовов
    // Критически важно для воркера: внешние API (Telegram, SendGrid) часто отдают 429/500.
    // Обеспечивает аннотации @Retry (с exponential backoff) и @CircuitBreaker.
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")

    // 5. Observability: Tracing & Metrics
    // Чтобы traceId из Kafka-сообщения пробрасывался в HTTP-запросы к внешним сервисам и в логи.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")

    // 6. Utilities
    implementation("org.springframework.boot:spring-boot-starter-validation") // Для @Valid на ConfigurationProperties
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // 7. Testing: Интеграционные тесты на реальных зависимостях
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Testcontainers: Junior мокает Kafka и БД, Senior поднимает реальные контейнеры в тестах.
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
    }
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("notify-worker.jar")
}

tasks.jar {
    enabled = false
}