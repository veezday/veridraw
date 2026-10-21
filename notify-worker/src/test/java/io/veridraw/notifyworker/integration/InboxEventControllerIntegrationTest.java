package io.veridraw.notifyworker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veridraw.notifyworker.domain.InboxEvent;
import io.veridraw.notifyworker.dto.InboxEventResponse;
import io.veridraw.notifyworker.listener.DrawEventListener;
import io.veridraw.notifyworker.repository.InboxEventRepository;
import io.veridraw.notifyworker.service.EmailService;
import io.veridraw.shared.event.DrawCompletedEvent;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class InboxEventControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InboxEventRepository inboxEventRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private DrawEventListener drawEventListener;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        inboxEventRepository.deleteAll();
        doNothing().when(emailService).sendEmail(any(DrawCompletedEvent.class));
    }

    @Test
    void unpublished_WhenAdmin_ShouldReturnUnpublishedEvents() {
        UUID drawId = UUID.randomUUID();
        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(UUID.randomUUID())
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(drawId)
                        .winnerEmail("test@mailpit.local")
                        .winnerName("Test")
                        .drawName("Test")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build());

        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ADMIN"));

        webTestClient
                .get()
                .uri("/api/inbox/unpublished")
                .header("Authorization", "Bearer fake-token")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(InboxEventResponse.class)
                .value(events -> {
                    assertThat(events).hasSize(1);
                    assertThat(events.get(0).eventType()).isEqualTo("DrawCompleted");
                });
    }

    @Test
    void unpublished_WhenUser_ShouldReturnForbidden() {
        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(UUID.randomUUID())
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(UUID.randomUUID())
                        .winnerEmail("test@test.com")
                        .winnerName("Test")
                        .drawName("Test")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build());

        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("USER"));

        webTestClient
                .get()
                .uri("/api/inbox/unpublished")
                .header("Authorization", "Bearer fake-user-token")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unpublished_WhenNoToken_ShouldReturnUnauthorized() {
        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(UUID.randomUUID())
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(UUID.randomUUID())
                        .winnerEmail("test@test.com")
                        .winnerName("Test")
                        .drawName("Test")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build());

        webTestClient
                .get()
                .uri("/api/inbox/unpublished")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unpublished_WhenNoUnpublishedEvents_ShouldReturnEmptyList() {
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ADMIN"));

        webTestClient
                .get()
                .uri("/api/inbox/unpublished")
                .header("Authorization", "Bearer fake-admin-token")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Object.class)
                .value(events -> assertThat(events).isEmpty());
    }

    @Test
    void resend_WhenAdmin_ShouldTriggerEmailService() {
        UUID aggregateId = UUID.randomUUID();
        UUID drawId = UUID.randomUUID();

        inboxEventRepository.save(InboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType("Draw")
                .eventType("DrawCompleted")
                .payload(DrawCompletedEvent.builder()
                        .drawId(drawId)
                        .winnerEmail("test@mailpit.local")
                        .winnerName("Test")
                        .drawName("Test")
                        .build())
                .published(false)
                .createdAt(Instant.now())
                .build());

        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ADMIN"));

        webTestClient
                .post()
                .uri("/api/inbox/resend")
                .header("Authorization", "Bearer fake-admin-token")
                .exchange()
                .expectStatus()
                .isOk();

        verify(emailService, times(1)).sendEmail(any(DrawCompletedEvent.class));

        InboxEvent updated = inboxEventRepository.findByAggregateId(aggregateId);
        assertThat(updated.getPublished()).isTrue();
        assertThat(updated.getPublishedAt()).isNotNull();
    }

    @Test
    void resend_WhenNoUnpublishedEvents_ShouldReturnOkAndDoNothing() {
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ADMIN"));

        webTestClient
                .post()
                .uri("/api/inbox/resend")
                .header("Authorization", "Bearer fake-admin-token")
                .exchange()
                .expectStatus()
                .isOk();

        verify(emailService, times(0)).sendEmail(any(DrawCompletedEvent.class));
    }

    private Jwt createMockJwt(String... roles) {
        return Jwt.withTokenValue("fake-token")
                .header("alg", "RS256")
                .claim("sub", "test-user-id")
                .claim("preferred_username", "testuser")
                .claim("roles", Arrays.asList(roles))
                .build();
    }
}
