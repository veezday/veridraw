package io.veridraw.drawcore.service;

import io.veridraw.drawcore.event.OutboxEvent;
import io.veridraw.drawcore.repository.OutboxRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        outboxRepository
                .findByPublishedFalse()
                .flatMap(this::publishEvent)
                .subscribe(
                        event -> log.info("Published event: {}", event.getEventType()),
                        error -> log.error("Failed to publish event", error));
    }

    private Mono<OutboxEvent> publishEvent(OutboxEvent event) {
        return Mono.fromFuture(() -> kafkaTemplate
                        .send(
                                "draw-events",
                                event.getEventType(),
                                event.getPayload().asString())
                        .toCompletableFuture())
                .doOnSuccess(result -> log.info("Event sent to Kafka: {}", result.getRecordMetadata()))
                .then(outboxRepository.save(event.toBuilder()
                        .published(true)
                        .publishedAt(Instant.now())
                        .build()));
    }
}
