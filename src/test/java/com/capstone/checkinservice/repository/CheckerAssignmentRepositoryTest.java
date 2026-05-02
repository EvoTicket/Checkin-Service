package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.CheckerAssignment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class CheckerAssignmentRepositoryTest {
    @Autowired
    private CheckerAssignmentRepository repository;

    @Test
    void findActiveAssignmentsByCheckerId() {
        repository.saveAndFlush(assignment(7001L, 99L, 501L, true));
        repository.saveAndFlush(assignment(7001L, 99L, 502L, false));
        repository.saveAndFlush(assignment(7002L, 99L, 501L, true));

        assertThat(repository.findByCheckerIdAndActiveTrue(7001L))
                .extracting(CheckerAssignment::getShowtimeId)
                .containsExactly(501L);
    }

    @Test
    void findActiveAssignmentByCheckerIdEventIdAndShowtimeId() {
        repository.saveAndFlush(assignment(7001L, 99L, 501L, true));
        repository.saveAndFlush(assignment(7001L, 99L, 502L, true));
        repository.saveAndFlush(assignment(7001L, 99L, 501L, false));

        assertThat(repository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L))
                .hasSize(1)
                .extracting(CheckerAssignment::getShowtimeId)
                .containsExactly(501L);
    }

    @Test
    void inactiveAssignmentIsNotReturnedByActiveTrueMethods() {
        repository.saveAndFlush(assignment(7001L, 99L, 501L, false));

        assertThat(repository.findByCheckerIdAndActiveTrue(7001L)).isEmpty();
        assertThat(repository.findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(7001L, 99L, 501L)).isEmpty();
    }

    private CheckerAssignment assignment(Long checkerId, Long eventId, Long showtimeId, boolean active) {
        return CheckerAssignment.builder()
                .checkerId(checkerId)
                .eventId(eventId)
                .showtimeId(showtimeId)
                .allowedGateIds("[\"A1\"]")
                .roleSnapshot("CHECKER")
                .validFrom(Instant.parse("2026-05-01T08:00:00Z"))
                .validUntil(Instant.parse("2026-05-01T23:00:00Z"))
                .active(active)
                .build();
    }
}
