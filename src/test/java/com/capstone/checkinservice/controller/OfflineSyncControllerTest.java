package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.response.OfflineSyncResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.mapper.ScanResultMessageMapper;
import com.capstone.checkinservice.service.OfflineSyncService;
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

@WebMvcTest(OfflineSyncController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class OfflineSyncControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OfflineSyncService offlineSyncService;

    @Test
    void syncOfflineScansReturnsOkEnvelopeWithSummaryAndItems() throws Exception {
        when(offlineSyncService.syncOfflineScans(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/checker/offline-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Offline sync processed"))
                .andExpect(jsonPath("$.data.packageId").value("pkg-1"))
                .andExpect(jsonPath("$.data.summary.total").value(1))
                .andExpect(jsonPath("$.data.summary.accepted").value(1))
                .andExpect(jsonPath("$.data.items[0].syncStatus").value("SYNC_ACCEPTED"))
                .andExpect(jsonPath("$.data.items[0].resultCode").value("SYNC_ACCEPTED"))
                .andExpect(jsonPath("$.data.items[0].scanResultCode").value("VALID_CHECKED_IN"));
    }

    @Test
    void invalidBatchRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/checker/offline-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packageId": "pkg-1",
                                  "deviceId": "device-abc",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void packageNotFoundReturnsTransportNotFoundEnvelope() throws Exception {
        when(offlineSyncService.syncOfflineScans(any())).thenThrow(new CheckinBusinessException(
                ScanResult.OFFLINE_PACKAGE_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Offline package was not found"
        ));

        mockMvc.perform(post("/api/v1/checker/offline-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.resultCode").value("OFFLINE_PACKAGE_NOT_FOUND"));
    }

    @Test
    void mismatchedDeviceHeaderReturnsBadRequestResultCode() throws Exception {
        mockMvc.perform(post("/api/v1/checker/offline-sync")
                        .header("X-Device-Id", "header-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resultCode").value("DEVICE_MISMATCH"));
    }

    private OfflineSyncResponse response() {
        OffsetDateTime syncedAt = OffsetDateTime.of(2026, 5, 1, 20, 5, 0, 0, ZoneOffset.UTC);
        return OfflineSyncResponse.builder()
                .packageId("pkg-1")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .deviceId("device-abc")
                .syncedAt(syncedAt)
                .summary(OfflineSyncResponse.Summary.builder()
                        .total(1)
                        .accepted(1)
                        .rejected(0)
                        .failed(0)
                        .conflict(0)
                        .build())
                .acceptedCount(1)
                .rejectedCount(0)
                .failedCount(0)
                .conflictCount(0)
                .items(List.of(OfflineSyncResponse.SyncItemResult.builder()
                        .localScanId("local-1")
                        .ticketAssetId(12345L)
                        .ticketCode("TCK-12345")
                        .syncStatus(SyncResult.SYNC_ACCEPTED)
                        .syncResult(SyncResult.SYNC_ACCEPTED)
                        .resultCode(ScanResult.SYNC_ACCEPTED)
                        .scanResultCode(ScanResult.VALID_CHECKED_IN)
                        .resultMessage(ScanResultMessageMapper.toMessage(ScanResult.SYNC_ACCEPTED))
                        .build()))
                .build();
    }

    private String validRequestJson() {
        return """
                {
                  "packageId": "pkg-1",
                  "eventId": 99,
                  "showtimeId": 501,
                  "gateId": "A1",
                  "deviceId": "device-abc",
                  "syncedAt": "2026-05-01T20:05:00Z",
                  "items": [
                    {
                      "localScanId": "local-1",
                      "qrToken": "signed-token",
                      "ticketAssetId": 12345,
                      "qrTokenId": "jti-12345",
                      "localResult": "OFFLINE_ACCEPTED_PENDING_SYNC",
                      "scannedAt": "2026-05-01T19:38:52Z",
                      "gateId": "A1",
                      "deviceId": "device-abc"
                    }
                  ]
                }
                """;
    }
}
