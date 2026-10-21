dependencies {
    implementation(project(":shared"))

    // MVC
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // Messaging
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Api
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Data
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Migration
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Resilence
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Utilities
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.latencyutils:LatencyUtils:2.0.3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.awaitility:awaitility")
}