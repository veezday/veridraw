package io.veridraw.notifyworker.event;

import io.veridraw.notifyworker.mail.EmailService;
import io.veridraw.shared.event.DrawCompletedEvent;
import jakarta.transaction.Transactional;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DrawEventListener {

    private final InboxEventRepository inboxEventRepository;
    private final EmailService emailService;

    @KafkaListener(topics = "draw-events", groupId = "draw-events-group")
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
                log.info("Event {} already exists in inbox. Skipping (idempotency).", aggregatedId);
                ack.acknowledge();
                return;
            }

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
            log.info("Event {} saved to inbox. ACK sent.", aggregatedId);

            processInboxEvent(id);
        } catch (Exception e) {
            log.error("Failed to process event. Partition: {}, Offset: {}", partition, offset, e);
            throw new RuntimeException("Processing failed", e);
        }
    }

    @Transactional
    public void processInboxEvent(UUID aggregateId) {
        InboxEvent inbox = inboxEventRepository.findByAggregateId(aggregateId);

        if (inbox.getPublished()) {
            log.info("Event {} already processed. Skipping.", aggregateId);
            return;
        }

        try {
            DrawCompletedEvent event = (DrawCompletedEvent) inbox.getPayload();
            emailService.sendEmail(event);
            inbox.setPublished(true);
            inbox.setPublishedAt(Instant.now());
        } catch (Exception e) {
            log.warn("Email sending failed for drawId: {}.", aggregateId, e);
        }
    }
}
