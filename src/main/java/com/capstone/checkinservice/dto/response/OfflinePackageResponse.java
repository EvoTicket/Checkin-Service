package com.capstone.checkinservice.dto.response;

import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OfflinePackageResponse {
    private String packageId;
    private Long eventId;
    private Long showtimeId;
    private String gateId;
    private Long checkerId;
    private String deviceId;
    private OffsetDateTime issuedAt;
    private OffsetDateTime validUntil;
    private String keyId;
    private String publicVerificationKey;
    private String keyVersion;
    private List<TicketSnapshot> ticketSnapshots;
    private String checksum;
    private String packageSignature;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TicketSnapshot {
        private Long ticketAssetId;
        private String ticketCode;
        private String ticketTypeName;
        private String zoneLabel;
        private String seatLabel;
        private Integer qrVersion;
        private TicketAccessStatus accessStatus;
        private OffsetDateTime usedAt;
        private List<String> allowedGateIds;
        private String gatePolicySnapshot;
    }
}
