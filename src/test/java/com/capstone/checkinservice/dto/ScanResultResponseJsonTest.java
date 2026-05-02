package com.capstone.checkinservice.dto;

import com.capstone.checkinservice.dto.common.CheckInContextResponse;
import com.capstone.checkinservice.dto.common.TicketSummaryResponse;
import com.capstone.checkinservice.dto.response.ScanResultResponse;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.mapper.ScanResultMessageMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanResultResponseJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scanResultResponseSerializesEnumFieldsAsStableUppercaseStrings() throws Exception {
        ScanResultResponse response = ScanResultResponse.builder()
                .resultCode(ScanResult.VALID_CHECKED_IN)
                .resultMessage(ScanResultMessageMapper.toMessage(ScanResult.VALID_CHECKED_IN))
                .ticketAssetId(12345L)
                .ticketCode("TCK-001")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .checkerId(7001L)
                .message("Ticket checked in successfully")
                .ticketSummary(TicketSummaryResponse.builder()
                        .ticketAssetId(12345L)
                        .ticketCode("TCK-001")
                        .eventId(99L)
                        .showtimeId(501L)
                        .ticketTypeName("VIP")
                        .accessStatus(TicketAccessStatus.USED)
                        .build())
                .context(CheckInContextResponse.builder()
                        .gateId("A1")
                        .checkerId(7001L)
                        .deviceId("device-abc")
                        .scanMode(ScanMode.ONLINE)
                        .build())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("resultCode").asText()).isEqualTo("VALID_CHECKED_IN");
        assertThat(json.get("resultMessage").get("resultCode").asText()).isEqualTo("VALID_CHECKED_IN");
        assertThat(json.get("resultMessage").get("admitAllowed").asBoolean()).isTrue();
        assertThat(json.get("ticketSummary").get("accessStatus").asText()).isEqualTo("USED");
        assertThat(json.get("context").get("scanMode").asText()).isEqualTo("ONLINE");
        assertThat(json.get("ticketAssetId").asLong()).isEqualTo(12345L);
    }
}
