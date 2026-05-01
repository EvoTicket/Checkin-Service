package com.capstone.checkinservice.enums;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EnumSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scanResultContainsAllStableContractValues() {
        assertThat(Arrays.stream(ScanResult.values()).map(Enum::name))
                .containsExactly(
                        "VALID_CHECKED_IN",
                        "ALREADY_USED",
                        "QR_EXPIRED",
                        "INVALID_QR",
                        "INVALID_SIGNATURE",
                        "INVALID_QR_VERSION",
                        "WRONG_EVENT",
                        "WRONG_SHOWTIME",
                        "WRONG_GATE",
                        "LOCKED_RESALE",
                        "CANCELLED",
                        "OFFLINE_ACCEPTED_PENDING_SYNC",
                        "SYNC_ACCEPTED",
                        "SYNC_REJECTED",
                        "SYNC_FAILED",
                        "SYNC_CONFLICT",
                        "UNAUTHORIZED_CHECKER",
                        "TICKET_NOT_FOUND",
                        "OFFLINE_PACKAGE_EXPIRED",
                        "OFFLINE_PACKAGE_NOT_FOUND",
                        "DEVICE_TIME_INVALID"
                );
    }

    @Test
    void ticketAccessStatusSerializesAsExactUppercaseString() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(TicketAccessStatus.VALID)).isEqualTo("\"VALID\"");
        assertThat(objectMapper.writeValueAsString(TicketAccessStatus.LOCKED_RESALE)).isEqualTo("\"LOCKED_RESALE\"");
        assertThat(objectMapper.writeValueAsString(TicketAccessStatus.USED)).isEqualTo("\"USED\"");
        assertThat(objectMapper.writeValueAsString(TicketAccessStatus.CANCELLED)).isEqualTo("\"CANCELLED\"");
    }

    @Test
    void scanResultSerializesAsExactUppercaseString() throws JsonProcessingException {
        for (ScanResult result : ScanResult.values()) {
            assertThat(objectMapper.writeValueAsString(result)).isEqualTo("\"" + result.name() + "\"");
        }
    }
}
