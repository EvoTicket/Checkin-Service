package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.crypto.key.QrKeyProvider;
import com.capstone.checkinservice.crypto.key.QrSigningKey;
import com.capstone.checkinservice.enums.ScanResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Objects;

public class QrTokenSigner {
    private final QrKeyProvider keyProvider;
    private final QrTokenProperties properties;
    private final ObjectMapper objectMapper;

    public QrTokenSigner(QrKeyProvider keyProvider) {
        this(keyProvider, new QrTokenProperties(), QrObjectMapperFactory.create());
    }

    public QrTokenSigner(QrKeyProvider keyProvider, QrTokenProperties properties) {
        this(keyProvider, properties, QrObjectMapperFactory.create());
    }

    public QrTokenSigner(QrKeyProvider keyProvider, QrTokenProperties properties, ObjectMapper objectMapper) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String sign(QrTokenPayload payload) {
        if (payload == null) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token payload must not be null");
        }
        payload.validate();

        QrSigningKey signingKey = keyProvider.getCurrentSigningKey();
        QrTokenHeader header = new QrTokenHeader(
                properties.getSigningAlgorithm(),
                signingKey.kid(),
                properties.getTokenType()
        );

        String headerSegment = serializeSegment(header);
        String payloadSegment = serializeSegment(payload);
        String signingInput = headerSegment + "." + payloadSegment;
        byte[] signatureBytes = sign(signingInput, signingKey);

        return signingInput + "." + QrTokenFormatUtils.encode(signatureBytes);
    }

    private String serializeSegment(Object value) {
        try {
            return QrTokenFormatUtils.encode(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new QrTokenException(ScanResult.INVALID_QR, "Could not serialize QR token segment", e);
        }
    }

    private byte[] sign(String signingInput, QrSigningKey signingKey) {
        try {
            Signature signature = Signature.getInstance(properties.getSigningAlgorithm());
            signature.initSign(signingKey.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new QrTokenException(ScanResult.INVALID_SIGNATURE, "Could not sign QR token", e);
        }
    }
}
