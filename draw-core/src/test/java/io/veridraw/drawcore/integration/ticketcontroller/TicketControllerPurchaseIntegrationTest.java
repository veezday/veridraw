package io.veridraw.drawcore.integration.ticketcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.dto.PurchaseTicketRequest;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

public class TicketControllerPurchaseIntegrationTest extends AbstractIntegrationTest {

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
    void purchaseTicket_WhenValidAndDrawActive_ShouldReturn201AndSave() {
        Draw draw = drawRepository.save(activeDraw().build()).block();
        PurchaseTicketRequest dto = new PurchaseTicketRequest(draw.getId(), "John Doe", "john@example.com");

        webTestClient
                .post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Ticket.class)
                .value(ticket -> {
                    assertThat(ticket.getDrawId()).isEqualTo(draw.getId());
                    assertThat(ticket.getParticipantEmail()).isEqualTo("john@example.com");
                    assertThat(ticket.getPurchasedAt()).isNotNull();
                });

        assertThat(ticketRepository.count().block()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void purchaseTicket_WhenInvalidEmail_ShouldReturn400() {
        Draw draw = drawRepository.save(activeDraw().build()).block();
        PurchaseTicketRequest dto = new PurchaseTicketRequest(draw.getId(), "John", "invalid-email");

        webTestClient
                .post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void purchaseTicket_WhenDrawNotActive_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Test")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.CREATED)
                        .build())
                .block();
        PurchaseTicketRequest dto = new PurchaseTicketRequest(draw.getId(), "John", "john@example.com");

        webTestClient
                .post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void purchaseTicket_WhenDuplicateEmailForDraw_ShouldReturn400() {
        Draw draw = drawRepository.save(activeDraw().build()).block();
        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("John")
                        .participantEmail("john@example.com")
                        .build())
                .block();

        PurchaseTicketRequest dto = new PurchaseTicketRequest(draw.getId(), "John Duplicate", "john@example.com");

        webTestClient
                .post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void purchaseTicket_WhenUnauthenticated_ShouldReturn401() {
        webTestClient
                .post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void purchaseTicket_WhenNotAdmin_ShouldReturn403() {
        PurchaseTicketRequest validRequest =
                new PurchaseTicketRequest(UUID.randomUUID(), "John Doe", "john.doe@example.com");
        webTestClient
                .post()
                .uri("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest)
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    private Draw.DrawBuilder activeDraw() {
        return Draw.builder()
                .name("Active Draw")
                .maxTickets(10)
                .ticketPrice(BigDecimal.ONE)
                .status(DrawStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void purchaseTicket_ConcurrentRequests_PreventOverselling() throws InterruptedException {
        Draw draw = drawRepository.save(activeDraw().maxTickets(1).build()).block();
        assertThat(draw).isNotNull();

        PurchaseTicketRequest request1 = new PurchaseTicketRequest(draw.getId(), "User One", "user1@example.com");
        PurchaseTicketRequest request2 = new PurchaseTicketRequest(draw.getId(), "User Two", "user2@example.com");

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                webTestClient
                        .post()
                        .uri("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request1)
                        .exchange()
                        .expectBody()
                        .consumeWith(
                                result -> statusCodes.add(result.getStatus().value()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                webTestClient
                        .post()
                        .uri("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request2)
                        .exchange()
                        .expectBody()
                        .consumeWith(
                                result -> statusCodes.add(result.getStatus().value()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread thread1 = new Thread(task1);
        Thread thread2 = new Thread(task2);
        thread1.start();
        thread2.start();

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        startLatch.countDown();

        thread1.join(5000);
        thread2.join(5000);

        Long totalTickets = ticketRepository.countByDrawId(draw.getId()).block();
        assertThat(totalTickets)
                .as("Количество проданных билетов не должно превышать maxTickets (1)")
                .isEqualTo(1L);

        assertThat(statusCodes)
                .as("Один запрос должен быть успешным (201), а второй отклонен (>= 400)")
                .contains(201)
                .anyMatch(status -> status >= 400);
    }
}
