package io.veridraw.drawcore.integration.drawcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.test.StepVerifier;

public class DrawControllerGetAllDrawsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DrawRepository drawRepository;

    @BeforeEach
    void setUp() {
        drawRepository.deleteAll().block();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllDraws_WhenDrawsExist_ShouldReturn200AndListOfDraws() {
        Instant now = Instant.now();
        Draw draw1 = Draw.builder()
                .name("Draw 1")
                .description("Description 1")
                .maxTickets(100)
                .ticketPrice(BigDecimal.valueOf(50.0))
                .status(DrawStatus.CREATED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Draw draw2 = Draw.builder()
                .name("Draw 2")
                .description("Description 2")
                .maxTickets(50)
                .ticketPrice(BigDecimal.valueOf(100.0))
                .status(DrawStatus.ACTIVATED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        drawRepository.saveAll(List.of(draw1, draw2)).blockLast();

        webTestClient
                .get()
                .uri("/api/draws")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Draw.class)
                .value(draws -> {
                    assertThat(draws).hasSize(2);

                    assertThat(draws).extracting(Draw::getName).containsExactlyInAnyOrder("Draw 1", "Draw 2");

                    assertThat(draws).extracting(Draw::getMaxTickets).containsExactlyInAnyOrder(100, 50);

                    assertThat(draws)
                            .extracting(Draw::getStatus)
                            .containsExactlyInAnyOrder(DrawStatus.CREATED, DrawStatus.ACTIVATED);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllDraws_WhenDatabaseIsEmpty_ShouldReturn200AndEmptyList() {
        webTestClient
                .get()
                .uri("/api/draws")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Draw.class)
                .value(draws -> assertThat(draws).isEmpty());
    }

    @Test
    void getAllDraws_WhenUnauthenticated_ShouldReturn401() {
        webTestClient.get().uri("/api/draws").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllDraws_WhenUserIsNotAdmin_ShouldReturn403() {
        webTestClient.get().uri("/api/draws").exchange().expectStatus().isForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllDraws_ShouldStreamDataCorrectly() {
        drawRepository
                .save(Draw.builder()
                        .name("Stream Test")
                        .description("Desc")
                        .maxTickets(10)
                        .ticketPrice(BigDecimal.ONE)
                        .build())
                .block();

        webTestClient
                .get()
                .uri("/api/draws")
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(Draw.class)
                .getResponseBody()
                .as(StepVerifier::create)
                .assertNext(draw -> assertThat(draw.getName()).isEqualTo("Stream Test"))
                .verifyComplete();
    }
}
