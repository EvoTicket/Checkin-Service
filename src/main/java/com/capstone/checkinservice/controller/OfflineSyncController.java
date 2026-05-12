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
        description = "Các API để đồng bộ hóa các lượt quét ngoại tuyến và phân loại kết quả: được chấp nhận, bị từ chối, thất bại hoặc xung đột"
)
public class OfflineSyncController {
    private final OfflineSyncService offlineSyncService;

    @PostMapping("/offline-sync")
    @Operation(
            summary = "Đồng bộ hóa lô quét ngoại tuyến",
            description = """
                    Xác thực lại các lượt quét ngoại tuyến tạm thời sau khi kết nối mạng được khôi phục.
                    Một lô hợp lệ sẽ trả về HTTP 200 với các kết quả cho từng mục: SYNC_ACCEPTED, SYNC_REJECTED,
                    SYNC_FAILED, hoặc SYNC_CONFLICT.
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
