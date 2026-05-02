package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.crypto.key.QrKeyProvider;
import com.capstone.checkinservice.crypto.key.QrVerificationKey;
import com.capstone.checkinservice.enums.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Instant;
import java.util.Objects;

public class QrTokenVerifier {
    private final QrKeyProvider keyProvider;
    private final QrTokenProperties properties;
    private final ObjectMapper objectMapper;

    public QrTokenVerifier(QrKeyProvider keyProvider) {
        this(keyProvider, new QrTokenProperties(), QrObjectMapperFactory.create());
    }

    public QrTokenVerifier(QrKeyProvider keyProvider, QrTokenProperties properties) {
        this(keyProvider, properties, QrObjectMapperFactory.create());
    }

    public QrTokenVerifier(QrKeyProvider keyProvider, QrTokenProperties properties, ObjectMapper objectMapper) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public QrTokenPayload verify(String token) {
        return verify(token, Instant.now());
    }

    public QrTokenPayload verify(String token, Instant verificationTime) {
        Objects.requireNonNull(verificationTime, "verificationTime must not be null");

        String[] segments = QrTokenFormatUtils.splitCompactToken(token);
        QrTokenHeader header = deserialize(segments[0], QrTokenHeader.class);
        validateHeader(header);

        QrVerificationKey verificationKey = keyProvider.findVerificationKey(header.kid())
                .orElseThrow(() -> new QrTokenException(
                        ScanResult.INVALID_SIGNATURE,
                        "QR token key id is not trusted"
                ));

        verifySignature(segments, header.alg(), verificationKey);

        QrTokenPayload payload = deserialize(segments[1], QrTokenPayload.class);
        payload.validate();
        validateIssuedAt(payload, verificationTime);
        validateExpiration(payload, verificationTime);

        return payload;
    }

    private void validateHeader(QrTokenHeader header) {
        if (header == null || header.alg() == null || header.alg().isBlank()) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token header is missing alg");
        }

        if (!properties.getSigningAlgorithm().equals(header.alg())) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token signing algorithm is not supported");
        }

        if (header.kid() == null || header.kid().isBlank()) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token header is missing kid");
        }
    }

    private <T> T deserialize(String segment, Class<T> type) {
        try {
            return objectMapper.readValue(QrTokenFormatUtils.decode(segment), type);
        } catch (IOException e) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token segment contains invalid JSON", e);
        }
    }

    private void verifySignature(String[] segments, String algorithm, QrVerificationKey verificationKey) {
        String signingInput = segments[0] + "." + segments[1];
        byte[] signatureBytes = QrTokenFormatUtils.decode(segments[2]);

        try {
            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(verificationKey.publicKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));

            if (!signature.verify(signatureBytes)) {
                throw new QrTokenException(ScanResult.INVALID_SIGNATURE, "QR token signature is invalid");
            }
        } catch (QrTokenException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new QrTokenException(ScanResult.INVALID_SIGNATURE, "QR token signature could not be verified", e);
        }
    }

    private void validateExpiration(QrTokenPayload payload, Instant verificationTime) {
        Instant exclusiveExpiry = payload.expiresAt().plusSeconds(properties.getClockSkewSeconds());
        if (!verificationTime.isBefore(exclusiveExpiry)) {
            throw new QrTokenException(ScanResult.QR_EXPIRED, "QR token has expired", payload);
        }
    }

    private void validateIssuedAt(QrTokenPayload payload, Instant verificationTime) {
        Instant allowedFutureIssuedAt = verificationTime.plusSeconds(properties.getClockSkewSeconds());
        if (payload.issuedAt().isAfter(allowedFutureIssuedAt)) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token issuedAt is in the future", payload);
        }
    }
}
