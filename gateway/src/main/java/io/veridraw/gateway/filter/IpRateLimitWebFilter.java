package io.veridraw.gateway.filter;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Глобальный фильтр ограничения частоты запросов по IP-адресу.
 * <p>
 * Выполняется с высоким приоритетом ({@link Ordered#HIGHEST_PRECEDENCE} + 100),
 * чтобы срабатывать {@code ДО} фильтров аутентификации и маршрутизации.
 * Это защищает сам механизм авторизации и другие публичные эндпоинты от brute-force и DDoS-атак.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class IpRateLimitWebFilter implements WebFilter {

    private final RedisRateLimiter ipRedisRateLimiter;
    private final KeyResolver ipKeyResolver;
    private final Counter ipRateLimitHitsCounter;

    @Override
    @NonNull public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        // For load testing
        if (exchange.getRequest().getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        return ipKeyResolver
                .resolve(exchange)
                .flatMap(key -> ipRedisRateLimiter
                        .isAllowed("global_ip_limit", key)
                        .flatMap(response -> {
                            if (!response.isAllowed()) {
                                ipRateLimitHitsCounter.increment();
                                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                return exchange.getResponse().setComplete();
                            }
                            return chain.filter(exchange);
                        }));
    }
}
