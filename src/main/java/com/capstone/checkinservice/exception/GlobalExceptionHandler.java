package com.capstone.checkinservice.exception;

import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.mapper.ScanResultMessageMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CheckinBusinessException.class)
    public ResponseEntity<BaseResponse<ResultMessage>> handleBusinessException(CheckinBusinessException exception) {
        ResultMessage resultMessage = ScanResultMessageMapper.toMessage(exception.getResultCode());
        return ResponseEntity
                .status(exception.getStatus())
                .body(BaseResponse.of(exception.getStatus().value(), exception.getMessage(), resultMessage));
    }

    @ExceptionHandler(QrTokenException.class)
    public ResponseEntity<BaseResponse<ResultMessage>> handleQrTokenException(QrTokenException exception) {
        ResultMessage resultMessage = ScanResultMessageMapper.toMessage(exception.getResultCode());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.of(HttpStatus.BAD_REQUEST.value(), exception.getMessage(), resultMessage));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<BaseResponse<ResultMessage>> handleBadRequest(Exception exception) {
        ResultMessage resultMessage = ResultMessage.builder()
                .title("Invalid request")
                .message(exception.getMessage())
                .severity("ERROR")
                .admitAllowed(false)
                .supportRequired(false)
                .build();
        return ResponseEntity
                .badRequest()
                .body(BaseResponse.of(HttpStatus.BAD_REQUEST.value(), "Invalid request", resultMessage));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<ResultMessage>> handleUnexpected(Exception exception, HttpServletRequest request) {
        ResultMessage resultMessage = ResultMessage.builder()
                .title("Internal server error")
                .message("An unexpected error occurred.")
                .severity("ERROR")
                .admitAllowed(false)
                .supportRequired(true)
                .build();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error",
                        resultMessage
                ));
    }
}
