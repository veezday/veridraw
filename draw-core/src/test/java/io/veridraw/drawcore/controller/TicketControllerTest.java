package io.veridraw.drawcore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.dto.PurchaseTicketRequest;
import io.veridraw.drawcore.dto.TicketResponse;
import io.veridraw.drawcore.service.TicketService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private TicketController ticketController;

    private final UUID drawId = UUID.randomUUID();
    private final PurchaseTicketRequest purchaseRequest = mock(PurchaseTicketRequest.class);

    @Test
    void purchaseTicket_WhenValidRequest_ShouldReturnCreated() {
        when(ticketService.purchaseTicket(purchaseRequest)).thenReturn(Mono.just(mock(Ticket.class)));

        ResponseEntity<TicketResponse> response =
                ticketController.purchaseTicket(purchaseRequest).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        verify(ticketService, times(1)).purchaseTicket(purchaseRequest);
    }

    @Test
    void purchaseTicket_WhenErrorOccurs_ShouldReturnBadRequest() {
        when(ticketService.purchaseTicket(purchaseRequest))
                .thenReturn(Mono.error(new RuntimeException("Ошибка покупки")));

        ResponseEntity<TicketResponse> response =
                ticketController.purchaseTicket(purchaseRequest).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(ticketService, times(1)).purchaseTicket(purchaseRequest);
    }

    @Test
    void getTicketsByDrawId_WhenTicketsExist_ShouldReturnFluxWithItems() {
        when(ticketService.getTicketsByDrawId(drawId)).thenReturn(Flux.just(mock(Ticket.class)));

        List<TicketResponse> result =
                ticketController.getTicketsByDrawId(drawId).collectList().block();

        assertThat(result).hasSize(1);
        verify(ticketService, times(1)).getTicketsByDrawId(drawId);
    }

    @Test
    void getTicketsByDrawId_WhenNoTickets_ShouldReturnEmptyFlux() {
        when(ticketService.getTicketsByDrawId(drawId)).thenReturn(Flux.empty());

        List<TicketResponse> result =
                ticketController.getTicketsByDrawId(drawId).collectList().block();

        assertThat(result).isEmpty();
        verify(ticketService, times(1)).getTicketsByDrawId(drawId);
    }
}
