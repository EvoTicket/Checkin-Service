package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.response.OfflinePackageResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.service.OfflinePackageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OfflinePackageController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class OfflinePackageControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OfflinePackageService offlinePackageService;

    @Test
    void generateOfflinePackageReturnsCreatedEnvelope() throws Exception {
        when(offlinePackageService.generateOfflinePackage(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/checker/offline-packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Offline package created successfully"))
                .andExpect(jsonPath("$.data.packageId").value("pkg-test"))
                .andExpect(jsonPath("$.data.snapshotCount").value(1))
                .andExpect(jsonPath("$.data.ticketSnapshots[0].qrVersion").value(3))
                .andExpect(jsonPath("$.data.ticketSnapshots[0].accessStatus").value("VALID"));
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/checker/offline-packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 99,
                                  "showtimeId": 501
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unauthorizedCheckerReturnsForbiddenEnvelope() throws Exception {
        when(offlinePackageService.generateOfflinePackage(any())).thenThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        ));

        mockMvc.perform(post("/api/v1/checker/offline-packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.resultCode").value("UNAUTHORIZED_CHECKER"));
    }

    @Test
    void mismatchedDeviceHeaderReturnsBadRequestResultCode() throws Exception {
        mockMvc.perform(post("/api/v1/checker/offline-packages")
                        .header("X-Device-Id", "header-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resultCode").value("DEVICE_MISMATCH"));
    }

    private OfflinePackageResponse response() {
        OffsetDateTime issuedAt = OffsetDateTime.of(2026, 5, 1, 18, 42, 0, 0, ZoneOffset.UTC);
        return OfflinePackageResponse.builder()
                .packageId("pkg-test")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .checkerId(7001L)
                .deviceId("device-abc")
                .issuedAt(issuedAt)
                .validUntil(issuedAt.plusHours(6))
                .keyId("local-dev-key-v1")
                .publicVerificationKey("public-key")
                .keyAlgorithm("EC")
                .snapshotCount(1)
                .ticketSnapshots(List.of(OfflinePackageResponse.TicketSnapshot.builder()
                        .ticketAssetId(12345L)
                        .ticketCode("TCK-12345")
                        .eventId(99L)
                        .showtimeId(501L)
                        .ticketTypeName("VIP")
                        .qrVersion(3)
                        .accessStatus(TicketAccessStatus.VALID)
                        .usedAtGateId(null)
                        .allowedGateIds(List.of("A1"))
                        .build()))
                .checksum("sha256:test")
                .build();
    }

    private String validRequestJson() {
        return """
                {
                  "eventId": 99,
                  "showtimeId": 501,
                  "gateId": "A1",
                  "deviceId": "device-abc",
                  "requestedAt": "2026-05-01T18:42:00Z"
                }
                """;
    }
}
