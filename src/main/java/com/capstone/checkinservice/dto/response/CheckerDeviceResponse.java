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
public class CheckerDeviceResponse {
    private String deviceId;
    private Long checkerId;
    private String deviceName;
    private String platform;
    private boolean trusted;
    private OffsetDateTime revokedAt;
    private OffsetDateTime lastSeenAt;
}
