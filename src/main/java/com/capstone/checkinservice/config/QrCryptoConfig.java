package com.capstone.checkinservice.config;

import com.capstone.checkinservice.crypto.QrTokenProperties;
import com.capstone.checkinservice.crypto.QrTokenSigner;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import com.capstone.checkinservice.crypto.key.QrKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class QrCryptoConfig {
    @Bean
    public QrTokenProperties qrTokenProperties() {
        return new QrTokenProperties();
    }

    @Bean
    public QrTokenSigner qrTokenSigner(QrKeyProvider qrKeyProvider, QrTokenProperties qrTokenProperties) {
        return new QrTokenSigner(qrKeyProvider, qrTokenProperties);
    }

    @Bean
    public QrTokenVerifier qrTokenVerifier(QrKeyProvider qrKeyProvider, QrTokenProperties qrTokenProperties) {
        return new QrTokenVerifier(qrKeyProvider, qrTokenProperties);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
