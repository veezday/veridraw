package io.veridraw.drawcore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class RandomNumberServiceTest {

    @Nested
    class LocalGeneration {
        private RandomNumberService randomNumberService;

        @BeforeEach
        void setUp() {
            randomNumberService =
                    new RandomNumberService(WebClient.builder(), "test-api-key", "http://localhost", false);
        }

        @Test
        void getRandomInteger_success_returnsNumberInRange() {
            StepVerifier.create(randomNumberService.getRandomInteger(1, 100))
                    .expectNextMatches(num -> num >= 1 && num <= 100)
                    .verifyComplete();
        }

        @Test
        void getRandomInteger_minEqualsMax_returnsExactNumber() {
            StepVerifier.create(randomNumberService.getRandomInteger(42, 42))
                    .expectNext(42)
                    .verifyComplete();
        }

        @Test
        void getRandomInteger_invalidInput_throwsException() {
            assertThatThrownBy(() -> randomNumberService.getRandomInteger(100, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("min must be less than or equal to max");
        }
    }

    @Nested
    class ExternalGeneration {
        private MockWebServer mockWebServer;
        private RandomNumberService randomNumberService;

        @BeforeEach
        void setUp() throws IOException {
            mockWebServer = new MockWebServer();
            mockWebServer.start();

            String baseUrl = mockWebServer.url("/").toString();
            randomNumberService = new RandomNumberService(WebClient.builder(), "test-api-key", baseUrl, true);
        }

        @AfterEach
        void tearDown() throws IOException {
            mockWebServer.shutdown();
        }

        @Test
        void getRandomInteger_success_returnsNumber() throws Exception {
            String successResponse = """
                    {
                      "result": {
                        "random": {
                          "data": [42]
                        }
                      },
                      "id": 1
                    }
                    """;
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(successResponse));

            StepVerifier.create(randomNumberService.getRandomInteger(1, 100))
                    .expectNext(42)
                    .verifyComplete();

            var recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getPath()).isEqualTo("/json-rpc/4/invoke");
            assertThat(recordedRequest.getBody().readUtf8()).contains("\"apiKey\":\"test-api-key\"");
        }

        @Test
        void getRandomInteger_jsonRpcError_throwsException() throws Exception {
            String errorResponse = """
                    {
                      "error": {
                        "code": 402,
                        "message": "API key not valid"
                      },
                      "id": 1
                    }
                    """;
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(errorResponse));

            StepVerifier.create(randomNumberService.getRandomInteger(1, 100))
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException && ex.getMessage().contains("API key not valid"))
                    .verify();
        }

        @Test
        void getRandomInteger_httpError_throwsException() throws Exception {
            mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

            StepVerifier.create(randomNumberService.getRandomInteger(1, 100))
                    .expectErrorMatches(ex ->
                            ex instanceof RuntimeException && ex.getMessage().contains("HTTP 503"))
                    .verify();
        }

        @Test
        void getRandomInteger_invalidInput_throwsException() {
            assertThatThrownBy(() -> randomNumberService.getRandomInteger(100, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("min must be less than or equal to max");

            assertThat(mockWebServer.getRequestCount()).isZero();
        }
    }
}
