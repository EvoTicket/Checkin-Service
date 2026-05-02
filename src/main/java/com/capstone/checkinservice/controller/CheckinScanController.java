package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.request.OnlineScanRequest;
import com.capstone.checkinservice.dto.response.ScanResultResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.service.CheckinScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker")
@RequiredArgsConstructor
@Tag(
        name = "Checker Scan",
        description = "APIs for online checker QR validation and atomic ticket check-in"
)
public class CheckinScanController {
    private final CheckinScanService checkinScanService;

    @PostMapping("/scan")
    @Operation(
            summary = "Scan ticket QR online",
            description = """
                    Validates a scanned Dynamic QR token for the selected event, showtime,
                    and optional gate, then atomically marks a valid ticket as used.
                    Business-denied outcomes are returned as stable resultCode values.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request shape"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Checker is not assigned or authorized"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<ScanResultResponse>> scanOnline(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader,
            @Valid @RequestBody OnlineScanRequest request
    ) {
        applyDeviceHeader(deviceIdHeader, request);
        ScanResultResponse response = checkinScanService.scanOnline(request);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Scan processed",
                response
        ));
    }

    private void applyDeviceHeader(String deviceIdHeader, OnlineScanRequest request) {
        if (deviceIdHeader == null || deviceIdHeader.isBlank()) {
            return;
        }
        String normalizedHeader = deviceIdHeader.trim();
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            request.setDeviceId(normalizedHeader);
            return;
        }
        if (!normalizedHeader.equals(request.getDeviceId().trim())) {
            throw new CheckinBusinessException(
                    ScanResult.DEVICE_MISMATCH,
                    HttpStatus.BAD_REQUEST,
                    "X-Device-Id header does not match request deviceId"
            );
        }
    }
}
