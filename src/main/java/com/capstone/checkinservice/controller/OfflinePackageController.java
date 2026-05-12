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
        description = "Các API để tạo các gói dữ liệu ngoại tuyến (offline packages) có giới hạn phạm vi cho các thiết bị của checker"
)
public class OfflinePackageController {
    private final OfflinePackageService offlinePackageService;

    @PostMapping("/offline-packages")
    @Operation(
            summary = "Tạo gói dữ liệu ngoại tuyến",
            description = """
                    Tạo một gói dữ liệu ngoại tuyến có giới hạn phạm vi cho một thiết bị checker.
                    Gói này chứa các bản chụp (snapshots) truy cập vé an toàn và siêu dữ liệu xác minh mã QR
                    để xác thực cục bộ tạm thời khi không có kết nối mạng.
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
