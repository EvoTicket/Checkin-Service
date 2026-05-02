package com.capstone.checkinservice.dto.response;

import com.capstone.checkinservice.dto.common.CheckInContextResponse;
import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.dto.common.TicketSummaryResponse;
import com.capstone.checkinservice.enums.ScanResult;
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
public class ScanResultResponse {
    private ScanResult resultCode;
    private ResultMessage resultMessage;
    private Long ticketAssetId;
    private String ticketCode;
    private Long eventId;
    private Long showtimeId;
    private String gateId;
    private OffsetDateTime checkedInAt;
    private Long checkerId;
    private String message;
    private TicketSummaryResponse ticketSummary;
    private CheckInContextResponse context;
    private OffsetDateTime firstCheckedInAt;
    private String firstGateId;
}
