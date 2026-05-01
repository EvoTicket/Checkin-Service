package com.capstone.checkinservice.config;

import com.capstone.checkinservice.crypto.key.EnvironmentQrKeyProvider;
import com.capstone.checkinservice.crypto.key.QrKeyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QrKeyProviderConfig {
    @Bean
    public QrKeyProvider qrKeyProvider(
            @Value("${app.qr.key-id}") String keyId,
            @Value("${app.qr.private-key-base64}") String privateKeyBase64,
            @Value("${app.qr.public-key-base64}") String publicKeyBase64
    ) {
        return new EnvironmentQrKeyProvider(keyId, privateKeyBase64, publicKeyBase64);
    }
}
