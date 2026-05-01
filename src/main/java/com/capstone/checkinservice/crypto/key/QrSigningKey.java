package com.capstone.checkinservice.crypto.key;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

public record QrSigningKey(
        String kid,
        PrivateKey privateKey,
        PublicKey publicKey
) {
    public QrSigningKey {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("kid must not be blank");
        }
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
    }

    @Override
    public String toString() {
        return "QrSigningKey[kid=" + kid + "]";
    }
}
