package com.capstone.checkinservice.crypto;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.enums.ScanResult;

import java.util.Base64;

final class QrTokenFormatUtils {
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private QrTokenFormatUtils() {
    }

    static String[] splitCompactToken(String token) {
        if (token == null || token.isBlank()) {
            throw invalid("QR token is empty");
        }

        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            throw invalid("QR token must contain exactly three segments");
        }

        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                throw invalid("QR token contains an empty segment");
            }
        }

        return segments;
    }

    static String encode(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    static byte[] decode(String segment) {
        try {
            return BASE64_URL_DECODER.decode(segment);
        } catch (IllegalArgumentException e) {
            throw new QrTokenException(ScanResult.INVALID_QR, "QR token contains invalid Base64 URL data", e);
        }
    }

    private static QrTokenException invalid(String message) {
        return new QrTokenException(ScanResult.INVALID_QR, message);
    }
}
