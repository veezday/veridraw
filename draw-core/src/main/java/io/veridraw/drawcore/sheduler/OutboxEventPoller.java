package io.veridraw.drawcore.sheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.drawcore.domain.OutboxEvent;
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

/**
 * Издатель событий из паттерна Transactional Outbox.
 * Периодически опрашивает базу данных на наличие неопубликованных событий
 * и отправляет их в Kafka, после чего помечает как опубликованные.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<UUID, DomainEvent> kafkaTemplate;

    private final Counter outboxPublishedCounter;
    private final Counter outboxPublishErrorsCounter;
    private final Timer outboxPublishDurationTimer;
    private final MeterRegistry meterRegistry;

    /**
     * Периодическая задача для публикации накопленных событий.
     * Выполняется каждые 5 секунд.
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        outboxRepository
                .findByPublishedFalse()
                .doOnNext(outbox -> log.info("Found pending listener: id={}", outbox.getId()))
                .flatMap(event -> publishEvent(event)
                        .doOnError(e -> log.error("Failed to publish listener id={}", event.getId(), e))
                        // Игнорируем ошибку для конкретного события, чтобы не прерывать поток обработки остальных
                        .onErrorResume(e -> Mono.empty()))
                .subscribe(
                        event -> log.info(
                                "Successfully published listener: type={}, id={}", event.getEventType(), event.getId()),
                        error -> log.error("Unexpected critical error in outbox publishing stream", error));
    }

    /**
     * Отправляет конкретное событие в Kafka и обновляет его статус в БД.
     * Также замеряет время выполнения и обновляет метрики.
     *
     * @param event событие для публикации
     * @return {@link Mono} с обновленным событием (помеченным как опубликованное)
     */
    private Mono<OutboxEvent> publishEvent(OutboxEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);

        return Mono.fromFuture(kafkaTemplate.send("draw-events", event.getAggregateId(), event.getPayload()))
                .doOnSuccess(result -> log.debug("Event sent to Kafka, metadata: {}", result.getRecordMetadata()))
                .then(Mono.defer(() -> outboxRepository.save(event.toBuilder()
                        .published(true)
                        .publishedAt(Instant.now())
                        .build())))
                .doOnSuccess(ignore -> {
                    outboxPublishedCounter.increment();
                    sample.stop(outboxPublishDurationTimer);
                })
                .doOnError(ignore -> {
                    outboxPublishErrorsCounter.increment();
                    sample.stop(outboxPublishDurationTimer);
                });
    }
}
