package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.common.CheckInContextResponse;
import com.capstone.checkinservice.dto.common.TicketSummaryResponse;
import com.capstone.checkinservice.dto.response.ScanResultResponse;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.mapper.ScanResultMessageMapper;
import com.capstone.checkinservice.service.CheckinScanService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckinScanController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckinScanControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckinScanService checkinScanService;

    @Test
    void scanOnlineReturnsResponseEnvelope() throws Exception {
        when(checkinScanService.scanOnline(any())).thenReturn(successResponse());

        mockMvc.perform(post("/api/v1/checker/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qrToken": "signed-token",
                                  "eventId": 99,
                                  "showtimeId": 501,
                                  "gateId": "A1",
                                  "deviceId": "device-abc",
                                  "scannedAt": "2026-05-01T19:42:18Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Scan processed"))
                .andExpect(jsonPath("$.data.resultCode").value("VALID_CHECKED_IN"))
                .andExpect(jsonPath("$.data.ticketAssetId").value(12345))
                .andExpect(jsonPath("$.data.context.scanMode").value("ONLINE"))
                .andExpect(jsonPath("$.data.ticketSummary.accessStatus").value("USED"));
    }

    @Test
    void businessDeniedScanStillReturnsHttpOkWithResultCode() throws Exception {
        when(checkinScanService.scanOnline(any())).thenReturn(ScanResultResponse.builder()
                .resultCode(ScanResult.ALREADY_USED)
                .resultMessage(ScanResultMessageMapper.toMessage(ScanResult.ALREADY_USED))
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .message("This ticket has already been checked in.")
                .build());

        mockMvc.perform(post("/api/v1/checker/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("ALREADY_USED"));
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/checker/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 99,
                                  "showtimeId": 501,
                                  "scannedAt": "2026-05-01T19:42:18Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unauthorizedCheckerUsesExistingForbiddenErrorEnvelope() throws Exception {
        when(checkinScanService.scanOnline(any())).thenThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        ));

        mockMvc.perform(post("/api/v1/checker/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.resultCode").value("UNAUTHORIZED_CHECKER"));
    }

    @Test
    void mismatchedDeviceHeaderReturnsBadRequestResultCode() throws Exception {
        mockMvc.perform(post("/api/v1/checker/scan")
                        .header("X-Device-Id", "header-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resultCode").value("DEVICE_MISMATCH"));
    }

    private ScanResultResponse successResponse() {
        OffsetDateTime checkedInAt = OffsetDateTime.of(2026, 5, 1, 19, 42, 18, 0, ZoneOffset.UTC);
        return ScanResultResponse.builder()
                .resultCode(ScanResult.VALID_CHECKED_IN)
                .resultMessage(ScanResultMessageMapper.toMessage(ScanResult.VALID_CHECKED_IN))
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .checkedInAt(checkedInAt)
                .checkerId(7001L)
                .message("Ticket is valid and has been checked in.")
                .ticketSummary(TicketSummaryResponse.builder()
                        .ticketAssetId(12345L)
                        .ticketCode("TCK-12345")
                        .eventId(99L)
                        .showtimeId(501L)
                        .ticketTypeName("VIP Standing")
                        .zoneLabel("Zone A")
                        .accessStatus(TicketAccessStatus.USED)
                        .build())
                .context(CheckInContextResponse.builder()
                        .gateId("A1")
                        .checkerId(7001L)
                        .deviceId("device-abc")
                        .scannedAt(checkedInAt)
                        .scanMode(ScanMode.ONLINE)
                        .build())
                .build();
    }

    private String validRequestJson() {
        return """
                {
                  "qrToken": "signed-token",
                  "eventId": 99,
                  "showtimeId": 501,
                  "gateId": "A1",
                  "deviceId": "device-abc",
                  "scannedAt": "2026-05-01T19:42:18Z"
                }
                """;
    }
}
