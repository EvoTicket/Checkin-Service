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
        description = "Các API dành cho việc xác thực QR trực tuyến của checker và thực hiện check-in vé nguyên tử (atomic check-in)"
)
public class CheckinScanController {
    private final CheckinScanService checkinScanService;

    @PostMapping("/scan")
    @Operation(
            summary = "Quét mã QR vé trực tuyến",
            description = """
                    Xác thực mã QR động (Dynamic QR) được quét cho sự kiện, suất diễn đã chọn
                    và cổng tùy chọn, sau đó đánh dấu vé hợp lệ là đã sử dụng một cách nguyên tử.
                    Các kết quả bị từ chối do nghiệp vụ được trả về dưới dạng các giá trị resultCode ổn định.
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
