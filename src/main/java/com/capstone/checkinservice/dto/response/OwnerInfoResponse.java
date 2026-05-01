package com.capstone.checkinservice.dto.response;

import com.capstone.checkinservice.enums.ConflictStatus;
import com.capstone.checkinservice.enums.FailureReason;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
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
public class OwnerInfoResponse {
    private Boolean supportOnly;
    private Boolean canOverride;
    private List<String> allowedActions;
    private TicketInfo ticket;
    private CurrentOwnerInfo currentOwner;
    private LatestSuccessfulCheckIn latestSuccessfulCheckIn;
    private List<RecentScanAttempt> recentScanAttempts;
    private List<OfflineSyncContext> offlineSyncContexts;
    private SupportContext supportContext;

    // Backward-compatible flat fields from the initial DTO foundation.
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TicketInfo {
        private Long ticketAssetId;
        private String ticketCode;
        private Long eventId;
        private Long showtimeId;
        private String eventName;
        private String showtimeLabel;
        private String ticketTypeName;
        private String zoneLabel;
        private String seatLabel;
        private TicketAccessStatus accessStatus;
        private Integer qrVersion;
        private List<String> allowedGateIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentOwnerInfo {
        private String ownerId;
        private String displayName;
        private String maskedEmail;
        private String maskedPhone;
        private OffsetDateTime effectiveFrom;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LatestSuccessfulCheckIn {
        private OffsetDateTime usedAt;
        private String usedAtGateId;
        private Long usedByCheckerId;
        private String deviceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecentScanAttempt {
        private ScanResult scanResult;
        private ScanMode scanMode;
        private OffsetDateTime scannedAt;
        private OffsetDateTime syncedAt;
        private String gateId;
        private Long checkerId;
        private String deviceId;
        private FailureReason failureReason;
        private ConflictStatus conflictStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OfflineSyncContext {
        private String localScanId;
        private String packageId;
        private SyncResult syncStatus;
        private ScanResult localResultCode;
        private ScanResult serverScanResult;
        private OffsetDateTime scannedAt;
        private OffsetDateTime syncedAt;
        private String gateId;
        private Long checkerId;
        private String deviceId;
        private FailureReason failureReason;
        private String conflictDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SupportContext {
        private ScanResult reason;
        private String message;
        private String recommendedAction;
    }
}
