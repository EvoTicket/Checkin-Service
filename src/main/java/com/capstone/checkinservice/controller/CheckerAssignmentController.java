package com.capstone.checkinservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.response.CheckerAssignmentResponse;
import com.capstone.checkinservice.service.CheckerAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker")
@RequiredArgsConstructor
@Tag(
        name = "Checker Assignments",
        description = "APIs for retrieving checker event, showtime, gate, and shift assignments"
)
public class CheckerAssignmentController {
    private final CheckerAssignmentService checkerAssignmentService;

    @GetMapping("/assignments")
    @Operation(
            summary = "Get checker assignments",
            description = """
                    Returns the events, showtimes, gates, and shifts assigned to the current checker.
                    The checker must select an assignment scope before scanning tickets.
                    """
    )
    public ResponseEntity<BaseResponse<CheckerAssignmentResponse>> getAssignments() {
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Fetched checker assignments successfully",
                checkerAssignmentService.getAssignmentsForCurrentChecker()
        ));
    }
}
