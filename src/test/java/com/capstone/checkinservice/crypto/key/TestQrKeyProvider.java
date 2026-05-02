package com.capstone.checkinservice.crypto.key;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class TestQrKeyProvider implements QrKeyProvider {
    private final String currentKid;
    private final Map<String, QrSigningKey> keys;

    private TestQrKeyProvider(String currentKid, Map<String, QrSigningKey> keys) {
        this.currentKid = currentKid;
        this.keys = Map.copyOf(keys);
    }

    public static TestQrKeyProvider single(String kid) {
        return withGeneratedKeys(kid);
    }

    public static TestQrKeyProvider withGeneratedKeys(String currentKid, String... additionalKids) {
        Map<String, QrSigningKey> generatedKeys = new LinkedHashMap<>();
        generatedKeys.put(currentKid, generateSigningKey(currentKid));

        for (String kid : additionalKids) {
            generatedKeys.put(kid, generateSigningKey(kid));
        }

        return new TestQrKeyProvider(currentKid, generatedKeys);
    }

    @Override
    public QrSigningKey getCurrentSigningKey() {
        return keys.get(currentKid);
    }

    @Override
    public Optional<QrVerificationKey> findVerificationKey(String kid) {
        return Optional.ofNullable(keys.get(kid))
                .map(key -> new QrVerificationKey(key.kid(), key.publicKey()));
    }

    public int verificationKeyCount() {
        return keys.size();
    }

    private static QrSigningKey generateSigningKey(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = generator.generateKeyPair();
            return new QrSigningKey(kid, keyPair.getPrivate(), keyPair.getPublic());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate test QR signing key", e);
        }
    }
}
