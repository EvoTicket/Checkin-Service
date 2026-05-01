package com.capstone.checkinservice.service;

import org.springframework.stereotype.Component;

@Component
public class SupportMaskingService {
    public String maskEmail(String email) {
        if (!hasText(email)) {
            return null;
        }

        String normalized = email.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1) {
            return maskMiddle(normalized, 1, 1);
        }

        String localPart = normalized.substring(0, atIndex);
        String domain = normalized.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domain;
        }
        int visiblePrefix = Math.min(6, Math.max(1, localPart.length() / 2));
        return localPart.substring(0, visiblePrefix) + "***" + domain;
    }

    public String maskPhone(String phone) {
        if (!hasText(phone)) {
            return null;
        }

        String normalized = phone.trim();
        String digits = normalized.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****" + digits;
        }
        if (digits.length() <= 8) {
            return maskMiddle(digits, 2, 2);
        }
        return digits.substring(0, 2) + "*".repeat(Math.max(4, digits.length() - 4)) + digits.substring(digits.length() - 2);
    }

    public String maskOwnerRef(Long ownerId) {
        if (ownerId == null) {
            return null;
        }

        String value = String.format("%04d", Math.abs(ownerId));
        return "usr_****" + value.substring(value.length() - 4);
    }

    public String maskDisplayName(String displayName) {
        if (!hasText(displayName)) {
            return null;
        }
        String[] parts = displayName.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            if (part.length() <= 1) {
                builder.append(part);
            } else {
                builder.append(part.charAt(0)).append("***");
            }
        }
        return builder.toString();
    }

    private String maskMiddle(String value, int prefix, int suffix) {
        if (value.length() <= prefix + suffix) {
            return value.charAt(0) + "***";
        }
        return value.substring(0, prefix)
                + "*".repeat(Math.max(3, value.length() - prefix - suffix))
                + value.substring(value.length() - suffix);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
