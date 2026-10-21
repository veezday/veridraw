package io.veridraw.gateway.filter;

import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Глобальный фильтр для проброса информации о пользователе в заголовках запроса.
 * <p>
 * Извлекает данные из валидированного JWT-токена (ID, имя, email) и добавляет их
 * в заголовки {@code X-User-Id}, {@code X-User-Name} и {@code X-User-Email}.
 * Это позволяет микросервисам нижнего уровня получать контекст пользователя
 * без необходимости самостоятельного парсинга и валидации JWT.
 * <p>
 * Выполняется с наименьшим приоритетом ({@link Ordered#LOWEST_PRECEDENCE}),
 * чтобы гарантировать, что он сработает строго {@code ПОСЛЕ} успешной аутентификации
 * и установки контекста безопасности.
 */
@Component
public class UserInfoHeaderFilter implements GlobalFilter, Ordered {

    @Override
    @NonNull public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(token -> {
                    Jwt jwt = token.getToken();

                    String userId = Optional.ofNullable(jwt.getSubject()).orElse("");
                    String userName = Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                            .orElse("");
                    String email =
                            Optional.ofNullable(jwt.getClaimAsString("email")).orElse("");

                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Name", userName)
                            .header("X-User-Email", email)
                            .build();

                    return chain.filter(
                            exchange.mutate().request(mutatedRequest).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
