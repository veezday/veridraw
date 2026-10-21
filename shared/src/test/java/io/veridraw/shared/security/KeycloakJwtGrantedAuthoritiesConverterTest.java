package io.veridraw.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtGrantedAuthoritiesConverterTest {

    private final KeycloakJwtGrantedAuthoritiesConverter converter = new KeycloakJwtGrantedAuthoritiesConverter();

    @Test
    void convert_WhenJwtHasRoles_ShouldReturnPrefixedAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user")
                .claim("roles", List.of("ADMIN", "USER"))
                .build();

        var authorities = converter.convert(jwt);

        assertThat(authorities).hasSize(2);
        assertThat(authorities.stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void convert_WhenJwtHasNoRoles_ShouldReturnEmptyCollection() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user")
                .build();

        var authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_WhenRolesClaimIsNull_ShouldReturnEmptyCollection() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user")
                .claim("roles", (List<String>) null)
                .build();

        var authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_WhenJwtIsNull_ShouldThrowNullPointerException() {
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("JWT must not be null");
    }

    @Test
    void constructor_WithCustomClaimAndPrefix_ShouldUseCustomValues() {
        var customConverter = new KeycloakJwtGrantedAuthoritiesConverter("realm_access", "AUTH_");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user")
                .claim("realm_access", List.of("admin", "user"))
                .build();

        var authorities = customConverter.convert(jwt);

        assertThat(authorities).hasSize(2);
        assertThat(authorities.stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("AUTH_admin", "AUTH_user");
    }
}
