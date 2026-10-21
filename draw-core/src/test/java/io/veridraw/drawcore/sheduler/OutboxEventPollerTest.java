package io.veridraw.drawcore.sheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.drawcore.domain.OutboxEvent;
import io.veridraw.drawcore.repository.OutboxRepository;
import io.veridraw.shared.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OutboxEventPollerTest {

    @Mock
    private Counter outboxPublishedCounter;

    @Mock
    private Counter outboxPublishErrorsCounter;

    @Mock
    private Timer outboxPublishDurationTimer;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<UUID, DomainEvent> kafkaTemplate;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks
    private OutboxEventPoller outboxEventPoller;

    private final UUID aggregateId = UUID.randomUUID();

    @Test
    void publishPendingEvents_successfullyPublishesAndUpdatesStatus() {
        DomainEvent mockPayload = mock(DomainEvent.class);
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .eventType("TestEvent")
                .payload(mockPayload)
                .published(false)
                .build();

        when(outboxRepository.findByPublishedFalse()).thenReturn(Flux.just(event));

        SendResult<UUID, DomainEvent> sendResult = mock(SendResult.class);
        CompletableFuture<SendResult<UUID, DomainEvent>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq("draw-events"), eq(aggregateId), eq(mockPayload)))
                .thenReturn(future);
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent saved = invocation.getArgument(0);
            return Mono.just(
                    saved.toBuilder().published(true).publishedAt(Instant.now()).build());
        });

        outboxEventPoller.publishPendingEvents();

        verify(kafkaTemplate, timeout(1000).times(1)).send(eq("draw-events"), eq(aggregateId), eq(mockPayload));
        verify(outboxRepository, timeout(1000).times(1))
                .save(argThat(saved -> saved.getPublished() && saved.getPublishedAt() != null));
    }

    @Test
    void publishPendingEvents_kafkaFailure_doesNotMarkAsPublished() {
        DomainEvent mockPayload = mock(DomainEvent.class);
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .eventType("TestEvent")
                .payload(mockPayload)
                .published(false)
                .build();

        when(outboxRepository.findByPublishedFalse()).thenReturn(Flux.just(event));

        CompletableFuture<SendResult<UUID, DomainEvent>> failedFuture =
                CompletableFuture.failedFuture(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        outboxEventPoller.publishPendingEvents();

        verify(kafkaTemplate, timeout(1000).times(1)).send(any(), any(), any());

        verify(outboxRepository, after(1000).never()).save(any(OutboxEvent.class));
    }

    @Test
    void publishPendingEvents_noPendingEvents_doesNothing() {
        when(outboxRepository.findByPublishedFalse()).thenReturn(Flux.empty());

        outboxEventPoller.publishPendingEvents();

        verify(kafkaTemplate, after(500).never()).send(any(), any(), any());
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }
}
