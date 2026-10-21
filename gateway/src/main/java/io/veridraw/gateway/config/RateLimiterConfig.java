package io.veridraw.gateway.config;

import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * Конфигурация двухуровневого ограничения частоты запросов (Rate Limiting).
 * <p>
 * 1. Уровень IP (pre-auth): Реализован через кастомный {@link io.veridraw.gateway.filter.IpRateLimitWebFilter}.
 *    Защищает все эндпоинты (включая авторизацию) от флуда с одного адреса.
 * <p>
 * 2. Уровень пользователя (post-auth): Используется встроенным фильтром Spring Cloud Gateway
 *    {@code RequestRateLimiter} (настраивается в application.yml через {@code key-resolver: "#{@userKeyResolver}"}).
 *    Ограничивает действия конкретных аутентифицированных пользователей.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Лимитер для IP-адресов.
     * Используется исключительно в кастомном {@link io.veridraw.gateway.filter.IpRateLimitWebFilter}.
     *
     * @param replenishRate скорость восстановления токенов (запросов в секунду)
     * @param burstCapacity максимальный всплеск запросов
     */
    @Bean
    public RedisRateLimiter ipRedisRateLimiter(
            @Value("${rate-limiter.ip.replenish-rate}") int replenishRate,
            @Value("${rate-limiter.ip.burst-capacity}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    /**
     * Резолвер ключа для IP-лимитирования.
     * Извлекает IP-адрес клиента, корректно обрабатывая заголовок {@code X-Forwarded-For},
     * что критично при работе за обратным прокси (Ingress/Nginx).
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            XForwardedRemoteAddressResolver resolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1);
            InetSocketAddress address = resolver.resolve(exchange);
            String ip = address != null ? address.getHostString() : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * Резолвер ключа для пользовательского лимитирования.
     * <p>
     * Помечен как {@link Primary}, чтобы встроенный фильтр {@code RequestRateLimiter}
     * из application.yml автоматически использовал именно его.
     * Извлекает имя пользователя (например, claim {@code sub} из JWT).
     * Если контекст безопасности еще не установлен (запрос до фильтра авторизации),
     * возвращает "anonymous".
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.just("anonymous"));
    }
}
