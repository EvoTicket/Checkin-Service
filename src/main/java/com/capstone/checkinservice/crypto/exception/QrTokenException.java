package com.capstone.checkinservice.crypto.exception;

import com.capstone.checkinservice.enums.ScanResult;

public class QrTokenException extends RuntimeException {
    private final ScanResult resultCode;

    public QrTokenException(ScanResult resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public QrTokenException(ScanResult resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }

    public ScanResult getResultCode() {
        return resultCode;
    }
}
