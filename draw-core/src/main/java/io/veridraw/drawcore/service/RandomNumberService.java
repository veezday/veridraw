package io.veridraw.drawcore.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RandomNumberService {

    private final WebClient webClient;
    private final String apiKey;

    public RandomNumberService(
            WebClient.Builder webClientBuilder,
            @Value("${random.org.api-key}") String apiKey,
            @Value("${random.org.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Mono<Integer> getRandomInteger(int min, int max) {
        log.info("Requesting random integer from random.org: [{}, {}]", min, max);

        Map<String, Object> requestBody = Map.of(
                "jsonrpc",
                "2.0",
                "method",
                "generateIntegers",
                "params",
                Map.of(
                        "apiKey", apiKey,
                        "n", 1,
                        "min", min,
                        "max", max,
                        "replacement", true),
                "id",
                1);

        return webClient
                .post()
                .uri("/json-rpc/4/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("HTTP error from random.org: {}", response.statusCode());
                    return response.createException().map(Exception::new);
                })
                .bodyToMono(Map.class)
                .map(response -> {
                    if (response.containsKey("error")) {
                        Map<String, Object> error = (Map<String, Object>) response.get("error");
                        String message = (String) error.getOrDefault("message", "Unknown error");
                        log.error("JSON-RPC error from random.org: {}", message);
                        throw new RuntimeException("Random.org API error: " + message);
                    }

                    Map<String, Object> result = (Map<String, Object>) response.get("result");
                    if (result == null) {
                        throw new RuntimeException("Invalid response from random.org: missing 'result'");
                    }

                    Map<String, Object> random = (Map<String, Object>) result.get("random");
                    if (random == null) {
                        throw new RuntimeException("Invalid response from random.org: missing 'random'");
                    }

                    List<Integer> data = (List<Integer>) random.get("data");
                    if (data == null || data.isEmpty()) {
                        throw new RuntimeException("Invalid response from random.org: missing 'data'");
                    }

                    return data.get(0);
                })
                .doOnSuccess(num -> log.info("Successfully received random number: {}", num))
                .doOnError(e -> log.error("Failed to get random number from random.org"));
    }
}
