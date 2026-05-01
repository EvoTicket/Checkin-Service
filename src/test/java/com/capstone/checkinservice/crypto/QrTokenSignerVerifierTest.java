package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.crypto.key.TestQrKeyProvider;
import com.capstone.checkinservice.enums.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class QrTokenSignerVerifierTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-01T10:00:30Z");

    private final TestQrKeyProvider keyProvider = TestQrKeyProvider.single("kid-main");
    private final QrTokenSigner signer = new QrTokenSigner(keyProvider);
    private final QrTokenVerifier verifier = new QrTokenVerifier(keyProvider);
    private final ObjectMapper objectMapper = QrObjectMapperFactory.create();

    @Test
    void signAndVerifySuccess() {
        QrTokenPayload payload = validPayload();

        String token = signer.sign(payload);
        QrTokenPayload verified = verifier.verify(token, ISSUED_AT.plusSeconds(5));

        assertThat(verified.ticketAssetId()).isEqualTo(payload.ticketAssetId());
        assertThat(verified.ticketCode()).isEqualTo(payload.ticketCode());
        assertThat(verified.eventId()).isEqualTo(payload.eventId());
        assertThat(verified.showtimeId()).isEqualTo(payload.showtimeId());
        assertThat(verified.qrVersion()).isEqualTo(payload.qrVersion());
        assertThat(verified.jti()).isEqualTo(payload.jti());
        assertThat(verified.issuedAt()).isEqualTo(payload.issuedAt());
        assertThat(verified.expiresAt()).isEqualTo(payload.expiresAt());
    }

    @Test
    void expiredTokenRejected() {
        String token = signer.sign(validPayload());

        assertResultCode(
                () -> verifier.verify(token, EXPIRES_AT.plusMillis(1)),
                ScanResult.QR_EXPIRED
        );
    }

    @Test
    void expiresAtEqualVerificationTimeRejected() {
        String token = signer.sign(validPayload());

        assertResultCode(
                () -> verifier.verify(token, EXPIRES_AT),
                ScanResult.QR_EXPIRED
        );
    }

    @Test
    void tamperedPayloadRejected() throws Exception {
        String token = signer.sign(validPayload());
        String[] segments = QrTokenFormatUtils.splitCompactToken(token);
        ObjectNode payloadJson = (ObjectNode) objectMapper.readTree(QrTokenFormatUtils.decode(segments[1]));
        payloadJson.put("eventId", 999L);
        String tamperedPayloadSegment = QrTokenFormatUtils.encode(objectMapper.writeValueAsBytes(payloadJson));
        String tamperedToken = segments[0] + "." + tamperedPayloadSegment + "." + segments[2];

        assertResultCode(
                () -> verifier.verify(tamperedToken, ISSUED_AT.plusSeconds(5)),
                ScanResult.INVALID_SIGNATURE
        );
    }

    @Test
    void tamperedSignatureRejected() {
        String token = signer.sign(validPayload());
        String[] segments = QrTokenFormatUtils.splitCompactToken(token);
        byte[] signatureBytes = QrTokenFormatUtils.decode(segments[2]);
        signatureBytes[signatureBytes.length - 1] = (byte) (signatureBytes[signatureBytes.length - 1] ^ 0x01);
        String tamperedToken = segments[0] + "." + segments[1] + "." + QrTokenFormatUtils.encode(signatureBytes);

        assertResultCode(
                () -> verifier.verify(tamperedToken, ISSUED_AT.plusSeconds(5)),
                ScanResult.INVALID_SIGNATURE
        );
    }

    @Test
    void wrongKeyRejected() {
        String token = signer.sign(validPayload());
        QrTokenVerifier wrongKeyVerifier = new QrTokenVerifier(TestQrKeyProvider.single("kid-main"));

        assertResultCode(
                () -> wrongKeyVerifier.verify(token, ISSUED_AT.plusSeconds(5)),
                ScanResult.INVALID_SIGNATURE
        );
    }

    @Test
    void unknownKidRejected() throws Exception {
        String token = signer.sign(validPayload());
        String[] segments = QrTokenFormatUtils.splitCompactToken(token);
        ObjectNode headerJson = (ObjectNode) objectMapper.readTree(QrTokenFormatUtils.decode(segments[0]));
        headerJson.put("kid", "unknown-kid");
        String unknownKidHeaderSegment = QrTokenFormatUtils.encode(objectMapper.writeValueAsBytes(headerJson));
        String unknownKidToken = unknownKidHeaderSegment + "." + segments[1] + "." + segments[2];

        assertResultCode(
                () -> verifier.verify(unknownKidToken, ISSUED_AT.plusSeconds(5)),
                ScanResult.INVALID_SIGNATURE
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "one-segment",
            "two.segments",
            "four.segment.tokens.fail",
            "@@@.payload.signature"
    })
    void invalidFormatRejected(String token) {
        assertResultCode(
                () -> verifier.verify(token, ISSUED_AT.plusSeconds(5)),
                ScanResult.INVALID_QR
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ticketAssetId",
            "eventId",
            "showtimeId",
            "qrVersion",
            "issuedAt",
            "expiresAt",
            "jti"
    })
    void missingRequiredClaimRejected(String fieldName) throws Exception {
        String token = tokenWithPayloadFieldRemoved(fieldName);

        assertResultCode(
                () -> verifier.verify(token, ISSUED_AT.plusSeconds(5)),
                ScanResult.INVALID_QR
        );
    }

    @Test
    void signatureNotStoredInsidePayload() throws Exception {
        String token = signer.sign(validPayload());
        String[] segments = QrTokenFormatUtils.splitCompactToken(token);
        JsonNode payloadJson = objectMapper.readTree(QrTokenFormatUtils.decode(segments[1]));

        assertThat(payloadJson.has("signature")).isFalse();
        assertThat(payloadJson.has("privateKey")).isFalse();
        assertThat(payloadJson.has("secret")).isFalse();
    }

    @Test
    void jtiAndQrVersionPreserved() {
        QrTokenPayload verified = verifier.verify(signer.sign(validPayload()), ISSUED_AT.plusSeconds(5));

        assertThat(verified.jti()).isEqualTo("jti-abc-123");
        assertThat(verified.qrVersion()).isEqualTo(7);
    }

    private QrTokenPayload validPayload() {
        return new QrTokenPayload(
                12345L,
                "TCK-12345",
                99L,
                501L,
                7,
                ISSUED_AT,
                EXPIRES_AT,
                "jti-abc-123"
        );
    }

    private String tokenWithPayloadFieldRemoved(String fieldName) throws Exception {
        String token = signer.sign(validPayload());
        String[] segments = QrTokenFormatUtils.splitCompactToken(token);
        ObjectNode payloadJson = (ObjectNode) objectMapper.readTree(QrTokenFormatUtils.decode(segments[1]));
        payloadJson.remove(fieldName);
        String payloadSegment = QrTokenFormatUtils.encode(objectMapper.writeValueAsBytes(payloadJson));
        String signingInput = segments[0] + "." + payloadSegment;

        return signingInput + "." + signInput(signingInput);
    }

    private String signInput(String signingInput) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(QrTokenProperties.DEFAULT_SIGNING_ALGORITHM);
        signature.initSign(keyProvider.getCurrentSigningKey().privateKey());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return QrTokenFormatUtils.encode(signature.sign());
    }

    private static void assertResultCode(Runnable action, ScanResult expectedResultCode) {
        assertThatExceptionOfType(QrTokenException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(expectedResultCode));
    }
}
