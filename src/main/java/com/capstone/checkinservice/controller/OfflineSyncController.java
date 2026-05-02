package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.request.OfflineSyncRequest;
import com.capstone.checkinservice.dto.response.OfflineSyncResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.service.OfflineSyncService;
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
        name = "Offline Sync",
        description = "APIs for synchronizing offline scans and classifying accepted, rejected, failed, and conflict results"
)
public class OfflineSyncController {
    private final OfflineSyncService offlineSyncService;

    @PostMapping("/offline-sync")
    @Operation(
            summary = "Sync offline scan batch",
            description = """
                    Revalidates provisional offline scans after network restoration. A valid
                    batch returns HTTP 200 with per-item SYNC_ACCEPTED, SYNC_REJECTED,
                    SYNC_FAILED, or SYNC_CONFLICT results.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offline sync batch processed"),
            @ApiResponse(responseCode = "400", description = "Invalid batch request or expired package"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Checker, device, or package scope is not authorized"),
            @ApiResponse(responseCode = "404", description = "Offline package not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<OfflineSyncResponse>> syncOfflineScans(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader,
            @Valid @RequestBody OfflineSyncRequest request
    ) {
        applyDeviceHeader(deviceIdHeader, request);
        OfflineSyncResponse response = offlineSyncService.syncOfflineScans(request);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Offline sync processed",
                response
        ));
    }

    private void applyDeviceHeader(String deviceIdHeader, OfflineSyncRequest request) {
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
