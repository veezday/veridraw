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

public class DrawControllerActivateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DrawRepository drawRepository;

    @BeforeEach
    void cleanDatabase() {
        drawRepository.deleteAll().block();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenDrawExistsInCreatedStatus_ShouldReturn200AndActivateIt() {
        Instant now = Instant.now().minusSeconds(1);
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Test Draw")
                        .description("Description")
                        .maxTickets(100)
                        .ticketPrice(BigDecimal.TEN)
                        .status(DrawStatus.CREATED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        UUID drawId = draw.getId();

        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", drawId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Draw.class)
                .value(activated -> {
                    assertThat(activated).isNotNull();
                    assertThat(activated.getId()).isEqualTo(drawId);
                    assertThat(activated.getStatus()).isEqualTo(DrawStatus.ACTIVATED);
                    assertThat(activated.getUpdatedAt()).isAfter(now);
                    assertThat(activated.getName()).isEqualTo("Test Draw");
                    assertThat(activated.getCompletedAt()).isNull();
                    assertThat(activated.getWinnerId()).isNull();
                });

        Draw fromDb = drawRepository.findById(drawId).block();
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getStatus()).isEqualTo(DrawStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenDrawDoesNotExist_ShouldReturn400() {
        UUID randomId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", randomId)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenDrawAlreadyActive_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Already Active")
                        .description("Desc")
                        .maxTickets(50)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.ACTIVATED)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();

        Draw fromDb = drawRepository.findById(draw.getId()).block();
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getStatus()).isEqualTo(DrawStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenDrawCompleted_ShouldReturn400() {
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
                .uri("/api/draws/{id}/activate", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenDrawCancelled_ShouldReturn400() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Cancelled Draw")
                        .description("Desc")
                        .maxTickets(50)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.CANCELLED)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenIdIsNotValidUuid_ShouldReturn400() {
        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", "not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void activateDraw_WhenUnauthenticated_ShouldReturn401() {
        UUID randomId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", randomId)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void activateDraw_WhenUserIsNotAdmin_ShouldReturn403() {
        UUID randomId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", randomId)
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateDraw_WhenCalledTwice_ShouldReturn400OnSecondCall() {
        Draw draw = drawRepository
                .save(Draw.builder()
                        .name("Idempotency Test")
                        .description("Desc")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .status(DrawStatus.CREATED)
                        .build())
                .block();

        assertThat(draw).isNotNull();
        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", draw.getId())
                .exchange()
                .expectStatus()
                .isOk();

        webTestClient
                .post()
                .uri("/api/draws/{id}/activate", draw.getId())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
