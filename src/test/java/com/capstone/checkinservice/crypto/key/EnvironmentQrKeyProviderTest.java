package com.capstone.checkinservice.crypto.key;

import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenSigner;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class EnvironmentQrKeyProviderTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-01T10:00:30Z");

    @Test
    void parsesValidGeneratedKeyMaterial() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("qr-key-local-v1");

        EnvironmentQrKeyProvider provider = providerFrom(material);

        assertThat(provider.getCurrentSigningKey().kid()).isEqualTo("qr-key-local-v1");
        assertThat(provider.getCurrentSigningKey().privateKey().getAlgorithm()).isEqualTo("EC");
        assertThat(provider.getCurrentSigningKey().publicKey().getAlgorithm()).isEqualTo("EC");
    }

    @Test
    void signerAndVerifierWorkUsingEnvironmentKeyMaterial() {
        EnvironmentQrKeyProvider provider = providerFrom(TestQrKeyMaterialFactory.generate("kid-main"));
        QrTokenSigner signer = new QrTokenSigner(provider);
        QrTokenVerifier verifier = new QrTokenVerifier(provider);

        QrTokenPayload verified = verifier.verify(signer.sign(validPayload()), ISSUED_AT.plusSeconds(5));

        assertThat(verified.ticketAssetId()).isEqualTo(12345L);
        assertThat(verified.jti()).isEqualTo("jti-env-123");
    }

    @Test
    void unknownKidReturnsNoVerificationKey() {
        EnvironmentQrKeyProvider provider = providerFrom(TestQrKeyMaterialFactory.generate("kid-main"));

        assertThat(provider.findVerificationKey("unknown-kid")).isEmpty();
    }

    @Test
    void blankKeyIdFailsClearly() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("kid-main");

        assertThatIllegalStateException()
                .isThrownBy(() -> new EnvironmentQrKeyProvider(
                        " ",
                        material.privateKeyBase64(),
                        material.publicKeyBase64()
                ))
                .withMessageContaining("QR key id must not be blank");
    }

    @Test
    void blankPrivateKeyFailsClearly() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("kid-main");

        assertThatIllegalStateException()
                .isThrownBy(() -> new EnvironmentQrKeyProvider(
                        material.kid(),
                        " ",
                        material.publicKeyBase64()
                ))
                .withMessageContaining("QR private key must not be blank");
    }

    @Test
    void blankPublicKeyFailsClearly() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("kid-main");

        assertThatIllegalStateException()
                .isThrownBy(() -> new EnvironmentQrKeyProvider(
                        material.kid(),
                        material.privateKeyBase64(),
                        " "
                ))
                .withMessageContaining("QR public key must not be blank");
    }

    @Test
    void invalidPrivateKeyMaterialFailsClearly() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("kid-main");

        assertThatIllegalStateException()
                .isThrownBy(() -> new EnvironmentQrKeyProvider(
                        material.kid(),
                        "not-base64",
                        material.publicKeyBase64()
                ))
                .withMessageContaining("Invalid QR private key material");
    }

    @Test
    void invalidPublicKeyMaterialFailsClearly() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("kid-main");

        assertThatIllegalStateException()
                .isThrownBy(() -> new EnvironmentQrKeyProvider(
                        material.kid(),
                        material.privateKeyBase64(),
                        "not-base64"
                ))
                .withMessageContaining("Invalid QR public key material");
    }

    private static EnvironmentQrKeyProvider providerFrom(TestQrKeyMaterialFactory.QrKeyMaterial material) {
        return new EnvironmentQrKeyProvider(
                material.kid(),
                material.privateKeyBase64(),
                material.publicKeyBase64()
        );
    }

    private static QrTokenPayload validPayload() {
        return new QrTokenPayload(
                12345L,
                "TCK-12345",
                99L,
                501L,
                7,
                ISSUED_AT,
                EXPIRES_AT,
                "jti-env-123"
        );
    }
}
