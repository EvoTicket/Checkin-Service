package com.capstone.checkinservice.dto.request;

import com.capstone.checkinservice.enums.ScanResult;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class OfflineSyncRequest {
    @NotBlank
    @Size(max = 128)
    private String packageId;

    @NotNull
    private Long eventId;

    @NotNull
    private Long showtimeId;

    @Size(max = 128)
    private String gateId;

    @NotBlank
    @Size(max = 128)
    private String deviceId;

    private OffsetDateTime syncedAt;

    @Valid
    @NotNull
    @Size(min = 1)
    private List<OfflineSyncItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfflineSyncItemRequest {
        @Size(max = 128)
        private String localScanId;

        @Size(max = 4096)
        private String qrToken;

        private Long ticketAssetId;

        @Size(max = 128)
        private String qrTokenId;

        private Long eventId;

        private Long showtimeId;

        @Size(max = 128)
        private String gateId;

        @Size(max = 128)
        private String deviceId;

        @JsonAlias("localResult")
        private ScanResult localResultCode;

        private OffsetDateTime scannedAt;
    }
}
