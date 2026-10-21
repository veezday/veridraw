package io.veridraw.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

public class GatewayIntegrationTest extends AbstractIntegrationTest {
    @Value("${rate-limiter.ip.burst-capacity}")
    private int ipBurstCapacity;

    @Value("${spring.cloud.gateway.server.webflux.default-filters[0].args.redis-rate-limiter.burstCapacity}")
    private int userBurstCapacity;

    @Test
    void rout_WhenNoToken_ShouldReturn401() {
        webTestClient.get().uri("/api/draws").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rout_WhenInvalidToken_ShouldReturn401() {
        when(reactiveJwtDecoder.decode("invalid.jwt.token"))
                .thenReturn(Mono.error(new BadJwtException("Invalid token")));

        webTestClient
                .get()
                .uri("/api/draws")
                .header("Authorization", "Bearer invalid.jwt.token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void route_WhenValidToken_ShouldRouteDrawAndAddHeaders() {
        Jwt mockJwt = Jwt.withTokenValue("mock-valid-token")
                .header("alg", "none")
                .claim("sub", "test-user-id")
                .claim("preferred_username", "testUsername")
                .claim("email", "test@service.com")
                .build();

        when(reactiveJwtDecoder.decode(anyString())).thenReturn(Mono.just(mockJwt));

        stubFor(get(urlEqualTo("/api/draws"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"success\"}")));

        webTestClient
                .get()
                .uri("/api/draws")
                .header("Authorization", "Bearer mock-valid-token")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("success");

        WireMock.verify(getRequestedFor(urlEqualTo("/api/draws"))
                .withHeader("X-User-Id", equalTo("test-user-id"))
                .withHeader("X-User-Name", equalTo("testUsername"))
                .withHeader("X-User-Email", equalTo("test@service.com")));
    }

    @Test
    void route_WhenDownstreamServiceFails_ShouldRouteToFallback() {
        Jwt mockJwt = Jwt.withTokenValue("mock-valid-token")
                .header("alg", "none")
                .claim("sub", "test-user-id")
                .claim("preferred_username", "testUsername")
                .claim("email", "test@service.com")
                .build();

        when(reactiveJwtDecoder.decode(anyString())).thenReturn(Mono.just(mockJwt));

        stubFor(get(urlPathEqualTo("/api/draws")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        webTestClient
                .get()
                .uri("/api/draws")
                .header("Authorization", "Bearer mock-valid-token")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("error")
                .jsonPath("$.service")
                .isEqualTo("draw-core")
                .jsonPath("$.message")
                .isEqualTo("Draw service unavailable, try later")
                .jsonPath("$.details")
                .exists();
    }

    @Test
    void route_WhenIpRateLimitExceeded_ShouldReturn429() {
        int burstCapacity = ipBurstCapacity;
        int totalRequests = burstCapacity + 5;

        boolean[] got429 = {false};
        int[] first429Request = {-1};

        IntStream.range(0, totalRequests).forEach(i -> {
            webTestClient
                    .get()
                    .uri("/api/draws")
                    .header("X-Forwarded-For", "192.168.1.100")
                    .exchange()
                    .expectStatus()
                    .value(status -> {
                        if (i < burstCapacity) {
                            assertEquals(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    status,
                                    "Request №" + i + ": before burst capacity (" + burstCapacity + ") must be 401");
                        } else {
                            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                                if (!got429[0]) {
                                    first429Request[0] = i;
                                }
                                got429[0] = true;
                            } else if (got429[0]) {
                                assertEquals(
                                        HttpStatus.TOO_MANY_REQUESTS.value(),
                                        status,
                                        "Request №" + i + ": after first 429 all requests must be 429");
                            }
                        }
                    });
        });

        assertTrue(
                got429[0],
                "After burst capacity (" + burstCapacity + ") must be at least one 429. " + "First 429 at request №"
                        + first429Request[0]);
    }

    @Test
    void route_WhenUserRateLimitExceeded_ShouldReturn429() {
        String testUser = "rate-limit-test-user";

        Jwt mockJwt = Jwt.withTokenValue("valid-token-for-rate-limit")
                .header("alg", "none")
                .claim("sub", testUser)
                .claim("preferred_username", testUser)
                .build();

        when(reactiveJwtDecoder.decode(anyString())).thenReturn(Mono.just(mockJwt));

        stubFor(get(urlPathEqualTo("/api/draws"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\"}")));

        int burstCapacity = userBurstCapacity;
        int totalRequests = burstCapacity + 2;
        boolean[] got429 = {false};
        int[] first429Request = {-1};

        IntStream.range(0, totalRequests).forEach(i -> {
            webTestClient
                    .get()
                    .uri("/api/draws")
                    .header("Authorization", "Bearer valid-token-for-rate-limit")
                    .exchange()
                    .expectStatus()
                    .value(status -> {
                        if (i < burstCapacity) {
                            assertEquals(
                                    HttpStatus.OK.value(),
                                    status,
                                    "Request №" + i + ": before burst capacity (" + burstCapacity + ") must be 200");
                        } else {
                            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                                if (!got429[0]) {
                                    first429Request[0] = i;
                                }
                                got429[0] = true;
                            } else if (got429[0]) {
                                assertEquals(
                                        HttpStatus.TOO_MANY_REQUESTS.value(),
                                        status,
                                        "Request №" + i + ": after first 429 all requests must be 429");
                            }
                        }
                    });
        });

        assertTrue(
                got429[0],
                "After burst capacity (" + burstCapacity + ") must be at least one 429. " + "First 429 at request №"
                        + first429Request[0]);
    }
}
