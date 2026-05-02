package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.enums.ScanResult;

import java.time.Instant;

public record QrTokenPayload(
        Long ticketAssetId,
        String ticketCode,
        Long eventId,
        Long showtimeId,
        Integer qrVersion,
        Instant issuedAt,
        Instant expiresAt,
        String jti
) {
    public void validate() {
        requireNotNull(ticketAssetId, "ticketAssetId");
        requireNotNull(eventId, "eventId");
        requireNotNull(showtimeId, "showtimeId");
        requireNotNull(qrVersion, "qrVersion");
        requireNotNull(issuedAt, "issuedAt");
        requireNotNull(expiresAt, "expiresAt");

        if (jti == null || jti.isBlank()) {
            throw invalid("Missing required QR token claim: jti");
        }

        if (!expiresAt.isAfter(issuedAt)) {
            throw invalid("QR token expiresAt must be after issuedAt");
        }
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw invalid("Missing required QR token claim: " + fieldName);
        }
    }

    private static QrTokenException invalid(String message) {
        return new QrTokenException(ScanResult.INVALID_QR, message);
    }
}
