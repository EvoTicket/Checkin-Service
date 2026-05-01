package com.capstone.checkinservice.dto.common;

import com.capstone.checkinservice.enums.ScanResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultMessage {
    private ScanResult resultCode;
    private String title;
    private String message;
    private String severity;
    private boolean admitAllowed;
    private boolean supportRequired;
}
