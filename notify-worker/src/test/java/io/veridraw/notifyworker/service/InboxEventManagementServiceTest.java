package io.veridraw.notifyworker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.dto.InboxEventResponse;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.sheduler.InboxEventPoller;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxEventManagementServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    @Mock
    private InboxEventPoller inboxEventPoller;

    @InjectMocks
    private InboxEventManagementService managementService;

    @Test
    void getUnpublishedEvents_WhenEventsExist_ShouldReturnMappedList() {
        UUID aggregateId = UUID.randomUUID();
        InboxEvent mockEvent = InboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(aggregateId)
                        .winnerEmail("test@mail.com")
                        .winnerName("Test")
                        .drawName("Test Draw")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build();

        when(inboxEventRepository.findByPublishedFalse()).thenReturn(List.of(mockEvent));

        List<InboxEventResponse> result = managementService.getUnpublishedEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).aggregateId()).isEqualTo(aggregateId);
        assertThat(result.get(0).eventType()).isEqualTo("DrawCompleted");

        verify(inboxEventRepository, times(1)).findByPublishedFalse();
    }

    @Test
    void getUnpublishedEvents_WhenNoEvents_ShouldReturnEmptyList() {
        when(inboxEventRepository.findByPublishedFalse()).thenReturn(List.of());

        List<InboxEventResponse> result = managementService.getUnpublishedEvents();

        assertThat(result).isEmpty();
        verify(inboxEventRepository, times(1)).findByPublishedFalse();
    }

    @Test
    void resendUnpublishedEvents_WhenCalled_ShouldDelegateToPoller() {
        managementService.resendUnpublishedEvents();

        verify(inboxEventPoller, times(1)).processPendingEvents();
    }

    @Test
    void resendUnpublishedEvents_WhenPollerFails_ShouldPropagateException() {
        RuntimeException expectedException = new RuntimeException("Database connection lost during resend");
        doThrow(expectedException).when(inboxEventPoller).processPendingEvents();

        assertThatThrownBy(() -> managementService.resendUnpublishedEvents())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection lost during resend");

        verify(inboxEventPoller, times(1)).processPendingEvents();
    }
}
