package com.capstone.checkinservice.config;

import com.capstone.checkinservice.crypto.key.EnvironmentQrKeyProvider;
import com.capstone.checkinservice.crypto.key.QrKeyProvider;
import com.capstone.checkinservice.crypto.key.TestQrKeyMaterialFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QrKeyProviderConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(QrKeyProviderConfig.class);

    @Test
    void createsEnvironmentQrKeyProviderWhenPropertiesAreSupplied() {
        TestQrKeyMaterialFactory.QrKeyMaterial material = TestQrKeyMaterialFactory.generate("kid-main");

        contextRunner
                .withPropertyValues(
                        "app.qr.key-id=" + material.kid(),
                        "app.qr.private-key-base64=" + material.privateKeyBase64(),
                        "app.qr.public-key-base64=" + material.publicKeyBase64()
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(QrKeyProvider.class);
                    assertThat(context.getBean(QrKeyProvider.class)).isInstanceOf(EnvironmentQrKeyProvider.class);
                    assertThat(context.getBean(QrKeyProvider.class).getCurrentSigningKey().kid()).isEqualTo("kid-main");
                });
    }
}
