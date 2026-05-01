package com.capstone.checkinservice.dto.request;

import com.capstone.checkinservice.enums.ScanResult;
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

    @NotBlank
    @Size(max = 128)
    private String deviceId;

    @Valid
    @NotNull
    @Size(min = 1)
    private List<OfflineSyncItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfflineSyncItemRequest {
        @NotBlank
        @Size(max = 128)
        private String localScanId;

        @NotBlank
        @Size(max = 4096)
        private String qrToken;

        @NotNull
        private Long eventId;

        @NotNull
        private Long showtimeId;

        @Size(max = 128)
        private String gateId;

        @NotNull
        private ScanResult localResultCode;

        @NotNull
        private OffsetDateTime scannedAt;
    }
}
