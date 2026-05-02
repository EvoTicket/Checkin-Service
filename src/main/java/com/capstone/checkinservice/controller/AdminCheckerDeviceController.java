package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.service.CheckerDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/checker/devices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CHECKER_SUPERVISOR', 'ROLE_ORGANIZER_MANAGER')")
@Tag(
        name = "Admin Checker Devices",
        description = "Admin APIs for approving and revoking checker devices"
)
public class AdminCheckerDeviceController {
    private final CheckerDeviceService checkerDeviceService;

    @GetMapping("/pending")
    @Operation(
            summary = "List pending checker devices",
            description = "Returns checker devices that are untrusted and not revoked, newest registrations first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending checker devices fetched"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to manage checker devices"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<List<CheckerDeviceResponse>>> listPendingDevices() {
        List<CheckerDeviceResponse> response = checkerDeviceService.listPendingDevices();
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Fetched pending checker devices successfully",
                response
        ));
    }

    @PatchMapping("/{deviceId}/trust")
    @Operation(
            summary = "Trust checker device",
            description = "Approves a checker device using its external business device id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checker device trusted"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to manage checker devices"),
            @ApiResponse(responseCode = "404", description = "Checker device was not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<CheckerDeviceResponse>> trustDevice(@PathVariable String deviceId) {
        CheckerDeviceResponse response = checkerDeviceService.trustDevice(deviceId);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Checker device trusted successfully",
                response
        ));
    }

    @PatchMapping("/{deviceId}/revoke")
    @Operation(
            summary = "Revoke checker device",
            description = "Revokes a checker device using its external business device id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checker device revoked"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to manage checker devices"),
            @ApiResponse(responseCode = "404", description = "Checker device was not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<CheckerDeviceResponse>> revokeDevice(@PathVariable String deviceId) {
        CheckerDeviceResponse response = checkerDeviceService.revokeDevice(deviceId);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Checker device revoked successfully",
                response
        ));
    }
}
