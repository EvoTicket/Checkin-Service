package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.response.CheckerAssignmentResponse;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.exception.GlobalExceptionHandler;
import com.capstone.checkinservice.service.CheckerAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckerAssignmentController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckerAssignmentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckerAssignmentService checkerAssignmentService;

    @Test
    void getAssignmentsReturnsAssignmentList() throws Exception {
        when(checkerAssignmentService.getAssignmentsForCurrentChecker()).thenReturn(CheckerAssignmentResponse.builder()
                .assignments(List.of(CheckerAssignmentResponse.Assignment.builder()
                        .assignmentId(10L)
                        .eventId(99L)
                        .showtimeId(501L)
                        .gateIds(List.of("A1", "A2"))
                        .role("CHECKER")
                        .validFrom(OffsetDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC))
                        .validUntil(OffsetDateTime.of(2026, 5, 1, 23, 0, 0, 0, ZoneOffset.UTC))
                        .build()))
                .build());

        mockMvc.perform(get("/api/v1/checker/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.assignments[0].assignmentId").value(10))
                .andExpect(jsonPath("$.data.assignments[0].gateIds[0]").value("A1"));
    }

    @Test
    void unauthorizedCheckerScopeReturnsStructuredResponse() throws Exception {
        when(checkerAssignmentService.getAssignmentsForCurrentChecker()).thenThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        ));

        mockMvc.perform(get("/api/v1/checker/assignments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.resultCode").value("UNAUTHORIZED_CHECKER"));
    }
}
