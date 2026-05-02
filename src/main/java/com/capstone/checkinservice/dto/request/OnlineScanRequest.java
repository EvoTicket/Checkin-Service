package com.capstone.checkinservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineScanRequest {
    @NotBlank
    @Size(max = 4096)
    private String qrToken;

    @NotNull
    private Long eventId;

    @NotNull
    private Long showtimeId;

    @Size(max = 128)
    private String gateId;

    @Size(max = 128)
    private String deviceId;

    @NotNull
    private OffsetDateTime scannedAt;
}
