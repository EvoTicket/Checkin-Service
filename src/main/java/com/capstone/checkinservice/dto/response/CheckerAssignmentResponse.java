package com.capstone.checkinservice.dto.response;

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
public class CheckerAssignmentResponse {
    private List<Assignment> assignments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Assignment {
        private Long assignmentId;
        private Long eventId;
        private String eventName;
        private Long showtimeId;
        private String showtimeLabel;
        private List<String> gateIds;
        private String role;
        private OffsetDateTime validFrom;
        private OffsetDateTime validUntil;
    }
}
