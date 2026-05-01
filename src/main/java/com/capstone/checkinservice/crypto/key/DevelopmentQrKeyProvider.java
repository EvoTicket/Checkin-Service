package com.capstone.checkinservice.crypto.key;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Optional;

/**
 * Development fallback for local startup and tests that load the Spring context.
 * Production key management must replace this with environment-backed or KMS-backed key material.
 */
public class DevelopmentQrKeyProvider implements QrKeyProvider {
    private final QrSigningKey signingKey;

    public DevelopmentQrKeyProvider(String kid) {
        this.signingKey = generateSigningKey(kid);
    }

    @Override
    public QrSigningKey getCurrentSigningKey() {
        return signingKey;
    }

    @Override
    public Optional<QrVerificationKey> findVerificationKey(String kid) {
        if (!signingKey.kid().equals(kid)) {
            return Optional.empty();
        }
        return Optional.of(new QrVerificationKey(signingKey.kid(), signingKey.publicKey()));
    }

    private static QrSigningKey generateSigningKey(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = generator.generateKeyPair();
            return new QrSigningKey(kid, keyPair.getPrivate(), keyPair.getPublic());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate development QR signing key", e);
        }
    }
}
