package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.response.QrTokenResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.service.BuyerQrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BuyerQrController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class BuyerQrControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BuyerQrService buyerQrService;

    @Test
    void getQrTokenSuccessReturnsResponseEnvelope() throws Exception {
        when(buyerQrService.issueQrToken(12345L)).thenReturn(QrTokenResponse.builder()
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .qrVersion(3)
                .qrToken("signed-token")
                .issuedAt(OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.of(2026, 5, 1, 10, 0, 30, 0, ZoneOffset.UTC))
                .refreshAfterSeconds(15)
                .build());

        mockMvc.perform(get("/api/v1/tickets/{ticketAssetId}/qr-token", 12345L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("QR token issued successfully"))
                .andExpect(jsonPath("$.data.ticketAssetId").value(12345))
                .andExpect(jsonPath("$.data.qrToken").value("signed-token"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.refreshAfterSeconds").value(15));
    }

    @Test
    void lockedResaleReturnsStructuredResultCode() throws Exception {
        when(buyerQrService.issueQrToken(12345L)).thenThrow(new CheckinBusinessException(
                ScanResult.LOCKED_RESALE,
                HttpStatus.CONFLICT,
                "Ticket is locked for resale"
        ));

        mockMvc.perform(get("/api/v1/tickets/{ticketAssetId}/qr-token", 12345L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.data.resultCode").value("LOCKED_RESALE"));
    }

    @Test
    void ticketNotFoundReturnsStructuredResultCode() throws Exception {
        when(buyerQrService.issueQrToken(12345L)).thenThrow(new CheckinBusinessException(
                ScanResult.TICKET_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Ticket access state was not found"
        ));

        mockMvc.perform(get("/api/v1/tickets/{ticketAssetId}/qr-token", 12345L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.data.resultCode").value("TICKET_NOT_FOUND"));
    }

    @Test
    void nonOwnerReturnsForbiddenStructuredResultCode() throws Exception {
        when(buyerQrService.issueQrToken(12345L)).thenThrow(new CheckinBusinessException(
                ScanResult.OWNERSHIP_MISMATCH,
                HttpStatus.FORBIDDEN,
                "Authenticated user does not own this ticket"
        ));

        mockMvc.perform(get("/api/v1/tickets/{ticketAssetId}/qr-token", 12345L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.data.resultCode").value("OWNERSHIP_MISMATCH"));
    }
}
