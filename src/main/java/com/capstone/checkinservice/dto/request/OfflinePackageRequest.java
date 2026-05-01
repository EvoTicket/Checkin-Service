package com.capstone.checkinservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class OfflinePackageRequest {
    @NotNull
    private Long eventId;

    @NotNull
    private Long showtimeId;

    @Size(max = 128)
    private String gateId;

    @NotBlank
    @Size(max = 128)
    private String deviceId;

    @Positive
    private Integer requestedValidityMinutes;

    private OffsetDateTime requestedAt;
}
