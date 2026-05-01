package com.capstone.checkinservice.crypto.key;

import java.security.PublicKey;
import java.util.Objects;

public record QrVerificationKey(
        String kid,
        PublicKey publicKey
) {
    public QrVerificationKey {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("kid must not be blank");
        }
        Objects.requireNonNull(publicKey, "publicKey must not be null");
    }
}
