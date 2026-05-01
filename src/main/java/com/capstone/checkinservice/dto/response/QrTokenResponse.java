package com.capstone.checkinservice.dto.response;

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
public class QrTokenResponse {
    private Long ticketAssetId;
    private String ticketCode;
    private Long eventId;
    private Long showtimeId;
    private Integer qrVersion;
    private String qrToken;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private Integer refreshAfterSeconds;
}
