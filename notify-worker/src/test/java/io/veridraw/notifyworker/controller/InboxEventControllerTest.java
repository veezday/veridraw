package io.veridraw.notifyworker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.veridraw.notifyworker.dto.InboxEventResponse;
import io.veridraw.notifyworker.service.InboxEventManagementService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class InboxEventControllerTest {

    @Mock
    private Counter adminResendTriggeredCounter;

    @Mock
    private DistributionSummary adminUnpublishedListSizeSummary;

    @Mock
    private InboxEventManagementService managementService;

    @InjectMocks
    private InboxEventController inboxEventController;

    @Test
    void getUnpublishedEvents_WhenEventsExist_ShouldReturnOkAndCallService() {
        InboxEventResponse mockResponse = mock(InboxEventResponse.class);
        List<InboxEventResponse> expectedList = List.of(mockResponse);
        when(managementService.getUnpublishedEvents()).thenReturn(expectedList);

        ResponseEntity<List<InboxEventResponse>> response = inboxEventController.getUnpublishedEvents();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedList);
        verify(managementService, times(1)).getUnpublishedEvents();
    }

    @Test
    void getUnpublishedEvents_WhenNoEvents_ShouldReturnOkWithEmptyList() {
        when(managementService.getUnpublishedEvents()).thenReturn(List.of());

        ResponseEntity<List<InboxEventResponse>> response = inboxEventController.getUnpublishedEvents();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(managementService, times(1)).getUnpublishedEvents();
    }

    @Test
    void resendUnpublishedEvents_WhenCalled_ShouldCallService() {
        inboxEventController.resendUnpublishedEvents();

        verify(managementService, times(1)).resendUnpublishedEvents();
    }
}
