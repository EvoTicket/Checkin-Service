package com.capstone.checkinservice.dto.response;

import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OwnerInfoResponse {
    private Long ticketAssetId;
    private String ticketCode;
    private Long eventId;
    private Long showtimeId;
    private String ticketTypeName;
    private String zoneLabel;
    private String seatLabel;
    private TicketAccessStatus accessStatus;
    private String maskedOwnerName;
    private String maskedOwnerEmail;
    private String maskedOwnerPhone;
    private ScanResult lastScanResult;
    private OffsetDateTime usedAt;
    private String usedAtGateId;
}
