package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.enums.ScanResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class QrTokenPayloadValidationTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-01T10:00:30Z");

    @Test
    void requiredIdsAndTimestampsCannotBeNull() {
        assertInvalid(validPayload(null, 99L, 501L, 7, ISSUED_AT, EXPIRES_AT, "jti-1"));
        assertInvalid(validPayload(12345L, null, 501L, 7, ISSUED_AT, EXPIRES_AT, "jti-1"));
        assertInvalid(validPayload(12345L, 99L, null, 7, ISSUED_AT, EXPIRES_AT, "jti-1"));
        assertInvalid(validPayload(12345L, 99L, 501L, null, ISSUED_AT, EXPIRES_AT, "jti-1"));
        assertInvalid(validPayload(12345L, 99L, 501L, 7, null, EXPIRES_AT, "jti-1"));
        assertInvalid(validPayload(12345L, 99L, 501L, 7, ISSUED_AT, null, "jti-1"));
    }

    @Test
    void jtiCannotBeBlank() {
        assertInvalid(validPayload(12345L, 99L, 501L, 7, ISSUED_AT, EXPIRES_AT, null));
        assertInvalid(validPayload(12345L, 99L, 501L, 7, ISSUED_AT, EXPIRES_AT, ""));
        assertInvalid(validPayload(12345L, 99L, 501L, 7, ISSUED_AT, EXPIRES_AT, "   "));
    }

    @Test
    void expiresAtMustBeAfterIssuedAt() {
        assertInvalid(validPayload(12345L, 99L, 501L, 7, ISSUED_AT, ISSUED_AT, "jti-1"));
        assertInvalid(validPayload(12345L, 99L, 501L, 7, ISSUED_AT, ISSUED_AT.minusSeconds(1), "jti-1"));
    }

    private static QrTokenPayload validPayload(
            Long ticketAssetId,
            Long eventId,
            Long showtimeId,
            Integer qrVersion,
            Instant issuedAt,
            Instant expiresAt,
            String jti
    ) {
        return new QrTokenPayload(
                ticketAssetId,
                "TCK-12345",
                eventId,
                showtimeId,
                qrVersion,
                issuedAt,
                expiresAt,
                jti
        );
    }

    private static void assertInvalid(QrTokenPayload payload) {
        assertThatExceptionOfType(QrTokenException.class)
                .isThrownBy(payload::validate)
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.INVALID_QR));
    }
}
