package io.veridraw.drawcore.integration.ticketcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

public class TicketControllerGetByDrawIdIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private DrawRepository drawRepository;

    @BeforeEach
    void cleanDatabase() {
        ticketRepository.deleteAll().block();
        drawRepository.deleteAll().block();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTicketsByDrawId_WhenTicketsExist_ShouldReturn200AndList() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Test")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        ticketRepository
                .saveAll(List.of(
                        Ticket.builder()
                                .drawId(draw.getId())
                                .participantName("Alice")
                                .participantEmail("alice@test.com")
                                .purchasedAt(Instant.now())
                                .build(),
                        Ticket.builder()
                                .drawId(draw.getId())
                                .participantName("Bob")
                                .participantEmail("bob@test.com")
                                .purchasedAt(Instant.now())
                                .build()))
                .blockLast();

        webTestClient
                .get()
                .uri("/api/tickets/draw/{drawId}", draw.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Ticket.class)
                .value(tickets -> {
                    assertThat(tickets).hasSize(2);
                    assertThat(tickets)
                            .extracting(Ticket::getParticipantEmail)
                            .containsExactlyInAnyOrder("alice@test.com", "bob@test.com");
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTicketsByDrawId_WhenNoTickets_ShouldReturn200AndEmptyList() {
        UUID randomDrawId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/tickets/draw/{drawId}", randomDrawId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Ticket.class)
                .value(tickets -> assertThat(tickets).isEmpty());
    }

    @Test
    void getTicketsByDrawId_WhenUnauthenticated_ShouldReturn401() {
        webTestClient
                .get()
                .uri("/api/tickets/draw/{drawId}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void getTicketsByDrawId_WhenNotAdmin_ShouldReturn403() {
        webTestClient
                .get()
                .uri("/api/tickets/draw/{drawId}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
