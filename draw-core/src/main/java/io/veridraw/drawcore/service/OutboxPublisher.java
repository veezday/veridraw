package io.veridraw.drawcore.service;

import io.veridraw.drawcore.event.OutboxEvent;
import io.veridraw.drawcore.repository.OutboxRepository;
import io.veridraw.shared.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<UUID, DomainEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        outboxRepository
                .findByPublishedFalse()
                .doOnNext(outbox -> log.info("Found event: id={}", outbox.getId()))
                .flatMap(this::publishEvent)
                .subscribe(
                        event -> log.info("Published event: {}", event.getEventType()),
                        error -> log.error("Failed to publish event", error));
    }

    private Mono<OutboxEvent> publishEvent(OutboxEvent event) {
        return Mono.fromFuture(() -> kafkaTemplate
                        .send("draw-events", event.getAggregateId(), event.getPayload())
                        .toCompletableFuture())
                .doOnSuccess(result -> log.info("Event sent to Kafka: {}", result.getRecordMetadata()))
                .then(outboxRepository.save(event.toBuilder()
                        .published(true)
                        .publishedAt(Instant.now())
                        .build()));
    }

    private DomainEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, DomainEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize outbox event", e);
        }
    }
}
