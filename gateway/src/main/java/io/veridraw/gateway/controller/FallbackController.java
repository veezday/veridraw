package io.veridraw.gateway.controller;

import java.util.Map;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Контроллер для обработки fallback-сценариев при срабатывании Circuit Breaker.
 * <p>
 * Предоставляет унифицированный JSON-ответ клиенту, когда целевой микросервис
 * недоступен, перегружен или превышено время ожидания. Это предотвращает передачу
 * клиенту сырых HTML-страниц ошибок или неинформативных ответов с кодом 503.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Fallback-обработчик для сервиса {@code draw-core}.
     * <p>
     * Извлекает информацию об исключении из атрибутов запроса (автоматически добавленных
     * Spring Cloud Gateway при разрыве цепи) и формирует структурированный ответ об ошибке.
     *
     * @param exchange контекст текущего HTTP-запроса
     * @return {@link Mono} с {@link ResponseEntity}, содержащим детали ошибки и статус 503
     */
    @GetMapping("/draw-core")
    public Mono<ResponseEntity<Map<String, Object>>> drawCoreFallback(ServerWebExchange exchange) {
        Throwable exception = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        String errorMessage = (exception != null) ? exception.getMessage() : "Service unavailable";

        Map<String, Object> fallbackResponse = Map.of(
                "status", "error",
                "service", "draw-core",
                "message", "Draw service unavailable, try later",
                "details", errorMessage);

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackResponse));
    }

    /**
     * Fallback-обработчик для сервиса {@code notify-worker}.
     * <p>
     * Извлекает информацию об исключении из атрибутов запроса (автоматически добавленных
     * Spring Cloud Gateway при разрыве цепи) и формирует структурированный ответ об ошибке.
     *
     * @param exchange контекст текущего HTTP-запроса
     * @return {@link Mono} с {@link ResponseEntity}, содержащим детали ошибки и статус 503
     */
    @GetMapping("/notify-worker")
    public Mono<ResponseEntity<Map<String, Object>>> notifyWorkerFallback(ServerWebExchange exchange) {
        Throwable exception = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        String errorMessage = (exception != null) ? exception.getMessage() : "Service unavailable";

        Map<String, Object> fallbackResponse = Map.of(
                "status", "error",
                "service", "notify-worker",
                "message", "Notify worker unavailable, try later",
                "details", errorMessage);

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackResponse));
    }
}
