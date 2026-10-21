package io.veridraw.drawcore.integration.drawcontroller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.domain.OutboxEvent;
import io.veridraw.drawcore.domain.Ticket;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import io.veridraw.drawcore.repository.OutboxRepository;
import io.veridraw.drawcore.repository.TicketRepository;
import io.veridraw.drawcore.service.RandomNumberService;
import io.veridraw.drawcore.sheduler.OutboxEventPoller;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Mono;

public class DrawControllerCompleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DrawRepository drawRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxEventPoller outboxEventPoller;

    @MockitoSpyBean
    private RandomNumberService randomNumberService;

    @BeforeEach
    void cleanUp() {
        drawRepository.deleteAll().block();
        ticketRepository.deleteAll().block();
        outboxRepository.deleteAll().block();

        reset(randomNumberService);
        doCallRealMethod().when(randomNumberService).getRandomInteger(anyInt(), anyInt());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void completeDraw_WhenValid_ShouldUpdateDraw_SaveOutbox_AndPublishToKafka() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Mega Draw")
                        .description("Win big")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.TEN)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Alice")
                        .participantEmail("alice@test.com")
                        .purchasedAt(Instant.now())
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Bob")
                        .participantEmail("bob@test.com")
                        .purchasedAt(Instant.now())
                        .build())
                .block();

        webTestClient
                .post()
                .uri("/api/draws/{id}/complete", draw.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("COMPLETED")
                .jsonPath("$.winnerId")
                .isNotEmpty();

        Draw savedDraw = drawRepository.findById(draw.getId()).block();
        assertThat(savedDraw.getStatus()).isEqualTo(DrawStatus.COMPLETED);
        assertThat(savedDraw.getWinnerId()).isNotNull();
        assertThat(savedDraw.getCompletedAt()).isNotNull();

        var outboxEvents = outboxRepository.findByPublishedFalse().collectList().block();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("DrawCompleted");

        outboxEventPoller.publishPendingEvents();
        OutboxEvent updatedOutboxEvent =
                waitForOutboxPublish(outboxEvents.get(0).getId());

        assertThat(updatedOutboxEvent.getPublished()).isTrue();
        assertThat(updatedOutboxEvent.getPublishedAt()).isAfterOrEqualTo(updatedOutboxEvent.getCreatedAt());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void completeDraw_WhenLessThan2Tickets_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Small Draw")
                        .maxTickets(1)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Only One")
                        .participantEmail("one@test.com")
                        .build())
                .block();

        webTestClient
                .post()
                .uri("/api/draws/{id}/complete", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();

        assertThat(drawRepository.findById(draw.getId()).block().getStatus()).isEqualTo(DrawStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void completeDraw_WhenDrawIsCompleted_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Already Done")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.COMPLETED)
                        .build())
                .block();

        webTestClient
                .post()
                .uri("/api/draws/{id}/complete", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void completeDraw_WhenRandomGenerationFails_ShouldReturn400_AndNotModifyDraw() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Unlucky Draw")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("A")
                        .participantEmail("a@test.com")
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("B")
                        .participantEmail("b@test.com")
                        .build())
                .block();

        doReturn(Mono.<Integer>error(new RuntimeException("Simulated generation failure")))
                .when(randomNumberService)
                .getRandomInteger(anyInt(), anyInt());

        webTestClient
                .post()
                .uri("/api/draws/{id}/complete", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();

        assertThat(drawRepository.findById(draw.getId()).block().getStatus()).isEqualTo(DrawStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void completeDraw_WhenConcurrentModificationOccurs_ShouldRollbackTransactionAndKeepDrawActivated()
            throws InterruptedException {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Concurrent Draw")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Alice")
                        .participantEmail("alice@test.com")
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Bob")
                        .participantEmail("bob@test.com")
                        .build())
                .block();

        doAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Mono<Integer> realResult = (Mono<Integer>) invocation.callRealMethod();
                    return realResult.delayElement(Duration.ofMillis(500));
                })
                .when(randomNumberService)
                .getRandomInteger(anyInt(), anyInt());

        Thread apiThread = new Thread(() -> {
            webTestClient
                    .post()
                    .uri("/api/draws/{id}/complete", draw.getId())
                    .exchange()
                    .expectBody()
                    .consumeWith(result -> {
                        int status = result.getStatus().value();
                        assertThat(status)
                                .as("Ожидаем 409 или 500 из-за OptimisticLockingFailureException")
                                .isIn(409, 500);
                    });
        });
        apiThread.start();

        Thread.sleep(200);

        Draw concurrentDraw = drawRepository.findById(draw.getId()).block();
        concurrentDraw.setName("Modified By Concurrent Process");
        drawRepository.save(concurrentDraw).block();

        apiThread.join(5000);

        Draw finalDraw = drawRepository.findById(draw.getId()).block();
        assertThat(finalDraw.getStatus()).isEqualTo(DrawStatus.ACTIVATED);
        assertThat(finalDraw.getName()).isEqualTo("Modified By Concurrent Process");
        assertThat(finalDraw.getWinnerId()).isNull();
        assertThat(finalDraw.getCompletedAt()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void completeDraw_WhenConcurrentModificationOccurs_ShouldPreventLostUpdate() throws Exception {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Concurrent Draw")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Alice")
                        .participantEmail("alice@test.com")
                        .build())
                .block();

        ticketRepository
                .save(Ticket.builder()
                        .drawId(draw.getId())
                        .participantName("Bob")
                        .participantEmail("bob@test.com")
                        .build())
                .block();

        doAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Mono<Integer> realResult = (Mono<Integer>) invocation.callRealMethod();
                    return realResult.delayElement(Duration.ofMillis(500));
                })
                .when(randomNumberService)
                .getRandomInteger(anyInt(), anyInt());

        CountDownLatch attackerReadLatch = new CountDownLatch(1);
        CountDownLatch attackerWriteLatch = new CountDownLatch(1);
        AtomicReference<Throwable> attackerException = new AtomicReference<>();

        Thread attackerThread = new Thread(() -> {
            try {
                Draw staleDraw = drawRepository.findById(draw.getId()).block();
                staleDraw.setName("OVERWRITTEN_BY_ATTACKER");
                attackerReadLatch.countDown();
                attackerWriteLatch.await(5, TimeUnit.SECONDS);
                drawRepository.save(staleDraw).block();
            } catch (Throwable e) {
                attackerException.set(e);
            }
        });
        attackerThread.start();

        assertThat(attackerReadLatch.await(2, TimeUnit.SECONDS)).isTrue();

        webTestClient
                .post()
                .uri("/api/draws/{id}/complete", draw.getId())
                .exchange()
                .expectStatus()
                .isOk();

        attackerWriteLatch.countDown();
        attackerThread.join(3000);

        Draw finalDraw = drawRepository.findById(draw.getId()).block();
        assertThat(finalDraw.getStatus()).isEqualTo(DrawStatus.COMPLETED);
        assertThat(finalDraw.getName()).isNotEqualTo("OVERWRITTEN_BY_ATTACKER");
        assertThat(finalDraw.getWinnerId()).isNotNull();
        assertThat(finalDraw.getCompletedAt()).isNotNull();
        assertThat(attackerException.get())
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(randomNumberService);
    }

    private OutboxEvent waitForOutboxPublish(UUID outboxId) {
        Instant deadline = Instant.now().plusMillis(10_000);
        while (Instant.now().isBefore(deadline)) {
            OutboxEvent event = outboxRepository.findById(outboxId).block();
            if (event != null && Boolean.TRUE.equals(event.getPublished())) {
                return event;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return outboxRepository.findById(outboxId).block();
    }
}
