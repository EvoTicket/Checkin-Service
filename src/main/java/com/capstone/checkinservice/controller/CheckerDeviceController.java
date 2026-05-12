package com.capstone.checkinservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.request.CheckerDeviceRegisterRequest;
import com.capstone.checkinservice.dto.response.CheckerDeviceReadinessResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.service.CheckerDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker/devices")
@RequiredArgsConstructor
@Tag(
        name = "Checker Devices",
        description = "Các API để đăng ký và kiểm tra các thiết bị client của checker"
)
public class CheckerDeviceController {
    private final CheckerDeviceService checkerDeviceService;

    @PostMapping({"/register", ""})
    @Operation(
            summary = "Đăng ký thiết bị checker",
            description = """
                    Đăng ký bản cài đặt client checker hiện tại và trả về ID thiết bị do máy chủ tạo ra.
                    Các thiết bị mới đăng ký sẽ bắt đầu ở trạng thái chờ (pending) và không được tin cậy theo mặc định.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device registered"),
            @ApiResponse(responseCode = "400", description = "Invalid request shape"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<CheckerDeviceResponse>> registerDevice(
            @Valid @RequestBody CheckerDeviceRegisterRequest request
    ) {
        CheckerDeviceResponse response = checkerDeviceService.registerDevice(request);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Checker device registered successfully",
                response
        ));
    }

    @GetMapping("/{deviceId}")
    @Operation(
            summary = "Lấy trạng thái thiết bị checker",
            description = "Trả về siêu dữ liệu trạng thái hiện tại của một thiết bị thuộc sở hữu của checker đã xác thực."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device status fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Device is not owned by current checker"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<CheckerDeviceResponse>> getDevice(@PathVariable String deviceId) {
        CheckerDeviceResponse response = checkerDeviceService.getDevice(deviceId);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Fetched checker device successfully",
                response
        ));
    }

    @GetMapping("/{deviceId}/readiness")
    @Operation(
            summary = "Kiểm tra mức độ sẵn sàng của thiết bị checker",
            description = """
                    Trả về thông tin về mức độ sẵn sàng của thiết bị checker, chẳng hạn như thiết bị đã sẵn sàng
                    để quét bằng camera, xác thực trực tuyến, xác minh mã QR nhạy cảm với thời gian,
                    và chuẩn bị dự phòng ngoại tuyến hay chưa.
                    """
    )
    public ResponseEntity<BaseResponse<CheckerDeviceReadinessResponse>> getReadiness(@PathVariable String deviceId) {
        CheckerDeviceReadinessResponse response = checkerDeviceService.getReadiness(deviceId);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Fetched checker device readiness successfully",
                response
        ));
    }
}
