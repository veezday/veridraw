package io.veridraw.drawcore.integration.drawcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.domain.DrawStatus;
import io.veridraw.drawcore.dto.CreateDrawRequest;
import io.veridraw.drawcore.integration.AbstractIntegrationTest;
import io.veridraw.drawcore.repository.DrawRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.test.StepVerifier;

public class DrawControllerCreateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DrawRepository drawRepository;

    @BeforeEach
    void setUp() {
        drawRepository.deleteAll().block();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_ShouldReturn201AndSaveToPostgres() {
        Draw draw = Draw.builder()
                .name("Test Draw")
                .description("Test Description")
                .ticketPrice(BigDecimal.ONE)
                .maxTickets(2)
                .build();

        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(draw)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Draw.class)
                .value(responseDraw -> {
                    assertThat(responseDraw.getId()).isNotNull();
                    assertThat(responseDraw.getName()).isEqualTo("Test Draw");
                    assertThat(responseDraw.getDescription()).isEqualTo("Test Description");
                    assertThat(responseDraw.getMaxTickets()).isEqualTo(2);
                    assertThat(responseDraw.getTicketPrice()).isEqualByComparingTo(BigDecimal.ONE);
                    assertThat(responseDraw.getStatus()).isEqualTo(DrawStatus.CREATED);
                    assertThat(responseDraw.getWinnerId()).isNull();
                    assertThat(responseDraw.getCreatedAt()).isNotNull();
                    assertThat(responseDraw.getUpdatedAt()).isEqualTo(responseDraw.getCreatedAt());
                    assertThat(responseDraw.getCompletedAt()).isNull();
                });

        StepVerifier.create(drawRepository.findByName("Test Draw"))
                .assertNext(savedDraw -> {
                    assertThat(savedDraw.getId()).isNotNull();
                    assertThat(savedDraw.getName()).isEqualTo("Test Draw");
                    assertThat(savedDraw.getDescription()).isEqualTo("Test Description");
                    assertThat(savedDraw.getMaxTickets()).isEqualTo(2);
                    assertThat(savedDraw.getTicketPrice()).isEqualByComparingTo(BigDecimal.ONE);
                    assertThat(savedDraw.getStatus()).isEqualTo(DrawStatus.CREATED);
                    assertThat(savedDraw.getWinnerId()).isNull();
                    assertThat(savedDraw.getCreatedAt()).isNotNull();
                    assertThat(savedDraw.getUpdatedAt()).isEqualTo(savedDraw.getCreatedAt());
                    assertThat(savedDraw.getCompletedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void create_WhenUnauthenticated_ShouldReturn401() {
        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_WhenUserIsNotAdmin_ShouldReturn403() {
        CreateDrawRequest validRequest =
                new CreateDrawRequest("Test Draw Name", "Valid Description", 100, new BigDecimal("10.00"));

        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest)
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_WhenNameIsBlank_ShouldReturn400() {
        Draw invalidDraw = Draw.builder()
                .name("")
                .ticketPrice(BigDecimal.ONE)
                .maxTickets(10)
                .build();

        assertBadRequestAndNoDbSave(invalidDraw);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_WhenTicketPriceIsNegative_ShouldReturn400() {
        Draw invalidDraw = Draw.builder()
                .name("Test")
                .ticketPrice(BigDecimal.valueOf(-1))
                .maxTickets(10)
                .build();

        assertBadRequestAndNoDbSave(invalidDraw);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_WhenMaxTicketsIsZero_ShouldReturn400() {
        Draw invalidDraw = Draw.builder()
                .name("Test")
                .ticketPrice(BigDecimal.ONE)
                .maxTickets(0)
                .build();

        assertBadRequestAndNoDbSave(invalidDraw);
    }

    private void assertBadRequestAndNoDbSave(Draw draw) {
        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(draw)
                .exchange()
                .expectStatus()
                .isBadRequest();

        StepVerifier.create(drawRepository.count()).expectNext(0L).verifyComplete();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_WhenNameAlreadyExists_ShouldReturn409() {
        Draw existingDraw = Draw.builder()
                .name("Unique Draw")
                .description("Original description")
                .ticketPrice(BigDecimal.ONE)
                .maxTickets(10)
                .build();
        drawRepository.save(existingDraw).block();

        Draw duplicateDraw = Draw.builder()
                .name("Unique Draw")
                .description("Duplicate description")
                .ticketPrice(BigDecimal.TEN)
                .maxTickets(5)
                .build();

        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(duplicateDraw)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_WhenInvalidJson_ShouldReturn400() {
        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{invalid json}")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_WhenNullBody_ShouldReturn400() {
        webTestClient
                .post()
                .uri("/api/draws")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
