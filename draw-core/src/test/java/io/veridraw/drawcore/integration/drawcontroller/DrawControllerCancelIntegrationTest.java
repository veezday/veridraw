package io.veridraw.drawcore.integration.drawcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

public class DrawControllerCancelIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DrawRepository drawRepository;

    @BeforeEach
    void cleanDatabase() {
        drawRepository.deleteAll().block();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelDraw_WhenStatusIsCreated_ShouldReturn200AndCancelIt() {
        Instant originalUpdatedAt = Instant.now().minusSeconds(10);
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Created Draw")
                        .description("Desc")
                        .maxTickets(50)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.CREATED)
                        .createdAt(originalUpdatedAt)
                        .updatedAt(originalUpdatedAt)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", draw.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Draw.class)
                .value(cancelled -> {
                    assertThat(cancelled).isNotNull();
                    assertThat(cancelled.getId()).isEqualTo(draw.getId());
                    assertThat(cancelled.getStatus()).isEqualTo(DrawStatus.CANCELLED);
                    assertThat(cancelled.getUpdatedAt()).isAfter(originalUpdatedAt);
                });

        Draw fromDb = drawRepository.findById(draw.getId()).block();
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getStatus()).isEqualTo(DrawStatus.CANCELLED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelDraw_WhenStatusIsActive_ShouldReturn200AndCancelIt() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Active Draw")
                        .description("Desc")
                        .maxTickets(50)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", draw.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Draw.class)
                .value(cancelled -> assertThat(cancelled.getStatus()).isEqualTo(DrawStatus.CANCELLED));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelDraw_WhenStatusIsCompleted_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Completed Draw")
                        .description("Desc")
                        .maxTickets(50)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.COMPLETED)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();

        assertThat(drawRepository.findById(draw.getId()).block().getStatus()).isEqualTo(DrawStatus.COMPLETED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelDraw_WhenAlreadyCancelled_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Already Cancelled")
                        .description("Desc")
                        .maxTickets(50)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.CANCELLED)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelDraw_WhenDrawDoesNotExist_ShouldReturn400() {
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelDraw_WhenIdIsNotValidUuid_ShouldReturn400() {
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", "not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void cancelDraw_WhenUnauthenticated_ShouldReturn401() {
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void cancelDraw_WhenUserIsNotAdmin_ShouldReturn403() {
        webTestClient
                .post()
                .uri("/api/draws/{id}/cancel", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
