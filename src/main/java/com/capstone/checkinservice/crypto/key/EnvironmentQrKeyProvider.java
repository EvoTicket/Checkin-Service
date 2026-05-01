package com.capstone.checkinservice.crypto.key;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

public class EnvironmentQrKeyProvider implements QrKeyProvider {
    private final QrSigningKey signingKey;
    private final QrVerificationKey verificationKey;

    public EnvironmentQrKeyProvider(String kid, String privateKeyBase64, String publicKeyBase64) {
        String requiredKid = requireText(kid, "QR key id");
        PrivateKey privateKey = parsePrivateKey(requireText(privateKeyBase64, "QR private key"));
        PublicKey publicKey = parsePublicKey(requireText(publicKeyBase64, "QR public key"));

        this.signingKey = new QrSigningKey(requiredKid, privateKey, publicKey);
        this.verificationKey = new QrVerificationKey(requiredKid, publicKey);
    }

    @Override
    public QrSigningKey getCurrentSigningKey() {
        return signingKey;
    }

    @Override
    public Optional<QrVerificationKey> findVerificationKey(String kid) {
        if (!verificationKey.kid().equals(kid)) {
            return Optional.empty();
        }
        return Optional.of(verificationKey);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value;
    }

    private static PrivateKey parsePrivateKey(String privateKeyBase64) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Invalid QR private key material", e);
        }
    }

    private static PublicKey parsePublicKey(String publicKeyBase64) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Invalid QR public key material", e);
        }
    }
}
