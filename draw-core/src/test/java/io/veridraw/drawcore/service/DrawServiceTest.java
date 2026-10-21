package io.veridraw.drawcore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.OutboxEvent;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.dto.CreateDrawRequest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.OutboxRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javax.management.InstanceAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DrawServiceTest {

    @Mock
    private Counter drawCreateTransitionsCounter;

    @Mock
    private Counter drawActiveTransitionsCounter;

    @Mock
    private Counter drawCompleteTransitionsCounter;

    @Mock
    private Counter drawCancelleTransitionsCounter;

    @Mock
    private DrawRepository drawRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RandomNumberService randomNumberService;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private DrawService drawService;

    private final UUID drawId = UUID.randomUUID();
    private final UUID ticket1Id = UUID.randomUUID();
    private final UUID ticket2Id = UUID.randomUUID();

    @Test
    void createDraw_Success() {
        CreateDrawRequest dto = new CreateDrawRequest("MyDraw", "Desc", 100, BigDecimal.valueOf(50.0));

        when(drawRepository.existsByName("MyDraw")).thenReturn(Mono.just(false));
        when(drawRepository.save(any(Draw.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(drawService.createDraw(dto))
                .assertNext(draw -> {
                    assertEquals("MyDraw", draw.getName());
                    assertEquals(DrawStatus.CREATED, draw.getStatus());
                    assertNotNull(draw.getCreatedAt());
                    assertNotNull(draw.getUpdatedAt());
                })
                .verifyComplete();

        verify(drawRepository, times(1)).save(any(Draw.class));
    }

    @Test
    void createDraw_AlreadyExists() {
        CreateDrawRequest dto = new CreateDrawRequest("MyDraw", "Desc", 100, BigDecimal.valueOf(50.0));

        when(drawRepository.existsByName("MyDraw")).thenReturn(Mono.just(true));

        StepVerifier.create(drawService.createDraw(dto))
                .expectErrorMatches(ex -> ex instanceof InstanceAlreadyExistsException
                        && ex.getMessage().equals("Draw with name 'MyDraw' already exists"))
                .verify();

        verify(drawRepository, never()).save(any(Draw.class));
    }

    @Test
    void getAllDraws_returnsFluxOfDraws() {
        Draw draw1 = Draw.builder().id(UUID.randomUUID()).name("Draw 1").build();
        Draw draw2 = Draw.builder().id(UUID.randomUUID()).name("Draw 2").build();
        when(drawRepository.findAll()).thenReturn(Flux.just(draw1, draw2));

        StepVerifier.create(drawService.getAllDraws()).expectNext(draw1, draw2).verifyComplete();
    }

    @Test
    void getAllDraws_returnsEmptyFluxWhenNoDraws() {
        when(drawRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(drawService.getAllDraws()).verifyComplete();
    }

    @Test
    void getDrawById_found_returnsDraw() {
        Draw draw = Draw.builder().id(drawId).name("Test Draw").build();
        when(drawRepository.findById(drawId)).thenReturn(Mono.just(draw));

        StepVerifier.create(drawService.getDrawById(drawId)).expectNext(draw).verifyComplete();
    }

    @Test
    void getDrawById_notFound_returnsEmpty() {
        when(drawRepository.findById(drawId)).thenReturn(Mono.empty());

        StepVerifier.create(drawService.getDrawById(drawId)).verifyComplete();
    }

    @Test
    void cancelDraw_success_updatesStatusAndTimestamp() {
        Draw draw = Draw.builder()
                .id(drawId)
                .name("Test")
                .status(DrawStatus.CREATED)
                .build();

        when(drawRepository.findByIdAndStatusIn(eq(drawId), eq(List.of(DrawStatus.CREATED, DrawStatus.ACTIVATED))))
                .thenReturn(Mono.just(draw));
        when(drawRepository.save(any(Draw.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(drawService.cancelDraw(drawId))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo(DrawStatus.CANCELLED);
                    assertThat(result.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(drawRepository).save(any(Draw.class));
    }

    @Test
    void cancelDraw_notFoundOrWrongStatus_throwsException() {
        when(drawRepository.findByIdAndStatusIn(eq(drawId), eq(List.of(DrawStatus.CREATED, DrawStatus.ACTIVATED))))
                .thenReturn(Mono.empty());

        StepVerifier.create(drawService.cancelDraw(drawId))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().equals("Draw not found or cannot be cancelled in its current status"))
                .verify();

        verify(drawRepository, never()).save(any(Draw.class));
    }

    @Test
    void activateDraw_success_updatesStatusAndTimestamp() {
        Draw draw = Draw.builder()
                .id(drawId)
                .name("Test")
                .status(DrawStatus.CREATED)
                .build();

        when(drawRepository.findByIdAndStatus(eq(drawId), eq(DrawStatus.CREATED)))
                .thenReturn(Mono.just(draw));
        when(drawRepository.save(any(Draw.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(drawService.activateDraw(drawId))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo(DrawStatus.ACTIVATED);
                    assertThat(result.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(drawRepository).save(any(Draw.class));
    }

    @Test
    void activateDraw_notFoundOrWrongStatus_throwsException() {
        when(drawRepository.findByIdAndStatus(eq(drawId), eq(DrawStatus.CREATED)))
                .thenReturn(Mono.empty());

        StepVerifier.create(drawService.activateDraw(drawId))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().equals("Draw not found or not in CREATED status"))
                .verify();

        verify(drawRepository, never()).save(any(Draw.class));
    }

    @Test
    void completeDraw_success_updatesDrawAndSavesOutbox() {
        Draw draw = Draw.builder()
                .id(drawId)
                .name("Test Draw")
                .status(DrawStatus.ACTIVATED)
                .build();

        Ticket ticket1 = Ticket.builder()
                .id(ticket1Id)
                .participantEmail("loser@test.com")
                .participantName("Loser")
                .build();
        Ticket ticket2 = Ticket.builder()
                .id(ticket2Id)
                .participantEmail("winner@test.com")
                .participantName("Winner")
                .build();

        when(drawRepository.findByIdAndStatus(drawId, DrawStatus.ACTIVATED)).thenReturn(Mono.just(draw));
        when(ticketRepository.findByDrawId(drawId)).thenReturn(Flux.just(ticket1, ticket2));

        when(randomNumberService.getRandomInteger(0, 1)).thenReturn(Mono.just(1));

        when(drawRepository.save(any(Draw.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(drawService.completeDraw(drawId))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo(DrawStatus.COMPLETED);
                    assertThat(result.getWinnerId()).isEqualTo(ticket2Id);
                    assertThat(result.getCompletedAt()).isNotNull();
                    assertThat(result.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        OutboxEvent savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getAggregateType()).isEqualTo("Draw");
        assertThat(savedOutbox.getEventType()).isEqualTo("DrawCompleted");
        assertThat(savedOutbox.getPublished()).isFalse();

        DrawCompletedEvent payload = (DrawCompletedEvent) savedOutbox.getPayload();
        assertThat(payload.getDrawId()).isEqualTo(drawId);
        assertThat(payload.getWinnerEmail()).isEqualTo("winner@test.com");
        assertThat(payload.getWinnerName()).isEqualTo("Winner");
    }

    @Test
    void completeDraw_drawNotFoundOrNotActive_throwsException() {
        when(drawRepository.findByIdAndStatus(drawId, DrawStatus.ACTIVATED)).thenReturn(Mono.empty());

        StepVerifier.create(drawService.completeDraw(drawId))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().equals("Draw not found or not in ACTIVATED status"))
                .verify();

        verify(ticketRepository, never()).findByDrawId(any());
        verify(drawRepository, never()).save(any());
    }

    @Test
    void completeDraw_lessThanTwoTickets_throwsException() {
        Draw draw = Draw.builder().id(drawId).status(DrawStatus.ACTIVATED).build();
        Ticket onlyTicket = Ticket.builder().id(ticket1Id).build();

        when(drawRepository.findByIdAndStatus(drawId, DrawStatus.ACTIVATED)).thenReturn(Mono.just(draw));
        when(ticketRepository.findByDrawId(drawId)).thenReturn(Flux.just(onlyTicket));

        StepVerifier.create(drawService.completeDraw(drawId))
                .expectErrorMatches(ex -> ex instanceof IllegalStateException
                        && ex.getMessage().equals("Cannot complete draw: at least 2 tickets are required"))
                .verify();

        verify(randomNumberService, never()).getRandomInteger(anyInt(), anyInt());
        verify(drawRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }
}
