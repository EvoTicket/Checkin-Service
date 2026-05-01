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
        private SyncResult syncResult;
        private ScanResult scanResultCode;
        private ResultMessage resultMessage;
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
}
