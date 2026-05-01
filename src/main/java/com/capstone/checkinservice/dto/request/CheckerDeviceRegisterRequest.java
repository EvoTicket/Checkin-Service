package com.capstone.checkinservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckerDeviceRegisterRequest {
    @NotBlank
    @Size(max = 128)
    private String deviceId;

    @Size(max = 255)
    private String deviceName;

    @Size(max = 64)
    private String platform;
}
