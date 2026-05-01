package com.capstone.checkinservice.dto.response;

import com.capstone.checkinservice.enums.CheckerDeviceStatus;
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
public class CheckerDeviceReadinessResponse {
    private String deviceId;
    private Long checkerId;
    private boolean registered;
    private CheckerDeviceStatus status;
    private boolean trusted;
    private boolean revoked;
    private OffsetDateTime serverTime;
    private String message;
}
