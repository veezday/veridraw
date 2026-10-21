package io.veridraw.drawcore.service;

import io.veridraw.drawcore.dto.RandomOrgRequest;
import io.veridraw.drawcore.dto.RandomOrgResponse;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Сервис для получения случайных чисел.
 * Поддерживает как внешний API (random.org), так и внутреннюю генерацию (ThreadLocalRandom).
 */
@Slf4j
@Service
public class RandomNumberService {

    private final WebClient webClient;
    private final String apiKey;
    private final boolean useExternalApi;

    public RandomNumberService(
            WebClient.Builder webClientBuilder,
            @Value("${veridraw.random.api-key}") String apiKey,
            @Value("${veridraw.random.base-url") String baseUrl,
            @Value("${veridraw.random.use_external_api:false}") boolean useExternalApi) {

        this.apiKey = apiKey;
        this.useExternalApi = useExternalApi;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Основной метод, выступающий в роли роутера.
     * Выбирает источник случайного числа на основе конфигурации.
     *
     * @param min минимальное значение (включительно)
     * @param max максимальное значение (включительно)
     * @return {@link Mono} со случайным числом
     */
    public Mono<Integer> getRandomInteger(int min, int max) {
        Assert.isTrue(min <= max, "min must be less than or equal to max");

        if (useExternalApi) {
            return getRandomExternal(min, max);
        } else {
            return getRandomLocal(min, max);
        }
    }

    /**
     * Запрашивает случайное число через внешний API random.org.
     */
    private Mono<Integer> getRandomExternal(int min, int max) {
        log.info("Requesting random integer from random.org: [{}, {}]", min, max);

        var request = new RandomOrgRequest(
                "2.0", "generateIntegers", new RandomOrgRequest.Params(apiKey, 1, min, max, true), 1);

        return webClient
                .post()
                .uri("/json-rpc/4/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("HTTP " + response.statusCode() + ": " + body))))
                .bodyToMono(RandomOrgResponse.class)
                .map(this::extractNumber)
                .doOnSuccess(num -> log.info("Successfully received random number from external API: {}", num))
                .doOnError(e -> log.error("Failed to get random number from random.org", e));
    }

    /**
     * Генерирует случайное число локально с использованием ThreadLocalRandom.
     * Потокобезопасен и не создает блокировок, идеален для нагрузочного тестирования.
     */
    private Mono<Integer> getRandomLocal(int min, int max) {
        log.debug("Using local ThreadLocalRandom for range: [{}, {}]", min, max);
        // nextInt(min, max + 1) потому что верхняя граница в ThreadLocalRandom эксклюзивна
        int randomNum = ThreadLocalRandom.current().nextInt(min, max + 1);
        return Mono.just(randomNum);
    }

    /**
     * Извлекает число из ответа API, проверяя наличие ошибок и валидность структуры.
     */
    private int extractNumber(RandomOrgResponse response) {
        if (response.error() != null) {
            throw new RuntimeException(
                    "Random.org API error: " + response.error().message());
        }
        if (response.result() == null
                || response.result().random() == null
                || response.result().random().data() == null) {
            throw new RuntimeException("Invalid response from random.org: missing data");
        }

        List<Integer> data = response.result().random().data();
        if (data.isEmpty()) {
            throw new RuntimeException("Invalid response from random.org: empty data array");
        }

        return data.getFirst(); // Или data.get(0) для версий Java < 21
    }
}
