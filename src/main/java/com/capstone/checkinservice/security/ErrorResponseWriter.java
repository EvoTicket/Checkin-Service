package com.capstone.checkinservice.security;

import com.capstone.checkinservice.exception.ErrorCode;
import com.capstone.checkinservice.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class ErrorResponseWriter {
    private final ObjectMapper mapper;

    public ErrorResponseWriter() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public void write(HttpServletResponse response,
                      HttpServletRequest request,
                      ErrorCode errorCode) throws IOException {

        response.setContentType("application/json");
        response.setStatus(errorCode.getStatus().value());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .path(request.getServletPath())
                .build();

        mapper.writeValue(response.getOutputStream(), errorResponse);
    }
}