package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.request.OfflinePackageRequest;
import com.capstone.checkinservice.dto.response.OfflinePackageResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.service.OfflinePackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker")
@RequiredArgsConstructor
@Tag(
        name = "Offline Package",
        description = "APIs for generating scoped offline packages for checker devices"
)
public class OfflinePackageController {
    private final OfflinePackageService offlinePackageService;

    @PostMapping("/offline-packages")
    @Operation(
            summary = "Generate offline package",
            description = """
                    Generates a scoped offline package for a checker device. The package
                    contains safe ticket access snapshots and QR verification metadata for
                    provisional local validation while offline.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Offline package created"),
            @ApiResponse(responseCode = "400", description = "Invalid request shape or package scope"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Checker or device is not authorized"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<OfflinePackageResponse>> generateOfflinePackage(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader,
            @Valid @RequestBody OfflinePackageRequest request
    ) {
        applyDeviceHeader(deviceIdHeader, request);
        OfflinePackageResponse response = offlinePackageService.generateOfflinePackage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.of(
                HttpStatus.CREATED.value(),
                "Offline package created successfully",
                response
        ));
    }

    private void applyDeviceHeader(String deviceIdHeader, OfflinePackageRequest request) {
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
