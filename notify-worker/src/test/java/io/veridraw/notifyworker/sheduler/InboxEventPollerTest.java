package io.veridraw.notifyworker.sheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.service.EmailService;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxEventPollerTest {

    @Mock
    private Timer pollerBatchDurationTimer;

    @Mock
    private DistributionSummary pollerBatchSizeSummary;

    @Mock
    private Counter pollerEventSkippedAlreadyPublishedCounter;

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private InboxEventPoller inboxEventPoller;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(0);
                    if (runnable != null) {
                        runnable.run();
                    }
                    return null;
                })
                .when(pollerBatchDurationTimer)
                .record(any(Runnable.class));
    }

    @Test
    void processPendingEvents_WhenValidEvents_ShouldProcessAndSave() {
        UUID aggregateId = UUID.randomUUID();
        DrawCompletedEvent payload = DrawCompletedEvent.builder()
                .drawId(aggregateId)
                .winnerEmail("test@test.com")
                .winnerName("Test")
                .drawName("Test")
                .build();

        InboxEvent event = InboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build();

        when(inboxEventRepository.findByPublishedFalseForUpdate()).thenReturn(List.of(event));

        inboxEventPoller.processPendingEvents();

        verify(emailService, times(1)).sendEmail(payload);

        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxEventRepository, times(1)).save(captor.capture());

        InboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getPublished()).isTrue();
        assertThat(savedEvent.getPublishedAt()).isNotNull();
    }

    @Test
    void processPendingEvents_WhenNoEvents_ShouldNotProcessAnything() {
        when(inboxEventRepository.findByPublishedFalseForUpdate()).thenReturn(List.of());

        inboxEventPoller.processPendingEvents();

        verify(emailService, never()).sendEmail(any(DrawCompletedEvent.class));
        verify(inboxEventRepository, never()).save(any(InboxEvent.class));
    }

    @Test
    void processPendingEvents_WhenEventAlreadyPublished_ShouldSkipProcessing() {
        UUID aggregateId = UUID.randomUUID();
        DrawCompletedEvent payload = DrawCompletedEvent.builder()
                .drawId(aggregateId)
                .winnerEmail("test@test.com")
                .winnerName("Test")
                .drawName("Test")
                .build();

        InboxEvent event = InboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(payload)
                .published(true)
                .createdAt(Instant.now())
                .build();

        when(inboxEventRepository.findByPublishedFalseForUpdate()).thenReturn(List.of(event));

        inboxEventPoller.processPendingEvents();

        verify(emailService, never()).sendEmail(any(DrawCompletedEvent.class));
        verify(inboxEventRepository, never()).save(any(InboxEvent.class));
    }

    @Test
    void processPendingEvents_WhenOneEventFails_ShouldContinueProcessingOthers() {
        UUID successId = UUID.randomUUID();
        UUID failId = UUID.randomUUID();

        DrawCompletedEvent successPayload = DrawCompletedEvent.builder()
                .drawId(successId)
                .winnerEmail("test@test.com")
                .winnerName("Test")
                .drawName("Test")
                .build();

        DrawCompletedEvent failPayload = DrawCompletedEvent.builder()
                .drawId(failId)
                .winnerEmail("test@test.com")
                .winnerName("Test")
                .drawName("Test")
                .build();

        InboxEvent successEvent = InboxEvent.builder()
                .aggregateId(successId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(successPayload)
                .published(false)
                .createdAt(Instant.now())
                .build();

        InboxEvent failEvent = InboxEvent.builder()
                .aggregateId(failId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(failPayload)
                .published(false)
                .createdAt(Instant.now())
                .build();

        when(inboxEventRepository.findByPublishedFalseForUpdate()).thenReturn(List.of(failEvent, successEvent));
        doThrow(new RuntimeException("SMTP error")).when(emailService).sendEmail(failPayload);

        inboxEventPoller.processPendingEvents();

        verify(emailService, times(1)).sendEmail(failPayload);
        verify(emailService, times(1)).sendEmail(successPayload);
        verify(inboxEventRepository, times(1)).save(successEvent);
    }
}
