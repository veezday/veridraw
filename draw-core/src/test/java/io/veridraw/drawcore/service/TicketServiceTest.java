package io.veridraw.drawcore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.dto.PurchaseTicketRequest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private Counter ticketPurchasesCounter;

    @Mock
    private Counter ticketPurchaseDuplicateErrorsCounter;

    @Mock
    private Counter ticketPurchaseDrawNotActiveErrorsCounter;

    @Mock
    private Counter ticketPurchaseSoldOutErrorsCounter;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DrawRepository drawRepository;

    @InjectMocks
    private TicketService ticketService;

    private final UUID drawId = UUID.randomUUID();
    private final PurchaseTicketRequest validDto = new PurchaseTicketRequest(drawId, "John Doe", "john@example.com");

    @Test
    void purchaseTicket_success_setsTimestampAndSaves() {
        Draw activeDraw = Draw.builder()
                .id(drawId)
                .status(DrawStatus.ACTIVATED)
                .maxTickets(100)
                .build();

        when(drawRepository.findByIdAndStatusLocked(drawId, DrawStatus.ACTIVATED))
                .thenReturn(Mono.just(activeDraw));
        when(ticketRepository.countByDrawId(drawId)).thenReturn(Mono.just(0L));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(ticketService.purchaseTicket(validDto))
                .assertNext(savedTicket -> {
                    assertThat(savedTicket.getDrawId()).isEqualTo(drawId);
                    assertThat(savedTicket.getParticipantName()).isEqualTo("John Doe");
                    assertThat(savedTicket.getParticipantEmail()).isEqualTo("john@example.com");
                    assertThat(savedTicket.getPurchasedAt()).isNotNull();
                    assertThat(savedTicket.getPurchasedAt()).isBeforeOrEqualTo(Instant.now());
                })
                .verifyComplete();

        verify(drawRepository).findByIdAndStatusLocked(drawId, DrawStatus.ACTIVATED);
        verify(ticketRepository).countByDrawId(drawId);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void purchaseTicket_drawNotFoundOrNotActive_throwsException() {
        when(drawRepository.findByIdAndStatusLocked(drawId, DrawStatus.ACTIVATED))
                .thenReturn(Mono.empty());

        StepVerifier.create(ticketService.purchaseTicket(validDto))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalStateException && ex.getMessage().equals("Draw not found or not active"))
                .verify();

        verify(ticketRepository, never()).countByDrawId(any());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchaseTicket_duplicateEmail_throwsCustomException() {
        Draw activeDraw = Draw.builder()
                .id(drawId)
                .status(DrawStatus.ACTIVATED)
                .maxTickets(100)
                .build();

        when(drawRepository.findByIdAndStatusLocked(drawId, DrawStatus.ACTIVATED))
                .thenReturn(Mono.just(activeDraw));
        when(ticketRepository.countByDrawId(drawId)).thenReturn(Mono.just(0L));
        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("Unique constraint violation")));

        StepVerifier.create(ticketService.purchaseTicket(validDto))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().equals("A ticket for this email already exists in this draw"))
                .verify();

        verify(ticketRepository).countByDrawId(drawId);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void purchaseTicket_whenMaxTicketsReached_throwsSoldOutException() {
        UUID testDrawId = UUID.randomUUID();
        Draw draw = Draw.builder()
                .id(testDrawId)
                .name("Sold Out Draw")
                .maxTickets(1)
                .status(DrawStatus.ACTIVATED)
                .build();

        when(drawRepository.findByIdAndStatusLocked(eq(testDrawId), eq(DrawStatus.ACTIVATED)))
                .thenReturn(Mono.just(draw));
        when(ticketRepository.countByDrawId(eq(testDrawId))).thenReturn(Mono.just(1L));

        PurchaseTicketRequest request = new PurchaseTicketRequest(testDrawId, "test@test.com", "Test User");

        StepVerifier.create(ticketService.purchaseTicket(request))
                .expectErrorMessage("All tickets for this draw have been sold")
                .verify();
    }

    @Test
    void getTicketsByDrawId_returnsFluxOfTickets() {
        Ticket ticket1 = Ticket.builder()
                .id(UUID.randomUUID())
                .drawId(drawId)
                .participantName("User 1")
                .build();
        Ticket ticket2 = Ticket.builder()
                .id(UUID.randomUUID())
                .drawId(drawId)
                .participantName("User 2")
                .build();

        when(ticketRepository.findByDrawId(drawId)).thenReturn(Flux.just(ticket1, ticket2));

        StepVerifier.create(ticketService.getTicketsByDrawId(drawId))
                .expectNext(ticket1, ticket2)
                .verifyComplete();

        verify(ticketRepository).findByDrawId(drawId);
    }

    @Test
    void getTicketsByDrawId_returnsEmptyFluxWhenNoTickets() {
        when(ticketRepository.findByDrawId(drawId)).thenReturn(Flux.empty());

        StepVerifier.create(ticketService.getTicketsByDrawId(drawId)).verifyComplete();
        verify(ticketRepository).findByDrawId(drawId);
    }
}
