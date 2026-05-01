package com.capstone.checkinservice.crypto;

public class QrTokenProperties {
    public static final String DEFAULT_TOKEN_TYPE = "evoticket-qr";
    public static final long DEFAULT_TTL_SECONDS = 30L;
    public static final long DEFAULT_REFRESH_AFTER_SECONDS = 15L;
    public static final long DEFAULT_CLOCK_SKEW_SECONDS = 0L;
    public static final String DEFAULT_SIGNING_ALGORITHM = "SHA256withECDSA";

    private final String tokenType;
    private final long ttlSeconds;
    private final long refreshAfterSeconds;
    private final long clockSkewSeconds;
    private final String signingAlgorithm;

    public QrTokenProperties() {
        this(
                DEFAULT_TOKEN_TYPE,
                DEFAULT_TTL_SECONDS,
                DEFAULT_REFRESH_AFTER_SECONDS,
                DEFAULT_CLOCK_SKEW_SECONDS,
                DEFAULT_SIGNING_ALGORITHM
        );
    }

    public QrTokenProperties(
            String tokenType,
            long ttlSeconds,
            long refreshAfterSeconds,
            long clockSkewSeconds,
            String signingAlgorithm
    ) {
        this.tokenType = requireText(tokenType, "tokenType");
        this.ttlSeconds = requirePositive(ttlSeconds, "ttlSeconds");
        this.refreshAfterSeconds = requirePositive(refreshAfterSeconds, "refreshAfterSeconds");
        this.clockSkewSeconds = requireNonNegative(clockSkewSeconds, "clockSkewSeconds");
        this.signingAlgorithm = requireText(signingAlgorithm, "signingAlgorithm");
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public long getRefreshAfterSeconds() {
        return refreshAfterSeconds;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static long requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
