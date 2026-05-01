package com.capstone.checkinservice.security;

import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {
    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unresolvedUser();
        }

        Long principalUserId = resolveUserId(authentication.getPrincipal());
        if (principalUserId != null) {
            return principalUserId;
        }

        Long authenticationNameUserId = parseLong(authentication.getName());
        if (authenticationNameUserId != null) {
            return authenticationNameUserId;
        }

        throw unresolvedUser();
    }

    private Long resolveUserId(Object principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof Number number) {
            return number.longValue();
        }
        if (principal instanceof String value) {
            if ("anonymousUser".equals(value)) {
                return null;
            }
            return parseLong(value);
        }
        if (principal instanceof UserDetails userDetails) {
            return parseLong(userDetails.getUsername());
        }
        if (principal instanceof Map<?, ?> claims) {
            Long userId = resolveClaim(claims, "userId");
            if (userId != null) {
                return userId;
            }
            userId = resolveClaim(claims, "id");
            if (userId != null) {
                return userId;
            }
            return resolveClaim(claims, "sub");
        }

        return resolveByMethod(principal, "getUserId");
    }

    private Long resolveClaim(Map<?, ?> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return parseLong(text);
        }
        return null;
    }

    private Long resolveByMethod(Object principal, String methodName) {
        try {
            Method method = principal.getClass().getMethod(methodName);
            Object value = method.invoke(principal);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text) {
                return parseLong(text);
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private CheckinBusinessException unresolvedUser() {
        return new CheckinBusinessException(
                ScanResult.OWNERSHIP_MISMATCH,
                HttpStatus.FORBIDDEN,
                "Authenticated user could not be resolved"
        );
    }
}
