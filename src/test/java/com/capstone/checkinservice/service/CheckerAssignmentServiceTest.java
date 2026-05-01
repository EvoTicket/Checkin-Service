package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.response.CheckerAssignmentResponse;
import com.capstone.checkinservice.entity.CheckerAssignment;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerAssignmentRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckerAssignmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");

    @Mock
    private CheckerAssignmentRepository checkerAssignmentRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CheckerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new CheckerAssignmentService(
                checkerAssignmentRepository,
                currentUserProvider,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void getAssignmentsForCurrentChecker_returnsActiveAssignments() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerAssignmentRepository.findByCheckerIdAndActiveTrue(7001L))
                .thenReturn(List.of(assignment(true, "[\"A1\",\"A2\"]")));

        CheckerAssignmentResponse response = service.getAssignmentsForCurrentChecker();

        assertThat(response.getAssignments()).hasSize(1);
        assertThat(response.getAssignments().getFirst().getAssignmentId()).isEqualTo(10L);
        assertThat(response.getAssignments().getFirst().getGateIds()).containsExactly("A1", "A2");
    }

    @Test
    void inactiveAssignment_notReturned() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerAssignmentRepository.findByCheckerIdAndActiveTrue(7001L))
                .thenReturn(List.of(assignment(false, "[\"A1\"]")));

        assertThat(service.getAssignmentsForCurrentChecker().getAssignments()).isEmpty();
    }

    @Test
    void assignmentOutsideValidWindow_notReturned() {
        CheckerAssignment future = assignment(true, "[\"A1\"]");
        future.setValidFrom(NOW.plusSeconds(60));
        future.setValidUntil(NOW.plusSeconds(3600));
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerAssignmentRepository.findByCheckerIdAndActiveTrue(7001L)).thenReturn(List.of(future));

        assertThat(service.getAssignmentsForCurrentChecker().getAssignments()).isEmpty();
    }

    @Test
    void isCheckerAssigned_returnsTrueForMatchingEventShowtimeAndAllowedGate() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L))
                .thenReturn(List.of(assignment(true, "[\"A1\",\"A2\"]")));

        assertThat(service.isCheckerAssigned(7001L, 99L, 501L, "A2")).isTrue();
    }

    @Test
    void isCheckerAssigned_returnsTrueWhenAllowedGateIdsEmpty() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L))
                .thenReturn(List.of(assignment(true, "[]")));

        assertThat(service.isCheckerAssigned(7001L, 99L, 501L, null)).isTrue();
    }

    @Test
    void isCheckerAssigned_returnsFalseForWrongGate() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L))
                .thenReturn(List.of(assignment(true, "[\"A1\"]")));

        assertThat(service.isCheckerAssigned(7001L, 99L, 501L, "B1")).isFalse();
    }

    @Test
    void isCheckerAssigned_returnsFalseForWrongEvent() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 100L, 501L))
                .thenReturn(List.of());

        assertThat(service.isCheckerAssigned(7001L, 100L, 501L, "A1")).isFalse();
    }

    @Test
    void isCheckerAssigned_returnsFalseForWrongShowtime() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 502L))
                .thenReturn(List.of());

        assertThat(service.isCheckerAssigned(7001L, 99L, 502L, "A1")).isFalse();
    }

    @Test
    void assertCheckerAssigned_throwsUnauthorizedCheckerForInvalidScope() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L))
                .thenReturn(List.of(assignment(true, "[\"A1\"]")));

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.assertCheckerAssigned(7001L, 99L, 501L, "B1"))
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.UNAUTHORIZED_CHECKER);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void allowedGateIdsJsonParsing_handlesValidJsonArray() {
        var parsedGateIds = service.parseAllowedGateIds("[\"A1\",\"A2\"]");

        assertThat(parsedGateIds).isPresent();
        assertThat(parsedGateIds.orElseThrow()).containsExactly("A1", "A2");
    }

    @Test
    void isCheckerAssigned_returnsFalseWhenGateRequiredButMissing() {
        when(checkerAssignmentRepository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L))
                .thenReturn(List.of(assignment(true, "[\"A1\"]")));

        assertThat(service.isCheckerAssigned(7001L, 99L, 501L, null)).isFalse();
    }

    private CheckerAssignment assignment(boolean active, String allowedGateIds) {
        CheckerAssignment assignment = CheckerAssignment.builder()
                .checkerId(7001L)
                .eventId(99L)
                .showtimeId(501L)
                .allowedGateIds(allowedGateIds)
                .roleSnapshot("CHECKER")
                .validFrom(NOW.minusSeconds(60))
                .validUntil(NOW.plusSeconds(3600))
                .active(active)
                .build();
        assignment.setId(10L);
        return assignment;
    }
}
