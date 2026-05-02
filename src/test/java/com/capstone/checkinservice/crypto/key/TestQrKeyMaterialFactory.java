package com.capstone.checkinservice.crypto.key;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public final class TestQrKeyMaterialFactory {
    private TestQrKeyMaterialFactory() {
    }

    public static QrKeyMaterial generate(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = generator.generateKeyPair();
            return new QrKeyMaterial(
                    kid,
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate test QR key material", e);
        }
    }

    public record QrKeyMaterial(
            String kid,
            String privateKeyBase64,
            String publicKeyBase64
    ) {
    }
}
