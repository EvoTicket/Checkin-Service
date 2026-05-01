package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.key.QrVerificationKey;
import com.capstone.checkinservice.crypto.key.TestQrKeyProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.assertj.core.api.Assertions.assertThat;

class QrKeyProviderTest {
    @Test
    void currentSigningKeyHasKid() {
        TestQrKeyProvider provider = TestQrKeyProvider.single("kid-main");

        assertThat(provider.getCurrentSigningKey().kid()).isEqualTo("kid-main");
    }

    @Test
    void verificationKeyCanBeFoundByKid() {
        TestQrKeyProvider provider = TestQrKeyProvider.single("kid-main");

        assertThat(provider.findVerificationKey("kid-main"))
                .isPresent()
                .get()
                .extracting(QrVerificationKey::kid)
                .isEqualTo("kid-main");
    }

    @Test
    void verificationKeyDoesNotExposePrivateKey() {
        assertThat(QrVerificationKey.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("kid", "publicKey")
                .doesNotContain("privateKey");
    }

    @Test
    void providerCanSupportMoreThanOneVerificationKey() {
        TestQrKeyProvider provider = TestQrKeyProvider.withGeneratedKeys("kid-main", "kid-next");

        assertThat(provider.verificationKeyCount()).isEqualTo(2);
        assertThat(provider.findVerificationKey("kid-main")).isPresent();
        assertThat(provider.findVerificationKey("kid-next")).isPresent();
    }
}
