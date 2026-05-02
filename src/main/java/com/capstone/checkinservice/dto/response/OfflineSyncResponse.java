package com.capstone.checkinservice.dto.response;

import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
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
public class OfflineSyncResponse {
    private String packageId;
    private Long eventId;
    private Long showtimeId;
    private String gateId;
    private String deviceId;
    private OffsetDateTime syncedAt;
    private Summary summary;
    private Integer acceptedCount;
    private Integer rejectedCount;
    private Integer failedCount;
    private Integer conflictCount;
    private List<SyncItemResult> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SyncItemResult {
        private String localScanId;
        private Long ticketAssetId;
        private String ticketCode;
        private SyncResult syncStatus;
        private SyncResult syncResult;
        private ScanResult resultCode;
        private ScanResult scanResultCode;
        private String title;
        private String message;
        private ResultMessage resultMessage;
        private LocalContext local;
        private ServerContext server;
        private ConflictDetails conflictDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConflictDetails {
        private ScanResult localResult;
        private ScanResult serverResult;
        private OffsetDateTime localScannedAt;
        private String currentGateId;
        private OffsetDateTime firstSuccessfulCheckInAt;
        private String firstSuccessfulGateId;
        private Long firstSuccessfulCheckerId;
        private String firstSuccessfulDeviceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        private Integer total;
        private Integer accepted;
        private Integer rejected;
        private Integer failed;
        private Integer conflict;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LocalContext {
        private ScanResult resultCode;
        private OffsetDateTime scannedAt;
        private String gateId;
        private String deviceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServerContext {
        private ScanResult resultCode;
        private OffsetDateTime usedAt;
        private String usedAtGateId;
        private Long usedByCheckerId;
        private String usedByDeviceId;
    }
}
