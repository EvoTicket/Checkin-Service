package com.capstone.checkinservice.controller;

import io.swagger.v3.oas.annotations.Operation;
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
        description = "APIs for checker device registration and readiness checks"
)
public class CheckerDeviceController {
    private final CheckerDeviceService checkerDeviceService;

    @PostMapping
    @Operation(
            summary = "Register checker device",
            description = """
                    Registers or updates a checker device used at the event gate.
                    Device registration is used for scan audit, offline package scope,
                    and readiness checks.
                    """
    )
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

    @GetMapping("/{deviceId}/readiness")
    @Operation(
            summary = "Get checker device readiness",
            description = """
                    Returns readiness information for a checker device, such as whether
                    the device is ready for camera scanning, online validation, time-sensitive
                    QR verification, and offline fallback preparation.
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
