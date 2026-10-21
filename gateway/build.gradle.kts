dependencies {
    implementation(project(":shared"))

    // Gateway
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Data
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Resilience
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Utilities
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.latencyutils:LatencyUtils:2.0.3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("com.redis:testcontainers-redis")
    testImplementation("org.wiremock:wiremock-standalone")
}