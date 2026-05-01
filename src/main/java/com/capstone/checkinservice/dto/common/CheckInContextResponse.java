package com.capstone.checkinservice.dto.common;

import com.capstone.checkinservice.enums.ScanMode;
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
public class CheckInContextResponse {
    private String gateId;
    private Long checkerId;
    private String deviceId;
    private OffsetDateTime scannedAt;
    private ScanMode scanMode;
}
