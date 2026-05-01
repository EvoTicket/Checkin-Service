package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.response.OwnerInfoResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.service.SupportLookupService;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportLookupController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class SupportLookupControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupportLookupService supportLookupService;

    @Test
    void getOwnerInfoReturnsSupportEnvelope() throws Exception {
        when(supportLookupService.getOwnerInfo(12345L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/checker/tickets/{ticketAssetId}/owner-info", 12345L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Fetched ticket owner info successfully"))
                .andExpect(jsonPath("$.data.supportOnly").value(true))
                .andExpect(jsonPath("$.data.canOverride").value(false))
                .andExpect(jsonPath("$.data.ticket.ticketAssetId").value(12345))
                .andExpect(jsonPath("$.data.currentOwner.ownerId").value("usr_****0010"))
                .andExpect(jsonPath("$.data.supportContext.reason").value("ALREADY_USED"));
    }

    @Test
    void unassignedCheckerReturnsForbiddenEnvelope() throws Exception {
        when(supportLookupService.getOwnerInfo(12345L)).thenThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        ));

        mockMvc.perform(get("/api/v1/checker/tickets/{ticketAssetId}/owner-info", 12345L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.resultCode").value("UNAUTHORIZED_CHECKER"));
    }

    @Test
    void ticketNotFoundReturnsNotFoundEnvelope() throws Exception {
        when(supportLookupService.getOwnerInfo(12345L)).thenThrow(new CheckinBusinessException(
                ScanResult.TICKET_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Ticket access state was not found"
        ));

        mockMvc.perform(get("/api/v1/checker/tickets/{ticketAssetId}/owner-info", 12345L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.resultCode").value("TICKET_NOT_FOUND"));
    }

    private OwnerInfoResponse response() {
        OffsetDateTime usedAt = OffsetDateTime.of(2026, 5, 1, 19, 31, 10, 0, ZoneOffset.UTC);
        return OwnerInfoResponse.builder()
                .supportOnly(true)
                .canOverride(false)
                .allowedActions(List.of("CALL_SUPPORT", "BACK_TO_SCAN"))
                .ticket(OwnerInfoResponse.TicketInfo.builder()
                        .ticketAssetId(12345L)
                        .ticketCode("TCK-12345")
                        .eventId(99L)
                        .showtimeId(501L)
                        .ticketTypeName("VIP Standing")
                        .accessStatus(TicketAccessStatus.USED)
                        .qrVersion(3)
                        .allowedGateIds(List.of("A1"))
                        .build())
                .currentOwner(OwnerInfoResponse.CurrentOwnerInfo.builder()
                        .ownerId("usr_****0010")
                        .maskedEmail("han.***@gmail.com")
                        .maskedPhone("09******28")
                        .build())
                .latestSuccessfulCheckIn(OwnerInfoResponse.LatestSuccessfulCheckIn.builder()
                        .usedAt(usedAt)
                        .usedAtGateId("A1")
                        .usedByCheckerId(7001L)
                        .deviceId("device-abc")
                        .build())
                .supportContext(OwnerInfoResponse.SupportContext.builder()
                        .reason(ScanResult.ALREADY_USED)
                        .message("Ve da duoc ghi nhan check-in truoc do.")
                        .recommendedAction("CALL_SUPPORT")
                        .build())
                .build();
    }
}
