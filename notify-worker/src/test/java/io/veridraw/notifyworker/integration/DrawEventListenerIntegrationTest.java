package io.veridraw.notifyworker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class DrawEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private InboxEventRepository inboxEventRepository;

    @BeforeEach
    void setUp() {
        inboxEventRepository.deleteAll();
    }

    @Test
    void listen_WhenValid_ShouldProcessMessageSaveToDatabaseAndSendEmail() {
        UUID drawId = UUID.randomUUID();
        DrawCompletedEvent event = DrawCompletedEvent.builder()
                .drawId(drawId)
                .winnerEmail("winner@mailpit.local")
                .winnerName("John Doe")
                .drawName("Super Draw")
                .build();

        kafkaTemplate.send("draw-events", drawId.toString(), event).join();

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    InboxEvent savedEvent = inboxEventRepository.findByAggregateId(drawId);

                    assertThat(savedEvent).isNotNull();
                    assertThat(savedEvent.getAggregateType()).isEqualTo("Draw");
                    assertThat(savedEvent.getEventType()).isEqualTo("DrawCompleted");
                    assertThat(savedEvent.getPublished()).isTrue();
                    assertThat(savedEvent.getPublishedAt()).isNotNull();
                    assertThat(savedEvent.getPayload()).isNotNull();

                    assertThat(getMailpitMessageCount())
                            .as("Email should be sent immediately upon message processing")
                            .isEqualTo(1);
                });
    }

    @Test
    void listen_WhenEventAlreadyExists_ShouldSkipProcessing() {
        UUID drawId = UUID.randomUUID();
        DrawCompletedEvent event = DrawCompletedEvent.builder()
                .drawId(drawId)
                .winnerEmail("winner@mailpit.local")
                .winnerName("John Doe")
                .drawName("Super Draw")
                .build();

        kafkaTemplate.send("draw-events", drawId.toString(), event).join();
        kafkaTemplate.send("draw-events", drawId.toString(), event).join();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long totalCount = inboxEventRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(drawId))
                    .count();
            assertThat(totalCount)
                    .as("There should be only one record in the DB with this aggregateId")
                    .isEqualTo(1);

            InboxEvent savedEvent = inboxEventRepository.findByAggregateId(drawId);
            assertThat(savedEvent).isNotNull();
            assertThat(savedEvent.getPublished()).isTrue();
            assertThat(savedEvent.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void listen_WhenInvalidUUID_ShouldHandle() {
        DrawCompletedEvent event = DrawCompletedEvent.builder()
                .drawId(UUID.randomUUID())
                .winnerEmail("test@test.com")
                .winnerName("Test")
                .drawName("Test")
                .build();

        kafkaTemplate.send("draw-events", "invalid-uuid-string", event).join();

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(inboxEventRepository.count()).isEqualTo(0);
        });
    }
}
