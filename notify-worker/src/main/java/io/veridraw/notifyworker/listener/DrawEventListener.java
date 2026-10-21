package io.veridraw.notifyworker.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.sheduler.InboxEventPoller;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Слушатель событий Kafka для топика {@code draw-events}.
 * Обеспечивает идемпотентную обработку: проверяет наличие события, сохраняет его в БД,
 * подтверждает получение (ACK) и передает на дальнейшую обработку.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrawEventListener {

    private final InboxEventRepository inboxEventRepository;
    private final InboxEventPoller inboxEventPoller;

    private final Counter kafkaMessagesReceivedCounter;
    private final Counter kafkaMessagesSkippedIdempotentCounter;
    private final Counter kafkaMessagesProcessingErrorsCounter;
    private final Timer inboxProcessingDurationTimer;
    private final MeterRegistry meterRegistry;

    /**
     * Обрабатывает входящее сообщение о завершении розыгрыша.
     *
     * @param event тело события
     * @param aggregatedId ключ сообщения (ID агрегата)
     * @param partition номер партиции
     * @param offset смещение сообщения
     * @param ack механизм подтверждения обработки
     */
    @KafkaListener(topics = "draw-events", groupId = "draw-events-group")
    @Transactional
    public void listen(
            @Payload DrawCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregatedId,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info(
                "Received event: type={}, drawId={}, partition={}, offset={}",
                event.getClass().getSimpleName(),
                event.getDrawId(),
                partition,
                offset);

        try {
            UUID id = UUID.fromString(aggregatedId);

            if (inboxEventRepository.existsByAggregateId(id)) {
                log.info("Event {} already exists. Skipping (idempotency).", aggregatedId);
                kafkaMessagesSkippedIdempotentCounter.increment();
                ack.acknowledge();
                return;
            }

            Timer.Sample sample = Timer.start(meterRegistry);

            try {
                InboxEvent inbox = InboxEvent.builder()
                        .aggregateId(id)
                        .aggregateType("Draw")
                        .eventType("DrawCompleted")
                        .payload(event)
                        .published(false)
                        .createdAt(Instant.now())
                        .build();

                inboxEventRepository.save(inbox);
                ack.acknowledge();
                inboxEventPoller.processPendingEvents();

                kafkaMessagesReceivedCounter.increment();
                log.info("Event {} saved to inbox. ACK sent.", aggregatedId);
            } finally {
                sample.stop(inboxProcessingDurationTimer);
            }

        } catch (Exception e) {
            log.error("Failed to process event. Partition: {}, Offset: {}", partition, offset, e);
            kafkaMessagesProcessingErrorsCounter.increment();
        }
    }
}
