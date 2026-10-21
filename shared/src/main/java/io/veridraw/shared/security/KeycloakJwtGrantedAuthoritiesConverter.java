package io.veridraw.shared.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Извлекает роли из JWT и преобразует их в {@link GrantedAuthority}.
 *
 * <p>Реализует стандартный интерфейс Spring Security {@link GrantedAuthoritiesConverter},
 * что позволяет использовать его как в сервлетных, так и в реактивных приложениях.
 *
 * <p>По умолчанию извлекает роли из клейма "roles" с префиксом "ROLE_".
 *
 * @see org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
 */
public class KeycloakJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String claimName;
    private final String rolePrefix;

    /**
     * Создаёт конвертер с настройками по умолчанию: клейм "roles", префикс "ROLE_".
     */
    public KeycloakJwtGrantedAuthoritiesConverter() {
        this("roles", "ROLE_");
    }

    /**
     * Создаёт конвертер с пользовательскими настройками.
     *
     * @param claimName  имя клейма, содержащего список ролей
     * @param rolePrefix префикс, добавляемый к каждой роли
     */
    public KeycloakJwtGrantedAuthoritiesConverter(String claimName, String rolePrefix) {
        this.claimName = Objects.requireNonNull(claimName, "Claim name must not be null");
        this.rolePrefix = Objects.requireNonNull(rolePrefix, "Role prefix must not be null");
    }

    /**
     * Извлекает роли из JWT и преобразует их в {@link GrantedAuthority}.
     *
     * @param jwt исходный JSON Web Token
     * @return коллекция {@link GrantedAuthority}, или пустая коллекция, если роли отсутствуют
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Objects.requireNonNull(jwt, "JWT must not be null");

        List<String> roles = jwt.getClaimAsStringList(claimName);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> new SimpleGrantedAuthority(rolePrefix + role))
                .collect(Collectors.toUnmodifiableList());
    }
}
