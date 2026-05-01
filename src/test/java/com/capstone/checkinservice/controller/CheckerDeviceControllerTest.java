package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.response.CheckerDeviceReadinessResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.service.CheckerDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckerDeviceController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckerDeviceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckerDeviceService checkerDeviceService;

    @Test
    void postDevicesRegistersDevice() throws Exception {
        when(checkerDeviceService.registerDevice(any())).thenReturn(CheckerDeviceResponse.builder()
                .deviceId("device-abc")
                .checkerId(7001L)
                .deviceName("Gate phone")
                .platform("WEB")
                .trusted(true)
                .lastSeenAt(OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .build());

        mockMvc.perform(post("/api/v1/checker/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "device-abc",
                                  "deviceName": "Gate phone",
                                  "platform": "WEB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("device-abc"))
                .andExpect(jsonPath("$.data.trusted").value(true));
    }

    @Test
    void getReadinessReturnsReadiness() throws Exception {
        when(checkerDeviceService.getReadiness(eq("device-abc"))).thenReturn(CheckerDeviceReadinessResponse.builder()
                .deviceId("device-abc")
                .checkerId(7001L)
                .registered(true)
                .trusted(true)
                .revoked(false)
                .serverTime(OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .message("Device is ready.")
                .build());

        mockMvc.perform(get("/api/v1/checker/devices/{deviceId}/readiness", "device-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("device-abc"))
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.trusted").value(true));
    }
}
