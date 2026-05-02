package com.capstone.checkinservice.dto.common;

import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketSummaryResponse {
    private Long ticketAssetId;
    private String ticketCode;
    private Long eventId;
    private Long showtimeId;
    private String ticketTypeName;
    private String zoneLabel;
    private String seatLabel;
    private TicketAccessStatus accessStatus;
}
