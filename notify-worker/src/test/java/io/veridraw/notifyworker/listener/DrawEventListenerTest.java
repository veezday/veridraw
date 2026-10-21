package io.veridraw.notifyworker.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class DrawEventListenerTest {

    @Mock
    private Counter kafkaMessagesSkippedIdempotentCounter;

    @Mock
    private Counter kafkaMessagesReceivedCounter;

    @Mock
    private Counter kafkaMessagesProcessingErrorsCounter;

    @Mock
    private Timer inboxProcessingDurationTimer;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private DrawEventListener drawEventListener;

    private final UUID validAggregateId = UUID.randomUUID();
    private final DrawCompletedEvent mockEvent = mock(DrawCompletedEvent.class);

    @Test
    void listen_newEvent_savesToInboxAndAcknowledges() {
        when(inboxEventRepository.existsByAggregateId(validAggregateId)).thenReturn(false);
        when(mockEvent.getDrawId()).thenReturn(validAggregateId);

        drawEventListener.listen(mockEvent, validAggregateId.toString(), 0, 1L, ack);

        ArgumentCaptor<InboxEvent> inboxCaptor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxEventRepository).save(inboxCaptor.capture());

        InboxEvent savedEvent = inboxCaptor.getValue();
        assertThat(savedEvent.getAggregateId()).isEqualTo(validAggregateId);
        assertThat(savedEvent.getAggregateType()).isEqualTo("Draw");
        assertThat(savedEvent.getEventType()).isEqualTo("DrawCompleted");
        assertThat(savedEvent.getPublished()).isFalse();
        assertThat(savedEvent.getCreatedAt()).isNotNull();

        verify(ack, times(1)).acknowledge();
    }

    @Test
    void listen_duplicateEvent_skipsProcessingAndAcknowledges() {
        when(inboxEventRepository.existsByAggregateId(validAggregateId)).thenReturn(true);

        drawEventListener.listen(mockEvent, validAggregateId.toString(), 0, 1L, ack);

        verify(inboxEventRepository, never()).save(any(InboxEvent.class));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void listen_invalidUuid_logsErrorAndDoesNotAcknowledge() {
        String invalidUuid = "not-a-valid-uuid";

        drawEventListener.listen(mockEvent, invalidUuid, 0, 1L, ack);

        verify(inboxEventRepository, never()).existsByAggregateId(any());
        verify(inboxEventRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void listen_repositorySaveFails_logsErrorAndDoesNotAcknowledge() {
        when(inboxEventRepository.existsByAggregateId(validAggregateId)).thenReturn(false);
        when(inboxEventRepository.save(any(InboxEvent.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        drawEventListener.listen(mockEvent, validAggregateId.toString(), 0, 1L, ack);

        verify(inboxEventRepository).save(any(InboxEvent.class));
        verify(ack, never()).acknowledge();
    }
}
