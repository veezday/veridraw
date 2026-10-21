package io.veridraw.drawcore.integration.drawcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.test.StepVerifier;

public class DrawControllerGetByIdIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DrawRepository drawRepository;

    @BeforeEach
    void cleanDatabase() {
        drawRepository.deleteAll().block();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrawById_WhenDrawExists_ShouldReturn200AndDraw() {
        Draw savedDraw = drawRepository
                .save(Draw.builder()
                        .name("Lucky Draw")
                        .description("Win a prize!")
                        .maxTickets(100)
                        .ticketPrice(BigDecimal.TEN)
                        .build())
                .block();

        assertThat(savedDraw).isNotNull();
        UUID drawId = savedDraw.getId();

        webTestClient
                .get()
                .uri("/api/draws/{id}", drawId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Draw.class)
                .value(draw -> {
                    assertThat(draw.getId()).isEqualTo(drawId);
                    assertThat(draw.getName()).isEqualTo("Lucky Draw");
                    assertThat(draw.getDescription()).isEqualTo("Win a prize!");
                    assertThat(draw.getMaxTickets()).isEqualTo(100);
                    assertThat(draw.getTicketPrice()).isEqualByComparingTo(BigDecimal.TEN);
                    assertThat(draw.getStatus()).isEqualTo(DrawStatus.CREATED);
                    assertThat(draw.getWinnerId()).isNull();
                    assertThat(draw.getCreatedAt()).isNotNull();
                    assertThat(draw.getUpdatedAt()).isNotNull();
                    assertThat(draw.getCompletedAt()).isNull();
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrawById_WhenDrawDoesNotExist_ShouldReturn404() {
        UUID randomId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/draws/{id}", randomId)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrawById_WhenIdIsNotValidUuid_ShouldReturn400() {
        webTestClient
                .get()
                .uri("/api/draws/{id}", "not-a-valid-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrawById_WhenIdIsEmpty_ShouldReturn404() {
        webTestClient.get().uri("/api/draws/").exchange().expectStatus().isNotFound();
    }

    @Test
    void getDrawById_WhenUnauthenticated_ShouldReturn401() {
        UUID randomId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/draws/{id}", randomId)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDrawById_WhenUserIsNotAdmin_ShouldReturn403() {
        UUID randomId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/draws/{id}", randomId)
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrawById_ShouldReturnMonoWithSingleElement() {
        Draw savedDraw = drawRepository
                .save(Draw.builder()
                        .name("Stream Test")
                        .description("Desc")
                        .maxTickets(5)
                        .ticketPrice(BigDecimal.ONE)
                        .build())
                .block();

        webTestClient
                .get()
                .uri("/api/draws/{id}", savedDraw.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(Draw.class)
                .getResponseBody()
                .as(StepVerifier::create)
                .assertNext(draw -> {
                    assertThat(draw.getName()).isEqualTo("Stream Test");
                    assertThat(draw.getId()).isEqualTo(savedDraw.getId());
                })
                .verifyComplete();
    }
}
