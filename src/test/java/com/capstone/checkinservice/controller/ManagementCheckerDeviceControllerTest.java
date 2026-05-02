package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.config.SecurityConfig;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.enums.CheckerDeviceStatus;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.service.CheckerDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagementCheckerDeviceController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@AutoConfigureMockMvc
class ManagementCheckerDeviceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckerDeviceService checkerDeviceService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void organizerCanListPendingDevices() throws Exception {
        when(checkerDeviceService.listPendingDevices()).thenReturn(List.of(
                device("checker-device-b-02", CheckerDeviceStatus.PENDING, false, false)
        ));

        mockMvc.perform(get("/api/v1/management/checker/devices/pending").with(role("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].deviceId").value("checker-device-b-02"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].trusted").value(false))
                .andExpect(jsonPath("$.data[0].revoked").value(false));
    }

    @Test
    void adminCanListPendingDevices() throws Exception {
        when(checkerDeviceService.listPendingDevices()).thenReturn(List.of(
                device("checker-device-b-02", CheckerDeviceStatus.PENDING, false, false)
        ));

        mockMvc.perform(get("/api/v1/management/checker/devices/pending").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].deviceId").value("checker-device-b-02"));
    }

    @Test
    void checkerCannotListPendingDevices() throws Exception {
        mockMvc.perform(get("/api/v1/management/checker/devices/pending").with(role("CHECKER")))
                .andExpect(status().isForbidden());

        verify(checkerDeviceService, never()).listPendingDevices();
    }

    @Test
    void organizerCanTrustDevice() throws Exception {
        when(checkerDeviceService.trustDevice(eq("checker-device-b-02")))
                .thenReturn(device("checker-device-b-02", CheckerDeviceStatus.TRUSTED, true, false));

        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/trust", "checker-device-b-02")
                        .with(role("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("checker-device-b-02"))
                .andExpect(jsonPath("$.data.status").value("TRUSTED"))
                .andExpect(jsonPath("$.data.trusted").value(true))
                .andExpect(jsonPath("$.data.revoked").value(false));
    }

    @Test
    void adminCanTrustDevice() throws Exception {
        when(checkerDeviceService.trustDevice(eq("checker-device-b-02")))
                .thenReturn(device("checker-device-b-02", CheckerDeviceStatus.TRUSTED, true, false));

        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/trust", "checker-device-b-02")
                        .with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value("checker-device-b-02"))
                .andExpect(jsonPath("$.data.status").value("TRUSTED"));
    }

    @Test
    void checkerCannotTrustDevice() throws Exception {
        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/trust", "checker-device-b-02")
                        .with(role("CHECKER")))
                .andExpect(status().isForbidden());

        verify(checkerDeviceService, never()).trustDevice(anyString());
    }

    @Test
    void organizerCanRevokeDevice() throws Exception {
        when(checkerDeviceService.revokeDevice(eq("checker-device-b-02")))
                .thenReturn(device("checker-device-b-02", CheckerDeviceStatus.REVOKED, false, true));

        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/revoke", "checker-device-b-02")
                        .with(role("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("checker-device-b-02"))
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.trusted").value(false))
                .andExpect(jsonPath("$.data.revoked").value(true));
    }

    @Test
    void adminCanRevokeDevice() throws Exception {
        when(checkerDeviceService.revokeDevice(eq("checker-device-b-02")))
                .thenReturn(device("checker-device-b-02", CheckerDeviceStatus.REVOKED, false, true));

        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/revoke", "checker-device-b-02")
                        .with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value("checker-device-b-02"))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void checkerCannotRevokeDevice() throws Exception {
        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/revoke", "checker-device-b-02")
                        .with(role("CHECKER")))
                .andExpect(status().isForbidden());

        verify(checkerDeviceService, never()).revokeDevice(anyString());
    }

    @Test
    void unknownDeviceReturnsBusinessError() throws Exception {
        when(checkerDeviceService.trustDevice(eq("missing-device")))
                .thenThrow(new CheckinBusinessException(
                        ScanResult.DEVICE_NOT_ALLOWED,
                        HttpStatus.NOT_FOUND,
                        "Device is not registered"
                ));

        mockMvc.perform(patch("/api/v1/management/checker/devices/{deviceId}/trust", "missing-device")
                        .with(role("ORGANIZER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Device is not registered"))
                .andExpect(jsonPath("$.data.resultCode").value("DEVICE_NOT_ALLOWED"));
    }

    @Test
    void oldAdminPendingRouteDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/admin/checker/devices/pending").with(role("ADMIN")))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private CheckerDeviceResponse device(
            String deviceId,
            CheckerDeviceStatus status,
            boolean trusted,
            boolean revoked
    ) {
        return CheckerDeviceResponse.builder()
                .deviceId(deviceId)
                .checkerId(7001L)
                .deviceName("Gate phone")
                .platform("PWA")
                .appVersion("0.9.3")
                .status(status)
                .trusted(trusted)
                .revoked(revoked)
                .registeredAt(OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .lastSeenAt(OffsetDateTime.of(2026, 5, 1, 10, 5, 0, 0, ZoneOffset.UTC))
                .build();
    }
}
