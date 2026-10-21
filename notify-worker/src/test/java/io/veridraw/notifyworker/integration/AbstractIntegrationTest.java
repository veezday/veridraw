package io.veridraw.notifyworker.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@DirtiesContext
public abstract class AbstractIntegrationTest {

    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected WebTestClient webTestClient;

    protected final RestClient mailpitApi = RestClient.create();

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka"));

    protected static final GenericContainer<?> mailpit = new GenericContainer<>(
                    DockerImageName.parse("axllent/mailpit:v1.20"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    static {
        mailpit.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {

        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");

        registry.add("resilience4j.retry.instances.emailSendRetry.max-attempts", () -> "2");
        registry.add("resilience4j.retry.instances.emailSendRetry.wait-duration", () -> "100ms");

        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> String.valueOf(mailpit.getMappedPort(1025)));
        registry.add("mailpit.api.url", () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));

        registry.add(
                "spring.kafka.producer.key-serializer",
                () -> "org.springframework.kafka.support.serializer.ToStringSerializer");
        registry.add(
                "spring.kafka.producer.value-serializer",
                () -> "org.springframework.kafka.support.serializer.JsonSerializer");

        registry.add("spring.mail.properties.mail.smtp.connectiontimeout", () -> "2000");
        registry.add("spring.mail.properties.mail.smtp.timeout", () -> "2000");

        registry.add("management.health.mail.enabled", () -> "false");
    }

    @BeforeEach
    void resetMailpit() {
        try {
            String baseUrl = "http://%s:%d".formatted(mailpit.getHost(), mailpit.getMappedPort(8025));
            mailpitApi.delete().uri(baseUrl + "/api/v1/messages").retrieve().toBodilessEntity();
        } catch (Exception ignore) {
        }
    }

    public int getMailpitMessageCount() {
        String baseUrl = "http://%s:%d".formatted(mailpit.getHost(), mailpit.getMappedPort(8025));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> response =
                mailpitApi.get().uri(baseUrl + "/api/v1/messages").retrieve().body(java.util.Map.class);
        return (Integer) response.get("total");
    }
}
