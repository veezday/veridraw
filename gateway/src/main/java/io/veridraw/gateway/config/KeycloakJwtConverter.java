package io.veridraw.gateway.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KeycloakJwtConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof List<?> rolesList) {
                for (Object role : rolesList) {
                    if (role instanceof String roleName) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                    }
                }
            }
        }

        return Mono.just(new JwtAuthenticationToken(jwt, authorities));
    }
}
