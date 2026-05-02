package com.capstone.checkinservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final JwtService jwtService;
    private final HttpServletRequest request;

    public TokenMetaData getDataFromAuth() {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return new TokenMetaData(null, false, null);
        }

        String token = header.substring(7);
        if (token.isEmpty()) {
            return new TokenMetaData(null, false, null);
        }

        try {
            Claims claims = jwtService.extractAllClaims(token);
            Long userId = claims.get("userId", Long.class);
            Boolean isOrg = claims.get("isOrganization", Boolean.class);
            boolean isOrganization = isOrg != null && isOrg;
            Long organizationId = claims.get("organizationId", Long.class);

            return new TokenMetaData(userId, isOrganization, organizationId);
        } catch (Exception e) {
            return new TokenMetaData(null, false, null);
        }
    }

    public String getToken(){
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token =header.substring(7);
        }
        return token;
    }
}
