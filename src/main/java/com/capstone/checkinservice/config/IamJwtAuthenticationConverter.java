package com.capstone.checkinservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class IamJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();

        addAuthoritiesFromClaim(jwt, authorities, "roles");
        addAuthoritiesFromClaim(jwt, authorities, "authorities");
        addSingleAuthorityFromClaim(jwt, authorities, "role");

        String principalName = resolvePrincipalName(jwt);

        return new JwtAuthenticationToken(jwt, new ArrayList<>(authorities), principalName);
    }

    private void addAuthoritiesFromClaim(
            Jwt jwt,
            Set<SimpleGrantedAuthority> authorities,
            String claimName
    ) {
        Object claim = jwt.getClaims().get(claimName);

        if (claim instanceof Collection<?> values) {
            for (Object value : values) {
                addAuthority(authorities, value);
            }
            return;
        }

        addAuthority(authorities, claim);
    }

    private void addSingleAuthorityFromClaim(
            Jwt jwt,
            Set<SimpleGrantedAuthority> authorities,
            String claimName
    ) {
        Object claim = jwt.getClaims().get(claimName);
        addAuthority(authorities, claim);
    }

    private void addAuthority(Set<SimpleGrantedAuthority> authorities, Object rawValue) {
        if (rawValue == null) {
            return;
        }

        String authority = String.valueOf(rawValue).trim();
        if (authority.isBlank()) {
            return;
        }

        authorities.add(new SimpleGrantedAuthority(normalizeAuthority(authority)));
    }

    private String normalizeAuthority(String raw) {
        String value = raw.trim();

        if (value.startsWith("ROLE_")) {
            return value;
        }

        return "ROLE_" + value;
    }

    private String resolvePrincipalName(Jwt jwt) {
        Object userId = jwt.getClaims().get("userId");

        if (userId != null) {
            return String.valueOf(userId);
        }

        return jwt.getSubject();
    }
}