package com.capstone.checkinservice.crypto.exception;

import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.enums.ScanResult;

public class QrTokenException extends RuntimeException {
    private final ScanResult resultCode;
    private final QrTokenPayload payload;

    public QrTokenException(ScanResult resultCode, String message) {
        this(resultCode, message, null, null);
    }

    public QrTokenException(ScanResult resultCode, String message, Throwable cause) {
        this(resultCode, message, cause, null);
    }

    public QrTokenException(ScanResult resultCode, String message, QrTokenPayload payload) {
        this(resultCode, message, null, payload);
    }

    public QrTokenException(ScanResult resultCode, String message, Throwable cause, QrTokenPayload payload) {
        super(message, cause);
        this.resultCode = resultCode;
        this.payload = payload;
    }

    public ScanResult getResultCode() {
        return resultCode;
    }

    public QrTokenPayload getPayload() {
        return payload;
    }
}
