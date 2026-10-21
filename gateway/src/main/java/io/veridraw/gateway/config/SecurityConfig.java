package io.veridraw.gateway.config;

import io.veridraw.shared.security.KeycloakJwtGrantedAuthoritiesConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Конфигурация безопасности API Gateway.
 * Обеспечивает валидацию JWT-токенов через Keycloak и управляет доступом к маршрутам.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Настраивает цепочку фильтров безопасности.
     * Отключает CSRF, разрешает доступ к метрикам, требует аутентификации для бизнес-эндпоинтов
     * и регистрирует кастомный конвертер для извлечения ролей из Keycloak JWT.
     *
     * @param http билдер настроек безопасности
     * @return цепочка фильтров {@link SecurityWebFilterChain}
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .pathMatchers("/api/draws", "/api/draws/**", "/api/tickets", "/api/tickets/**", "/api/inbox/**")
                        .authenticated()
                        .anyExchange()
                        .denyAll())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())));

        return http.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> authenticationConverter() {
        var authoritiesConverter = new KeycloakJwtGrantedAuthoritiesConverter();
        return jwt -> Mono.just(new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt)));
    }
}
