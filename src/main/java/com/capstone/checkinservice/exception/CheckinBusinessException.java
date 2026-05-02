package com.capstone.checkinservice.exception;

import com.capstone.checkinservice.enums.ScanResult;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CheckinBusinessException extends RuntimeException {
    private final ScanResult resultCode;
    private final HttpStatus status;

    public CheckinBusinessException(ScanResult resultCode, HttpStatus status, String message) {
        super(message);
        this.resultCode = resultCode;
        this.status = status;
    }

}